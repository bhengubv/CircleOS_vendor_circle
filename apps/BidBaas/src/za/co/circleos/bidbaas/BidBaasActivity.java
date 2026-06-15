package za.co.circleos.bidbaas;

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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import za.co.circleos.mesh.ICircleMeshService;

public final class BidBaasActivity extends Activity {

    private static final String TAG = "BidBaas";
    private static final String API = "https://bidbaasapi.thegeeknetwork.co.za/api";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int CARD   = 0xFF1A1A2E;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;
    private static final int BID_ACTIVE = 0xFF4CAF50;

    private final ExecutorService mExec = Executors.newFixedThreadPool(3);
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final List<JSONObject> mAuctions = new ArrayList<>();

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
        loadAuctions();
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
        String[] tabs = {"Browse", "My Bids", "Sell", "Wallet", "Profile"};
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
            case 0: showBrowse(); break;
            case 1: showMyBids(); break;
            case 2: showSell(); break;
            case 3: showWallet(); break;
            case 4: showProfile(); break;
        }
    }

    private void showBrowse() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Live Auctions"));
        for (JSONObject a : mAuctions) col.addView(auctionCard(a));
        if (mAuctions.isEmpty()) col.addView(emptyText("No live auctions"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showMyBids() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("My Bids"));
        col.addView(emptyText("Loading bids..."));
        sv.addView(col);
        mContent.addView(sv);
        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/bids/mine");
                JSONArray arr = new JSONArray(json);
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("My Bids (" + arr.length() + ")"));
                    for (int i = 0; i < arr.length(); i++) {
                        try { col.addView(auctionCard(arr.getJSONObject(i))); }
                        catch (Exception ignored) {}
                    }
                });
            } catch (Exception e) {
                mUi.post(() -> {
                    col.removeAllViews();
                    col.addView(sectionTitle("My Bids"));
                    col.addView(emptyText("Offline"));
                });
            }
        });
    }

    private void showSell() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Create Auction"));
        String[] fields = {"Title", "Description", "Starting Price", "Category"};
        for (String f : fields) {
            EditText et = new EditText(this);
            et.setHint(f); et.setTextColor(TEXT1); et.setHintTextColor(TEXT2);
            et.setBackgroundColor(CARD); et.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8); et.setLayoutParams(lp);
            col.addView(et);
        }
        col.addView(sectionTitle("Auction Type"));
        String[] types = {"English (ascending)", "Dutch (descending)", "Sealed Bid"};
        for (String t : types) {
            Button btn = new Button(this);
            btn.setText(t); btn.setTextColor(TEXT1); btn.setBackgroundColor(CARD);
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8); btn.setLayoutParams(lp);
            col.addView(btn);
        }
        Button create = new Button(this);
        create.setText("Create Auction"); create.setTextColor(BG);
        create.setBackgroundColor(ACCENT);
        col.addView(create);
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showWallet() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Wallet"));
        col.addView(emptyText("Balance: Loading..."));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showProfile() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Seller Profile"));
        col.addView(emptyText("Sign in to manage your profile"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private View auctionCard(JSONObject a) {
        LinearLayout card = cardContainer();
        TextView title = new TextView(this);
        title.setText(a.optString("title", "Auction"));
        title.setTextColor(TEXT1); title.setTextSize(16); title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView price = new TextView(this);
        price.setText("Current: R" + a.optString("currentPrice", "0") +
                " — Bids: " + a.optInt("bidCount", 0));
        price.setTextColor(BID_ACTIVE); price.setTextSize(13);
        card.addView(price);
        TextView time = new TextView(this);
        time.setText("Ends: " + a.optString("endsAt", "unknown"));
        time.setTextColor(TEXT2); time.setTextSize(12);
        card.addView(time);
        return card;
    }

    private void loadAuctions() {
        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/auctions?status=live");
                JSONArray arr = new JSONArray(json);
                mAuctions.clear();
                for (int i = 0; i < arr.length(); i++) mAuctions.add(arr.getJSONObject(i));
                mUi.post(() -> { if (mTab == 0) switchTab(0); });
            } catch (Exception e) { Log.d(TAG, "loadAuctions failed", e); }
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
