/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Shows the Glance display when the phone starts charging, if enabled.
 */
package za.co.circleos.glance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class GlanceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_POWER_CONNECTED.equals(i.getAction())) return;
        boolean onCharge = c.getSharedPreferences("glance", Context.MODE_PRIVATE)
                .getBoolean("on_charge", false);
        if (!onCharge) return;
        try {
            c.startActivity(new Intent(c, GlanceActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
        }
    }
}
