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
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.mesh.ICircleMeshService;
import za.co.circleos.messages.db.MessageDatabase;

/**
 * Receives {@code za.co.circleos.mesh.action.MESSAGE_RECEIVED} broadcasts from
 * {@link com.circleos.server.mesh.CircleMeshService}.
 *
 * The wire string is one of:
 *   CKX:&lt;key&gt;  — a peer's public key (handshake). Store it, reply with ours
 *                  if it was new, and flush any message queued for that peer.
 *   CE1:&lt;blob&gt; — an end-to-end-encrypted message. Decrypt with {@link MeshCrypto}.
 *   (other)      — legacy plaintext (back-compat); stored as-is.
 */
public class MeshMessageReceiver extends BroadcastReceiver {

    private static final String TAG            = "CircleMessages";
    private static final String CHANNEL_ID     = "circle_messages";
    private static final int    NOTIF_BASE_ID  = 5000;
    private static final int    TYPE_MSG_TEXT  = 0x10;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"za.co.circleos.mesh.action.MESSAGE_RECEIVED".equals(intent.getAction())) return;

        String senderId = intent.getStringExtra("sender_id");
        String wire     = intent.getStringExtra("msg_text");
        if (senderId == null || wire == null) {
            Log.w(TAG, "Received malformed MESSAGE_RECEIVED intent");
            return;
        }

        MeshCrypto crypto = new MeshCrypto(context);

        // ── Key exchange ──
        if (MeshCrypto.isKeyExchange(wire)) {
            boolean isNew = crypto.storePeerKey(senderId, wire);
            if (isNew && crypto.isReady()) {
                sendWire(context, senderId, crypto.keyExchangeMessage()); // reply so they can encrypt to us
            }
            // We can now encrypt to this peer — flush anything that was waiting.
            String pending = PendingStore.take(context, senderId);
            if (pending != null) {
                String env = crypto.encrypt(senderId, pending);
                if (env != null && sendWire(context, senderId, env)) {
                    new MessageDatabase(context).insertMessage(
                            senderId, MessageDatabase.DIR_OUTBOUND, pending);
                } else {
                    PendingStore.queue(context, senderId, pending);
                }
            }
            return; // handshake is not a user-visible message
        }

        // ── Encrypted message ──
        if (MeshCrypto.isEncrypted(wire)) {
            String text = crypto.decrypt(senderId, wire);
            if (text == null) {
                // No key yet (or tampered) — ask for theirs and drop. Never show ciphertext.
                if (crypto.isReady()) sendWire(context, senderId, crypto.keyExchangeMessage());
                Log.w(TAG, "Undecryptable message from " + senderId + " — requested key");
                return;
            }
            deliver(context, senderId, text);
            return;
        }

        // ── Legacy plaintext (back-compat) ──
        deliver(context, senderId, wire);
    }

    private void deliver(Context context, String senderId, String text) {
        new MessageDatabase(context).insertMessage(senderId, MessageDatabase.DIR_INBOUND, text);
        postNotification(context, senderId, text);
    }

    private boolean sendWire(Context context, String peerId, String wire) {
        if (wire == null) return false;
        try {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder == null) return false;
            ICircleMeshService mesh = ICircleMeshService.Stub.asInterface(binder);
            return mesh.sendMessage(peerId, wire.getBytes("UTF-8"), TYPE_MSG_TEXT);
        } catch (Exception e) {
            Log.e(TAG, "sendWire failed", e);
            return false;
        }
    }

    private void postNotification(Context context, String senderId, String preview) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannel(nm);

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
