/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.inferencebridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts InferenceBridgeService on device boot. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent svc = new Intent(context, InferenceBridgeService.class);
            context.startForegroundService(svc);
        }
    }
}
