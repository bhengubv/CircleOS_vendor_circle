/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Blind-to-us E2E for Circle Rooms. A room is shared by a secret code that only
 * its members know; that same code is the encryption secret. Message bodies are
 * sealed on-device, so the rooms bridge — and every relay between — only ever
 * stores "CE1:<ciphertext>", never the text and never the author.
 *
 * Key = PBKDF2WithHmacSHA256(code, salt, 120k iters) -> ChaCha20-Poly1305 AEAD.
 * PBKDF2 makes a low-entropy room code expensive to guess offline for whoever
 * holds the stored ciphertext (i.e. us). Standard JCA primitives, composed; the
 * envelope is byte-compatible with the mesh "CE1:" format. Fail-closed: seal()
 * returns null rather than ever exposing plaintext, and the caller must not post
 * a message it could not seal.
 */
package za.co.circleos.rooms;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class RoomCrypto {

    static final String PREFIX = "CE1:";

    private static final byte VERSION = 1;
    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN = 16;
    private static final int ITERS = 120000;
    private static final byte[] SALT = "circle-rooms-e2e-v1".getBytes(StandardCharsets.UTF_8);

    /** Derived room keys are cached per process so PBKDF2 runs once per code, not per message. */
    private static final ConcurrentHashMap<String, byte[]> KEYS = new ConcurrentHashMap<>();

    private RoomCrypto() {}

    static boolean isSealed(String s) {
        return s != null && s.startsWith(PREFIX);
    }

    /** Seal {@code plaintext} for room {@code code} -> "CE1:base64", or null on failure. */
    static String seal(String code, String plaintext) {
        try {
            byte[] key = keyFor(code);
            byte[] nonce = new byte[NONCE_LEN];
            new SecureRandom().nextBytes(nonce);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(1 + NONCE_LEN + ct.length);
            buf.put(VERSION).put(nonce).put(ct);
            return PREFIX + Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Open a "CE1:" body for room {@code code} -> plaintext, or null on any failure. */
    static String open(String code, String body) {
        try {
            byte[] env = Base64.decode(body.substring(PREFIX.length()), Base64.NO_WRAP);
            if (env.length < 1 + NONCE_LEN + TAG_LEN || env[0] != VERSION) return null;
            byte[] nonce = Arrays.copyOfRange(env, 1, 1 + NONCE_LEN);
            byte[] ct = Arrays.copyOfRange(env, 1 + NONCE_LEN, env.length);
            byte[] key = keyFor(code);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] keyFor(String code) throws Exception {
        byte[] cached = KEYS.get(code);
        if (cached != null) return cached;
        KeySpec spec = new PBEKeySpec(code.toCharArray(), SALT, ITERS, 256);
        byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        KEYS.put(code, k);
        return k;
    }
}
