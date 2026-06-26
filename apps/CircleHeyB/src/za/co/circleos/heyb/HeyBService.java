/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle "Hey B" (WP-26 follow-up) — always-listening wake trigger. Runs the
 * on-device recognizer in a loop and launches B! when it hears the wake phrase.
 * This works wherever a recognizer is present; a battery-efficient on-device
 * wake-word model (openWakeWord / Vosk) is the drop-in upgrade.
 */
package za.co.circleos.heyb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

public final class HeyBService extends Service {

    static final String ACTION_STOP = "za.co.circleos.heyb.STOP";
    private static final String CHANNEL = "heyb";
    private static final String BUTLER_PKG = "za.co.circleos.butler";
    private static final String[] WAKE = {"hey b", "hey bee", "hey be", "hey bea", "ok b", "hey bea"};

    private SpeechRecognizer mRecognizer;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private volatile boolean mRunning = false;
    private long mLastTrigger = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNotice();
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                || !SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        mRunning = true;
        mMain.post(this::startListening);
        return START_STICKY;
    }

    private void startListening() {
        if (!mRunning) return;
        try {
            if (mRecognizer == null) {
                mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                mRecognizer.setRecognitionListener(mListener);
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            mRecognizer.startListening(intent);
        } catch (Throwable t) {
            scheduleRestart(600);
        }
    }

    private void scheduleRestart(long delayMs) {
        if (!mRunning) return;
        try { if (mRecognizer != null) { mRecognizer.destroy(); mRecognizer = null; } } catch (Throwable ignored) {}
        mMain.postDelayed(this::startListening, delayMs);
    }

    private final RecognitionListener mListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override public void onPartialResults(Bundle partial) {
            if (matches(partial)) trigger();
        }

        @Override public void onResults(Bundle results) {
            if (matches(results)) trigger();
            scheduleRestart(150);
        }

        @Override public void onError(int error) {
            // No-match / timeout are normal in a listen loop — just go again.
            scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 800 : 250);
        }
    };

    private boolean matches(Bundle b) {
        if (b == null) return false;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null) return false;
        for (String s : list) {
            if (s == null) continue;
            String low = s.toLowerCase(Locale.US);
            for (String w : WAKE) {
                if (low.contains(w)) return true;
            }
        }
        return false;
    }

    private void trigger() {
        long now = System.currentTimeMillis();
        if (now - mLastTrigger < 3000) return; // debounce
        mLastTrigger = now;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(BUTLER_PKG);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launch);
            }
        } catch (Throwable ignored) {
        }
    }

    private void startForegroundNotice() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Hey B",
                NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Listening for “Hey B”")
                .setContentText("Say “Hey B” to open your assistant")
                .setOngoing(true)
                .build();
        startForeground(2611, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        mMain.removeCallbacksAndMessages(null);
        try { if (mRecognizer != null) { mRecognizer.destroy(); mRecognizer = null; } } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
