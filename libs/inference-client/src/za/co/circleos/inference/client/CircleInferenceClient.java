/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.inference.client;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.inference.DeviceCapabilities;
import za.co.circleos.inference.ICircleInference;
import za.co.circleos.inference.IInferenceCallback;
import za.co.circleos.inference.InferenceError;
import za.co.circleos.inference.InferenceRequest;
import za.co.circleos.inference.InferenceResponse;
import za.co.circleos.inference.ModelInfo;
import za.co.circleos.inference.ResourceMetrics;
import za.co.circleos.inference.Token;

import java.util.List;

/**
 * High-level client for the CircleOS on-device inference service.
 *
 * Hides AIDL binding boilerplate and provides a straightforward API:
 *
 * <pre>
 *   CircleInferenceClient client = new CircleInferenceClient();
 *   if (client.connect()) {
 *       client.generate("Explain quantum computing simply", new StreamingCallback() {
 *           public void onToken(String text) { append(text); }
 *           public void onComplete(String full, long ms, int tokens) { done(); }
 *           public void onError(int code, String msg, boolean retry) { showError(msg); }
 *       });
 *   }
 * </pre>
 *
 * Required permission: com.circleos.permission.ACCESS_INFERENCE
 */
public class CircleInferenceClient {

    private static final String TAG = "CircleInferenceClient";
    private static final String SERVICE_NAME = "circle.inference";

    private ICircleInference mService;

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Acquires the inference service Binder. Call once before any other method.
     * May be called on any thread.
     *
     * @return true if the service is available; false otherwise.
     */
    public boolean connect() {
        IBinder binder = ServiceManager.getService(SERVICE_NAME);
        if (binder == null) {
            Log.e(TAG, "circle.inference not available");
            return false;
        }
        mService = ICircleInference.Stub.asInterface(binder);
        return true;
    }

    /** Returns true if connect() has succeeded and the service is reachable. */
    public boolean isConnected() { return mService != null; }

    // ── Capability ────────────────────────────────────────────────────────────

    /**
     * Returns device capabilities including tier (1–5) and available backends.
     * Returns null if not connected.
     */
    public DeviceCapabilities getDeviceCapabilities() {
        if (!isConnected()) return null;
        try { return mService.getDeviceCapabilities(); }
        catch (RemoteException e) { Log.e(TAG, "getDeviceCapabilities", e); return null; }
    }

    /** Returns the service protocol version. */
    public int getServiceVersion() {
        if (!isConnected()) return -1;
        try { return mService.getServiceVersion(); }
        catch (RemoteException e) { return -1; }
    }

    // ── Model management ──────────────────────────────────────────────────────

    /** Returns all models available on device (bundled + downloaded). */
    public List<ModelInfo> listModels() {
        if (!isConnected()) return null;
        try { return mService.listModels(); }
        catch (RemoteException e) { Log.e(TAG, "listModels", e); return null; }
    }

    /** Returns the ID of the currently loaded model, or null if none. */
    public String getLoadedModelId() {
        if (!isConnected()) return null;
        try { return mService.getLoadedModelId(); }
        catch (RemoteException e) { return null; }
    }

    /** Returns live resource usage metrics. */
    public ResourceMetrics getResourceMetrics() {
        if (!isConnected()) return null;
        try { return mService.getResourceMetrics(); }
        catch (RemoteException e) { return null; }
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    /**
     * Streaming inference with simplified callback.
     *
     * The callback is invoked on the Binder thread pool — not the main thread.
     * Post to a Handler if you need to update UI from the callback.
     *
     * @param prompt   The user prompt.
     * @param callback Receives token stream, completion, and errors.
     */
    public void generate(String prompt, StreamingCallback callback) {
        generate(prompt, null, 512, 0.7f, callback);
    }

    /**
     * Streaming inference with full parameter control.
     *
     * @param prompt       User prompt.
     * @param systemPrompt Optional system prompt (persona / instructions).
     * @param maxTokens    Maximum tokens to generate.
     * @param temperature  Sampling temperature (0.0 = deterministic, 1.0 = creative).
     * @param callback     Receives token stream, completion, and errors.
     */
    public void generate(String prompt, String systemPrompt,
                         int maxTokens, float temperature,
                         StreamingCallback callback) {
        if (!isConnected()) {
            callback.onError(InferenceError.ERROR_PERMISSION_DENIED,
                    "Not connected to inference service", false);
            return;
        }

        InferenceRequest req = new InferenceRequest();
        req.prompt       = prompt;
        req.systemPrompt = systemPrompt;
        req.maxTokens    = maxTokens;
        req.temperature  = temperature;
        req.topP         = 0.9f;

        try {
            mService.generateStream(req, new IInferenceCallback.Stub() {
                private final StringBuilder mAccumulator = new StringBuilder();

                @Override
                public void onToken(Token token) {
                    mAccumulator.append(token.text);
                    callback.onToken(token.text);
                }

                @Override
                public void onComplete(InferenceResponse response) {
                    callback.onComplete(
                            mAccumulator.toString(),
                            response.latencyMs,
                            response.completionTokens);
                }

                @Override
                public void onError(InferenceError error) {
                    callback.onError(error.code, error.message, error.recoverable);
                }

                @Override
                public void onModelLoaded(String modelId) {}
            });
        } catch (RemoteException e) {
            Log.e(TAG, "generateStream failed", e);
            callback.onError(InferenceError.ERROR_INTERNAL,
                    "IPC failure: " + e.getMessage(), true);
        }
    }

    /**
     * Synchronous inference. Blocks the calling thread until complete.
     * Do NOT call on the main thread.
     *
     * @return The generated text, or null on error.
     */
    public String generateSync(String prompt) {
        if (!isConnected()) return null;
        InferenceRequest req = new InferenceRequest();
        req.prompt      = prompt;
        req.maxTokens   = 512;
        req.temperature = 0.7f;
        try {
            InferenceResponse r = mService.generate(req);
            return r != null ? r.text : null;
        } catch (RemoteException e) {
            Log.e(TAG, "generate failed", e);
            return null;
        }
    }

    /** Cancels any in-progress generation from this client's UID. */
    public void cancel() {
        if (!isConnected()) return;
        try { mService.cancelGeneration(); }
        catch (RemoteException e) { Log.w(TAG, "cancel failed", e); }
    }
}
