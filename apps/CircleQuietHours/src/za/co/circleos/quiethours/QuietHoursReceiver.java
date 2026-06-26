/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Flips Do Not Disturb on the scheduled start/end alarms, and re-arms the
 * schedule after a reboot.
 */
package za.co.circleos.quiethours;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class QuietHoursReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        if (action == null) return;
        switch (action) {
            case QuietHours.ACTION_START:
                QuietHours.applyPolicy(c, true);
                break;
            case QuietHours.ACTION_END:
                QuietHours.applyPolicy(c, false);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                QuietHours.schedule(c);
                break;
            default:
                break;
        }
    }
}
