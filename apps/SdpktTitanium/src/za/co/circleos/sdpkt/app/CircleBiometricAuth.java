/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.sdpkt.app;

import android.app.Activity;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.util.Log;
import android.widget.Toast;

/**
 * CircleBiometricAuth — the native "PhonePin + Biometrics" gate.
 *
 * Requires a strong biometric (fingerprint / face) OR the device credential
 * (PIN / pattern / password) before a sensitive action runs. Used to authorise
 * wallet money operations (send, accept transfer). Fail-closed: if the user
 * cancels, errors, or has nothing enrolled, the protected action does NOT run —
 * money never moves without an explicit, fresh authorisation.
 */
final class CircleBiometricAuth {

    private static final String TAG = "CircleBiometricAuth";

    /** Run after a successful biometric / device-credential authentication. */
    interface Result { void onAuthenticated(); }

    private CircleBiometricAuth() {}

    static void require(Activity activity, String title, String subtitle, Result onOk) {
        final int authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        try {
            final BiometricManager bm = activity.getSystemService(BiometricManager.class);
            if (bm == null || bm.canAuthenticate(authenticators)
                    != BiometricManager.BIOMETRIC_SUCCESS) {
                // Nothing enrolled (no biometric AND no PIN) -> block the action.
                Log.w(TAG, "no biometric / device credential available -- blocking sensitive action");
                Toast.makeText(activity,
                        "Set a screen lock or fingerprint to authorise payments",
                        Toast.LENGTH_LONG).show();
                return;
            }
            final BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build();
            prompt.authenticate(new CancellationSignal(), activity.getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            onOk.onAuthenticated();
                        }
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            Log.i(TAG, "biometric auth error " + errorCode + ": " + errString);
                            // fail-closed: action not run
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "biometric prompt failed -- blocking sensitive action", t);
        }
    }
}
