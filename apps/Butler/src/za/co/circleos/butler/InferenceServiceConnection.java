/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.inference.ICircleInference;
import za.co.circleos.inference.IInferenceCallback;
import za.co.circleos.inference.InferenceError;
import za.co.circleos.inference.InferenceRequest;
import za.co.circleos.inference.InferenceResponse;
import za.co.circleos.inference.Token;

/**
 * Manages the connection to the CircleOS Inference Service.
 *
 * Acquires the Binder from ServiceManager (system service — no binding needed)
 * and exposes a simple async generate() API with a callback.
 */
public class InferenceServiceConnection {

    private static final String TAG = "Butler.InfConn";
    private static final String SERVICE_NAME = "circle.inference";

    public interface GenerateCallback {
        void onToken(String tokenText);
        void onComplete(String fullText, long latencyMs);
        void onError(String message);
    }

    public interface ReadyCallback {
        void onReady(String modelId);
        void onError(String message);
    }

    private ICircleInference mService;
    private volatile boolean mConnected = false;

    public boolean connect() {
        IBinder binder = ServiceManager.getService(SERVICE_NAME);
        if (binder == null) {
            Log.e(TAG, "circle.inference service not found");
            return false;
        }
        mService = ICircleInference.Stub.asInterface(binder);
        mConnected = true;
        Log.i(TAG, "Connected to circle.inference");
        return true;
    }

    public boolean isConnected() { return mConnected && mService != null; }

    public String getLoadedModelId() {
        if (!isConnected()) return null;
        try { return mService.getLoadedModelId(); }
        catch (RemoteException e) { Log.e(TAG, "getLoadedModelId failed", e); return null; }
    }

    public void loadOptimalModel(ReadyCallback cb) {
        if (!isConnected()) { cb.onError("Not connected to inference service"); return; }
        try {
            mService.loadModel(null, new IInferenceCallback.Stub() {
                @Override public void onToken(Token t) {}
                @Override public void onComplete(InferenceResponse r) {}
                @Override public void onError(InferenceError e) {
                    cb.onError(e.message);
                }
                @Override public void onModelLoaded(String modelId) {
                    cb.onReady(modelId);
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "loadModel failed", e);
            cb.onError(e.getMessage());
        }
    }

    public void generate(String prompt, String systemPrompt, GenerateCallback cb) {
        if (!isConnected()) { cb.onError("Not connected"); return; }

        InferenceRequest req = new InferenceRequest();
        req.prompt = prompt;
        req.systemPrompt = systemPrompt;
        req.maxTokens = 512;
        req.temperature = 0.7f;
        req.topP = 0.9f;

        try {
            mService.generateStream(req, new IInferenceCallback.Stub() {
                private final StringBuilder mBuilder = new StringBuilder();

                @Override public void onToken(Token t) {
                    mBuilder.append(t.text);
                    cb.onToken(t.text);
                }

                @Override public void onComplete(InferenceResponse r) {
                    cb.onComplete(mBuilder.toString(), r.latencyMs);
                }

                @Override public void onError(InferenceError e) {
                    cb.onError(e.message);
                }

                @Override public void onModelLoaded(String modelId) {}
            });
        } catch (RemoteException e) {
            Log.e(TAG, "generateStream failed", e);
            cb.onError(e.getMessage());
        }
    }
}
