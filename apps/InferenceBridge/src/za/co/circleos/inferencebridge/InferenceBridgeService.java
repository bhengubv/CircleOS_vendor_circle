/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.inferencebridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.inference.ICircleInference;

/**
 * Foreground service that hosts the InferenceHttpServer.
 *
 * Lifecycle:
 *   1. Started at boot via BootReceiver (or manually by any app).
 *   2. Acquires the circle.inference system service Binder.
 *   3. Generates a session token and publishes it via BridgeTokenProvider.
 *   4. Starts InferenceHttpServer on 127.0.0.1:11434.
 *   5. Runs until explicitly stopped or the system kills it (it will restart).
 */
public class InferenceBridgeService extends Service {

    private static final String TAG              = "InferenceBridge";
    private static final String NOTIF_CHANNEL_ID = "inference_bridge";
    private static final int    NOTIF_ID         = 0xC1E4;

    private InferenceHttpServer mHttpServer;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mHttpServer != null) return START_STICKY; // already running

        // Connect to system service
        IBinder binder = ServiceManager.getService("circle.inference");
        if (binder == null) {
            Log.e(TAG, "circle.inference not available — stopping bridge");
            stopSelf();
            return START_NOT_STICKY;
        }
        ICircleInference service = ICircleInference.Stub.asInterface(binder);
        Log.i(TAG, "Connected to circle.inference");

        // Create token and publish via ContentProvider
        SessionTokenManager tokenMgr = new SessionTokenManager();
        BridgeTokenProvider.setTokenManager(tokenMgr);

        // Start HTTP server
        mHttpServer = new InferenceHttpServer(service, tokenMgr);
        try {
            mHttpServer.start();
            Log.i(TAG, "HTTP bridge started on 127.0.0.1:" + InferenceHttpServer.PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start HTTP server", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mHttpServer != null) {
            mHttpServer.stop();
            mHttpServer = null;
        }
        BridgeTokenProvider.setTokenManager(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN);
        channel.setDescription(getString(R.string.notif_channel_desc));
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_bridge)
                .setOngoing(true)
                .build();
    }
}
