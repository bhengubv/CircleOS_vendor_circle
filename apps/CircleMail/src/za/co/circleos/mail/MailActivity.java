/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Mail (WP-24) - inbox + read + compose over the Circle mail bridge.
 * (Calendar lives in the AOSP Calendar app; this is the mail half.)
 */
package za.co.circleos.mail;

import android.app.Activity;
import android.app.AlertDialog;
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

public final class MailActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private TextView mTitle;
    private TextView mAction;
    private LinearLayout mContainer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        MailCrypto.init(getApplicationContext());
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(44), dp(20), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        mTitle = new TextView(this);
        mTitle.setTextColor(TEXT);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTitle.setMaxLines(1);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(mTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mAction = new TextView(this);
        mAction.setTextColor(ACCENT);
        mAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mAction.setTypeface(Typeface.DEFAULT_BOLD);
        mAction.setPadding(dp(12), dp(8), dp(4), dp(8));
        header.addView(mAction);
        root.addView(header);

        mContainer = new LinearLayout(this);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        root.addView(mContainer, clp);

        setContentView(scroll);
        showInbox();
    }

    private void showInbox() {
        mTitle.setText("Mail");
        mAction.setText("Compose");
        mAction.setOnClickListener(v -> compose());
        message("Loading inbox…");
        new Thread(() -> {
            try {
                final JSONArray arr = MailBridge.inbox();
                runOnUiThread(() -> renderInbox(arr));
            } catch (Exception e) {
                runOnUiThread(() -> message("Inbox unavailable — check your mail bridge."));
            }
        }).start();
    }

    private void renderInbox(JSONArray arr) {
        mContainer.removeAllViews();
        if (arr.length() == 0) {
            message("No messages.");
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            mContainer.addView(inboxCard(o));
        }
    }

    private View inboxCard(final JSONObject o) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        c.setClickable(true);
        c.setOnClickListener(v -> openMessage(o.optString("id", "")));

        TextView from = new TextView(this);
        from.setText(o.optString("from", "(unknown)"));
        from.setTextColor(ACCENT);
        from.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        from.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(from);

        TextView subj = new TextView(this);
        subj.setText(o.optString("subject", "(no subject)"));
        subj.setTextColor(TEXT);
        subj.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subj.setTypeface(Typeface.DEFAULT_BOLD);
        subj.setMaxLines(1);
        subj.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(subj);

        String snip = o.optString("snippet", "");
        if (!TextUtils.isEmpty(snip)) {
            TextView s = new TextView(this);
            s.setText(snip);
            s.setTextColor(DIM);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            s.setMaxLines(1);
            s.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(s);
        }
        return c;
    }

    private void openMessage(final String id) {
        if (TextUtils.isEmpty(id)) return;
        message("Loading…");
        new Thread(() -> {
            try {
                final JSONObject o = MailBridge.message(id);
                runOnUiThread(() -> renderMessage(o));
            } catch (Exception e) {
                runOnUiThread(() -> { showInbox(); toast("Couldn't open message"); });
            }
        }).start();
    }

    private void renderMessage(JSONObject o) {
        mTitle.setText(o.optString("subject", "(no subject)"));
        mAction.setText("‹ Inbox");
        mAction.setOnClickListener(v -> showInbox());
        mContainer.removeAllViews();

        TextView from = new TextView(this);
        from.setText(o.optString("from", "(unknown)"));
        from.setTextColor(ACCENT);
        from.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        from.setTypeface(Typeface.DEFAULT_BOLD);
        from.setPadding(0, 0, 0, dp(12));
        mContainer.addView(from);

        TextView body = new TextView(this);
        body.setText(o.optString("body", ""));
        body.setTextColor(TEXT);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        body.setLineSpacing(dp(4), 1f);
        mContainer.addView(body);
    }

    private void compose() {
        final EditText to = new EditText(this);
        to.setHint("To");
        to.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText subject = new EditText(this);
        subject.setHint("Subject");
        final EditText body = new EditText(this);
        body.setHint("Message");
        body.setMinLines(4);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(to);
        box.addView(subject);
        box.addView(body);

        new AlertDialog.Builder(this)
                .setTitle("New message")
                .setView(box)
                .setPositiveButton("Send", (d, w) -> {
                    final String t = to.getText().toString().trim();
                    final String s = subject.getText().toString().trim();
                    final String bd = body.getText().toString();
                    if (TextUtils.isEmpty(t)) { toast("Add a recipient"); return; }
                    new Thread(() -> {
                        try {
                            MailBridge.send(t, s, bd);
                            runOnUiThread(() -> toast("Sent"));
                        } catch (Exception e) {
                            runOnUiThread(() -> toast("Couldn't send"));
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void message(String msg) {
        mContainer.removeAllViews();
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(20), 0, 0);
        mContainer.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
