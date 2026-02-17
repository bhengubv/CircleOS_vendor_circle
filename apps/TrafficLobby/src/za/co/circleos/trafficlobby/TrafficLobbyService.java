/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground service that manages the TrafficLobbyVpnService lifecycle.
 * Starts at boot, persists until explicitly stopped.
 */
public class TrafficLobbyService extends Service {

    private static final String TAG              = "TrafficLobby";
    private static final String NOTIF_CHANNEL_ID = "traffic_lobby";
    private static final int    NOTIF_ID         = 0xC14B;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent vpnIntent = new Intent(this, TrafficLobbyVpnService.class);
        startService(vpnIntent);
        Log.i(TAG, "Traffic Lobby service started");
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN);
        ch.setDescription(getString(R.string.notif_channel_desc));
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
    }
}
