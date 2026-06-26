/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Helper screen — enable Circle Keyboard in system settings, switch to it, and
 * try it in a sample field. Also the IME's settings activity.
 */
package za.co.circleos.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class CircleKeyboardActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(28));

        root.addView(title("Circle Keyboard"));
        root.addView(subtitle("A clean, private keyboard — true black, no data sent anywhere."));

        root.addView(step("1", "Enable it in your keyboard settings"));
        root.addView(button("Open keyboard settings", () -> {
            try { startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); }
            catch (Throwable ignored) {}
        }));

        root.addView(step("2", "Switch your active keyboard to Circle"));
        root.addView(button("Switch keyboard", () -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));

        root.addView(step("3", "Try it out below"));
        EditText tryIt = new EditText(this);
        tryIt.setHint("Type here to test Circle Keyboard…");
        tryIt.setHintTextColor(MUTED);
        tryIt.setTextColor(TEXT);
        tryIt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tryIt.setBackgroundColor(CARD);
        tryIt.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(8);
        tryIt.setLayoutParams(elp);
        root.addView(tryIt);

        scroll.addView(root);
        setContentView(scroll);
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
        t.setPadding(0, dp(6), 0, dp(22));
        return t;
    }

    private TextView step(String n, String label) {
        TextView t = new TextView(this);
        t.setText(n + "   " + label);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setPadding(0, dp(18), 0, dp(8));
        return t;
    }

    private TextView button(String label, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackgroundColor(ACCENT);
        t.setPadding(dp(16), dp(15), dp(16), dp(15));
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
