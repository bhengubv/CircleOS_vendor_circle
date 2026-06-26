/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conversational chat UI for Butler.
 *
 * Messages are displayed in a ListView; generation is streamed token-by-token
 * via the IInferenceCallback. The input field is disabled during generation.
 *
 * Voice (WP-26): the mic button dictates a message with the on-device
 * SpeechRecognizer and B!'s replies are spoken back via TextToSpeech. Both
 * degrade gracefully when the device has no recognizer / TTS engine. The
 * always-on "Hey B" wake word is a separate always-listening service (follow-up).
 */
public class ChatActivity extends Activity {

    private static final String TAG = "Butler.Chat";
    private static final int REQ_MIC = 7;

    private static final String SYSTEM_PROMPT =
            "You are Butler, a helpful, concise, and privacy-respecting AI assistant "
            + "running entirely on the user's device. You never connect to the internet. "
            + "Be friendly, direct, and honest about what you know and don't know.";

    private InferenceServiceConnection mInference;
    private ListView mLvMessages;
    private EditText mEtInput;
    private Button mBtnSend;
    private ImageButton mBtnMic;
    private TextView mTvBackend;

    private final List<ChatMessage> mMessages = new ArrayList<>();
    private MessageAdapter mAdapter;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    private volatile boolean mGenerating = false;
    private int mPendingIndex = -1; // index of the "thinking..." placeholder

    private TextToSpeech mTts;
    private volatile boolean mTtsReady = false;
    private SpeechRecognizer mRecognizer;
    private boolean mListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mLvMessages = findViewById(R.id.lv_messages);
        mEtInput    = findViewById(R.id.et_input);
        mBtnSend    = findViewById(R.id.btn_send);
        mBtnMic     = findViewById(R.id.btn_mic);
        mTvBackend  = findViewById(R.id.tv_backend);

        mAdapter = new MessageAdapter(this, mMessages);
        mLvMessages.setAdapter(mAdapter);

        mBtnSend.setOnClickListener(v -> sendMessage());
        if (mBtnMic != null) mBtnMic.setOnClickListener(v -> onMicTapped());

        // Text-to-speech for spoken replies (no-op if no engine is installed).
        mTts = new TextToSpeech(this, status -> {
            mTtsReady = (status == TextToSpeech.SUCCESS);
            if (mTtsReady) {
                try { mTts.setLanguage(Locale.getDefault()); } catch (Throwable ignored) {}
            }
        });

        mInference = new InferenceServiceConnection();
        new Thread(() -> {
            mInference.connect();
            String modelId = mInference.getLoadedModelId();
            mUiHandler.post(() -> mTvBackend.setText(modelId != null ? modelId : ""));
        }).start();
    }

    /* ── Voice input ─────────────────────────────────────────── */

    private void onMicTapped() {
        if (mListening) { stopListening(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startListening();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] grants) {
        if (rc == REQ_MIC) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                toast("Microphone permission is needed for voice");
            }
        }
    }

    private void startListening() {
        if (mGenerating) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice input isn't available on this device");
            return;
        }
        try {
            if (mRecognizer == null) {
                mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                mRecognizer.setRecognitionListener(mRecognition);
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            mListening = true;
            setMicActive(true);
            mRecognizer.startListening(intent);
        } catch (Throwable t) {
            Log.e(TAG, "startListening failed", t);
            mListening = false;
            setMicActive(false);
            toast("Couldn't start voice input");
        }
    }

    private void stopListening() {
        mListening = false;
        setMicActive(false);
        try { if (mRecognizer != null) mRecognizer.stopListening(); } catch (Throwable ignored) {}
    }

    private void setMicActive(boolean active) {
        if (mBtnMic == null) return;
        mBtnMic.setColorFilter(active ? 0xFF2196F3 : 0xFF1A1A2E);
        mEtInput.setHint(active ? "Listening…" : getString(R.string.hint_message));
    }

    private String firstResult(Bundle b) {
        if (b == null) return null;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    private final RecognitionListener mRecognition = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override public void onPartialResults(Bundle partial) {
            String text = firstResult(partial);
            if (text != null) mEtInput.setText(text);
        }

        @Override public void onResults(Bundle results) {
            mListening = false;
            setMicActive(false);
            String text = firstResult(results);
            if (text != null && !text.trim().isEmpty()) {
                mEtInput.setText(text);
                sendMessage();
            }
        }

        @Override public void onError(int error) {
            mListening = false;
            setMicActive(false);
            if (error == SpeechRecognizer.ERROR_NO_MATCH
                    || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                toast("Didn't catch that — try again");
            }
        }
    };

    /* ── Spoken output ───────────────────────────────────────── */

    private void speak(String text) {
        if (!mTtsReady || mTts == null || text == null || text.isEmpty()) return;
        String clean = text.replace("**", "").replace("`", "")
                .replace("•", " ").replace("_", " ").replace("#", "").trim();
        if (clean.isEmpty()) return;
        try {
            mTts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "butler-reply");
        } catch (Throwable ignored) {}
    }

    private void sendMessage() {
        String text = mEtInput.getText().toString().trim();
        if (text.isEmpty() || mGenerating) return;
        if (mListening) stopListening();

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
                speak(answer);
            });
            return;
        }

        // Notebook: let the user tell B! what to remember / forget / recall
        String noteAnswer = NotebookSkill.tryHandle(this, text);
        if (noteAnswer != null) {
            final String answer = noteAnswer;
            mUiHandler.post(() -> {
                if (mPendingIndex >= 0 && mPendingIndex < mMessages.size()) {
                    mMessages.get(mPendingIndex).text = answer;
                    mMessages.get(mPendingIndex).isThinking = false;
                    mAdapter.notifyDataSetChanged();
                }
                mPendingIndex = -1;
                mGenerating = false;
                mBtnSend.setEnabled(true);
                speak(answer);
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
                    speak(fullText);
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        try { if (mRecognizer != null) { mRecognizer.destroy(); mRecognizer = null; } } catch (Throwable ignored) {}
        try { if (mTts != null) { mTts.stop(); mTts.shutdown(); mTts = null; } } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
