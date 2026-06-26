/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Reminders - fires the notification when a reminder's alarm goes off,
 * then removes the spent reminder from the store.
 */
package za.co.circleos.reminders;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

public final class ReminderReceiver extends BroadcastReceiver {

    static final String CHANNEL   = "circle_reminders";
    static final String EXTRA_ID  = "id";
    static final String EXTRA_TEXT = "text";

    static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Circle reminder alerts");
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int id = intent.getIntExtra(EXTRA_ID, 0);
        String text = intent.getStringExtra(EXTRA_TEXT);
        if (text == null || text.isEmpty()) text = "Reminder";

        ensureChannel(ctx);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) {
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_reminders)
                    .setContentTitle("Reminder")
                    .setContentText(text)
                    .setColor(0xFF2196F3)
                    .setAutoCancel(true)
                    .build();
            nm.notify(id, n);
        }

        // Drop the spent reminder.
        ReminderStore store = new ReminderStore(ctx);
        List<ReminderStore.Reminder> list = store.load();
        store.remove(list, id);
    }
}
