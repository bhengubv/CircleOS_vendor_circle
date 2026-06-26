/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Commute (WP-28) — proactive "time to leave" alerts. You set your
 * destination and an arrive-by time; each chosen morning B! checks your live
 * location, estimates travel time and tells you when to leave. Fully offline
 * (GPS + a speed estimate); live-traffic ETA is the follow-up.
 */
package za.co.circleos.commute;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import java.util.Calendar;
import java.util.Locale;

final class Commute {

    static final String PREFS = "commute";
    static final String K_ENABLED = "enabled";
    static final String K_WORK_SET = "work_set";
    static final String K_WORK_LAT = "work_lat";
    static final String K_WORK_LNG = "work_lng";
    static final String K_ARRIVE_H = "arrive_h";
    static final String K_ARRIVE_M = "arrive_m";
    static final String K_DAYS = "days";          // bitmask, bit 0=Sun … 6=Sat
    static final String K_KMH = "avg_kmh";

    static final int DEFAULT_DAYS = 0b0111110;     // Mon–Fri
    static final int DEFAULT_KMH = 28;
    static final int LEAD_MINUTES = 90;            // check this long before arrival

    static final String ACTION_CHECK = "za.co.circleos.commute.CHECK";
    private static final int RC_CHECK = 201;
    private static final String CHANNEL = "commute";

    private Commute() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(checkIntent(c));
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_ENABLED, false) || !p.getBoolean(K_WORK_SET, false)) return;

        int arriveMin = p.getInt(K_ARRIVE_H, 9) * 60 + p.getInt(K_ARRIVE_M, 0);
        int checkMin = arriveMin - LEAD_MINUTES;
        if (checkMin < 0) checkMin += 24 * 60;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, checkMin / 60);
        cal.set(Calendar.MINUTE, checkMin % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, checkIntent(c));
    }

    private static PendingIntent checkIntent(Context c) {
        Intent i = new Intent(c, CommuteReceiver.class).setAction(ACTION_CHECK);
        return PendingIntent.getBroadcast(c, RC_CHECK, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Fired by the daily alarm: if today is selected, estimate and notify. */
    static void check(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_ENABLED, false) || !p.getBoolean(K_WORK_SET, false)) return;
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1; // 0=Sun
        if ((p.getInt(K_DAYS, DEFAULT_DAYS) & (1 << dow)) == 0) return;

        int arriveH = p.getInt(K_ARRIVE_H, 9);
        int arriveM = p.getInt(K_ARRIVE_M, 0);
        String arrive = String.format(Locale.US, "%02d:%02d", arriveH, arriveM);

        Location loc = lastKnown(c);
        if (loc == null) {
            notify(c, "Time to head to work", "Leave soon to arrive by " + arrive + ".");
            return;
        }
        float[] r = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                p.getFloat(K_WORK_LAT, 0), p.getFloat(K_WORK_LNG, 0), r);
        double km = r[0] / 1000.0;
        int kmh = Math.max(8, p.getInt(K_KMH, DEFAULT_KMH));
        int etaMin = (int) Math.ceil((km / kmh) * 60 * 1.3); // 1.3 = road vs straight-line
        int arriveMin = arriveH * 60 + arriveM;
        int leaveMin = arriveMin - etaMin;
        if (leaveMin < 0) leaveMin += 24 * 60;
        String leaveBy = String.format(Locale.US, "%02d:%02d", leaveMin / 60, leaveMin % 60);
        notify(c, "Leave by " + leaveBy + " for work",
                String.format(Locale.US, "About %d min (%.1f km) to arrive by %s.", etaMin, km, arrive));
    }

    private static Location lastKnown(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null) return null;
        Location best = null;
        for (String prov : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(prov);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Throwable ignored) {
            }
        }
        return best;
    }

    private static void notify(Context c, String title, String body) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Commute",
                NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build();
        nm.notify(2801, n);
    }
}
