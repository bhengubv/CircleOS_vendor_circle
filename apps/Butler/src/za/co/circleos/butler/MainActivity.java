/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

/**
 * Butler entry point. Connects to the inference service, loads the
 * optimal model, and navigates to ChatActivity when ready.
 */
public class MainActivity extends Activity {

    private static final String TAG = "Butler.Main";

    private InferenceServiceConnection mInference;
    private TextView mTvStatus;
    private Button mBtnStart;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvStatus = findViewById(R.id.tv_status);
        mBtnStart = findViewById(R.id.btn_start);

        mBtnStart.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatActivity.class));
        });

        connectAndLoad();
    }

    private void connectAndLoad() {
        mInference = new InferenceServiceConnection();

        new Thread(() -> {
            boolean connected = mInference.connect();
            if (!connected) {
                setStatus(getString(R.string.error_service_unavailable), false);
                return;
            }

            // Check if a model is already loaded from boot
            String loaded = mInference.getLoadedModelId();
            if (loaded != null) {
                Log.i(TAG, "Model already loaded: " + loaded);
                setStatus(getString(R.string.model_ready) + " — " + loaded, true);
                return;
            }

            setStatus(getString(R.string.loading_model), false);
            mInference.loadOptimalModel(new InferenceServiceConnection.ReadyCallback() {
                @Override public void onReady(String modelId) {
                    setStatus(getString(R.string.model_ready) + " — " + modelId, true);
                }
                @Override public void onError(String message) {
                    setStatus(getString(R.string.error_no_model), false);
                    Log.e(TAG, "Model load error: " + message);
                }
            });
        }).start();
    }

    private void setStatus(String text, boolean ready) {
        mUiHandler.post(() -> {
            mTvStatus.setText(text);
            mBtnStart.setEnabled(ready);
        });
    }
}
