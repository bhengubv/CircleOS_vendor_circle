/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Forwards Wi-Fi Direct system broadcasts to the Quick Share activity.
 * Registered dynamically while the activity is resumed.
 */
package za.co.circleos.quickshare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;

public final class QuickShareReceiver extends BroadcastReceiver {

    private final WifiP2pManager mManager;
    private final WifiP2pManager.Channel mChannel;
    private final CircleQuickShareActivity mActivity;

    QuickShareReceiver(WifiP2pManager m, WifiP2pManager.Channel c, CircleQuickShareActivity a) {
        mManager = m;
        mChannel = c;
        mActivity = a;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                mActivity.setP2pEnabled(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
                break;
            }
            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                mActivity.requestPeers();
                break;
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                mActivity.onConnectionChanged();
                break;
            default:
                // THIS_DEVICE_CHANGED — no action needed
                break;
        }
    }
}
