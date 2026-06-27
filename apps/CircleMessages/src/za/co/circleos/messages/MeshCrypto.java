/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Blind-to-us E2E for Circle mesh messages. The mesh layer ships an opaque
 * byte[] payload and delivers it as a String, so messages are encrypted and
 * wrapped in a Base64 ASCII envelope: the mesh, every relay node, and the
 * operator only ever see "CE1:<ciphertext>".
 *
 * Construction: per-device X25519 identity (persisted app-private) -> ECDH with
 * the peer -> HKDF-SHA256 -> ChaCha20-Poly1305 AEAD. Standard JCA/Conscrypt
 * primitives, composed; nothing rolls its own primitive. Trust-on-first-use key
 * exchange. Forward secrecy (Double Ratchet) + fingerprint verification are
 * follow-ups; this is the blind-to-us floor.
 *
 * Fail-closed: if XDH is unavailable or no peer key is known, encrypt() returns
 * null and the caller must NOT fall back to plaintext.
 */
package za.co.circleos.messages;

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

    public static final String PREFIX_ENC = "CE1:";  // encrypted message
    public static final String PREFIX_KEX = "CKX:";  // key-exchange handshake

    public static final int KEY_UNCHANGED = 0;
    public static final int KEY_NEW = 1;
    public static final int KEY_CHANGED = 2;          // suspicious — possible MITM

    private static final String PREFS = "mesh_e2e";
    private static final String K_PRIV = "id_priv";
    private static final String K_PUB = "id_pub";
    private static final String PEER_PREFIX = "peer_";
    private static final byte VERSION = 1;
    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN = 16;
    private static final byte[] INFO = "circle-mesh-e2e-v1".getBytes(StandardCharsets.UTF_8);

    private final SharedPreferences mPrefs;
    private PrivateKey mPriv;
    private PublicKey mPub;

    public MeshCrypto(Context ctx) {
        mPrefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadOrCreateIdentity();
    }

    /** True only if the platform supports X25519 and we hold an identity. */
    public boolean isReady() {
        return mPriv != null && mPub != null;
    }

    public static boolean isKeyExchange(String s) {
        return s != null && s.startsWith(PREFIX_KEX);
    }

    public static boolean isEncrypted(String s) {
        return s != null && s.startsWith(PREFIX_ENC);
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
     * the caller should warn the user (possible MITM).
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
                    .putBoolean("changed_" + peerId, true).apply();
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

    /** Encrypt {@code text} to {@code peerId} -> "CE1:base64", or null (no key / not ready). */
    public String encrypt(String peerId, String text) {
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

    /** Decrypt a "CE1:" message from {@code peerId} -> text, or null on any failure. */
    public String decrypt(String peerId, String body) {
        try {
            if (!isReady()) return null;
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

    /* ── internals ── */

    private void loadOrCreateIdentity() {
        try {
            KeyFactory kf = KeyFactory.getInstance("XDH");
            String privB64 = mPrefs.getString(K_PRIV, null);
            String pubB64 = mPrefs.getString(K_PUB, null);
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
                    .putString(K_PRIV, b64e(mPriv.getEncoded()))
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
