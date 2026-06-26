/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Circle desktop shown on a connected external display: an app grid plus a
 * taskbar with the Circle button and a clock. Tapping an app launches it onto
 * this display. Freeform resizable windows are the depth follow-up.
 */
package za.co.circleos.desktop;

import android.app.ActivityOptions;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DesktopPresentation extends Presentation {

    private final int mDisplayId;
    private TextView mClock;
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            if (mClock != null) {
                mClock.setText(new SimpleDateFormat("EEE  HH:mm", Locale.getDefault()).format(new Date()));
            }
            mUi.postDelayed(this, 10_000);
        }
    };

    DesktopPresentation(Context outer, Display display) {
        super(outer, display);
        mDisplayId = display.getDisplayId();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        ScrollView scroll = new ScrollView(getContext());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setVerticalScrollBarEnabled(false);

        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(8);
        grid.setPadding(dp(40), dp(40), dp(40), dp(40));
        populate(grid);
        scroll.addView(grid);
        root.addView(scroll);

        root.addView(taskbar());

        setContentView(root);
        mTick.run();
    }

    @Override
    public void dismiss() {
        mUi.removeCallbacks(mTick);
        super.dismiss();
    }

    private View taskbar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF0A0A0A);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), dp(12), dp(24), dp(12));

        TextView circle = new TextView(getContext());
        circle.setText("Circle");
        circle.setTextColor(0xFF2196F3);
        circle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        circle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(circle);

        mClock = new TextView(getContext());
        mClock.setTextColor(0xFFFFFFFF);
        mClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        bar.addView(mClock);
        return bar;
    }

    private void populate(GridLayout grid) {
        PackageManager pm = getContext().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : apps) {
            grid.addView(appCell(ri.loadLabel(pm).toString(), ri.loadIcon(pm),
                    ri.activityInfo.packageName, ri.activityInfo.name));
        }
    }

    private View appCell(String label, Drawable icon, String pkg, String cls) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(14), dp(14), dp(14), dp(14));
        cell.setClickable(true);

        ImageView iv = new ImageView(getContext());
        iv.setImageDrawable(icon);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        cell.addView(iv);

        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setMaxLines(1);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(6), 0, 0);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(76),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(tlp);
        cell.addView(tv);

        cell.setOnClickListener(v -> launch(pkg, cls));
        return cell;
    }

    private void launch(String pkg, String cls) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName(pkg, cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(mDisplayId);
            getContext().startActivity(i, opts.toBundle());
        } catch (Throwable ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
