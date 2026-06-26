/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Find My Phone (WP-55) - SMS-triggered ring + locate. A text of the
 * form "circle ring <PIN>" or "circle locate <PIN>" from any phone makes this
 * device ring loudly or reply with its location. PIN-gated, opt-in. No backend.
 */
package za.co.circleos.findmy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

public final class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        SharedPreferences sp = ctx.getSharedPreferences("circle_findmy", Context.MODE_PRIVATE);
        if (!sp.getBoolean("enabled", false)) return;
        String pin = sp.getString("pin", "");
        if (pin.isEmpty()) return;

        Bundle b = intent.getExtras();
        if (b == null) return;
        Object[] pdus = (Object[]) b.get("pdus");
        if (pdus == null) return;
        String format = b.getString("format");

        StringBuilder body = new StringBuilder();
        String sender = null;
        for (Object pdu : pdus) {
            SmsMessage m = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (m == null) continue;
            body.append(m.getMessageBody());
            if (sender == null) sender = m.getOriginatingAddress();
        }

        String[] parts = body.toString().trim().toLowerCase().split("\\s+");
        if (parts.length < 3 || !"circle".equals(parts[0])) return;
        if (!pin.equals(parts[2])) return;

        String cmd = parts[1];
        if ("ring".equals(cmd)) {
            ring(ctx);
        } else if ("locate".equals(cmd) || "find".equals(cmd)) {
            locate(ctx, sender);
        }
    }

    private void ring(Context ctx) {
        try {
            AudioManager am = ctx.getSystemService(AudioManager.class);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_ALARM,
                        am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            }
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone rt = RingtoneManager.getRingtone(ctx, uri);
            if (rt != null) {
                rt.setStreamType(AudioManager.STREAM_ALARM);
                rt.play();
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    private void locate(Context ctx, String sender) {
        if (sender == null) return;
        Location best = null;
        try {
            LocationManager lm = ctx.getSystemService(LocationManager.class);
            if (lm != null) {
                for (String p : new String[]{
                        LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                    try {
                        Location l = lm.getLastKnownLocation(p);
                        if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                    } catch (SecurityException se) {
                        // location permission (incl. background) not granted
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        String msg = (best != null)
                ? "Circle: https://maps.google.com/?q=" + best.getLatitude() + "," + best.getLongitude()
                : "Circle: location unavailable right now.";
        try {
            SmsManager sm = ctx.getSystemService(SmsManager.class);
            if (sm != null) sm.sendTextMessage(sender, null, msg, null, null);
        } catch (Throwable t) {
            // ignore
        }
    }
}
