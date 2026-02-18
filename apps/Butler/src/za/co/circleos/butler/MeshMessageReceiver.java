/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receives {@code za.co.circleos.mesh.action.MESSAGE_RECEIVED} broadcasts
 * from CircleMeshService and shows a notification for incoming mesh messages.
 *
 * When Butler is in the foreground, {@link MeshActivity} registers its own
 * dynamic receiver and handles messages directly. This static receiver handles
 * the case where Butler is in the background.
 */
public class MeshMessageReceiver extends BroadcastReceiver {

    private static final String TAG     = "Butler.MeshReceiver";
    private static final String CHANNEL = "mesh_messages";
    private static final int    NOTIF_ID_BASE = 0x4D455348; // "MESH"

    @Override
    public void onReceive(Context context, Intent intent) {
        String senderId = intent.getStringExtra(MeshActivity.EXTRA_SENDER_ID);
        String text     = intent.getStringExtra(MeshActivity.EXTRA_MSG_TEXT);
        if (text == null || text.isEmpty()) return;

        String label = (senderId != null && senderId.length() >= 8)
                ? senderId.substring(0, 8) + "…" : "Peer";

        Log.d(TAG, "Incoming mesh message from " + label);

        showNotification(context, senderId, label, text);
    }

    private void showNotification(Context ctx, String senderId,
            String senderLabel, String text) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Create channel on first use
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Mesh Messages", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Incoming Circle Mesh P2P messages");
            nm.createNotificationChannel(ch);
        }

        // Tap → open MeshActivity with the sender pre-filled
        Intent openIntent = new Intent(ctx, MeshActivity.class);
        openIntent.putExtra(MeshActivity.EXTRA_PEER_ID, senderId);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(ctx, senderId.hashCode(),
                openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Message from " + senderLabel)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        nm.notify(CHANNEL, senderId.hashCode(), notif);
    }
}
