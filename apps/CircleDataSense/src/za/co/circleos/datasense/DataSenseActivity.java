/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Data Sense (WP-51) - see where your mobile + Wi-Fi data goes, per app,
 * over the last 30 days. Reads NetworkStatsManager (needs Usage Access). All
 * on-device; nothing leaves the phone.
 */
package za.co.circleos.datasense;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DataSenseActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private LinearLayout mRoot;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setPadding(dp(20), dp(44), dp(20), dp(24));
        scroll.addView(mRoot, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        mRoot.removeAllViews();
        mRoot.addView(h1("Data Sense"));
        if (!hasUsageAccess()) {
            mRoot.addView(body("Data Sense needs Usage Access to read per-app data usage. "
                    + "It stays on your phone."));
            TextView grant = button("Grant usage access");
            grant.setOnClickListener(v -> {
                try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
                catch (Throwable t) { /* ignore */ }
            });
            mRoot.addView(grant);
            return;
        }
        loadStats();
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager aom = getSystemService(AppOpsManager.class);
            int mode = aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void loadStats() {
        long end = System.currentTimeMillis();
        long start = end - 30L * 24 * 3600 * 1000L;
        NetworkStatsManager nsm = getSystemService(NetworkStatsManager.class);
        long total = 0L;
        Map<Integer, Long> perUid = new HashMap<>();

        if (nsm != null) {
            for (int type : new int[]{ConnectivityManager.TYPE_WIFI,
                    ConnectivityManager.TYPE_MOBILE}) {
                try {
                    NetworkStats s = nsm.querySummary(type, null, start, end);
                    if (s != null) {
                        NetworkStats.Bucket bk = new NetworkStats.Bucket();
                        while (s.hasNextBucket()) {
                            s.getNextBucket(bk);
                            long bytes = bk.getRxBytes() + bk.getTxBytes();
                            total += bytes;
                            Long cur = perUid.get(bk.getUid());
                            perUid.put(bk.getUid(), (cur == null ? 0L : cur) + bytes);
                        }
                        s.close();
                    }
                } catch (Throwable t) {
                    // this network type unavailable on the device
                }
            }
        }

        mRoot.addView(big(human(total), "Last 30 days, all apps"));

        PackageManager pm = getPackageManager();
        List<Map.Entry<Integer, Long>> list = new ArrayList<>(perUid.entrySet());
        Collections.sort(list, (a, c) -> Long.compare(c.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<Integer, Long> e : list) {
            if (shown >= 25) break;
            if (e.getValue() <= 0L) continue;
            String label = labelForUid(pm, e.getKey());
            if (label == null) continue;
            mRoot.addView(row(label, human(e.getValue())));
            shown++;
        }
        if (shown == 0) {
            mRoot.addView(body("No per-app usage recorded yet."));
        }
    }

    private String labelForUid(PackageManager pm, int uid) {
        String[] pkgs = pm.getPackagesForUid(uid);
        if (pkgs == null || pkgs.length == 0) return null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkgs[0], 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Throwable t) {
            return pkgs[0];
        }
    }

    private String human(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private View row(String label, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        c.setLayoutParams(lp);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(TEXT);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        l.setMaxLines(1);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(ACCENT);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(v);
        return c;
    }

    private View big(String value, String sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(TEXT);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(v);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(DIM);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        c.addView(s);
        return c;
    }

    private TextView h1(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(12), 0, dp(8));
        return t;
    }

    private TextView button(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF000000);
        t.setBackgroundColor(ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
