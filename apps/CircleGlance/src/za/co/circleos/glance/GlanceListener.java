/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tracks the active notification count for the Glance display. Needs the user to
 * grant notification access; otherwise the count is simply hidden.
 */
package za.co.circleos.glance;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class GlanceListener extends NotificationListenerService {

    private static volatile int sCount = -1;

    static int getCount() {
        return sCount;
    }

    @Override
    public void onListenerConnected() {
        refresh();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refresh();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refresh();
    }

    private void refresh() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            sCount = (active != null) ? active.length : 0;
        } catch (Throwable t) {
            // listener not ready
        }
    }
}
