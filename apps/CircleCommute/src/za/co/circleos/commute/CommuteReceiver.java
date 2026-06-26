/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Runs the daily commute check, and re-arms the schedule after a reboot.
 */
package za.co.circleos.commute;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class CommuteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        if (action == null) return;
        if (Commute.ACTION_CHECK.equals(action)) {
            Commute.check(c);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            Commute.schedule(c);
        }
    }
}
