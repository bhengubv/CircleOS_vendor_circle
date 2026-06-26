/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Security — File DMZ + Quarantine (SEC-3/4). Files shared to it land in
 * an app-private airlock and are risk-assessed; you review, then Release (to
 * Downloads) or Delete. Nothing reaches the rest of the phone until you allow it.
 */
package za.co.circleos.security;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class CircleSecurityActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    private LinearLayout mList;
    private File mDir;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mDir = new File(getFilesDir(), "quarantine");
        if (!mDir.exists()) mDir.mkdirs();

        handleIncoming(getIntent());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("Security");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("A safe airlock for incoming files. Share anything here to scan it before it touches your phone.");
        sub.setTextColor(MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mList);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            if (list != null) uris.addAll(list);
        }
        int n = 0;
        for (Uri u : uris) {
            if (quarantine(u)) n++;
        }
        if (n > 0) toast(n + (n == 1 ? " file held in the airlock" : " files held in the airlock"));
    }

    private boolean quarantine(Uri uri) {
        try {
            String name = displayName(uri);
            File dest = new File(mDir, System.currentTimeMillis() + "__" + name);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                try (OutputStream out = new java.io.FileOutputStream(dest)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void reload() {
        mList.removeAllViews();
        File[] files = mDir.listFiles();
        if (files == null || files.length == 0) {
            mList.addView(empty());
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : files) mList.addView(card(f));
    }

    private View card(File f) {
        String display = original(f.getName());
        boolean risky = SecurityScan.isRisky(display);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(CARD);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(2);
        c.setLayoutParams(clp);

        TextView name = new TextView(this);
        name.setText(display);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(name);

        TextView why = new TextView(this);
        why.setText((risky ? "⚠  " : "✓  ") + SecurityScan.reason(display));
        why.setTextColor(risky ? TEXT : ACCENT);
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        why.setPadding(0, dp(4), 0, dp(8));
        c.addView(why);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView meta = new TextView(this);
        meta.setText(DateUtils.getRelativeTimeSpanString(f.lastModified()).toString());
        meta.setTextColor(MUTED);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(meta);

        actions.addView(action("Release", ACCENT, () -> release(f, display)));
        actions.addView(action("Delete", MUTED, () -> { f.delete(); reload(); }));
        c.addView(actions);
        return c;
    }

    private TextView action(String label, int color, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(16), dp(2), 0, dp(2));
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private void release(File f, String name) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download");
            Uri out = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (out == null) { toast("Couldn't release"); return; }
            try (InputStream in = new java.io.FileInputStream(f);
                 OutputStream os = getContentResolver().openOutputStream(out)) {
                if (os == null) { toast("Couldn't release"); return; }
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
            }
            f.delete();
            toast("Released to Downloads");
            reload();
        } catch (Throwable t) {
            toast("Couldn't release that file");
        }
    }

    private View empty() {
        TextView t = new TextView(this);
        t.setText("The airlock is empty.\n\nShare a file to Circle Security and it'll be scanned and held here for your review before it reaches the rest of your phone.");
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(dp(4), dp(36), dp(4), 0);
        return t;
    }

    private String original(String stored) {
        int i = stored.indexOf("__");
        return i >= 0 ? stored.substring(i + 2) : stored;
    }

    private String displayName(Uri uri) {
        String name = "file_" + System.currentTimeMillis();
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String s = c.getString(idx);
                    if (s != null && !s.isEmpty()) name = s;
                }
            }
        } catch (Throwable ignored) {
        }
        return name.replace("/", "_").replace("__", "_");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
