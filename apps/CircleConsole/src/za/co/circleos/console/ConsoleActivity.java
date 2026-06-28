/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Console (SOS-A3) — a big-picture, controller-first front-end that runs
 * the device as a single-app "console session". Pick a game/app from a row of
 * large D-pad-navigable tiles and it launches full-screen, pinned via lock-task
 * (kiosk) so the session can't be wandered out of. Exit by holding Back.
 *
 * On a device provisioned as device-owner (with Circle Console + the target in
 * the lock-task allowlist) the pin is unbreakable; otherwise it falls back to
 * screen pinning, which the user confirms once. Programmatic UI, true-black,
 * brand accent only.
 */
package za.co.circleos.console;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConsoleActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int TILE = 0xFF141414;
    private static final int TILE_FOCUS = 0xFF2196F3;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9AA0A6;

    private LinearLayout mRow;
    private boolean mInSession;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(40), dp(40), dp(40), dp(28));

        TextView title = new TextView(this);
        title.setText("Console");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Pick a game or app to start a session. Hold Back to exit.");
        sub.setTextColor(DIM);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setPadding(0, dp(8), 0, dp(28));
        root.addView(sub);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        mRow = new LinearLayout(this);
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(mRow);
        root.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
        mInSession = false; // back in the console front-end
    }

    private void load() {
        mRow.removeAllViews();
        List<App> apps = queryApps();
        if (apps.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No launchable apps found.");
            t.setTextColor(DIM);
            mRow.addView(t);
            return;
        }
        boolean first = true;
        for (App a : apps) {
            View tile = tile(a);
            mRow.addView(tile);
            if (first) { tile.requestFocus(); first = false; }
        }
    }

    private View tile(final App a) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackgroundColor(TILE);
        tile.setPadding(dp(18), dp(18), dp(18), dp(18));
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setClickable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), dp(220));
        lp.rightMargin = dp(16);
        tile.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        if (a.icon != null) icon.setImageDrawable(a.icon);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView name = new TextView(this);
        name.setText(a.label);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(16), 0, 0);
        name.setSingleLine(true);
        tile.addView(name);

        tile.setOnFocusChangeListener((v, has) ->
                tile.setBackgroundColor(has ? TILE_FOCUS : TILE));
        tile.setOnClickListener(v -> startSession(a));
        return tile;
    }

    private void startSession(App a) {
        try {
            // Lock the session (kiosk). Unbreakable when device-owner-provisioned;
            // otherwise this requests screen pinning, which the user confirms once.
            try { startLockTask(); } catch (Throwable ignored) {}
            mInSession = true;
            Intent i = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (i == null) { Toast.makeText(this, "Can't launch " + a.label, Toast.LENGTH_SHORT).show(); return; }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Couldn't start the session", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            endSession();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking(); // enables onKeyLongPress
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void endSession() {
        try { stopLockTask(); } catch (Throwable ignored) {}
        immersive();
        Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show();
    }

    private List<App> queryApps() {
        List<App> out = new ArrayList<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        String self = getPackageName();
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(main, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (self.equals(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = ri.loadLabel(getPackageManager()).toString();
            try { a.icon = ri.loadIcon(getPackageManager()); } catch (Throwable ignored) {}
            // Surface Circle Play / games first.
            a.game = pkg.contains("circleplay") || pkg.contains("game") || pkg.contains("play");
            out.add(a);
        }
        Collections.sort(out, (x, y) -> {
            if (x.game != y.game) return x.game ? -1 : 1;
            return x.label.compareToIgnoreCase(y.label);
        });
        return out;
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class App {
        String pkg, label;
        Drawable icon;
        boolean game;
    }
}
