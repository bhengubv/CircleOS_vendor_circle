/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Project My Screen (WP-68) — start/stop the screen stream and show the
 * address a viewer connects to.
 */
package za.co.circleos.castscreen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public final class ProjectMyScreenActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    private static final int REQ_CAST = 31;
    private static final int REQ_NOTIF = 32;

    private TextView mStatus;

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

        root.addView(title("Project My Screen"));
        root.addView(subtitle("Mirror your screen to a PC or TV over Wi-Fi — no cable, no account."));

        root.addView(button("Start projecting", ACCENT, this::startCast));
        root.addView(button("Stop", CARD, this::stopCast));

        mStatus = new TextView(this);
        mStatus.setTextColor(MUTED);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mStatus.setLineSpacing(dp(4), 1f);
        mStatus.setPadding(dp(4), dp(20), dp(4), 0);
        mStatus.setText(readyText());
        root.addView(mStatus);

        scroll.addView(root);
        setContentView(scroll);
    }

    private String readyText() {
        String ip = localIp();
        return "Ready.\n\nWhen projecting, connect a viewer to:\n\n    "
                + (ip != null ? ip : "<your Wi-Fi IP>") + ":" + ScreenStreamService.PORT
                + "\n\nAny H.264 viewer works, e.g.  ffplay tcp://" + (ip != null ? ip : "<ip>")
                + ":" + ScreenStreamService.PORT;
    }

    private void startCast() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm == null) { toast("Screen projection unavailable"); return; }
        try {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAST);
        } catch (Throwable t) {
            toast("Couldn't start projection");
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == REQ_CAST) {
            if (res == RESULT_OK && data != null) {
                Intent svc = new Intent(this, ScreenStreamService.class);
                svc.putExtra(ScreenStreamService.EXTRA_RESULT, res);
                svc.putExtra(ScreenStreamService.EXTRA_DATA, data);
                startForegroundService(svc);
                String ip = localIp();
                mStatus.setText("Projecting now.\n\nConnect your viewer to:\n\n    "
                        + (ip != null ? ip : "<your Wi-Fi IP>") + ":" + ScreenStreamService.PORT
                        + "\n\nThe stream is live until you tap Stop.");
            } else {
                toast("Projection cancelled");
            }
        }
    }

    private void stopCast() {
        Intent svc = new Intent(this, ScreenStreamService.class).setAction(ScreenStreamService.ACTION_STOP);
        startService(svc);
        mStatus.setText(readyText());
        toast("Projection stopped");
    }

    private String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private TextView subtitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(6), 0, dp(22));
        return t;
    }

    private TextView button(String label, int bg, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(bg);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
