/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversational chat UI for Butler.
 *
 * Messages are displayed in a ListView; generation is streamed token-by-token
 * via the IInferenceCallback. The input field is disabled during generation.
 */
public class ChatActivity extends Activity {

    private static final String TAG = "Butler.Chat";

    private static final String SYSTEM_PROMPT =
            "You are Butler, a helpful, concise, and privacy-respecting AI assistant "
            + "running entirely on the user's device. You never connect to the internet. "
            + "Be friendly, direct, and honest about what you know and don't know.";

    private InferenceServiceConnection mInference;
    private ListView mLvMessages;
    private EditText mEtInput;
    private Button mBtnSend;
    private TextView mTvBackend;

    private final List<ChatMessage> mMessages = new ArrayList<>();
    private MessageAdapter mAdapter;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    private volatile boolean mGenerating = false;
    private int mPendingIndex = -1; // index of the "thinking..." placeholder

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mLvMessages = findViewById(R.id.lv_messages);
        mEtInput    = findViewById(R.id.et_input);
        mBtnSend    = findViewById(R.id.btn_send);
        mTvBackend  = findViewById(R.id.tv_backend);

        mAdapter = new MessageAdapter(this, mMessages);
        mLvMessages.setAdapter(mAdapter);

        mBtnSend.setOnClickListener(v -> sendMessage());

        mInference = new InferenceServiceConnection();
        new Thread(() -> {
            mInference.connect();
            String modelId = mInference.getLoadedModelId();
            mUiHandler.post(() -> mTvBackend.setText(modelId != null ? modelId : ""));
        }).start();
    }

    private void sendMessage() {
        String text = mEtInput.getText().toString().trim();
        if (text.isEmpty() || mGenerating) return;

        mEtInput.setText("");
        mGenerating = true;
        mBtnSend.setEnabled(false);

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mEtInput.getWindowToken(), 0);

        // Add user message
        addMessage(new ChatMessage(getString(R.string.you), text, false));

        // Add thinking placeholder
        ChatMessage thinking = new ChatMessage(getString(R.string.butler),
                getString(R.string.thinking), true);
        mPendingIndex = mMessages.size();
        addMessage(thinking);

        // Phase 4: intercept wallet queries before hitting the LLM
        String walletAnswer = WalletSkill.tryHandle(text);
        if (walletAnswer != null) {
            final String answer = walletAnswer;
            mUiHandler.post(() -> {
                if (mPendingIndex >= 0 && mPendingIndex < mMessages.size()) {
                    mMessages.get(mPendingIndex).text = answer;
                    mMessages.get(mPendingIndex).isThinking = false;
                    mAdapter.notifyDataSetChanged();
                }
                mPendingIndex = -1;
                mGenerating = false;
                mBtnSend.setEnabled(true);
            });
            return;
        }

        mInference.generate(text, SYSTEM_PROMPT, new InferenceServiceConnection.GenerateCallback() {
            private final StringBuilder mBuffer = new StringBuilder();

            @Override
            public void onToken(String tokenText) {
                mBuffer.append(tokenText);
                mUiHandler.post(() -> {
                    if (mPendingIndex >= 0 && mPendingIndex < mMessages.size()) {
                        mMessages.get(mPendingIndex).text = mBuffer.toString();
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onComplete(String fullText, long latencyMs) {
                Log.i(TAG, "Generation complete: " + fullText.length() + " chars in " + latencyMs + "ms");
                mUiHandler.post(() -> {
                    if (mPendingIndex >= 0 && mPendingIndex < mMessages.size()) {
                        mMessages.get(mPendingIndex).text = fullText;
                        mMessages.get(mPendingIndex).isThinking = false;
                        mAdapter.notifyDataSetChanged();
                    }
                    mPendingIndex = -1;
                    mGenerating = false;
                    mBtnSend.setEnabled(true);
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Generate error: " + message);
                mUiHandler.post(() -> {
                    if (mPendingIndex >= 0 && mPendingIndex < mMessages.size()) {
                        mMessages.get(mPendingIndex).text = "Error: " + message;
                        mMessages.get(mPendingIndex).isThinking = false;
                        mAdapter.notifyDataSetChanged();
                    }
                    mPendingIndex = -1;
                    mGenerating = false;
                    mBtnSend.setEnabled(true);
                });
            }
        });
    }

    private void addMessage(ChatMessage msg) {
        mUiHandler.post(() -> {
            mMessages.add(msg);
            mAdapter.notifyDataSetChanged();
            mLvMessages.setSelection(mAdapter.getCount() - 1);
        });
    }
}
