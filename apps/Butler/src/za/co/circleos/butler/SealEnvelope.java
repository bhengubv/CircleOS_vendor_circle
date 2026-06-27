/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * AES-256-GCM seal/unseal envelope used to wrap mesh key material at rest. The
 * SecretKey is supplied by the caller — on device it is a non-exportable
 * Android Keystore key ({@link KeyVault}); in tests it is an ordinary software
 * key — so this class is pure JDK (no Android imports) and unit-testable.
 *
 * Wire: "V1:" + base64([ivLen u8][iv][ciphertext+tag]). On encrypt the IV is
 * chosen by the provider (Keystore requires this for AES-GCM) and read back via
 * Cipher.getIV(); on decrypt it is supplied from the stored envelope.
 */
package za.co.circleos.butler;

import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SealEnvelope {

    static final String PREFIX = "V1:";
    private static final int TAG_BITS = 128;

    private SealEnvelope() {}

    static boolean isSealed(String s) {
        return s != null && s.startsWith(PREFIX);
    }

    /** Seal {@code plain} under {@code key} -> "V1:base64", or null on failure. */
    static String sealWith(SecretKey key, String plain) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);          // provider/Keystore generates the IV
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
            byte[] out = new byte[1 + iv.length + ct.length];
            out[0] = (byte) iv.length;
            System.arraycopy(iv, 0, out, 1, iv.length);
            System.arraycopy(ct, 0, out, 1 + iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Unseal a "V1:" value under {@code key} -> the original string, or null on any failure. */
    static String unsealWith(SecretKey key, String stored) {
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            int ivLen = raw[0] & 0xFF;
            if (ivLen <= 0 || ivLen > raw.length - 1) return null;
            byte[] iv = Arrays.copyOfRange(raw, 1, 1 + ivLen);
            byte[] ct = Arrays.copyOfRange(raw, 1 + ivLen, raw.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }
}
