package za.co.circleos.whatwewant;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ServiceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import za.co.circleos.mesh.ICircleMeshService;

public final class WhatWeWantActivity extends Activity {

    private static final String TAG = "WhatWeWant";
    private static final String API = "https://whatwewantapi.thegeeknetwork.co.za/api";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int CARD   = 0xFF1A1A2E;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;

    private final ExecutorService mExec = Executors.newFixedThreadPool(2);
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final List<JSONObject> mStories = new ArrayList<>();

    private ICircleMeshService mMesh;
    private FrameLayout mContent;
    private LinearLayout mTabBar;
    private int mTab = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        try {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder != null) mMesh = ICircleMeshService.Stub.asInterface(binder);
        } catch (Throwable ignored) {}
        buildUI();
        loadFeed("latest");
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        mContent = new FrameLayout(this);
        root.addView(mContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mTabBar = new LinearLayout(this);
        mTabBar.setOrientation(LinearLayout.HORIZONTAL);
        mTabBar.setBackgroundColor(0xFF121212);
        mTabBar.setGravity(Gravity.CENTER);
        mTabBar.setPadding(0, dp(8), 0, dp(8));
        String[] tabs = {"Feed", "Trending", "Submit", "Bookmarks", "Search"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(tabs[i]); tv.setTextColor(i == 0 ? ACCENT : TEXT2);
            tv.setTextSize(11); tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(4), dp(8), dp(4), dp(8));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tv.setOnClickListener(v -> switchTab(idx));
            mTabBar.addView(tv);
        }
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        root.addView(mTabBar, barLp);
        setContentView(root);
        switchTab(0);
    }

    private void switchTab(int idx) {
        mTab = idx;
        mContent.removeAllViews();
        for (int i = 0; i < mTabBar.getChildCount(); i++)
            ((TextView) mTabBar.getChildAt(i)).setTextColor(i == idx ? ACCENT : TEXT2);
        switch (idx) {
            case 0: showFeed(); break;
            case 1: loadFeed("trending"); showFeed(); break;
            case 2: showSubmit(); break;
            case 3: showBookmarks(); break;
            case 4: showSearch(); break;
        }
    }

    private void showFeed() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle(mTab == 1 ? "Trending" : "Latest Stories"));
        for (JSONObject s : mStories) col.addView(storyCard(s));
        if (mStories.isEmpty()) col.addView(emptyText("Loading stories..."));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showSubmit() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Submit a Story"));
        col.addView(emptyText("Share news with your community"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showBookmarks() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Bookmarks"));
        col.addView(emptyText("No bookmarks yet"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showSearch() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Search Stories"));
        col.addView(emptyText("Type to search..."));
        sv.addView(col);
        mContent.addView(sv);
    }

    private View storyCard(JSONObject s) {
        LinearLayout card = cardContainer();
        TextView title = new TextView(this);
        title.setText(s.optString("title", "Untitled"));
        title.setTextColor(TEXT1); title.setTextSize(16); title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView meta = new TextView(this);
        meta.setText(s.optString("author", "") + " — " + s.optString("region", "") +
                " — " + s.optInt("votes", 0) + " votes");
        meta.setTextColor(TEXT2); meta.setTextSize(12);
        card.addView(meta);
        TextView summary = new TextView(this);
        String desc = s.optString("summary", "");
        summary.setText(desc.substring(0, Math.min(120, desc.length())));
        summary.setTextColor(TEXT2); summary.setTextSize(13);
        card.addView(summary);
        return card;
    }

    private void loadFeed(String sort) {
        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/stories?sort=" + sort + "&limit=20");
                JSONArray arr = new JSONArray(json);
                mStories.clear();
                for (int i = 0; i < arr.length(); i++) mStories.add(arr.getJSONObject(i));
                mUi.post(() -> { if (mTab == 0 || mTab == 1) switchTab(mTab); });
            } catch (Exception e) {
                Log.d(TAG, "loadFeed failed", e);
                loadMeshFeed();
            }
        });
    }

    private void loadMeshFeed() {
        if (mMesh == null) return;
        try {
            mMesh.sendMessage("broadcast",
                    "{\"type\":\"STORY_QUERY\"}".getBytes("UTF-8"), 0x40);
        } catch (Exception e) {
            Log.w(TAG, "Mesh feed query failed", e);
        }
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(10_000); c.setReadTimeout(10_000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close(); return sb.toString();
    }

    private LinearLayout cardContainer() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setBackgroundColor(CARD);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12); c.setLayoutParams(lp); return c;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this); t.setText(text); t.setTextColor(TEXT1);
        t.setTextSize(20); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(8)); return t;
    }

    private TextView emptyText(String text) {
        TextView t = new TextView(this); t.setText(text); t.setTextColor(TEXT2);
        t.setTextSize(14); t.setPadding(0, dp(8), 0, dp(8)); return t;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
