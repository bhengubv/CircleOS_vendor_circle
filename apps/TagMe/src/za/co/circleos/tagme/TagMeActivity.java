package za.co.circleos.tagme;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import za.co.circleos.mesh.ICircleMeshService;

public final class TagMeActivity extends Activity implements LocationListener {

    private static final String TAG = "TagMe";
    private static final String API = "https://tagmeapi.thegeeknetwork.co.za/api";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int CARD   = 0xFF1A1A2E;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;

    private final ExecutorService mExec = Executors.newFixedThreadPool(3);
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final List<JSONObject> mResults = new ArrayList<>();
    private final List<JSONObject> mFeed = new ArrayList<>();

    private LocationManager mLocMgr;
    private Location mLastLoc;
    private ICircleMeshService mMesh;
    private FrameLayout mContent;
    private LinearLayout mTabBar;
    private int mTab = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        mLocMgr = getSystemService(LocationManager.class);
        try {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder != null) mMesh = ICircleMeshService.Stub.asInterface(binder);
        } catch (Throwable ignored) {}

        buildUI();
        startLocation();

        Intent intent = getIntent();
        if (Intent.ACTION_WEB_SEARCH.equals(intent.getAction())
                || Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (query != null && !query.isEmpty()) doSearch(query);
        }
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
        String[] tabs = {"Search", "Feed", "Places", "Friends", "Profile"};
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
            case 0: showSearch(); break;
            case 1: showFeed(); break;
            case 2: showPlaces(); break;
            case 3: showFriends(); break;
            case 4: showProfile(); break;
        }
    }

    private void showSearch() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("TagMe Search"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText input = new EditText(this);
        input.setHint("Search places, people, tags...");
        input.setTextColor(TEXT1); input.setHintTextColor(TEXT2);
        input.setBackgroundColor(CARD); input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(input);
        Button go = new Button(this);
        go.setText("Go"); go.setTextColor(BG); go.setBackgroundColor(ACCENT);
        go.setOnClickListener(v -> doSearch(input.getText().toString()));
        row.addView(go);
        col.addView(row);

        col.addView(sectionTitle("Results"));
        for (JSONObject r2 : mResults) col.addView(resultCard(r2));
        if (mResults.isEmpty()) col.addView(emptyText("Search to discover"));

        sv.addView(col);
        mContent.addView(sv);
    }

    private void showFeed() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Activity Feed"));
        col.addView(emptyText("Loading..."));
        sv.addView(col);
        mContent.addView(sv);
        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/feed");
                JSONArray arr = new JSONArray(json);
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("Activity Feed"));
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            JSONObject o = arr.getJSONObject(i);
                            col.addView(resultCard(o));
                        } catch (Exception ignored) {}
                    }
                    if (arr.length() == 0) col.addView(emptyText("No activity yet"));
                });
            } catch (Exception e) {
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("Feed"));
                    col.addView(emptyText("Offline"));
                });
            }
        });
    }

    private void showPlaces() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Nearby Places"));
        col.addView(emptyText("Getting your location..."));
        sv.addView(col);
        mContent.addView(sv);
        if (mLastLoc != null) {
            mExec.execute(() -> {
                try {
                    String json = httpGet(API + "/places?lat=" + mLastLoc.getLatitude()
                            + "&lng=" + mLastLoc.getLongitude() + "&radius=5000");
                    JSONArray arr = new JSONArray(json);
                    mUi.post(() -> {
                        col.removeAllViews();
                        col.addView(sectionTitle("Nearby (" + arr.length() + ")"));
                        for (int i = 0; i < arr.length(); i++) {
                            try { col.addView(resultCard(arr.getJSONObject(i))); }
                            catch (Exception ignored) {}
                        }
                    });
                } catch (Exception e) {
                    mUi.post(() -> {
                        col.removeAllViews();
                        col.addView(sectionTitle("Places"));
                        col.addView(emptyText("Offline"));
                    });
                }
            });
        }
    }

    private void showFriends() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Friends Nearby"));
        col.addView(emptyText("No friends nearby"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showProfile() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("My Profile"));
        col.addView(emptyText("Check-ins, badges, leaderboard"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void doSearch(String query) {
        mExec.execute(() -> {
            try {
                String url = API + "/search?q=" + java.net.URLEncoder.encode(query, "UTF-8");
                if (mLastLoc != null) {
                    url += "&lat=" + mLastLoc.getLatitude() + "&lng=" + mLastLoc.getLongitude();
                }
                String json = httpGet(url);
                JSONArray arr = new JSONArray(json);
                mResults.clear();
                for (int i = 0; i < arr.length(); i++) mResults.add(arr.getJSONObject(i));
                mUi.post(() -> switchTab(0));
            } catch (Exception e) { Log.d(TAG, "search failed", e); }
        });
    }

    private void startLocation() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                mLocMgr.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 30_000, 50f, this);
                mLastLoc = mLocMgr.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
            } else {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        } catch (Exception e) { Log.w(TAG, "location failed", e); }
    }

    @Override
    public void onLocationChanged(Location loc) { mLastLoc = loc; }

    private View resultCard(JSONObject o) {
        LinearLayout card = cardContainer();
        TextView title = new TextView(this);
        title.setText(o.optString("name", o.optString("title", "Result")));
        title.setTextColor(TEXT1); title.setTextSize(15); title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView desc = new TextView(this);
        String d = o.optString("description", o.optString("address", ""));
        desc.setText(d.substring(0, Math.min(100, d.length())));
        desc.setTextColor(TEXT2); desc.setTextSize(13);
        card.addView(desc);
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
