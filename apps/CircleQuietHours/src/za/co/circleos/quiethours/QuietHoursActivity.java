/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Quiet Hours (WP-31) — schedule Do Not Disturb and choose who breaks
 * through. Starred contacts and repeat callers are the "inner circle".
 */
package za.co.circleos.quiethours;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public final class QuietHoursActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;
    private static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private SharedPreferences mP;
    private TextView mAccessNote;
    private TextView mStartVal;
    private TextView mEndVal;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mP = QuietHours.prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(28));

        root.addView(title("Quiet Hours"));
        root.addView(subtitle("Silence the noise on a schedule — and let the people who matter break through."));

        mAccessNote = new TextView(this);
        mAccessNote.setText("Allow Quiet Hours to control Do Not Disturb  →");
        mAccessNote.setTextColor(ACCENT);
        mAccessNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mAccessNote.setPadding(dp(16), dp(14), dp(16), dp(14));
        mAccessNote.setBackgroundColor(0xFF0E1726);
        mAccessNote.setClickable(true);
        mAccessNote.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Throwable ignored) {
            }
        });
        LinearLayout.LayoutParams anlp = rowParams();
        anlp.bottomMargin = dp(16);
        mAccessNote.setLayoutParams(anlp);
        root.addView(mAccessNote);

        root.addView(switchRow("Quiet Hours on a schedule", QuietHours.K_ENABLED, false));

        mStartVal = valueLabel();
        root.addView(timeRow("Starts", mStartVal, true));
        mEndVal = valueLabel();
        root.addView(timeRow("Ends", mEndVal, false));

        root.addView(gap(dp(10)));
        root.addView(switchRow("Let my inner circle through", QuietHours.K_INNER, true));
        root.addView(hint("Starred contacts, and anyone who calls twice within 15 minutes."));
        root.addView(switchRow("Allow alarms", QuietHours.K_ALARMS, true));

        scroll.addView(root);
        setContentView(scroll);
        refreshTimes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAccessNote.setVisibility(QuietHours.hasPolicyAccess(this) ? View.GONE : View.VISIBLE);
    }

    private void save() {
        QuietHours.schedule(this);
        if (!QuietHours.hasPolicyAccess(this)) mAccessNote.setVisibility(View.VISIBLE);
    }

    private View switchRow(String label, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(CARD);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setLayoutParams(rowParams());

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        Switch sw = new Switch(this);
        sw.setChecked(mP.getBoolean(key, def));
        sw.setOnCheckedChangeListener((btn, v) -> {
            mP.edit().putBoolean(key, v).apply();
            save();
        });
        row.addView(sw);
        return row;
    }

    private View timeRow(String label, TextView valueView, boolean isStart) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(CARD);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setClickable(true);
        row.setLayoutParams(rowParams());

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        row.addView(valueView);

        row.setOnClickListener(v -> {
            int h = mP.getInt(isStart ? QuietHours.K_START_H : QuietHours.K_END_H, isStart ? 22 : 7);
            int m = mP.getInt(isStart ? QuietHours.K_START_M : QuietHours.K_END_M, 0);
            new TimePickerDialog(this, DIALOG, (view, hh, mm) -> {
                mP.edit()
                        .putInt(isStart ? QuietHours.K_START_H : QuietHours.K_END_H, hh)
                        .putInt(isStart ? QuietHours.K_START_M : QuietHours.K_END_M, mm)
                        .apply();
                refreshTimes();
                save();
            }, h, m, false).show();
        });
        return row;
    }

    private void refreshTimes() {
        mStartVal.setText(fmt(mP.getInt(QuietHours.K_START_H, 22), mP.getInt(QuietHours.K_START_M, 0)));
        mEndVal.setText(fmt(mP.getInt(QuietHours.K_END_H, 7), mP.getInt(QuietHours.K_END_M, 0)));
    }

    private String fmt(int h, int m) {
        int hour12 = h % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.US, "%d:%02d %s", hour12, m, h < 12 ? "AM" : "PM");
    }

    private TextView valueLabel() {
        TextView t = new TextView(this);
        t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

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

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setPadding(dp(16), dp(6), dp(16), dp(12));
        return t;
    }

    private View gap(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        return v;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
