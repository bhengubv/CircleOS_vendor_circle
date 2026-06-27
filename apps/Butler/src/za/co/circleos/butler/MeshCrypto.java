/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Blind-to-us E2E for Circle mesh messages. The mesh layer ships an opaque
 * byte[] payload and delivers it as a String, so messages are encrypted and
 * wrapped in a Base64 ASCII envelope: the mesh, every relay node, and the
 * operator only ever see "CE1:<ciphertext>" or "CE2:<ciphertext>".
 *
 * Two layers:
 *   CE1 — static X25519 identity ECDH -> HKDF -> ChaCha20-Poly1305. The
 *         bootstrap floor and back-compat path. No forward secrecy on its own.
 *   CE2 — Double Ratchet ({@link DoubleRatchet}) seeded from the same identity
 *         ECDH. Forward secrecy + break-in recovery: every message gets a fresh
 *         key that is deleted after use, and a DH ratchet step is folded in each
 *         round-trip. This is the default the moment a sending chain exists.
 *
 * Roles are assigned deterministically (lexicographic identity-key compare), so
 * two peers that send at the same moment never desync. The responder cannot
 * ratchet-send until it has received once, so its very first message (only) may
 * go out as CE1; everything after is CE2.
 *
 * Trust-on-first-use key exchange. Fail-closed: if XDH is unavailable or no peer
 * key is known, encrypt() returns null and the caller must NOT fall back to
 * plaintext. Fingerprint verification is via safetyNumber().
 */
package za.co.circleos.butler;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.util.Locale;

public final class MeshCrypto {

    public static final String PREFIX_ENC = "CE1:";  // static-key encrypted message
    public static final String PREFIX_KEX = "CKX:";  // key-exchange handshake
    // CE2 (DoubleRatchet.PREFIX) is the forward-secret envelope.

    public static final int KEY_UNCHANGED = 0;
    public static final int KEY_NEW = 1;
    public static final int KEY_CHANGED = 2;          // suspicious — possible MITM

    private static final String PREFS = "mesh_e2e";
    private static final String K_PRIV = "id_priv";
    private static final String K_PUB = "id_pub";
    private static final String PEER_PREFIX = "peer_";
    private static final String RAT_PREFIX = "rat_";   // per-peer Double Ratchet state
    private static final byte VERSION = 1;
    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN = 16;
    private static final byte[] INFO = "circle-mesh-e2e-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO_RATCHET = "circle-mesh-ratchet-seed".getBytes(StandardCharsets.UTF_8);

    /** Guards the load-mutate-save of ratchet state (Activity + receiver share this process). */
    private static final Object RATCHET_LOCK = new Object();

    private final SharedPreferences mPrefs;
    private final KeyVault mVault;
    private PrivateKey mPriv;
    private PublicKey mPub;

    public MeshCrypto(Context ctx) {
        mPrefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mVault = new KeyVault();   // TEE/StrongBox sealing of persisted key material
        loadOrCreateIdentity();
    }

    /** True only if the platform supports X25519 and we hold an identity. */
    public boolean isReady() {
        return mPriv != null && mPub != null;
    }

    public static boolean isKeyExchange(String s) {
        return s != null && s.startsWith(PREFIX_KEX);
    }

    /** True for either encrypted envelope — static (CE1) or forward-secret (CE2). */
    public static boolean isEncrypted(String s) {
        return s != null && (s.startsWith(PREFIX_ENC) || s.startsWith(DoubleRatchet.PREFIX));
    }

    /** The "CKX:" message announcing my public key, or null if E2E isn't available. */
    public String keyExchangeMessage() {
        if (mPub == null) return null;
        return PREFIX_KEX + b64e(mPub.getEncoded());
    }

    public boolean hasPeerKey(String peerId) {
        return mPrefs.getString(PEER_PREFIX + peerId, null) != null;
    }

    /** Store a peer's key from a received "CKX:". Returns true if it was new or changed. */
    public boolean storePeerKey(String peerId, String kexMessage) {
        return storePeerKeyStatus(peerId, kexMessage) != KEY_UNCHANGED;
    }

    /**
     * Store a peer's key and report whether it was {@link #KEY_NEW}, {@link #KEY_UNCHANGED},
     * or {@link #KEY_CHANGED}. A changed key is accepted (TOFU) but flagged for re-verification —
     * the caller should warn the user (possible MITM). A changed key also resets the ratchet so a
     * fresh session is negotiated against the new identity.
     */
    public int storePeerKeyStatus(String peerId, String kexMessage) {
        try {
            byte[] x509 = b64d(kexMessage.substring(PREFIX_KEX.length()));
            KeyFactory.getInstance("XDH").generatePublic(new X509EncodedKeySpec(x509)); // validate
            String now = b64e(x509);
            String existing = mPrefs.getString(PEER_PREFIX + peerId, null);
            if (existing == null) {
                mPrefs.edit().putString(PEER_PREFIX + peerId, now).apply();
                return KEY_NEW;
            }
            if (now.equals(existing)) return KEY_UNCHANGED;
            mPrefs.edit().putString(PEER_PREFIX + peerId, now)
                    .putBoolean("changed_" + peerId, true)
                    .remove(RAT_PREFIX + peerId)   // drop the old ratchet; renegotiate vs new key
                    .apply();
            return KEY_CHANGED;
        } catch (Throwable t) {
            return KEY_UNCHANGED;
        }
    }

    /** Returns true (and clears the flag) if this peer's key changed since last verified. */
    public boolean consumeKeyChanged(String peerId) {
        if (!mPrefs.getBoolean("changed_" + peerId, false)) return false;
        mPrefs.edit().remove("changed_" + peerId).apply();
        return true;
    }

    /**
     * A stable 60-digit security code for the pair — identical on both devices. The two users
     * compare it out-of-band; if it matches, there is no man-in-the-middle. Returns null until a
     * peer key is known.
     */
    public String safetyNumber(String peerId) {
        try {
            if (mPub == null) return null;
            String pb = mPrefs.getString(PEER_PREFIX + peerId, null);
            if (pb == null) return null;
            byte[] mine = mPub.getEncoded();
            byte[] theirs = b64d(pb);
            byte[] first, second;
            if (lexCompare(mine, theirs) <= 0) { first = mine; second = theirs; }
            else { first = theirs; second = mine; }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(first);
            md.update(second);
            byte[] h = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int v = (((h[i * 2] & 0xFF) << 8) | (h[i * 2 + 1] & 0xFF)) % 100000;
                sb.append(String.format(Locale.US, "%05d", v));
                sb.append((i % 4 == 3) ? "\n" : "  ");
            }
            return sb.toString().trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int lexCompare(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int d = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (d != 0) return d;
        }
        return a.length - b.length;
    }

    /**
     * Encrypt {@code text} to {@code peerId}. Uses the forward-secret ratchet (CE2) once a sending
     * chain exists, otherwise the static envelope (CE1). Returns null (no key / not ready); the
     * caller must NOT fall back to plaintext.
     */
    public String encrypt(String peerId, String text) {
        if (!isReady() || !hasPeerKey(peerId)) return null;
        synchronized (RATCHET_LOCK) {
            try {
                DoubleRatchet.State st = ensureRatchet(peerId);
                if (DoubleRatchet.canSend(st)) {
                    String ce2 = DoubleRatchet.encrypt(st, text);
                    saveRatchet(peerId, st);
                    return ce2;
                }
            } catch (Throwable t) {
                // fall through to the static floor
            }
        }
        return encryptStatic(peerId, text);
    }

    /** Decrypt a "CE1:"/"CE2:" message from {@code peerId} -> text, or null on any failure. */
    public String decrypt(String peerId, String body) {
        if (DoubleRatchet.isRatchet(body)) {
            synchronized (RATCHET_LOCK) {
                try {
                    DoubleRatchet.State st = ensureRatchet(peerId);
                    if (st == null) return null;
                    String pt = DoubleRatchet.decrypt(st, body);
                    saveRatchet(peerId, st);
                    return pt;
                } catch (Throwable t) {
                    return null;
                }
            }
        }
        return decryptStatic(peerId, body);
    }

    /* ── static (CE1) layer ── */

    private String encryptStatic(String peerId, String text) {
        try {
            if (!isReady()) return null;
            PublicKey peer = peerKey(peerId);
            if (peer == null) return null;
            byte[] key = deriveKey(peer);
            byte[] nonce = new byte[NONCE_LEN];
            new SecureRandom().nextBytes(nonce);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            byte[] ct = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(1 + NONCE_LEN + ct.length);
            buf.put(VERSION).put(nonce).put(ct);
            Arrays.fill(key, (byte) 0);
            return PREFIX_ENC + b64e(buf.array());
        } catch (Throwable t) {
            return null;
        }
    }

    private String decryptStatic(String peerId, String body) {
        try {
            if (!isReady() || !body.startsWith(PREFIX_ENC)) return null;
            PublicKey peer = peerKey(peerId);
            if (peer == null) return null;
            byte[] env = b64d(body.substring(PREFIX_ENC.length()));
            if (env.length < 1 + NONCE_LEN + TAG_LEN || env[0] != VERSION) return null;
            byte[] nonce = Arrays.copyOfRange(env, 1, 1 + NONCE_LEN);
            byte[] ct = Arrays.copyOfRange(env, 1 + NONCE_LEN, env.length);
            byte[] key = deriveKey(peer);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            byte[] pt = c.doFinal(ct);
            Arrays.fill(key, (byte) 0);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /* ── ratchet (CE2) session management ── */

    /** Load the peer's ratchet, or bootstrap one from the identity ECDH if the peer key is known. */
    private DoubleRatchet.State ensureRatchet(String peerId) throws Exception {
        String packed = mVault.unseal(mPrefs.getString(RAT_PREFIX + peerId, null));
        if (packed != null) {
            try {
                return DoubleRatchet.unpack(b64d(packed));
            } catch (Throwable t) {
                mPrefs.edit().remove(RAT_PREFIX + peerId).apply(); // corrupt -> rebuild
            }
        }
        if (!isReady()) return null;
        PublicKey peer = peerKey(peerId);
        if (peer == null) return null;
        byte[] seed = seedSecret(peer);
        KeyPair selfId = new KeyPair(mPub, mPriv);
        DoubleRatchet.State st = DoubleRatchet.init(seed, mPub, selfId, peer);
        saveRatchet(peerId, st);
        return st;
    }

    private void saveRatchet(String peerId, DoubleRatchet.State st) {
        try {
            mPrefs.edit().putString(RAT_PREFIX + peerId, mVault.seal(b64e(DoubleRatchet.pack(st)))).apply();
        } catch (Throwable t) {
            // best-effort; a lost save just means a fresh session next time
        }
    }

    /** Initial shared secret for the ratchet — identity ECDH under a distinct info label. */
    private byte[] seedSecret(PublicKey peer) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("XDH");
        ka.init(mPriv);
        ka.doPhase(peer, true);
        byte[] shared = ka.generateSecret();
        byte[] seed = hkdfSha256(shared, null, INFO_RATCHET, 32);
        Arrays.fill(shared, (byte) 0);
        return seed;
    }

    /* ── identity + static internals ── */

    private void loadOrCreateIdentity() {
        try {
            KeyFactory kf = KeyFactory.getInstance("XDH");
            String privB64 = mVault.unseal(mPrefs.getString(K_PRIV, null)); // sealed at rest
            String pubB64 = mPrefs.getString(K_PUB, null);                  // public: not secret
            if (privB64 != null && pubB64 != null) {
                mPriv = kf.generatePrivate(new PKCS8EncodedKeySpec(b64d(privB64)));
                mPub = kf.generatePublic(new X509EncodedKeySpec(b64d(pubB64)));
                return;
            }
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("XDH");
            kpg.initialize(NamedParameterSpec.X25519);
            KeyPair kp = kpg.generateKeyPair();
            mPriv = kp.getPrivate();
            mPub = kp.getPublic();
            mPrefs.edit()
                    .putString(K_PRIV, mVault.seal(b64e(mPriv.getEncoded())))
                    .putString(K_PUB, b64e(mPub.getEncoded()))
                    .apply();
        } catch (Throwable t) {
            // XDH unavailable on this platform -> E2E disabled, fail closed.
            mPriv = null;
            mPub = null;
        }
    }

    private PublicKey peerKey(String peerId) throws Exception {
        String b = mPrefs.getString(PEER_PREFIX + peerId, null);
        if (b == null) return null;
        return KeyFactory.getInstance("XDH").generatePublic(new X509EncodedKeySpec(b64d(b)));
    }

    private byte[] deriveKey(PublicKey peer) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("XDH");
        ka.init(mPriv);
        ka.doPhase(peer, true);
        byte[] shared = ka.generateSecret();
        byte[] key = hkdfSha256(shared, null, INFO, 32);
        Arrays.fill(shared, (byte) 0);
        return key;
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int len) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        if (salt == null) salt = new byte[mac.getMacLength()];
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);                     // HKDF-Extract
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));    // HKDF-Expand
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0;
        int counter = 1;
        while (pos < len) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update((byte) counter);
            t = mac.doFinal();
            int n = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n;
            counter++;
        }
        return out;
    }

    private static String b64e(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    private static byte[] b64d(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
