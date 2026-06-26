/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Find My Phone - setup screen. Opt-in, PIN-gated. Explains the SMS
 * commands and requests the permissions ring + locate need.
 */
package za.co.circleos.findmy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class FindMyActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int GREEN  = 0xFF1B5E20;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private SharedPreferences mSp;
    private EditText mPin;
    private TextView mToggle;
    private boolean mEnabled;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mSp = getSharedPreferences("circle_findmy", MODE_PRIVATE);
        mEnabled = mSp.getBoolean("enabled", false);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(h1("Find My Phone"));
        root.addView(body("Text a command to this phone from any other phone to make it "
                + "ring loudly or report its location. PIN-protected and opt-in."));

        root.addView(label("PIN (4+ digits)"));
        mPin = new EditText(this);
        mPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        mPin.setText(mSp.getString("pin", ""));
        mPin.setTextColor(TEXT);
        mPin.setBackgroundColor(TILE);
        mPin.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(mPin);

        mToggle = new TextView(this);
        mToggle.setTextColor(TEXT);
        mToggle.setTypeface(Typeface.DEFAULT_BOLD);
        mToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mToggle.setGravity(Gravity.CENTER);
        mToggle.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(20);
        mToggle.setLayoutParams(tlp);
        mToggle.setClickable(true);
        mToggle.setOnClickListener(v -> toggle());
        root.addView(mToggle);
        updateToggle();

        root.addView(body("\nCommands — text these to this phone:\n"
                + "    circle ring <PIN>\n"
                + "    circle locate <PIN>\n\n"
                + "Ring works immediately. For locate, set this app's Location permission to "
                + "“Allow all the time” so it can report position from the background."));

        setContentView(scroll);
        requestPerms();
    }

    private void toggle() {
        String pin = mPin.getText().toString().trim();
        if (!mEnabled) {
            if (pin.length() < 4) {
                Toast.makeText(this, "Set a PIN of at least 4 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            mSp.edit().putBoolean("enabled", true).putString("pin", pin).apply();
            mEnabled = true;
        } else {
            mSp.edit().putBoolean("enabled", false).apply();
            mEnabled = false;
        }
        updateToggle();
    }

    private void updateToggle() {
        mToggle.setText(mEnabled ? "Enabled — tap to disable" : "Enable Find My Phone");
        mToggle.setBackgroundColor(mEnabled ? GREEN : ACCENT);
    }

    private void requestPerms() {
        String[] perms = {
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
        };
        List<String> need = new ArrayList<>();
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1);
    }

    private TextView h1(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(6));
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
