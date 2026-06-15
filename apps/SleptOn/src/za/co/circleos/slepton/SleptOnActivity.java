package za.co.circleos.slepton;

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
import android.widget.Button;
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

public final class SleptOnActivity extends Activity {

    private static final String TAG = "SleptOn";
    private static final String API = "https://sleptonapi.thegeeknetwork.co.za/api";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int CARD   = 0xFF1A1A2E;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;

    private final ExecutorService mExec = Executors.newFixedThreadPool(3);
    private final Handler mUi = new Handler(Looper.getMainLooper());

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
        String[] tabs = {"Apps", "Music", "Art", "Books", "Dev"};
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
            case 0: showApps(); break;
            case 1: showMusic(); break;
            case 2: showArt(); break;
            case 3: showBooks(); break;
            case 4: showDev(); break;
        }
    }

    private void showApps() {
        showCategory("Apps", API + "/apps?featured=true", "App Store — install apps for Circle OS");
    }

    private void showMusic() {
        showCategory("Music", API + "/music?featured=true", "Independent artists and creators");
    }

    private void showArt() {
        showCategory("Art", API + "/art?featured=true", "Digital art, photography, 3D models");
    }

    private void showBooks() {
        showCategory("Books", API + "/books?featured=true", "E-books, audiobooks, comics, courses");
    }

    private void showDev() {
        showCategory("Developer", API + "/developer/apps", "Developer portal — publish your apps");
    }

    private void showCategory(String title, String url, String subtitle) {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle(title));
        TextView sub = emptyText(subtitle);
        col.addView(sub);
        col.addView(emptyText("Loading..."));
        sv.addView(col);
        mContent.addView(sv);

        mExec.execute(() -> {
            try {
                String json = httpGet(url);
                JSONArray arr = new JSONArray(json);
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle(title + " (" + arr.length() + ")"));
                    col.addView(emptyText(subtitle));
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            JSONObject item = arr.getJSONObject(i);
                            LinearLayout card = cardContainer();
                            TextView name = new TextView(this);
                            name.setText(item.optString("name", item.optString("title", "Untitled")));
                            name.setTextColor(TEXT1); name.setTextSize(16);
                            name.setTypeface(Typeface.DEFAULT_BOLD);
                            card.addView(name);
                            TextView desc = new TextView(this);
                            desc.setText(item.optString("description", "").substring(0,
                                    Math.min(100, item.optString("description", "").length())));
                            desc.setTextColor(TEXT2); desc.setTextSize(13);
                            card.addView(desc);
                            if (mTab == 0) {
                                Button install = new Button(this);
                                install.setText("Install");
                                install.setTextColor(BG); install.setBackgroundColor(ACCENT);
                                card.addView(install);
                            }
                            col.addView(card);
                        } catch (Exception ignored) {}
                    }
                    if (arr.length() == 0) col.addView(emptyText("Nothing here yet"));
                });
            } catch (Exception e) {
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle(title));
                    col.addView(emptyText("Offline — checking mesh for cached content..."));
                });
            }
        });
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
