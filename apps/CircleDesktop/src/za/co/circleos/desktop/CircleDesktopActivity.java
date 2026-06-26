/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Desktop (WP-42-44) — when an external display is connected, show the
 * Circle desktop on it. This controller screen runs on the phone and manages the
 * desktop window(s) on any presentation display.
 */
package za.co.circleos.desktop;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class CircleDesktopActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    private DisplayManager mDm;
    private final SparseArray<DesktopPresentation> mShown = new SparseArray<>();
    private TextView mStatus;

    private final DisplayManager.DisplayListener mListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { maybeShow(id); updateStatus(); }
        @Override public void onDisplayRemoved(int id) { dismiss(id); updateStatus(); }
        @Override public void onDisplayChanged(int id) { }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mDm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("Desktop Mode");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Connect your phone to a monitor or TV and your Circle desktop appears on the big screen — your phone becomes the trackpad.");
        sub.setTextColor(MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setPadding(0, dp(8), 0, dp(24));
        root.addView(sub);

        mStatus = new TextView(this);
        mStatus.setTextColor(ACCENT);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mStatus.setBackgroundColor(0xFF111111);
        mStatus.setPadding(dp(16), dp(18), dp(16), dp(18));
        mStatus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mStatus);

        TextView note = new TextView(this);
        note.setText("Apps launch onto the external display. Resizable, overlapping windows are the next step.");
        note.setTextColor(MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setPadding(dp(2), dp(14), dp(2), 0);
        root.addView(note);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDm != null) {
            mDm.registerDisplayListener(mListener, null);
            for (Display d : mDm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
                maybeShow(d.getDisplayId());
            }
        }
        updateStatus();
    }

    @Override
    protected void onPause() {
        if (mDm != null) mDm.unregisterDisplayListener(mListener);
        for (int i = 0; i < mShown.size(); i++) {
            try { mShown.valueAt(i).dismiss(); } catch (Throwable ignored) {}
        }
        mShown.clear();
        super.onPause();
    }

    private void maybeShow(int id) {
        if (id == Display.DEFAULT_DISPLAY || mDm == null) return;
        if (mShown.get(id) != null) return;
        Display d = mDm.getDisplay(id);
        if (d == null || (d.getFlags() & Display.FLAG_PRESENTATION) == 0) {
            if (d == null) return;
        }
        try {
            DesktopPresentation p = new DesktopPresentation(this, d);
            p.show();
            mShown.put(id, p);
        } catch (Throwable ignored) {
        }
    }

    private void dismiss(int id) {
        DesktopPresentation p = mShown.get(id);
        if (p != null) {
            try { p.dismiss(); } catch (Throwable ignored) {}
            mShown.remove(id);
        }
    }

    private void updateStatus() {
        int n = (mDm == null) ? 0
                : mDm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).length;
        if (n > 0) {
            mStatus.setText("Connected — your desktop is on the external screen.");
        } else {
            mStatus.setText("No external display yet.\nConnect a monitor (USB-C/HDMI) or cast to a screen.");
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
