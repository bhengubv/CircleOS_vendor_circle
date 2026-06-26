/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Music (WP-75) - browse your subscription library from the Circle
 * music bridge (ro.circle.music.url) and play a track through the system audio
 * handler. Background HTTP off the main thread; the bridge is the external
 * service, the app code is complete.
 */
package za.co.circleos.music;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.SystemProperties;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class MusicActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private LinearLayout mList;

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
        title.setText("Music");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(12);
        root.addView(mList, llp);

        setContentView(scroll);
        load();
    }

    private static String base() {
        String b = "";
        try {
            b = SystemProperties.get("ro.circle.music.url", "");
        } catch (Throwable t) {
            // not on a Circle build
        }
        return (b == null || b.isEmpty()) ? "https://music.circleos.co.za" : b;
    }

    private void load() {
        message("Loading your library…");
        new Thread(() -> {
            try {
                final JSONArray arr = fetchLibrary();
                runOnUiThread(() -> render(arr));
            } catch (Exception e) {
                runOnUiThread(() -> message("Library unavailable — check your music subscription."));
            }
        }).start();
    }

    private JSONArray fetchLibrary() throws Exception {
        HttpURLConnection c =
                (HttpURLConnection) new URL(base() + "/api/music/library").openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "CircleMusic/1.0");
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray a = root.optJSONArray("tracks");
            return a != null ? a : new JSONArray();
        } finally {
            c.disconnect();
        }
    }

    private void render(JSONArray arr) {
        mList.removeAllViews();
        if (arr.length() == 0) {
            message("No tracks in your library yet.");
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            mList.addView(trackCard(o.optString("id", ""),
                    o.optString("title", "(untitled)"), o.optString("artist", "")));
        }
    }

    private View trackCard(final String id, String trackTitle, String artist) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        c.setClickable(true);
        c.setOnClickListener(v -> play(id));

        TextView t = new TextView(this);
        t.setText(trackTitle);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(t);

        if (!TextUtils.isEmpty(artist)) {
            TextView a = new TextView(this);
            a.setText(artist);
            a.setTextColor(DIM);
            a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            c.addView(a);
        }
        return c;
    }

    private void play(String id) {
        if (TextUtils.isEmpty(id)) return;
        try {
            String url = base() + "/api/music/stream?id=" + URLEncoder.encode(id, "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(url), "audio/*");
            startActivity(i);
        } catch (Throwable t) {
            toast("No audio player available");
        }
    }

    private void message(String msg) {
        mList.removeAllViews();
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(20), 0, 0);
        mList.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
