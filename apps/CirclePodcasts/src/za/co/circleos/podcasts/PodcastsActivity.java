/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Podcasts (WP-69) - subscribe to RSS feeds, browse episodes, play via
 * the system audio handler. Subscriptions live on-device; nothing is tracked.
 */
package za.co.circleos.podcasts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
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

import java.util.List;

public final class PodcastsActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private PodcastStore mStore;
    private List<PodcastStore.Feed> mFeeds;
    private TextView mTitle;
    private TextView mAction;
    private LinearLayout mContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStore = new PodcastStore(this);

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
        showFeeds();
    }

    private void showFeeds() {
        mFeeds = mStore.load();
        mTitle.setText("Podcasts");
        mAction.setText("+ Add");
        mAction.setOnClickListener(v -> promptAddFeed());
        mContainer.removeAllViews();
        if (mFeeds.isEmpty()) {
            message("No subscriptions yet. Tap “+ Add” and paste a podcast RSS feed URL.");
            return;
        }
        for (PodcastStore.Feed f : mFeeds) mContainer.addView(feedCard(f));
    }

    private View feedCard(final PodcastStore.Feed f) {
        LinearLayout c = card();
        c.setOnClickListener(v -> loadEpisodes(f));
        c.setOnLongClickListener(v -> {
            confirmRemove(f);
            return true;
        });
        TextView t = new TextView(this);
        t.setText(TextUtils.isEmpty(f.title) ? f.url : f.title);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(t);
        return c;
    }

    private void promptAddFeed() {
        final EditText in = new EditText(this);
        in.setHint("https://…/feed.xml");
        in.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        in.setTextColor(TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Add podcast feed")
                .setView(in)
                .setPositiveButton("Add", (d, w) -> {
                    String url = in.getText().toString().trim();
                    if (!TextUtils.isEmpty(url)) subscribe(url);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void subscribe(final String url) {
        message("Adding…");
        new Thread(() -> {
            try {
                final Rss.Channel ch = Rss.fetch(url);
                runOnUiThread(() -> {
                    mStore.add(mFeeds, url, ch.title);
                    showFeeds();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showFeeds();
                    toast("Couldn't load that feed");
                });
            }
        }).start();
    }

    private void loadEpisodes(final PodcastStore.Feed f) {
        message("Loading episodes…");
        mAction.setText("");
        new Thread(() -> {
            try {
                final Rss.Channel ch = Rss.fetch(f.url);
                runOnUiThread(() -> showEpisodes(f, ch));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showFeeds();
                    toast("Couldn't load episodes");
                });
            }
        }).start();
    }

    private void showEpisodes(PodcastStore.Feed f, Rss.Channel ch) {
        mTitle.setText(!TextUtils.isEmpty(ch.title) ? ch.title
                : (TextUtils.isEmpty(f.title) ? "Episodes" : f.title));
        mAction.setText("‹ Back");
        mAction.setOnClickListener(v -> showFeeds());
        mContainer.removeAllViews();
        if (ch.episodes.isEmpty()) {
            message("No playable episodes found in this feed.");
            return;
        }
        for (Rss.Episode ep : ch.episodes) mContainer.addView(episodeCard(ep));
    }

    private View episodeCard(final Rss.Episode ep) {
        LinearLayout c = card();
        c.setOnClickListener(v -> play(ep));
        TextView t = new TextView(this);
        t.setText(ep.title);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(t);
        if (!TextUtils.isEmpty(ep.date)) {
            TextView d = new TextView(this);
            d.setText(ep.date);
            d.setTextColor(ACCENT);
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(4);
            c.addView(d, lp);
        }
        return c;
    }

    private void play(Rss.Episode ep) {
        if (TextUtils.isEmpty(ep.audioUrl)) {
            toast("No audio in this episode");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(ep.audioUrl), "audio/*");
            startActivity(i);
        } catch (Throwable t) {
            toast("No audio player available");
        }
    }

    private void confirmRemove(final PodcastStore.Feed f) {
        new AlertDialog.Builder(this)
                .setTitle("Unsubscribe?")
                .setMessage(TextUtils.isEmpty(f.title) ? f.url : f.title)
                .setPositiveButton("Unsubscribe", (d, w) -> {
                    mStore.remove(mFeeds, f.url);
                    showFeeds();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        c.setClickable(true);
        return c;
    }

    private void message(String msg) {
        mContainer.removeAllViews();
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(30), 0, 0);
        mContainer.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
