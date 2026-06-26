/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Rooms (WP-20) - a shared room you join by code, with a synced message
 * thread over the Circle rooms bridge. (Shared calendar + album are follow-ups
 * on the same bridge.)
 */
package za.co.circleos.rooms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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

public final class RoomsActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int MINE   = 0xFF12344A; // my messages (dark blue)
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private SharedPreferences mSp;
    private TextView mHeader;
    private ScrollView mScroll;
    private LinearLayout mMessages;
    private EditText mComposer;
    private String mCode = "";
    private String mName = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mSp = getSharedPreferences("circle_rooms", MODE_PRIVATE);
        mCode = mSp.getString("code", "");
        mName = mSp.getString("name", "");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(40), dp(16), dp(12));

        LinearLayout hr = new LinearLayout(this);
        hr.setOrientation(LinearLayout.HORIZONTAL);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        mHeader = new TextView(this);
        mHeader.setTextColor(TEXT);
        mHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        mHeader.setTypeface(Typeface.DEFAULT_BOLD);
        mHeader.setMaxLines(1);
        mHeader.setEllipsize(TextUtils.TruncateAt.END);
        hr.addView(mHeader, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView sw = new TextView(this);
        sw.setText("Switch");
        sw.setTextColor(ACCENT);
        sw.setTypeface(Typeface.DEFAULT_BOLD);
        sw.setPadding(dp(10), dp(8), dp(4), dp(8));
        sw.setOnClickListener(v -> promptCode());
        hr.addView(sw);
        root.addView(hr);

        mScroll = new ScrollView(this);
        mMessages = new LinearLayout(this);
        mMessages.setOrientation(LinearLayout.VERTICAL);
        mMessages.setPadding(0, dp(12), 0, dp(12));
        mScroll.addView(mMessages);
        root.addView(mScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout cr = new LinearLayout(this);
        cr.setOrientation(LinearLayout.HORIZONTAL);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        mComposer = new EditText(this);
        mComposer.setHint("Message");
        mComposer.setTextColor(TEXT);
        mComposer.setHintTextColor(0x66FFFFFF);
        mComposer.setBackgroundColor(TILE);
        mComposer.setPadding(dp(12), dp(10), dp(12), dp(10));
        mComposer.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        cr.addView(mComposer, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView send = new TextView(this);
        send.setText("Send");
        send.setTextColor(0xFF000000);
        send.setBackgroundColor(ACCENT);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setGravity(Gravity.CENTER);
        send.setPadding(dp(16), dp(12), dp(16), dp(12));
        send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams selp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selp.leftMargin = dp(8);
        cr.addView(send, selp);
        root.addView(cr);

        setContentView(root);

        if (mName.isEmpty() || mCode.isEmpty()) promptSetup();
        else showThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mCode.isEmpty() && !mName.isEmpty()) loadMessages();
    }

    private void promptSetup() {
        final EditText name = new EditText(this);
        name.setHint("Your name");
        name.setText(mName);
        final EditText code = new EditText(this);
        code.setHint("Room code (any word)");
        code.setText(mCode);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(name);
        box.addView(code);
        new AlertDialog.Builder(this)
                .setTitle("Join a room")
                .setView(box)
                .setPositiveButton("Join", (d, w) -> {
                    mName = name.getText().toString().trim();
                    mCode = code.getText().toString().trim();
                    if (mName.isEmpty() || mCode.isEmpty()) { promptSetup(); return; }
                    mSp.edit().putString("name", mName).putString("code", mCode).apply();
                    showThread();
                })
                .setCancelable(false)
                .show();
    }

    private void promptCode() {
        final EditText code = new EditText(this);
        code.setHint("Room code");
        code.setText(mCode);
        new AlertDialog.Builder(this)
                .setTitle("Switch room")
                .setView(code)
                .setPositiveButton("Go", (d, w) -> {
                    String c = code.getText().toString().trim();
                    if (!c.isEmpty()) {
                        mCode = c;
                        mSp.edit().putString("code", mCode).apply();
                        showThread();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showThread() {
        mHeader.setText("# " + mCode);
        loadMessages();
    }

    private void loadMessages() {
        new Thread(() -> {
            try {
                final JSONArray arr = RoomBridge.messages(mCode);
                runOnUiThread(() -> renderMessages(arr));
            } catch (Exception e) {
                runOnUiThread(() -> info("No messages yet - say hello."));
            }
        }).start();
    }

    private void renderMessages(JSONArray arr) {
        mMessages.removeAllViews();
        if (arr.length() == 0) { info("No messages yet - say hello."); return; }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            mMessages.addView(bubble(o.optString("author", "?"), o.optString("text", "")));
        }
        mScroll.post(() -> mScroll.fullScroll(View.FOCUS_DOWN));
    }

    private View bubble(String author, String text) {
        boolean mine = author.equals(mName);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(mine ? MINE : TILE);
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        c.setLayoutParams(lp);
        if (!mine) {
            TextView a = new TextView(this);
            a.setText(author);
            a.setTextColor(ACCENT);
            a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            a.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(a);
        }
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        c.addView(t);
        return c;
    }

    private void send() {
        final String text = mComposer.getText().toString().trim();
        if (TextUtils.isEmpty(text) || mCode.isEmpty()) return;
        mComposer.setText("");
        new Thread(() -> {
            try {
                RoomBridge.post(mCode, mName, text);
                runOnUiThread(this::loadMessages);
            } catch (Exception e) {
                runOnUiThread(() -> toast("Couldn't send"));
            }
        }).start();
    }

    private void info(String s) {
        mMessages.removeAllViews();
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(8), 0, 0);
        mMessages.addView(t);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
