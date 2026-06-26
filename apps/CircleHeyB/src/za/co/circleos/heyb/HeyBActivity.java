/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle "Hey B" control — turn the always-listening wake trigger on/off.
 */
package za.co.circleos.heyb;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class HeyBActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;
    private static final int REQ_MIC = 5;

    private SharedPreferences mP;
    private Switch mSwitch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mP = getSharedPreferences("heyb", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("Hey B");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Say “Hey B” and your assistant opens — hands-free.");
        sub.setTextColor(MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setPadding(0, dp(8), 0, dp(24));
        root.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(CARD);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText("Listen for “Hey B”");
        label.setTextColor(TEXT);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        mSwitch = new Switch(this);
        mSwitch.setChecked(mP.getBoolean("on", false));
        mSwitch.setOnCheckedChangeListener((btn, v) -> toggle(v));
        row.addView(mSwitch);
        root.addView(row);

        TextView note = new TextView(this);
        note.setText("Always-listening uses the microphone continuously, which costs battery. The efficient on-device wake-word model is the next step. Nothing is sent off the device.");
        note.setTextColor(MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setPadding(dp(2), dp(14), dp(2), 0);
        root.addView(note);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void toggle(boolean on) {
        if (on) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                return;
            }
            mP.edit().putBoolean("on", true).apply();
            startForegroundService(new Intent(this, HeyBService.class));
        } else {
            mP.edit().putBoolean("on", false).apply();
            startService(new Intent(this, HeyBService.class).setAction(HeyBService.ACTION_STOP));
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ_MIC) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
                mP.edit().putBoolean("on", true).apply();
                startForegroundService(new Intent(this, HeyBService.class));
            } else {
                mSwitch.setChecked(false);
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
