/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * CircleNotes - notes list. Circle OS identity: true-black, brand blue,
 * typography-led. UI built programmatically (no layout drift).
 */
package za.co.circleos.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class NotesActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private NoteStore mStore;
    private List<NoteStore.Note> mNotes;
    private LinearLayout mListContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStore = new NoteStore(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(44), dp(20), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header: title + "+ New"
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Notes");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = new TextView(this);
        add.setText("+ New");
        add.setTextColor(ACCENT);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setPadding(dp(12), dp(8), dp(4), dp(8));
        add.setOnClickListener(v -> openEditor(0));
        header.addView(add);

        root.addView(header);

        mListContainer = new LinearLayout(this);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        root.addView(mListContainer, clp);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        mNotes = mStore.load();
        mListContainer.removeAllViews();
        if (mNotes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No notes yet. Tap “+ New” to write one.\n"
                    + "Notes stay on this device — never uploaded.");
            empty.setTextColor(DIM);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setPadding(0, dp(40), 0, 0);
            mListContainer.addView(empty);
            return;
        }
        for (NoteStore.Note n : mNotes) {
            mListContainer.addView(buildCard(n));
        }
    }

    private View buildCard(final NoteStore.Note n) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(TILE);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setOnClickListener(v -> openEditor(n.id));
        card.setOnLongClickListener(v -> {
            confirmDelete(n);
            return true;
        });

        TextView t = new TextView(this);
        t.setText(TextUtils.isEmpty(n.title) ? "(untitled)" : n.title);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(t);

        if (!TextUtils.isEmpty(n.body)) {
            TextView b = new TextView(this);
            b.setText(n.body.replace('\n', ' '));
            b.setTextColor(DIM);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setMaxLines(1);
            b.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(4);
            card.addView(b, blp);
        }

        TextView meta = new TextView(this);
        meta.setText(DateUtils.getRelativeTimeSpanString(n.updated));
        meta.setTextColor(ACCENT);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(6);
        card.addView(meta, mlp);

        return card;
    }

    private void openEditor(long id) {
        Intent i = new Intent(this, NoteEditorActivity.class);
        i.putExtra(NoteEditorActivity.EXTRA_ID, id);
        startActivity(i);
    }

    private void confirmDelete(final NoteStore.Note n) {
        new AlertDialog.Builder(this)
                .setTitle("Delete note?")
                .setMessage(TextUtils.isEmpty(n.title) ? "(untitled)" : n.title)
                .setPositiveButton("Delete", (d, w) -> {
                    mStore.delete(mNotes, n.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
