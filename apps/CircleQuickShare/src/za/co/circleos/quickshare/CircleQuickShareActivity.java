/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Quick Share (WP-57) — send files phone-to-phone over Wi-Fi Direct.
 * No internet, no account, no Google services. The sender picks a file and
 * taps a nearby device; the receiver just waits. Whichever side becomes the
 * Wi-Fi Direct group owner runs the server socket; the file always flows from
 * the sender to the receiver over that socket.
 */
package za.co.circleos.quickshare;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class CircleQuickShareActivity extends Activity
        implements WifiP2pManager.ConnectionInfoListener, WifiP2pManager.PeerListListener {

    static final int PORT = 8988;
    private static final int REQ_PERMS = 11;
    private static final int REQ_PICK = 12;

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private QuickShareReceiver mReceiver;
    private IntentFilter mFilter;

    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final List<WifiP2pDevice> mPeers = new ArrayList<>();

    private TextView mStatus;
    private LinearLayout mPeerBox;

    private Uri mSendUri;          // non-null => send mode
    private boolean mReceiveMode;
    private boolean mTransferStarted;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(28));

        root.addView(title("Quick Share"));
        root.addView(subtitle("Send files phone-to-phone over Wi-Fi Direct — no internet, no account, no Google."));

        root.addView(bigButton("Send a file", ACCENT, () -> { ensurePerms(); pickFile(); }));
        root.addView(bigButton("Receive", CARD, () -> { ensurePerms(); startReceive(); }));

        mStatus = new TextView(this);
        mStatus.setTextColor(MUTED);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mStatus.setPadding(dp(4), dp(18), dp(4), dp(10));
        mStatus.setText("Ready.");
        root.addView(mStatus);

        TextView peersHdr = new TextView(this);
        peersHdr.setText("NEARBY DEVICES");
        peersHdr.setTextColor(ACCENT);
        peersHdr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        peersHdr.setLetterSpacing(0.08f);
        peersHdr.setPadding(dp(4), dp(14), dp(4), dp(8));
        root.addView(peersHdr);

        mPeerBox = new LinearLayout(this);
        mPeerBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(mPeerBox);

        scroll.addView(root);
        setContentView(scroll);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (mManager != null) mChannel = mManager.initialize(this, getMainLooper(), null);

        mFilter = new IntentFilter();
        mFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        renderPeers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mManager == null) { status("Wi-Fi Direct isn't available on this device."); return; }
        mReceiver = new QuickShareReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, mFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        if (mReceiver != null) {
            try { unregisterReceiver(mReceiver); } catch (Throwable ignored) {}
            mReceiver = null;
        }
        super.onPause();
    }

    /* ── permissions ── */

    private void ensurePerms() {
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ_PERMS && (g.length == 0 || g[0] != PackageManager.PERMISSION_GRANTED)) {
            status("Nearby-devices permission is needed to find phones around you.");
        }
    }

    /* ── send / receive entry points ── */

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choose a file to send"), REQ_PICK);
        } catch (Throwable t) {
            toast("No file picker available");
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            mSendUri = data.getData();
            mReceiveMode = false;
            mTransferStarted = false;
            status("Searching for nearby devices… tap one to send to.");
            discover();
        }
    }

    private void startReceive() {
        mSendUri = null;
        mReceiveMode = true;
        mTransferStarted = false;
        status("Ready to receive. Keep this screen open while the sender connects.");
        discover();
    }

    private void discover() {
        if (mManager == null || mChannel == null) { toast("Wi-Fi Direct unavailable"); return; }
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) { ensurePerms(); return; }
        try {
            mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {}
                @Override public void onFailure(int reason) {
                    status("Couldn't start discovery (code " + reason + "). Is Wi-Fi on?");
                }
            });
        } catch (SecurityException e) {
            ensurePerms();
        }
    }

    /* ── receiver callbacks ── */

    void setP2pEnabled(boolean on) {
        if (!on) status("Turn on Wi-Fi to use Quick Share.");
    }

    void requestPeers() {
        if (mManager == null || mChannel == null) return;
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) return;
        try { mManager.requestPeers(mChannel, this); } catch (SecurityException ignored) {}
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        mPeers.clear();
        mPeers.addAll(peers.getDeviceList());
        renderPeers();
    }

    void onConnectionChanged() {
        if (mManager != null && mChannel != null) mManager.requestConnectionInfo(mChannel, this);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (info == null || !info.groupFormed || mTransferStarted) return;
        mTransferStarted = true;
        String host = info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : null;
        boolean go = info.isGroupOwner;
        if (mReceiveMode) {
            status("Connected — receiving…");
            new FileTransfer(this, go, host, false, null, mTransferCb).start();
        } else if (mSendUri != null) {
            status("Connected — sending…");
            new FileTransfer(this, go, host, true, mSendUri, mTransferCb).start();
        } else {
            mTransferStarted = false;
        }
    }

    private final FileTransfer.Callback mTransferCb = (ok, msg) -> mUi.post(() -> {
        status(msg);
        mTransferStarted = false;
        if (mManager != null && mChannel != null) {
            try { mManager.removeGroup(mChannel, null); } catch (Throwable ignored) {}
        }
    });

    /* ── peers + connect ── */

    private void renderPeers() {
        mPeerBox.removeAllViews();
        if (mPeers.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No devices yet. Open Quick Share on the other phone too.");
            t.setTextColor(MUTED);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setPadding(dp(4), dp(8), dp(4), 0);
            mPeerBox.addView(t);
            return;
        }
        for (WifiP2pDevice d : mPeers) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundColor(CARD);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(2);
            row.setLayoutParams(lp);

            TextView name = new TextView(this);
            name.setText(d.deviceName == null || d.deviceName.isEmpty() ? d.deviceAddress : d.deviceName);
            name.setTextColor(TEXT);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.addView(name);

            TextView sub = new TextView(this);
            sub.setText(statusLabel(d.status));
            sub.setTextColor(MUTED);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            row.addView(sub);

            final WifiP2pDevice dev = d;
            row.setOnClickListener(v -> connect(dev));
            mPeerBox.addView(row);
        }
    }

    private void connect(WifiP2pDevice device) {
        if (mManager == null || mChannel == null) return;
        if (mReceiveMode) { toast("You're receiving — let the sender tap your device."); return; }
        if (mSendUri == null) { toast("Tap \"Send a file\" and pick a file first."); return; }
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) { ensurePerms(); return; }
        status("Connecting to " + (device.deviceName != null ? device.deviceName : device.deviceAddress) + "…");
        try {
            mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {}
                @Override public void onFailure(int reason) { status("Connection failed (code " + reason + ")."); }
            });
        } catch (SecurityException e) {
            ensurePerms();
        }
    }

    private String statusLabel(int s) {
        switch (s) {
            case WifiP2pDevice.AVAILABLE: return "Available";
            case WifiP2pDevice.INVITED: return "Invited";
            case WifiP2pDevice.CONNECTED: return "Connected";
            default: return "Unavailable";
        }
    }

    /* ── view helpers ── */

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private TextView subtitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(6), 0, dp(18));
        return t;
    }

    private View bigButton(String label, int bg, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(bg);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private void status(String s) { mUi.post(() -> mStatus.setText(s)); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
