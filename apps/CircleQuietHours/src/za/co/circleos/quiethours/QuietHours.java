/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Quiet Hours (WP-31) — scheduled Do Not Disturb with an inner-circle
 * breakthrough. Policy + scheduling helper shared by the UI and the receiver.
 */
package za.co.circleos.quiethours;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

final class QuietHours {

    static final String PREFS = "quiet_hours";
    static final String K_ENABLED = "enabled";
    static final String K_START_H = "start_h";
    static final String K_START_M = "start_m";
    static final String K_END_H = "end_h";
    static final String K_END_M = "end_m";
    static final String K_INNER = "inner_circle";
    static final String K_ALARMS = "allow_alarms";

    static final String ACTION_START = "za.co.circleos.quiethours.START";
    static final String ACTION_END = "za.co.circleos.quiethours.END";
    private static final int RC_START = 101;
    private static final int RC_END = 102;

    private QuietHours() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasPolicyAccess(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    /** Turn DND on/off, honouring the inner-circle breakthrough + alarms prefs. */
    static void applyPolicy(Context c, boolean on) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) return;
        if (!on) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            return;
        }
        SharedPreferences p = prefs(c);
        boolean inner = p.getBoolean(K_INNER, true);
        boolean alarms = p.getBoolean(K_ALARMS, true);
        int categories = 0;
        if (inner) {
            categories |= NotificationManager.Policy.PRIORITY_CATEGORY_CALLS;
            categories |= NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES;
            categories |= NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
        }
        if (alarms) {
            categories |= NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
        }
        int senders = inner
                ? NotificationManager.Policy.PRIORITY_SENDERS_STARRED
                : NotificationManager.Policy.PRIORITY_SENDERS_ANY;
        NotificationManager.Policy policy =
                new NotificationManager.Policy(categories, senders, senders);
        nm.setNotificationPolicy(policy);
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    /** (Re)arm the daily start/end alarms and apply the policy for "now". */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(pi(c, ACTION_START, RC_START));
        am.cancel(pi(c, ACTION_END, RC_END));

        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_ENABLED, false)) {
            applyPolicy(c, false);
            return;
        }
        long start = nextTime(p.getInt(K_START_H, 22), p.getInt(K_START_M, 0));
        long end = nextTime(p.getInt(K_END_H, 7), p.getInt(K_END_M, 0));
        am.setRepeating(AlarmManager.RTC_WAKEUP, start, AlarmManager.INTERVAL_DAY,
                pi(c, ACTION_START, RC_START));
        am.setRepeating(AlarmManager.RTC_WAKEUP, end, AlarmManager.INTERVAL_DAY,
                pi(c, ACTION_END, RC_END));
        applyPolicy(c, insideWindow(p));
    }

    private static PendingIntent pi(Context c, String action, int rc) {
        Intent i = new Intent(c, QuietHoursReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, rc, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static long nextTime(int h, int m) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    /** True if the current wall-clock time is within the quiet window (handles overnight). */
    static boolean insideWindow(SharedPreferences p) {
        int start = p.getInt(K_START_H, 22) * 60 + p.getInt(K_START_M, 0);
        int end = p.getInt(K_END_H, 7) * 60 + p.getInt(K_END_M, 0);
        Calendar now = Calendar.getInstance();
        int cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (start == end) return false;
        if (start < end) return cur >= start && cur < end;   // same-day window
        return cur >= start || cur < end;                    // overnight window
    }
}
