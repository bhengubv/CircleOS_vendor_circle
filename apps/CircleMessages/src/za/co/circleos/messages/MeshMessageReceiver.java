/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.messages;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import za.co.circleos.messages.db.MessageDatabase;

/**
 * Receives {@code za.co.circleos.mesh.action.MESSAGE_RECEIVED} broadcasts
 * from {@link com.circleos.server.mesh.CircleMeshService} and stores them
 * in the local message database, then posts a notification.
 */
public class MeshMessageReceiver extends BroadcastReceiver {

    private static final String TAG            = "CircleMessages";
    private static final String CHANNEL_ID     = "circle_messages";
    private static final int    NOTIF_BASE_ID  = 5000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!"za.co.circleos.mesh.action.MESSAGE_RECEIVED".equals(action)) return;

        String senderId = intent.getStringExtra("sender_id");
        String text     = intent.getStringExtra("msg_text");
        if (senderId == null || text == null) {
            Log.w(TAG, "Received malformed MESSAGE_RECEIVED intent");
            return;
        }

        Log.i(TAG, "Received message from " + senderId + ": " + text.length() + " chars");

        // Persist
        MessageDatabase db = new MessageDatabase(context);
        db.insertMessage(senderId, MessageDatabase.DIR_INBOUND, text);

        // Notify
        postNotification(context, senderId, text);
    }

    private void postNotification(Context context, String senderId, String preview) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        ensureChannel(nm);

        // Tapping opens the conversation
        Intent open = new Intent(context, ConversationActivity.class);
        open.putExtra(ConversationActivity.EXTRA_PEER_ID, senderId);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, senderId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String shortId = senderId.length() > 8 ? senderId.substring(0, 8) : senderId;
        Notification notif = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("Message from " + shortId + "…")
                .setContentText(preview.length() > 80 ? preview.substring(0, 80) + "…" : preview)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        // Use a stable ID per sender so repeated messages from the same peer update the notif
        nm.notify(NOTIF_BASE_ID + Math.abs(senderId.hashCode() % 1000), notif);
    }

    private void ensureChannel(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Circle Mesh Messages", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Incoming messages from the Circle mesh network");
        nm.createNotificationChannel(ch);
    }
}
