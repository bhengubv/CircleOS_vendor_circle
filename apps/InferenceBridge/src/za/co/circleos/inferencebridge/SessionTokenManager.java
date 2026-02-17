/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.inferencebridge;

import android.util.Log;

import java.security.SecureRandom;
import java.util.Formatter;

/**
 * Issues and validates the single shared session token for the HTTP bridge.
 *
 * The token is generated once at service start from a SecureRandom source
 * and lives only in memory — it is never written to disk.  Any caller that
 * can read it from the ContentProvider (i.e. holds ACCESS_INFERENCE) is
 * authorised to use the HTTP API.
 */
public class SessionTokenManager {

    private static final String TAG = "InferenceBridge.Token";
    private static final int TOKEN_BYTES = 16; // 128-bit

    private final String mToken;

    public SessionTokenManager() {
        SecureRandom rng = new SecureRandom();
        byte[] bytes = new byte[TOKEN_BYTES];
        rng.nextBytes(bytes);
        mToken = toHex(bytes);
        Log.i(TAG, "Session token initialised (length=" + mToken.length() + ")");
    }

    /** Returns the current session token (hex string). */
    public String getToken() {
        return mToken;
    }

    /** Returns true if the supplied value matches the session token. */
    public boolean validate(String candidate) {
        if (candidate == null || candidate.length() != mToken.length()) return false;
        // Constant-time comparison to prevent timing attacks.
        int diff = 0;
        for (int i = 0; i < mToken.length(); i++) {
            diff |= mToken.charAt(i) ^ candidate.charAt(i);
        }
        return diff == 0;
    }

    private static String toHex(byte[] bytes) {
        Formatter f = new Formatter();
        for (byte b : bytes) f.format("%02x", b);
        String result = f.toString();
        f.close();
        return result;
    }
}
