/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Start — the Circle OS home screen (WP-04/05/10/11/12/13/78). A
 * true-black canvas of live, resizable, pinnable tiles, with a pivot to an
 * alphabetical all-apps list. Programmatic UI (no XML), matching the other Circle
 * apps. Brand palette only: true-black #000, accent #2196F3, white text.
 */
package za.co.circleos.start;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CircleStartActivity extends Activity {

    private static final int BG = 0xFF000000;       // true black
    private static final int TILE = 0xFF1A1A1A;     // app-tile surface
    private static final int ACCENT = 0xFF2196F3;   // brand blue
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9AA0A6;

    private TileStore mStore;
    private List<TileStore.Tile> mTiles;
    private List<AppEntry> mApps;

    private FrameLayout mContent;
    private TextView mTabStart, mTabApps;
    private boolean mShowingApps = false;

    private TextView mClockView, mDateView, mBatteryView;
    private GestureDetector mGestures;

    private int mCell, mGap, mPad;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mStore = new TileStore(this);

        mGap = dp(8);
        mPad = dp(16);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        mCell = (screenW - mPad * 2 - mGap * 3) / 4; // 4 columns

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(mPad, dp(36), mPad, 0);

        // Pivot header
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        mTabStart = tab("start", true);
        mTabApps = tab("all apps", false);
        mTabStart.setOnClickListener(v -> showStart());
        mTabApps.setOnClickListener(v -> showApps());
        tabs.addView(mTabStart);
        tabs.addView(mTabApps);
        root.addView(tabs);

        mContent = new FrameLayout(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clp.topMargin = dp(12);
        mContent.setLayoutParams(clp);
        root.addView(mContent);

        setContentView(root);

        mGestures = new GestureDetector(this, new SwipeListener());

        loadData();
        showStart();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mGestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Live tiles refresh while we're foreground.
        registerReceiver(mTick, new IntentFilter(Intent.ACTION_TIME_TICK));
        Intent batt = registerReceiver(mBattery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateClock();
        if (batt != null) updateBattery(batt);
        // Apps may have been installed/removed; refresh both views.
        loadData();
        if (mShowingApps) showApps(); else showStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(mTick); } catch (Throwable ignored) {}
        try { unregisterReceiver(mBattery); } catch (Throwable ignored) {}
    }

    @Override
    public void onBackPressed() {
        // Home is the root — back returns to Start, never exits.
        if (mShowingApps) showStart();
    }

    // ── data ──

    private void loadData() {
        mApps = queryApps();
        mTiles = mStore.load();
        if (!mStore.isSeeded()) {
            seedDefaults();
            mStore.markSeeded();
            mStore.save(mTiles);
        }
    }

    private void seedDefaults() {
        mTiles = new ArrayList<>();
        mTiles.add(new TileStore.Tile(TileStore.TYPE_CLOCK, "", "", TileStore.WIDE));
        mTiles.add(new TileStore.Tile(TileStore.TYPE_BATTERY, "", "", TileStore.SMALL));
        // Pin a few common Circle apps if present.
        String[] seed = {
                "za.co.circleos.messages", "za.co.circleos.circlemaps",
                "za.co.circleos.circlephotos", "za.co.circleos.settings",
                "za.co.circleos.circleplay", "za.co.circleos.butler"
        };
        for (String pkg : seed) {
            AppEntry e = findApp(pkg);
            if (e != null) {
                mTiles.add(new TileStore.Tile(TileStore.TYPE_APP, e.pkg, e.cls,
                        "za.co.circleos.messages".equals(pkg) ? TileStore.MEDIUM : TileStore.SMALL));
            }
        }
    }

    private List<AppEntry> queryApps() {
        List<AppEntry> out = new ArrayList<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = getPackageManager().queryIntentActivities(main, 0);
        String self = getPackageName();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (self.equals(pkg)) continue;
            AppEntry e = new AppEntry();
            e.pkg = pkg;
            e.cls = ri.activityInfo.name;
            e.label = ri.loadLabel(getPackageManager()).toString();
            try { e.icon = ri.loadIcon(getPackageManager()); } catch (Throwable ignored) {}
            out.add(e);
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private AppEntry findApp(String pkg) {
        for (AppEntry e : mApps) if (e.pkg.equals(pkg)) return e;
        return null;
    }

    // ── pivot ──

    private void showStart() {
        mShowingApps = false;
        styleTab(mTabStart, true);
        styleTab(mTabApps, false);
        mContent.removeAllViews();
        mContent.addView(buildStart());
    }

    private void showApps() {
        mShowingApps = true;
        styleTab(mTabStart, false);
        styleTab(mTabApps, true);
        mContent.removeAllViews();
        mContent.addView(buildApps());
    }

    // ── Start: live-tile grid ──

    private View buildStart() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        FrameLayout grid = new FrameLayout(this);

        boolean[][] occ = new boolean[64][4]; // plenty of rows
        int maxRow = 0;
        for (TileStore.Tile t : mTiles) {
            int[] wh = spanOf(t.size);
            int w = wh[0], h = wh[1];
            int[] rc = firstFree(occ, w, h);
            int r = rc[0], c = rc[1];
            for (int rr = r; rr < r + h; rr++)
                for (int cc = c; cc < c + w; cc++) occ[rr][cc] = true;
            maxRow = Math.max(maxRow, r + h);

            View tile = buildTile(t, w, h);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    w * mCell + (w - 1) * mGap, h * mCell + (h - 1) * mGap);
            lp.leftMargin = c * (mCell + mGap);
            lp.topMargin = r * (mCell + mGap);
            grid.addView(tile, lp);
        }
        grid.setMinimumHeight(maxRow * (mCell + mGap) + dp(80));
        sv.addView(grid);
        return sv;
    }

    private View buildTile(TileStore.Tile t, int w, int h) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundColor(TileStore.TYPE_APP.equals(t.type) ? TILE : ACCENT);

        if (TileStore.TYPE_CLOCK.equals(t.type)) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_VERTICAL);
            col.setPadding(dp(14), dp(10), dp(14), dp(10));
            mClockView = new TextView(this);
            mClockView.setTextColor(TEXT);
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            mClockView.setTypeface(Typeface.DEFAULT_BOLD);
            mDateView = new TextView(this);
            mDateView.setTextColor(0xCCFFFFFF);
            mDateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            col.addView(mClockView);
            col.addView(mDateView);
            tile.addView(col);
            updateClock();
        } else if (TileStore.TYPE_BATTERY.equals(t.type)) {
            mBatteryView = new TextView(this);
            mBatteryView.setTextColor(TEXT);
            mBatteryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            mBatteryView.setTypeface(Typeface.DEFAULT_BOLD);
            mBatteryView.setGravity(Gravity.CENTER);
            mBatteryView.setText("🔋");
            tile.addView(mBatteryView, centered());
        } else {
            AppEntry e = findApp(t.pkg);
            ImageView icon = new ImageView(this);
            if (e != null && e.icon != null) icon.setImageDrawable(e.icon);
            else icon.setImageResource(android.R.drawable.sym_def_app_icon);
            int isz = dp(36);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(isz, isz);
            ip.gravity = Gravity.CENTER;
            tile.addView(icon, ip);
            if (h > 1 || w > 1) {
                TextView label = new TextView(this);
                label.setText(e != null ? e.label : t.pkg);
                label.setTextColor(TEXT);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                label.setSingleLine(true);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.BOTTOM | Gravity.START;
                lp.leftMargin = dp(10);
                lp.bottomMargin = dp(8);
                tile.addView(label, lp);
            }
        }

        tile.setOnClickListener(v -> onTileTap(t));
        tile.setOnLongClickListener(v -> { onTileLongPress(t); return true; });
        return tile;
    }

    private void onTileTap(TileStore.Tile t) {
        if (TileStore.TYPE_APP.equals(t.type)) launch(t.pkg, t.cls);
        else if (TileStore.TYPE_CLOCK.equals(t.type)) launchAction(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
        else if (TileStore.TYPE_BATTERY.equals(t.type)) launchAction(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS);
    }

    private void onTileLongPress(TileStore.Tile t) {
        final String[] opts = {"Resize", TileStore.TYPE_APP.equals(t.type) ? "Unpin" : "Remove"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Tile")
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        t.size = (t.size + 1) % 3; // cycle SMALL -> MEDIUM -> WIDE
                        mStore.save(mTiles);
                        showStart();
                    } else {
                        mTiles.remove(t);
                        mStore.save(mTiles);
                        showStart();
                    }
                })
                .show();
    }

    // ── All apps: alphabetical + A-Z jump ──

    private View buildApps() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        final ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        final java.util.HashMap<Character, View> sections = new java.util.HashMap<>();
        char last = 0;
        for (AppEntry e : mApps) {
            char first = Character.toUpperCase(e.label.charAt(0));
            if (!Character.isLetter(first)) first = '#';
            View appRow = appRow(e);
            if (first != last) { sections.put(first, appRow); last = first; }
            list.addView(appRow);
        }
        ScrollView.LayoutParams svlp = new ScrollView.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        sv.setLayoutParams(svlp);
        sv.addView(list);
        row.addView(sv);

        // A-Z index strip
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setGravity(Gravity.CENTER);
        strip.setPadding(dp(6), 0, dp(2), 0);
        for (char ch = 'A'; ch <= 'Z'; ch++) {
            final char c = ch;
            TextView l = new TextView(this);
            l.setText(String.valueOf(c));
            l.setTextColor(sections.containsKey(c) ? ACCENT : 0xFF444444);
            l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            l.setPadding(dp(4), dp(1), dp(4), dp(1));
            l.setOnClickListener(v -> {
                View target = sections.get(c);
                if (target != null) sv.post(() -> sv.smoothScrollTo(0, target.getTop()));
            });
            strip.addView(l);
        }
        row.addView(strip);
        return row;
    }

    private View appRow(final AppEntry e) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(6), dp(10), dp(6), dp(10));
        r.setClickable(true);

        ImageView icon = new ImageView(this);
        if (e.icon != null) icon.setImageDrawable(e.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.rightMargin = dp(16);
        r.addView(icon, ip);

        TextView name = new TextView(this);
        name.setText(e.label);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        r.addView(name);

        r.setOnClickListener(v -> launch(e.pkg, e.cls));
        r.setOnLongClickListener(v -> { pinDialog(e); return true; });
        return r;
    }

    private void pinDialog(final AppEntry e) {
        boolean pinned = mStore.isPinned(mTiles, e.pkg);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.label)
                .setItems(new String[]{pinned ? "Already pinned to Start" : "Pin to Start"},
                        (d, w) -> {
                            if (!pinned) {
                                mTiles.add(new TileStore.Tile(TileStore.TYPE_APP, e.pkg, e.cls, TileStore.SMALL));
                                mStore.save(mTiles);
                                showStart();
                            }
                        })
                .show();
    }

    // ── live tiles ──

    private final BroadcastReceiver mTick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver mBattery = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateBattery(i); }
    };

    private void updateClock() {
        if (mClockView == null) return;
        Date now = new Date();
        mClockView.setText(DateFormat.getTimeFormat(this).format(now));
        if (mDateView != null) {
            mDateView.setText(DateFormat.format("EEEE d MMMM", now).toString());
        }
    }

    private void updateBattery(Intent i) {
        if (mBatteryView == null || i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level >= 0 && scale > 0) {
            mBatteryView.setText(Math.round(level * 100f / scale) + "%");
        }
    }

    // ── helpers ──

    private void launch(String pkg, String cls) {
        try {
            Intent i;
            if (cls != null && !cls.isEmpty()) {
                i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                i.setComponent(new ComponentName(pkg, cls));
            } else {
                i = getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
            }
        } catch (Throwable ignored) {
        }
    }

    private void launchAction(String action) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable ignored) {}
    }

    private static int[] spanOf(int size) {
        switch (size) {
            case TileStore.WIDE: return new int[]{4, 2};
            case TileStore.MEDIUM: return new int[]{2, 2};
            default: return new int[]{1, 1};
        }
    }

    private static int[] firstFree(boolean[][] occ, int w, int h) {
        for (int r = 0; r + h <= occ.length; r++) {
            for (int c = 0; c + w <= 4; c++) {
                boolean ok = true;
                for (int rr = r; rr < r + h && ok; rr++)
                    for (int cc = c; cc < c + w; cc++)
                        if (occ[rr][cc]) { ok = false; break; }
                if (ok) return new int[]{r, c};
            }
        }
        return new int[]{occ.length - h, 0};
    }

    private TextView tab(String text, boolean active) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, dp(20), dp(8));
        styleTab(t, active);
        return t;
    }

    private void styleTab(TextView t, boolean active) {
        t.setTextColor(active ? TEXT : DIM);
    }

    private FrameLayout.LayoutParams centered() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        String pkg, cls, label;
        Drawable icon;
    }

    private final class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (e1 == null || e2 == null) return false;
            float dx = e2.getX() - e1.getX();
            if (Math.abs(dx) > dp(60) && Math.abs(dx) > Math.abs(e2.getY() - e1.getY())) {
                if (dx < 0) showApps(); else showStart();
                return true;
            }
            return false;
        }
    }
}
