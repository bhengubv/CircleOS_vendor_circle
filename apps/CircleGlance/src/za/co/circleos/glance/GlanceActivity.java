/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Glance (WP-54/74) — the always-on display surface. A true-black screen
 * with a large clock, date, battery and live notification count, shown over the
 * lock screen at low brightness. Tap to dismiss. Staying lit during idle is the
 * doze-integration follow-up.
 */
package za.co.circleos.glance;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class GlanceActivity extends Activity {

    private TextView mClock;
    private TextView mDate;
    private TextView mInfo;
    private BroadcastReceiver mTick;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.08f; // dim, AOD-style
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF000000);
        root.setOnClickListener(v -> finish());

        mClock = new TextView(this);
        mClock.setTextColor(0xFFFFFFFF);
        mClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 86);
        mClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        mClock.setGravity(Gravity.CENTER);
        root.addView(mClock);

        mDate = new TextView(this);
        mDate.setTextColor(0xFF9AA0A6);
        mDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        mDate.setGravity(Gravity.CENTER);
        mDate.setPadding(0, dp(8), 0, dp(28));
        root.addView(mDate);

        mInfo = new TextView(this);
        mInfo.setTextColor(0xFF2196F3);
        mInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mInfo.setGravity(Gravity.CENTER);
        root.addView(mInfo);

        setContentView(root);
        update();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTick = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { update(); }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mTick, f);
        update();
    }

    @Override
    protected void onPause() {
        if (mTick != null) {
            try { unregisterReceiver(mTick); } catch (Throwable ignored) {}
            mTick = null;
        }
        super.onPause();
    }

    private void update() {
        Date now = new Date();
        mClock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        mDate.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));

        StringBuilder sb = new StringBuilder();
        int batt = batteryPercent();
        if (batt >= 0) sb.append(batt).append("%");
        int n = GlanceListener.getCount();
        if (n > 0) {
            if (sb.length() > 0) sb.append("   ·   ");
            sb.append(n).append(n == 1 ? " notification" : " notifications");
        }
        mInfo.setText(sb.toString());
        mInfo.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private int batteryPercent() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1;
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return -1;
            return Math.round(level * 100f / scale);
        } catch (Throwable t) {
            return -1;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
