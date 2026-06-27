/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Hardware-backed sealing of mesh key material at rest. A non-exportable
 * AES-256-GCM key is generated in the Android Keystore — in the StrongBox secure
 * element when the device has one, otherwise the TEE — and used to wrap every
 * persisted secret (the X25519 identity private key and each per-peer ratchet
 * state). A stolen flash image or cloud backup therefore yields only ciphertext;
 * the wrapping key never leaves secure hardware.
 *
 * Fail-safe: if the platform has no usable Keystore (e.g. a stripped emulator),
 * seal()/unseal() pass the value through unchanged, so the device keeps working
 * exactly as it did before this class existed. Reads transparently accept both
 * sealed ("V1:") and legacy plaintext values, so existing installs migrate on
 * their next write with no key loss.
 */
package za.co.circleos.messages;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

final class KeyVault {

    private static final String KS = "AndroidKeyStore";
    private static final String ALIAS = "circle_mesh_kek";

    private final SecretKey mKek; // null only if the platform has no usable Keystore

    KeyVault() {
        mKek = obtainKek();
    }

    /** True if a Keystore-backed wrapping key is in use (TEE or StrongBox). */
    boolean isHardwareBacked() {
        return mKek != null;
    }

    /** Seal a secret string -> "V1:...", or pass it through unchanged if no Keystore. */
    String seal(String plain) {
        if (mKek == null || plain == null) return plain;
        String sealed = SealEnvelope.sealWith(mKek, plain);
        return sealed != null ? sealed : plain; // fail-safe: never lose the value
    }

    /** Unseal a stored value; accepts both "V1:" sealed and legacy plaintext. */
    String unseal(String stored) {
        if (stored == null) return null;
        if (!SealEnvelope.isSealed(stored)) return stored; // legacy plaintext
        if (mKek == null) return null;                     // sealed but key is gone -> unreadable
        return SealEnvelope.unsealWith(mKek, stored);
    }

    private SecretKey obtainKek() {
        try {
            KeyStore ks = KeyStore.getInstance(KS);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) {
                KeyStore.Entry e = ks.getEntry(ALIAS, null);
                if (e instanceof KeyStore.SecretKeyEntry) {
                    return ((KeyStore.SecretKeyEntry) e).getSecretKey();
                }
            }
            return generateKek(true); // prefer the secure element
        } catch (Throwable t) {
            return null;
        }
    }

    private SecretKey generateKek(boolean strongBox) {
        try {
            KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256);
            if (strongBox) b.setIsStrongBoxBacked(true);
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
            kg.init(b.build());
            return kg.generateKey();
        } catch (Throwable t) {
            if (strongBox) return generateKek(false); // no secure element -> fall back to TEE
            return null;
        }
    }
}
