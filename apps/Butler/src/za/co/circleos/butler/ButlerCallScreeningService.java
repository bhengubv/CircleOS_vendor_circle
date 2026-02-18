/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

/**
 * AI-powered call screening service for Butler.
 *
 * When an incoming call arrives, Butler queries CircleOS Inference Service
 * with the caller's number and display name. The model responds with one of:
 *   ALLOW   — pass the call through to the user
 *   SCREEN  — send to voicemail and show notification
 *   REJECT  — silently reject
 *
 * The system binds to this service via the BIND_SCREENING_SERVICE permission.
 * Only one default call screening app is active at a time; Butler requests
 * this role via RoleManager from the setup wizard.
 *
 * Verdict is determined by keyword scan of the LLM response:
 *   "reject"  → REJECT
 *   "screen"  → SCREEN (send to voicemail, notify user)
 *   default   → ALLOW
 *
 * System prompt is kept short to minimise latency on first boot before
 * a large model is loaded; falls back to ALLOW if inference times out.
 */
public class ButlerCallScreeningService extends CallScreeningService {

    private static final String TAG = "Butler.CallScreen";

    private static final String SYSTEM_PROMPT =
            "You are a call screening assistant. You receive an incoming call's caller ID "
          + "and display name. Reply with exactly one word: ALLOW, SCREEN, or REJECT. "
          + "ALLOW: known contact or likely legitimate call. "
          + "SCREEN: unknown number, possible spam. "
          + "REJECT: known scam/spam number or silent call.";

    @Override
    public void onScreenCall(Call.Details callDetails) {
        String number  = safeString(callDetails.getHandle() != null
                ? callDetails.getHandle().getSchemeSpecificPart() : "unknown");
        String display = safeString(callDetails.getCallerDisplayName());

        Log.i(TAG, "Screening call from: " + number + " (" + display + ")");

        // Connect to inference service and ask for verdict
        InferenceServiceConnection conn = new InferenceServiceConnection();
        if (!conn.connect() || conn.getLoadedModelId() == null) {
            // Inference unavailable — allow the call
            Log.w(TAG, "Inference not available; allowing call from " + number);
            respondToCall(callDetails, new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .build());
            return;
        }

        String prompt = "Incoming call:\nNumber: " + number + "\nDisplay name: " + display;

        conn.generate(prompt, SYSTEM_PROMPT, new InferenceServiceConnection.GenerateCallback() {
            @Override
            public void onToken(String tokenText) { /* accumulate in onComplete */ }

            @Override
            public void onComplete(String fullText, long latencyMs) {
                CallResponse response = buildResponse(callDetails, fullText.trim());
                Log.i(TAG, "Call verdict for " + number + ": " + fullText.trim()
                        + " (latency=" + latencyMs + "ms)");
                respondToCall(callDetails, response);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Inference error during call screening: " + message + "; allowing");
                respondToCall(callDetails, new CallResponse.Builder()
                        .setDisallowCall(false)
                        .setRejectCall(false)
                        .setSilenceCall(false)
                        .build());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CallResponse buildResponse(Call.Details details, String verdict) {
        String v = verdict.toUpperCase();
        if (v.contains("REJECT")) {
            return new CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
        } else if (v.contains("SCREEN")) {
            // Send to voicemail silently; user sees notification
            return new CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(false)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
        } else {
            // ALLOW (default)
            return new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .build();
        }
    }

    private static String safeString(String s) {
        return s != null ? s : "";
    }
}
