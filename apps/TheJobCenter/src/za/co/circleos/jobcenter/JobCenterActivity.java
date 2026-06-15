package za.co.circleos.jobcenter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import za.co.circleos.mesh.ICircleMeshService;

public final class JobCenterActivity extends Activity {

    private static final String TAG = "JobCenter";
    private static final String API = "https://jobcenterapi.thegeeknetwork.co.za/api";

    private static final int BG       = 0xFF0A0A0A;
    private static final int ACCENT   = 0xFF2196F3;
    private static final int CARD     = 0xFF1A1A2E;
    private static final int TEXT1    = 0xFFF5F0EB;
    private static final int TEXT2    = 0x99F5F0EB;

    private final ExecutorService mExec = Executors.newFixedThreadPool(3);
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final List<JSONObject> mJobs = new ArrayList<>();

    private ICircleMeshService mMesh;
    private FrameLayout mContent;
    private LinearLayout mTabBar;
    private int mTab = 0;
    private String mSearchQuery = "";

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
        loadJobs("");
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
        String[] tabs = {"Search", "Applied", "Saved", "AI Match", "Profile"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(tabs[i]);
            tv.setTextColor(i == 0 ? ACCENT : TEXT2);
            tv.setTextSize(11);
            tv.setGravity(Gravity.CENTER);
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
            case 0: showSearch(); break;
            case 1: showApplied(); break;
            case 2: showSaved(); break;
            case 3: showAiMatch(); break;
            case 4: showProfile(); break;
        }
    }

    private void showSearch() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = sectionTitle("Find Jobs");
        col.addView(title);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText input = new EditText(this);
        input.setHint("Search jobs, skills, companies...");
        input.setTextColor(TEXT1);
        input.setHintTextColor(TEXT2);
        input.setBackgroundColor(CARD);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchRow.addView(input);
        Button go = new Button(this);
        go.setText("Go");
        go.setTextColor(BG);
        go.setBackgroundColor(ACCENT);
        go.setOnClickListener(v -> {
            mSearchQuery = input.getText().toString();
            loadJobs(mSearchQuery);
        });
        searchRow.addView(go);
        col.addView(searchRow);

        col.addView(sectionTitle("Results"));
        LinearLayout jobList = new LinearLayout(this);
        jobList.setOrientation(LinearLayout.VERTICAL);
        jobList.setTag("jobList");
        for (JSONObject job : mJobs) {
            jobList.addView(jobCard(job));
        }
        if (mJobs.isEmpty()) jobList.addView(emptyText("Search for jobs..."));
        col.addView(jobList);

        sv.addView(col);
        mContent.addView(sv);
    }

    private void showApplied() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Applications"));
        col.addView(emptyText("Loading your applications..."));
        sv.addView(col);
        mContent.addView(sv);

        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/applications/mine");
                JSONArray arr = new JSONArray(json);
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("Applications (" + arr.length() + ")"));
                    for (int i = 0; i < arr.length(); i++) {
                        try { col.addView(jobCard(arr.getJSONObject(i))); }
                        catch (Exception ignored) {}
                    }
                    if (arr.length() == 0) col.addView(emptyText("No applications yet"));
                });
            } catch (Exception e) {
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("Applications"));
                    col.addView(emptyText("Offline — cached data unavailable"));
                });
            }
        });
    }

    private void showSaved() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Saved Jobs"));
        col.addView(emptyText("No saved jobs"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showAiMatch() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("B! AI Job Matching"));
        col.addView(emptyText("B! is analyzing your profile and finding matches..."));
        sv.addView(col);
        mContent.addView(sv);

        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/ai/matches");
                JSONArray arr = new JSONArray(json);
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("AI Matches (" + arr.length() + ")"));
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            JSONObject job = arr.getJSONObject(i);
                            LinearLayout card = cardContainer();
                            TextView name = new TextView(this);
                            name.setText(job.optString("title", "Untitled"));
                            name.setTextColor(TEXT1);
                            name.setTextSize(16);
                            name.setTypeface(Typeface.DEFAULT_BOLD);
                            card.addView(name);
                            TextView match = new TextView(this);
                            match.setText("Match: " + job.optInt("matchScore", 0) + "%");
                            match.setTextColor(ACCENT);
                            match.setTextSize(13);
                            card.addView(match);
                            Button apply = new Button(this);
                            apply.setText("Auto-Apply with B!");
                            apply.setTextColor(BG);
                            apply.setBackgroundColor(ACCENT);
                            apply.setOnClickListener(v -> autoApply(job.optString("id")));
                            card.addView(apply);
                            col.addView(card);
                        } catch (Exception ignored) {}
                    }
                    if (arr.length() == 0) col.addView(emptyText("Upload your CV to get matches"));
                });
            } catch (Exception e) {
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("AI Matches"));
                    col.addView(emptyText("Offline — B! needs internet for matching"));
                });
            }
        });
    }

    private void showProfile() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Profile"));
        col.addView(emptyText("Sign in to manage your profile"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void autoApply(String jobId) {
        mExec.execute(() -> {
            try {
                httpPost(API + "/ai/auto-apply", "{\"jobId\":\"" + jobId + "\"}");
            } catch (Exception e) {
                Log.w(TAG, "Auto-apply failed", e);
            }
        });
    }

    private void loadJobs(String query) {
        mExec.execute(() -> {
            try {
                String url = API + "/jobs/search?q=" + java.net.URLEncoder.encode(query, "UTF-8");
                String json = httpGet(url);
                JSONArray arr = new JSONArray(json);
                mJobs.clear();
                for (int i = 0; i < arr.length(); i++) mJobs.add(arr.getJSONObject(i));
                mUi.post(() -> { if (mTab == 0) switchTab(0); });
            } catch (Exception e) {
                Log.d(TAG, "loadJobs failed", e);
            }
        });
    }

    private View jobCard(JSONObject job) {
        LinearLayout card = cardContainer();
        TextView title = new TextView(this);
        title.setText(job.optString("title", "Untitled Position"));
        title.setTextColor(TEXT1);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView company = new TextView(this);
        company.setText(job.optString("company", "") + " — " + job.optString("location", ""));
        company.setTextColor(TEXT2);
        company.setTextSize(13);
        card.addView(company);
        TextView salary = new TextView(this);
        salary.setText(job.optString("salary", "Salary not disclosed"));
        salary.setTextColor(ACCENT);
        salary.setTextSize(13);
        card.addView(salary);
        return card;
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

    private String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true); c.setConnectTimeout(10_000); c.setReadTimeout(10_000);
        OutputStream os = c.getOutputStream(); os.write(body.getBytes("UTF-8")); os.close();
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close(); return sb.toString();
    }

    private LinearLayout cardContainer() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(CARD);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(TEXT1); t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(8));
        return t;
    }

    private TextView emptyText(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(TEXT2); t.setTextSize(14);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
