/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Blind-to-us E2E for Circle mail. A Circle↔Circle message is sealed on-device to
 * the recipient's published X25519 key (looked up from the mail bridge's key
 * directory), so the bridge — which fronts IMAP/SMTP — only ever relays
 * ciphertext for Circle recipients. The sealed envelope carries the sender's
 * public key, so the recipient needs nothing but its own identity to open it.
 *
 * Mail to non-Circle addresses (no published key) is sent as today — you cannot
 * force E2E on an arbitrary SMTP recipient. Standard JCA primitives, composed;
 * the envelope is the same CE1 ChaCha20-Poly1305 format used by the mesh.
 *
 * Singleton so the static {@link MailBridge} can reach it; initialise once from
 * the activity.
 */
package za.co.circleos.mail;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
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

final class MailCrypto {

    static final String PREFIX = "CE1:";

    private static final String PREFS = "mail_e2e";
    private static final String K_PRIV = "id_priv";
    private static final String K_PUB = "id_pub";
    private static final byte VERSION = 1;
    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN = 16;
    private static final byte[] INFO = "circle-mail-e2e-v1".getBytes(StandardCharsets.UTF_8);

    private static volatile MailCrypto sInstance;

    private final SharedPreferences mPrefs;
    private PrivateKey mPriv;
    private PublicKey mPub;

    private MailCrypto(Context ctx) {
        mPrefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadOrCreateIdentity();
    }

    static synchronized void init(Context ctx) {
        if (sInstance == null) sInstance = new MailCrypto(ctx);
    }

    static MailCrypto get() {
        return sInstance;
    }

    boolean isReady() {
        return mPriv != null && mPub != null;
    }

    static boolean isSealed(String s) {
        return s != null && s.startsWith(PREFIX);
    }

    /** My X25519 public key (X509 base64) — published to the directory so others can seal to me. */
    String myPublicKeyB64() {
        return mPub == null ? null : b64e(mPub.getEncoded());
    }

    /**
     * Seal {@code plaintext} to {@code recipientPubB64} (the recipient's published key).
     * The envelope embeds my public key so the recipient can derive the shared secret.
     * Returns "CE1:base64" or null on failure (caller falls back to plaintext only for
     * non-Circle recipients, never for a recipient that has a key).
     */
    String seal(String recipientPubB64, String plaintext) {
        try {
            if (!isReady()) return null;
            PublicKey peer = pub(recipientPubB64);
            byte[] key = deriveKey(peer);
            byte[] nonce = new byte[NONCE_LEN];
            new SecureRandom().nextBytes(nonce);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] spk = mPub.getEncoded();
            ByteBuffer buf = ByteBuffer.allocate(1 + 2 + spk.length + NONCE_LEN + ct.length);
            buf.put(VERSION).putShort((short) spk.length).put(spk).put(nonce).put(ct);
            Arrays.fill(key, (byte) 0);
            return PREFIX + b64e(buf.array());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Open a "CE1:" mail body addressed to me — derives the key from the embedded sender pubkey. */
    String open(String sealedBody) {
        try {
            if (!isReady()) return null;
            byte[] env = b64d(sealedBody.substring(PREFIX.length()));
            ByteBuffer buf = ByteBuffer.wrap(env);
            if (buf.get() != VERSION) return null;
            int spkLen = buf.getShort() & 0xFFFF;
            if (spkLen <= 0 || spkLen > buf.remaining() - NONCE_LEN - TAG_LEN) return null;
            byte[] spk = new byte[spkLen];
            buf.get(spk);
            byte[] nonce = new byte[NONCE_LEN];
            buf.get(nonce);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            byte[] key = deriveKey(pub(b64e(spk)));
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            byte[] pt = c.doFinal(ct);
            Arrays.fill(key, (byte) 0);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    // ── internals ──

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
            mPriv = null;
            mPub = null; // XDH unavailable -> fail closed (no sealing; plaintext path only)
        }
    }

    private static PublicKey pub(String b64) throws Exception {
        return KeyFactory.getInstance("XDH").generatePublic(new X509EncodedKeySpec(b64d(b64)));
    }

    private byte[] deriveKey(PublicKey peer) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("XDH");
        ka.init(mPriv);
        ka.doPhase(peer, true);
        byte[] shared = ka.generateSecret();
        byte[] key = hkdf(shared, INFO, 32);
        Arrays.fill(shared, (byte) 0);
        return key;
    }

    private static byte[] hkdf(byte[] ikm, byte[] info, int len) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[mac.getMacLength()], "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0, counter = 1;
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
