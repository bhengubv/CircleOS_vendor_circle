/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Quick Settings tile that shows the Circle privacy status.
 * Tap → opens Privacy Dashboard.
 * Long press → opens full CircleSettings.
 *
 * Registered in the default QS tile list via the SystemUI overlay
 * config string "quick_settings_tiles_default".
 *
 * NOTE: This tile runs inside the SystemUI process. It queries the
 * circle.privacy binder for live counters. If the binder is
 * unavailable (service not started yet, early boot) the tile shows
 * "Protected" as a safe default.
 */
package com.circleos.systemui.qs;

import android.circleos.privacy.ICirclePrivacyManagerService;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public final class CirclePrivacyQSTile extends TileService {

    private static final String TAG = "CirclePrivacyQS";

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        // Open Privacy Dashboard
        Intent i = new Intent("com.circleos.action.OPEN_PRIVACY_DASHBOARD");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivityAndCollapse(i);
        } catch (Throwable t) {
            Log.w(TAG, "Cannot open Privacy Dashboard", t);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        String label = "Circle Privacy";
        String subtitle = "Protected";
        int state = Tile.STATE_ACTIVE;

        try {
            IBinder b = ServiceManager.getService("circle.privacy");
            if (b != null) {
                ICirclePrivacyManagerService svc =
                        ICirclePrivacyManagerService.Stub.asInterface(b);
                int denied = svc.getDeniedPermissionCount();
                int faked  = svc.getFakedIdentifierCount();
                int grants = svc.getNetworkGrantCount();
                subtitle = denied + " blocked · " + faked + " faked · " + grants + " grants";
            }
        } catch (RemoteException e) {
            Log.w(TAG, "circle.privacy unreachable", e);
        } catch (Throwable t) {
            Log.w(TAG, "QS tile update failed", t);
        }

        tile.setLabel(label);
        tile.setSubtitle(subtitle);
        tile.setState(state);
        tile.updateTile();
    }
}
