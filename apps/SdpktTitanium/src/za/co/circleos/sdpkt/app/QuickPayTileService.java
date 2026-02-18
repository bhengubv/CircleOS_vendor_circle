/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.sdpkt.app;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import za.co.circleos.sdpkt.IShongololoWallet;
import za.co.circleos.sdpkt.WalletBalance;

/**
 * Quick Settings tile for lock screen quick pay.
 *
 * Tile label: "Shongololo"
 * Tile subtitle: current available balance or "Not ready"
 *
 * Tap (any state):
 *   Opens WalletActivity with EXTRA_QUICK_PAY=true, which skips the amount
 *   dialog and goes straight into NFC reader mode using the lock-screen
 *   per-tap limit (₷100 by default).
 *
 * The tile works on both the lock screen and QS panel:
 *   - On lock screen: startActivityAndCollapse launches the wallet UI
 *     with FLAG_ACTIVITY_REORDER_TO_FRONT and FLAG_SHOW_WHEN_LOCKED.
 *   - The WalletActivity handles lock-screen mode via NfcTransferRequest.lockScreenMode=true.
 *
 * Register in SdpktTitanium AndroidManifest.xml with:
 *   <action android:name="android.service.quicksettings.action.QS_TILE"/>
 */
public class QuickPayTileService extends TileService {

    private static final String TAG = "SdpktQuickPay";

    /** Intent extra that tells WalletActivity to enter quick-pay mode immediately. */
    public static final String EXTRA_QUICK_PAY = "za.co.circleos.sdpkt.QUICK_PAY";

    private IShongololoWallet mWallet;

    /* ── TileService lifecycle ───────────────────────────── */

    @Override
    public void onStartListening() {
        bindWallet();
        refreshTile();
    }

    @Override
    public void onStopListening() {
        mWallet = null;
    }

    @Override
    public void onClick() {
        Intent intent = new Intent(this, WalletActivity.class);
        intent.putExtra(EXTRA_QUICK_PAY, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(0x00800000); // FLAG_SHOW_WHEN_LOCKED (= WindowManager.LayoutParams)
        try {
            startActivityAndCollapse(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch WalletActivity", e);
        }
    }

    /* ── Tile state ──────────────────────────────────────── */

    private void bindWallet() {
        try {
            IBinder b = ServiceManager.getService("circle.sdpkt");
            if (b != null) mWallet = IShongololoWallet.Stub.asInterface(b);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind circle.sdpkt", e);
        }
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (mWallet == null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle("Not ready");
            tile.updateTile();
            return;
        }

        new Thread(() -> {
            try {
                if (!mWallet.hasWallet()) {
                    post(() -> {
                        tile.setState(Tile.STATE_INACTIVE);
                        tile.setSubtitle("No wallet");
                        tile.updateTile();
                    });
                    return;
                }

                WalletBalance bal = mWallet.getBalance();
                long limit = mWallet.getEffectivePerTapLimitCents(/*lockScreen=*/true);
                boolean protectionActive = mWallet.isProtectionActive();

                post(() -> {
                    if (protectionActive) {
                        tile.setState(Tile.STATE_INACTIVE);
                        tile.setSubtitle("Protected");
                    } else if (bal.availableCents <= 0) {
                        tile.setState(Tile.STATE_INACTIVE);
                        tile.setSubtitle("No balance");
                    } else {
                        tile.setState(Tile.STATE_ACTIVE);
                        tile.setSubtitle(formatCents(bal.availableCents)
                                + " · up to " + formatCents(limit));
                    }
                    tile.updateTile();
                });
            } catch (RemoteException e) {
                Log.e(TAG, "refreshTile error", e);
            }
        }).start();
    }

    private void post(Runnable r) {
        // Run on main thread via Handler
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private String formatCents(long cents) {
        return String.format(java.util.Locale.US, "₷%,d.%02d",
                cents / 100, Math.abs(cents % 100));
    }
}
