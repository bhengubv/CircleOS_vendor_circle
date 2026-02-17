/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.inference.client;

/**
 * Simplified callback for streaming inference from CircleInferenceClient.
 *
 * Implement this instead of IInferenceCallback to avoid AIDL boilerplate.
 * All methods are called on a background Binder thread — post to main
 * thread if updating UI.
 */
public interface StreamingCallback {
    /** Called for each generated token. */
    void onToken(String text);

    /** Called when generation finishes. fullText is the complete output. */
    void onComplete(String fullText, long latencyMs, int totalTokens);

    /** Called on any error. The request has been aborted. */
    void onError(int errorCode, String message, boolean recoverable);
}
