/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Notebook (WP-27) — the transparency screen for B!'s memory. Lists every
 * fact grouped by category, lets you add your own and "Forget" any single fact.
 * B! writes facts here through the provider; nothing is hidden from you.
 */
package za.co.circleos.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class NotebookActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private static final String[] CATEGORIES = {
            "About you", "Preferences", "People", "Places", "Other"
    };

    private LinearLayout mList;

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

        TextView title = new TextView(this);
        title.setText("Notebook");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("What B! knows about you — and you decide what stays.");
        sub.setTextColor(MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);

        TextView add = new TextView(this);
        add.setText("+   Add a note");
        add.setTextColor(ACCENT);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        add.setTypeface(add.getTypeface(), Typeface.BOLD);
        add.setPadding(dp(16), dp(14), dp(16), dp(14));
        add.setBackgroundColor(CARD);
        add.setClickable(true);
        add.setOnClickListener(v -> showAddDialog());
        root.addView(add);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(0, dp(8), 0, 0);
        root.addView(mList);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        mList.removeAllViews();
        Cursor c = null;
        int count = 0;
        try {
            c = getContentResolver().query(NotebookProvider.FACTS, null, null, null, null);
            String lastCat = null;
            if (c != null) {
                while (c.moveToNext()) {
                    count++;
                    long id = c.getLong(c.getColumnIndexOrThrow(NotebookStore.COL_ID));
                    String cat = str(c, NotebookStore.COL_CATEGORY, "Other");
                    String label = str(c, NotebookStore.COL_LABEL, "");
                    String value = str(c, NotebookStore.COL_VALUE, "");
                    String source = str(c, NotebookStore.COL_SOURCE, "");
                    long when = c.getLong(c.getColumnIndexOrThrow(NotebookStore.COL_CREATED));
                    if (!cat.equals(lastCat)) {
                        mList.addView(sectionHeader(cat));
                        lastCat = cat;
                    }
                    mList.addView(factCard(id, label, value, source, when));
                }
            }
        } catch (Throwable t) {
            // provider unavailable — fall through to empty state
        } finally {
            if (c != null) c.close();
        }
        if (count == 0) mList.addView(emptyState());
    }

    private View sectionHeader(String cat) {
        TextView t = new TextView(this);
        t.setText(cat.toUpperCase());
        t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(22), 0, dp(8));
        return t;
    }

    private View factCard(long id, String label, String value, String source, long when) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(CARD);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        card.setLayoutParams(lp);

        if (!TextUtils.isEmpty(label)) {
            TextView l = new TextView(this);
            l.setText(label);
            l.setTextColor(MUTED);
            l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            card.addView(l);
        }

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(TEXT);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setPadding(0, dp(2), 0, dp(6));
        card.addView(v);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);

        TextView meta = new TextView(this);
        String rel = when > 0 ? DateUtils.getRelativeTimeSpanString(when).toString() : "";
        meta.setText(TextUtils.isEmpty(rel) ? source
                : (TextUtils.isEmpty(source) ? rel : source + "  ·  " + rel));
        meta.setTextColor(MUTED);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        footer.addView(meta);

        TextView forget = new TextView(this);
        forget.setText("Forget");
        forget.setTextColor(ACCENT);
        forget.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        forget.setTypeface(forget.getTypeface(), Typeface.BOLD);
        forget.setPadding(dp(12), dp(4), 0, dp(4));
        forget.setClickable(true);
        forget.setOnClickListener(x -> confirmForget(id, value));
        footer.addView(forget);

        card.addView(footer);
        return card;
    }

    private View emptyState() {
        TextView t = new TextView(this);
        t.setText("B! hasn't noted anything yet.\n\nWhat it picks up as you chat will appear here — and you can delete anything, any time. You can also add your own notes above.");
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(dp(4), dp(40), dp(4), 0);
        return t;
    }

    private void confirmForget(long id, String value) {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Forget this?")
                .setMessage("B! will no longer remember:\n\n" + value)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Forget", (d, w) -> {
                    try {
                        getContentResolver().delete(
                                Uri.withAppendedPath(NotebookProvider.FACTS, String.valueOf(id)),
                                null, null);
                    } catch (Throwable t) {
                        toast("Couldn't remove that");
                    }
                    reload();
                })
                .show();
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);

        final int[] catIdx = {0};
        final TextView catBtn = new TextView(this);
        catBtn.setText("Category:   " + CATEGORIES[0]);
        catBtn.setTextColor(ACCENT);
        catBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        catBtn.setPadding(0, dp(8), 0, dp(14));
        catBtn.setClickable(true);
        catBtn.setOnClickListener(v -> {
            catIdx[0] = (catIdx[0] + 1) % CATEGORIES.length;
            catBtn.setText("Category:   " + CATEGORIES[catIdx[0]]);
        });
        box.addView(catBtn);

        final EditText label = new EditText(this);
        label.setHint("Label (e.g. Home address)");
        label.setSingleLine(true);
        box.addView(label);

        final EditText value = new EditText(this);
        value.setHint("Note");
        box.addView(value);

        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Add a note")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String val = value.getText().toString().trim();
                    if (TextUtils.isEmpty(val)) {
                        toast("Nothing to save");
                        return;
                    }
                    ContentValues cv = new ContentValues();
                    cv.put(NotebookStore.COL_CATEGORY, CATEGORIES[catIdx[0]]);
                    cv.put(NotebookStore.COL_LABEL, label.getText().toString().trim());
                    cv.put(NotebookStore.COL_VALUE, val);
                    cv.put(NotebookStore.COL_SOURCE, "You added this");
                    cv.put(NotebookStore.COL_CREATED, System.currentTimeMillis());
                    try {
                        getContentResolver().insert(NotebookProvider.FACTS, cv);
                    } catch (Throwable t) {
                        toast("Couldn't save");
                    }
                    reload();
                })
                .show();
    }

    private String str(Cursor c, String col, String def) {
        int i = c.getColumnIndex(col);
        if (i < 0) return def;
        String s = c.getString(i);
        return s != null ? s : def;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
