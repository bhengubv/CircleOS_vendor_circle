package za.co.circleos.takemehome;

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

public final class TakemehomeActivity extends Activity {

    private static final String TAG = "Takemehome";
    private static final String API = "https://takemehomeapi.thegeeknetwork.co.za/api";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int CARD   = 0xFF1A1A2E;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;
    private static final int DEAL   = 0xFF4CAF50;

    private final ExecutorService mExec = Executors.newFixedThreadPool(2);
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final List<JSONObject> mProperties = new ArrayList<>();

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
        String[] tabs = {"Search", "Trending", "Bookings", "Alerts", "Saved"};
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
            case 1: showTrending(); break;
            case 2: showBookings(); break;
            case 3: showAlerts(); break;
            case 4: showSaved(); break;
        }
    }

    private void showSearch() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Takemehome"));

        EditText dest = new EditText(this);
        dest.setHint("Where to?"); dest.setTextColor(TEXT1); dest.setHintTextColor(TEXT2);
        dest.setBackgroundColor(CARD); dest.setPadding(dp(12), dp(16), dp(12), dp(16));
        col.addView(dest);

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setPadding(0, dp(8), 0, dp(8));
        EditText checkIn = new EditText(this);
        checkIn.setHint("Check-in"); checkIn.setTextColor(TEXT1); checkIn.setHintTextColor(TEXT2);
        checkIn.setBackgroundColor(CARD); checkIn.setPadding(dp(12), dp(12), dp(12), dp(12));
        checkIn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dateRow.addView(checkIn);
        EditText checkOut = new EditText(this);
        checkOut.setHint("Check-out"); checkOut.setTextColor(TEXT1); checkOut.setHintTextColor(TEXT2);
        checkOut.setBackgroundColor(CARD); checkOut.setPadding(dp(12), dp(12), dp(12), dp(12));
        checkOut.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dateRow.addView(checkOut);
        col.addView(dateRow);

        Button search = new Button(this);
        search.setText("Compare Prices"); search.setTextColor(BG);
        search.setBackgroundColor(ACCENT);
        search.setOnClickListener(v -> searchProperties(dest.getText().toString()));
        col.addView(search);

        col.addView(sectionTitle("Results"));
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setTag("results");
        for (JSONObject p : mProperties) results.addView(propertyCard(p));
        if (mProperties.isEmpty()) results.addView(emptyText("Search a destination to compare"));
        col.addView(results);

        sv.addView(col);
        mContent.addView(sv);
    }

    private void showTrending() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Trending Destinations"));
        col.addView(emptyText("Loading..."));
        sv.addView(col);
        mContent.addView(sv);
        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/trending");
                JSONArray arr = new JSONArray(json);
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("Trending"));
                    for (int i = 0; i < arr.length(); i++) {
                        try { col.addView(propertyCard(arr.getJSONObject(i))); }
                        catch (Exception ignored) {}
                    }
                });
            } catch (Exception e) {
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("Trending"));
                    col.addView(emptyText("Offline"));
                });
            }
        });
    }

    private void showBookings() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("My Bookings"));
        col.addView(emptyText("No bookings yet"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showAlerts() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Price Alerts"));
        col.addView(emptyText("Set alerts to get notified of price drops"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showSaved() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Saved Properties"));
        col.addView(emptyText("No saved properties"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void searchProperties(String dest) {
        mExec.execute(() -> {
            try {
                String url = API + "/search?destination=" + java.net.URLEncoder.encode(dest, "UTF-8");
                String json = httpGet(url);
                JSONArray arr = new JSONArray(json);
                mProperties.clear();
                for (int i = 0; i < arr.length(); i++) mProperties.add(arr.getJSONObject(i));
                mUi.post(() -> switchTab(0));
            } catch (Exception e) { Log.d(TAG, "search failed", e); }
        });
    }

    private View propertyCard(JSONObject p) {
        LinearLayout card = cardContainer();
        TextView name = new TextView(this);
        name.setText(p.optString("name", "Property")); name.setTextColor(TEXT1);
        name.setTextSize(16); name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);
        TextView price = new TextView(this);
        price.setText("From R" + p.optString("pricePerNight", "0") + "/night — " +
                p.optString("propertyType", "Hotel")); price.setTextColor(DEAL);
        price.setTextSize(14);
        card.addView(price);
        TextView rating = new TextView(this);
        rating.setText(p.optDouble("rating", 0) + " stars — " + p.optString("location", ""));
        rating.setTextColor(TEXT2); rating.setTextSize(12);
        card.addView(rating);
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
