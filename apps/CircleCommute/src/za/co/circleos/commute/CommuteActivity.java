/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Commute (WP-28) — set your destination, arrive-by time and days; B!
 * proactively works out when to leave from your live location.
 */
package za.co.circleos.commute;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class CommuteActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;
    private static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private static final int REQ_LOC = 21;
    private static final int REQ_BG = 22;
    private static final int REQ_NOTIF = 23;
    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private SharedPreferences mP;
    private TextView mWorkVal;
    private TextView mTimeVal;
    private LinearLayout mDaysRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mP = Commute.prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(28));

        root.addView(title("Commute"));
        root.addView(subtitle("B! checks your live location and tells you when to leave to arrive on time."));

        root.addView(label("DESTINATION"));
        mWorkVal = valueRow(workSummary(), () -> setWorkToHere());
        root.addView(mWorkVal);

        root.addView(label("ARRIVE BY"));
        mTimeVal = valueRow(timeSummary(), this::pickTime);
        root.addView(mTimeVal);

        root.addView(label("ON THESE DAYS"));
        mDaysRow = new LinearLayout(this);
        mDaysRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(mDaysRow);
        renderDays();

        root.addView(gap(dp(14)));
        root.addView(switchRow("Proactive commute alerts", Commute.K_ENABLED, false));
        root.addView(hint("For alerts while the app is closed, allow location \"all the time\" when asked."));

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWorkVal.setText(workSummary());
    }

    /* ── destination ── */

    private void setWorkToHere() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        captureLocation();
    }

    private void captureLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        if (lm == null) { toast("Location unavailable"); return; }
        toast("Getting your location…");
        try {
            String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            lm.getCurrentLocation(provider, new CancellationSignal(), getMainExecutor(), loc -> {
                if (loc == null) { toast("Couldn't get a fix — try outdoors"); return; }
                mP.edit()
                        .putFloat(Commute.K_WORK_LAT, (float) loc.getLatitude())
                        .putFloat(Commute.K_WORK_LNG, (float) loc.getLongitude())
                        .putBoolean(Commute.K_WORK_SET, true)
                        .apply();
                mWorkVal.setText(workSummary());
                Commute.schedule(this);
                toast("Destination set to here");
            });
        } catch (Throwable t) {
            toast("Couldn't read location");
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ_LOC && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
            captureLocation();
        }
    }

    private String workSummary() {
        if (!mP.getBoolean(Commute.K_WORK_SET, false)) return "Tap to set your destination";
        return String.format(Locale.US, "Set  ·  %.4f, %.4f",
                mP.getFloat(Commute.K_WORK_LAT, 0), mP.getFloat(Commute.K_WORK_LNG, 0));
    }

    /* ── arrive-by ── */

    private void pickTime() {
        int h = mP.getInt(Commute.K_ARRIVE_H, 9);
        int m = mP.getInt(Commute.K_ARRIVE_M, 0);
        new TimePickerDialog(this, DIALOG, (view, hh, mm) -> {
            mP.edit().putInt(Commute.K_ARRIVE_H, hh).putInt(Commute.K_ARRIVE_M, mm).apply();
            mTimeVal.setText(timeSummary());
            Commute.schedule(this);
        }, h, m, false).show();
    }

    private String timeSummary() {
        int h = mP.getInt(Commute.K_ARRIVE_H, 9);
        int m = mP.getInt(Commute.K_ARRIVE_M, 0);
        int h12 = h % 12; if (h12 == 0) h12 = 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, h < 12 ? "AM" : "PM");
    }

    /* ── days ── */

    private void renderDays() {
        mDaysRow.removeAllViews();
        int days = mP.getInt(Commute.K_DAYS, Commute.DEFAULT_DAYS);
        for (int i = 0; i < 7; i++) {
            boolean on = (days & (1 << i)) != 0;
            TextView chip = new TextView(this);
            chip.setText(DAY_LETTERS[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextColor(on ? TEXT : MUTED);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            chip.setTypeface(chip.getTypeface(), Typeface.BOLD);
            chip.setBackgroundColor(on ? ACCENT : CARD);
            chip.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.rightMargin = (i < 6) ? dp(4) : 0;
            chip.setLayoutParams(lp);
            final int bit = i;
            chip.setOnClickListener(v -> {
                int d = mP.getInt(Commute.K_DAYS, Commute.DEFAULT_DAYS) ^ (1 << bit);
                mP.edit().putInt(Commute.K_DAYS, d).apply();
                renderDays();
                Commute.schedule(this);
            });
            mDaysRow.addView(chip);
        }
    }

    /* ── enable ── */

    private View switchRow(String labelText, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(CARD);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setLayoutParams(rowParams());

        TextView t = new TextView(this);
        t.setText(labelText);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        Switch sw = new Switch(this);
        sw.setChecked(mP.getBoolean(key, def));
        sw.setOnCheckedChangeListener((btn, v) -> {
            mP.edit().putBoolean(key, v).apply();
            if (v) requestRuntimeForBackground();
            Commute.schedule(this);
        });
        row.addView(sw);
        return row;
    }

    private void requestRuntimeForBackground() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG);
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
        t.setPadding(0, dp(6), 0, dp(14));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(18), 0, dp(8));
        return t;
    }

    private TextView valueRow(String value, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setBackgroundColor(CARD);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        t.setLayoutParams(rowParams());
        return t;
    }

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setPadding(dp(2), dp(8), dp(2), 0);
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

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
