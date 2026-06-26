/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * CircleNotes - note editor. Saves automatically on pause; no save button.
 * An empty note is discarded rather than stored.
 */
package za.co.circleos.notes;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.List;

public final class NoteEditorActivity extends Activity {

    static final String EXTRA_ID = "note_id";

    private static final int BG   = 0xFF000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0x66FFFFFF;

    private NoteStore mStore;
    private List<NoteStore.Note> mNotes;
    private long mId;
    private EditText mTitle;
    private EditText mBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStore = new NoteStore(this);
        mNotes = mStore.load();
        mId = getIntent().getLongExtra(EXTRA_ID, 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(44), dp(20), dp(20));

        mTitle = new EditText(this);
        mTitle.setHint("Title");
        mTitle.setTextColor(TEXT);
        mTitle.setHintTextColor(HINT);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTitle.setBackground(null);
        mTitle.setMaxLines(2);
        mTitle.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(mTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mBody = new EditText(this);
        mBody.setHint("Write something…");
        mBody.setTextColor(TEXT);
        mBody.setHintTextColor(HINT);
        mBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mBody.setBackground(null);
        mBody.setGravity(Gravity.TOP | Gravity.START);
        mBody.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = dp(8);
        root.addView(mBody, blp);

        setContentView(root);

        if (mId != 0) {
            NoteStore.Note n = mStore.find(mNotes, mId);
            if (n != null) {
                mTitle.setText(n.title);
                mBody.setText(n.body);
                mBody.requestFocus();
            }
        } else {
            mTitle.requestFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        String t = mTitle.getText().toString().trim();
        String b = mBody.getText().toString().trim();
        if (TextUtils.isEmpty(t) && TextUtils.isEmpty(b)) {
            if (mId != 0) mStore.delete(mNotes, mId); // discard emptied note
            return;
        }
        NoteStore.Note saved = mStore.upsert(mNotes, mId, t, b);
        mId = saved.id; // subsequent pauses update, not duplicate
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
