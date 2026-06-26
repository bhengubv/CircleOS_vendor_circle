/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle "Me" (WP-19) - one place to post a status to all your connected
 * networks (via the Circle social bridge) and read the replies coming back.
 */
package za.co.circleos.me;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public final class MeActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private EditText mComposer;
    private LinearLayout mFeed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(44), dp(20), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Me");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        // Composer
        mComposer = new EditText(this);
        mComposer.setHint("Share to all your networks…");
        mComposer.setTextColor(TEXT);
        mComposer.setHintTextColor(0x66FFFFFF);
        mComposer.setBackgroundColor(TILE);
        mComposer.setPadding(dp(14), dp(12), dp(14), dp(12));
        mComposer.setMinLines(3);
        mComposer.setGravity(Gravity.TOP | Gravity.START);
        mComposer.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(14);
        root.addView(mComposer, clp);

        TextView post = new TextView(this);
        post.setText("Post");
        post.setTextColor(0xFF000000);
        post.setBackgroundColor(ACCENT);
        post.setTypeface(Typeface.DEFAULT_BOLD);
        post.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        post.setGravity(Gravity.CENTER);
        post.setPadding(0, dp(12), 0, dp(12));
        post.setClickable(true);
        post.setOnClickListener(v -> doPost());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        root.addView(post, plp);

        TextView heading = new TextView(this);
        heading.setText("Replies");
        heading.setTextColor(ACCENT);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(24);
        hlp.bottomMargin = dp(8);
        root.addView(heading, hlp);

        mFeed = new LinearLayout(this);
        mFeed.setOrientation(LinearLayout.VERTICAL);
        root.addView(mFeed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReplies();
    }

    private void doPost() {
        final String text = mComposer.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        new Thread(() -> {
            try {
                Bridge.post(text);
                runOnUiThread(() -> {
                    mComposer.setText("");
                    toast("Posted");
                    loadReplies();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Couldn't post — check your bridge connection"));
            }
        }).start();
    }

    private void loadReplies() {
        message("Loading replies…");
        new Thread(() -> {
            try {
                final JSONArray arr = Bridge.replies();
                runOnUiThread(() -> showReplies(arr));
            } catch (Exception e) {
                runOnUiThread(() -> message("No replies to show yet."));
            }
        }).start();
    }

    private void showReplies(JSONArray arr) {
        mFeed.removeAllViews();
        if (arr.length() == 0) {
            message("No replies to show yet.");
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            mFeed.addView(replyCard(o.optString("author", "Someone"), o.optString("text", "")));
        }
    }

    private View replyCard(String author, String text) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);

        TextView a = new TextView(this);
        a.setText(author);
        a.setTextColor(ACCENT);
        a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        a.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(a);

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(2);
        c.addView(t, tlp);
        return c;
    }

    private void message(String msg) {
        mFeed.removeAllViews();
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(8), 0, 0);
        mFeed.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
