/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Kid's Corner (WP-50) - a PIN-gated child mode. The parent picks the
 * allowed apps and an exit PIN; the phone locks to just those apps (screen
 * pinning) until the PIN is entered. On-device, no account.
 */
package za.co.circleos.kids;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KidsCornerActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private SharedPreferences mSp;
    private boolean mKidMode;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mSp = getSharedPreferences("circle_kids", MODE_PRIVATE);
        if (mSp.getString("pin", "").isEmpty()) showSetup();
        else showKid();
    }

    // ------------------------------------------------------------------ setup
    private void showSetup() {
        mKidMode = false;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(h1("Kid's Corner"));
        root.addView(body("Pick the apps your child may use, set an exit PIN, and start. "
                + "The phone locks to just these apps until you enter the PIN."));

        root.addView(label("Exit PIN (4+ digits)"));
        final EditText pin = new EditText(this);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setText(mSp.getString("pin", ""));
        pin.setTextColor(TEXT);
        pin.setBackgroundColor(TILE);
        pin.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(pin);

        root.addView(label("Allowed apps"));
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        Set<String> allowed = mSp.getStringSet("allowed", new HashSet<>());
        final List<CheckBox> boxes = new ArrayList<>();
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText("  " + ri.loadLabel(pm));
            cb.setTag(pkg);
            cb.setTextColor(TEXT);
            cb.setChecked(allowed.contains(pkg));
            boxes.add(cb);
            root.addView(cb);
        }

        TextView start = button("Start Kid's Corner");
        start.setOnClickListener(v -> {
            String p = pin.getText().toString().trim();
            if (p.length() < 4) { toast("Set a PIN of at least 4 digits"); return; }
            Set<String> sel = new HashSet<>();
            for (CheckBox cb : boxes) if (cb.isChecked()) sel.add((String) cb.getTag());
            if (sel.isEmpty()) { toast("Pick at least one app"); return; }
            mSp.edit().putString("pin", p).putStringSet("allowed", sel).apply();
            showKid();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(20);
        root.addView(start, slp);

        setContentView(scroll);
    }

    // --------------------------------------------------------------- kid mode
    private void showKid() {
        mKidMode = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(48), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("Kid's Corner");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setOnLongClickListener(v -> { promptExit(); return true; });
        root.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(0, dp(20), 0, 0);

        PackageManager pm = getPackageManager();
        Set<String> allowed = mSp.getStringSet("allowed", new HashSet<>());
        for (String pkg : allowed) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            Drawable icon;
            CharSequence name;
            try {
                icon = pm.getApplicationIcon(pkg);
                name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            } catch (Throwable t) {
                continue;
            }
            grid.addView(appTile(icon, name, launch));
        }
        root.addView(grid);
        setContentView(root);

        try { startLockTask(); } catch (Throwable t) { /* allowlist/confirm dependent */ }
    }

    private View appTile(Drawable icon, CharSequence name, final Intent launch) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(8), dp(16), dp(8), dp(16));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = dp(100);
        glp.setGravity(Gravity.CENTER);
        cell.setLayoutParams(glp);
        cell.setClickable(true);
        cell.setOnClickListener(v -> {
            try {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            } catch (Throwable t) { /* ignore */ }
        });

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        cell.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextColor(TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(6);
        cell.addView(tv, tlp);
        return cell;
    }

    private void promptExit() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Exit Kid's Corner")
                .setMessage("Enter the PIN")
                .setView(in)
                .setPositiveButton("Exit", (d, w) -> {
                    if (mSp.getString("pin", "").equals(in.getText().toString().trim())) {
                        try { stopLockTask(); } catch (Throwable t) { /* ignore */ }
                        showSetup();
                    } else {
                        toast("Wrong PIN");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (!mKidMode) super.onBackPressed(); // swallow back inside Kid's Corner
    }

    private TextView h1(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }
    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }
    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }
    private TextView button(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(0xFF000000);
        t.setBackgroundColor(ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        return t;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
