/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Achievements (WP-25) - identity + achievements tied to your real
 * privacy record. Badges are computed from live counters published by the
 * circle.privacy system service; nothing is invented.
 */
package za.co.circleos.achievements;

import android.app.Activity;
import android.circleos.privacy.ICirclePrivacyManagerService;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class AchievementsActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        SharedPreferences sp = getSharedPreferences("circle_ach", MODE_PRIVATE);
        long first = sp.getLong("first", 0L);
        if (first == 0L) {
            first = System.currentTimeMillis();
            sp.edit().putLong("first", first).apply();
        }
        long days = Math.max(1L, (System.currentTimeMillis() - first) / 86400000L + 1L);

        int denied = 0, faked = 0, grants = 0;
        boolean live = false;
        try {
            IBinder bn = ServiceManager.getService("circle.privacy");
            if (bn != null) {
                ICirclePrivacyManagerService svc =
                        ICirclePrivacyManagerService.Stub.asInterface(bn);
                denied = svc.getDeniedPermissionCount();
                faked = svc.getFakedIdentifierCount();
                grants = svc.getNetworkGrantCount();
                live = true;
            }
        } catch (Throwable t) {
            // privacy service unavailable on this build
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(h1("Achievements"));
        root.addView(sub(live ? "Your privacy, earned." : "Your privacy, earned. (Counters update on a Circle OS build.)"));

        root.addView(badge(tier(days, 7, 30, 100), "Day " + days,
                "Time in control of your phone."));
        root.addView(badge(tier(denied, 10, 100, 1000), denied + " blocked",
                "Permissions you denied apps."));
        root.addView(badge(tier(faked, 5, 50, 500), faked + " faked",
                "Identifiers hidden from trackers."));
        root.addView(badge("Curator", grants + " trusted",
                "Apps you chose to grant network access."));

        setContentView(scroll);
    }

    private View badge(String tier, String big, String desc) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(tier);
        t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);

        TextView bigv = new TextView(this);
        bigv.setText(big);
        bigv.setTextColor(TEXT);
        bigv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        bigv.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(bigv);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(DIM);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        c.addView(d);
        return c;
    }

    private String tier(long v, long s, long g, long p) {
        if (v >= p) return "Platinum";
        if (v >= g) return "Gold";
        if (v >= s) return "Silver";
        return "Bronze";
    }

    private TextView h1(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView sub(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(4), 0, dp(8));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
