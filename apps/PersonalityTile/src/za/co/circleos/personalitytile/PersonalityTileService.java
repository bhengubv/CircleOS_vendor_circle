/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalitytile;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import za.co.circleos.personality.ICirclePersonalityManager;

/**
 * Quick Settings tile that shows the active personality mode and opens the
 * mode chooser dialog when tapped.
 */
public class PersonalityTileService extends TileService {

    private static final String TAG = "PersonalityTile";

    private ICirclePersonalityManager mService;

    @Override
    public void onStartListening() {
        super.onStartListening();
        connectService();
        updateTile();
    }

    @Override
    public void onClick() {
        Intent intent = new Intent(this, ModeChooserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
    }

    private void connectService() {
        if (mService != null) return;
        try {
            IBinder binder = ServiceManager.getService("circle.personality");
            if (binder != null) {
                mService = ICirclePersonalityManager.Stub.asInterface(binder);
            }
        } catch (Exception e) {
            Log.w(TAG, "Cannot connect to circle.personality: " + e.getMessage());
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        String label = "Mode";
        if (mService != null) {
            try {
                String modeId = mService.getActiveModeId();
                if (modeId != null) {
                    // Capitalise first letter for display
                    label = Character.toUpperCase(modeId.charAt(0))
                            + modeId.substring(1).replace('_', ' ');
                }
            } catch (RemoteException e) {
                Log.w(TAG, "getActiveModeId failed: " + e.getMessage());
            }
        }

        tile.setLabel(label);
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }
}
