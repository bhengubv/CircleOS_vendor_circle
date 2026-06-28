/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Music (WP-75) — browse your subscription library from the Circle music
 * bridge (ro.circle.music.url) and play it IN-APP: a real MediaPlayer streams the
 * track with a persistent now-playing bar (play/pause, seek, prev/next, auto-
 * advance). True-black + brand accent only. Background HTTP off the main thread.
 */
package za.co.circleos.music;

import android.app.Activity;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
import java.util.ArrayList;
import java.util.List;

public final class MusicActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int BAR     = 0xFF101010;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private LinearLayout mList;

    // now-playing bar
    private LinearLayout mBar;
    private TextView mNpTitle, mNpArtist, mPlayPause;
    private SeekBar mSeek;

    private final List<Track> mTracks = new ArrayList<>();
    private MediaPlayer mPlayer;
    private int mCurrent = -1;
    private boolean mPrepared;

    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            if (mPlayer != null && mPrepared && mPlayer.isPlaying()) {
                mSeek.setProgress(mPlayer.getCurrentPosition());
                mUi.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(44), dp(20), dp(120)); // leave room for the bar
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Music");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(12);
        col.addView(mList, llp);

        root.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(buildBar());

        setContentView(root);
        load();
    }

    // ── now-playing bar ──

    private View buildBar() {
        mBar = new LinearLayout(this);
        mBar.setOrientation(LinearLayout.VERTICAL);
        mBar.setBackgroundColor(BAR);
        mBar.setPadding(dp(16), dp(10), dp(16), dp(16));
        mBar.setVisibility(View.GONE);

        mSeek = new SeekBar(this);
        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && mPlayer != null && mPrepared) mPlayer.seekTo(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        mBar.addView(mSeek);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mNpTitle = new TextView(this);
        mNpTitle.setTextColor(TEXT);
        mNpTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mNpTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mNpTitle.setMaxLines(1);
        mNpTitle.setEllipsize(TextUtils.TruncateAt.END);
        mNpArtist = new TextView(this);
        mNpArtist.setTextColor(DIM);
        mNpArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mNpArtist.setMaxLines(1);
        meta.addView(mNpTitle);
        meta.addView(mNpArtist);
        row.addView(meta);

        row.addView(ctrl("⏮", v -> prev()));
        mPlayPause = ctrl("⏸", v -> togglePlay());
        row.addView(mPlayPause);
        row.addView(ctrl("⏭", v -> next()));

        mBar.addView(row);

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        mBar.setLayoutParams(lp);
        return mBar;
    }

    private TextView ctrl(String glyph, View.OnClickListener onClick) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setOnClickListener(onClick);
        return t;
    }

    // ── library ──

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
        mTracks.clear();
        if (arr.length() == 0) {
            message("No tracks in your library yet.");
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Track t = new Track(o.optString("id", ""),
                    o.optString("title", "(untitled)"), o.optString("artist", ""));
            if (t.id.isEmpty()) continue;
            final int index = mTracks.size();
            mTracks.add(t);
            mList.addView(trackCard(t, index));
        }
    }

    private View trackCard(Track t, final int index) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        c.setClickable(true);
        c.setOnClickListener(v -> play(index));

        TextView tv = new TextView(this);
        tv.setText(t.title);
        tv.setTextColor(TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(tv);

        if (!TextUtils.isEmpty(t.artist)) {
            TextView a = new TextView(this);
            a.setText(t.artist);
            a.setTextColor(DIM);
            a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            c.addView(a);
        }
        return c;
    }

    // ── playback ──

    private void play(int index) {
        if (index < 0 || index >= mTracks.size()) return;
        mCurrent = index;
        Track t = mTracks.get(index);
        try {
            releasePlayer();
            mPlayer = new MediaPlayer();
            mPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mPlayer.setDataSource(base() + "/api/music/stream?id=" + enc(t.id));
            mPrepared = false;
            mPlayer.setOnPreparedListener(mp -> {
                mPrepared = true;
                mSeek.setMax(mp.getDuration());
                mp.start();
                mPlayPause.setText("⏸");
                mUi.post(mTick);
            });
            mPlayer.setOnCompletionListener(mp -> next());
            mPlayer.setOnErrorListener((mp, what, extra) -> { toast("Couldn't play this track"); return true; });
            mPlayer.prepareAsync();

            mNpTitle.setText(t.title);
            mNpArtist.setText(t.artist);
            mBar.setVisibility(View.VISIBLE);
        } catch (Throwable e) {
            toast("Couldn't play this track");
        }
    }

    private void togglePlay() {
        if (mPlayer == null || !mPrepared) return;
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            mPlayPause.setText("▶");
        } else {
            mPlayer.start();
            mPlayPause.setText("⏸");
            mUi.post(mTick);
        }
    }

    private void next() {
        if (mTracks.isEmpty()) return;
        play((mCurrent + 1) % mTracks.size());
    }

    private void prev() {
        if (mTracks.isEmpty()) return;
        if (mPlayer != null && mPrepared && mPlayer.getCurrentPosition() > 3000) {
            mPlayer.seekTo(0);
            return;
        }
        play((mCurrent - 1 + mTracks.size()) % mTracks.size());
    }

    private void releasePlayer() {
        mUi.removeCallbacks(mTick);
        if (mPlayer != null) {
            try { mPlayer.reset(); mPlayer.release(); } catch (Throwable ignored) {}
            mPlayer = null;
        }
        mPrepared = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
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

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class Track {
        final String id, title, artist;
        Track(String id, String title, String artist) { this.id = id; this.title = title; this.artist = artist; }
    }
}
