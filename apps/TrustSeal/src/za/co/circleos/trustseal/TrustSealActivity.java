package za.co.circleos.trustseal;

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

public final class TrustSealActivity extends Activity {

    private static final String TAG = "TrustSeal";
    private static final String API = "https://trustsealapi.thegeeknetwork.co.za/api";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int CARD   = 0xFF1A1A2E;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;
    private static final int GOLD   = 0xFFFFD700;

    private final ExecutorService mExec = Executors.newFixedThreadPool(2);
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final List<JSONObject> mDocs = new ArrayList<>();

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
        loadDocuments();
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
        String[] tabs = {"Vault", "Upload", "Verify", "Sign", "Credentials"};
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
            case 0: showVault(); break;
            case 1: showUpload(); break;
            case 2: showVerify(); break;
            case 3: showSign(); break;
            case 4: showCredentials(); break;
        }
    }

    private void showVault() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Document Vault"));
        for (JSONObject doc : mDocs) { col.addView(docCard(doc)); }
        if (mDocs.isEmpty()) col.addView(emptyText("No documents — upload your first"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showUpload() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Upload Document"));
        col.addView(emptyText("Step 1: Select document type"));
        String[] types = {"ID Document", "Passport", "Driver's License", "Proof of Address",
                          "Qualification", "Certificate", "Contract", "Other"};
        for (String type : types) {
            Button btn = new Button(this);
            btn.setText(type); btn.setTextColor(TEXT1); btn.setBackgroundColor(CARD);
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            btn.setLayoutParams(lp);
            col.addView(btn);
        }
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showVerify() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Verification Tiers"));
        String[][] tiers = {
            {"Bronze", "Self-declared — no verification"},
            {"Silver", "AI-verified document authenticity"},
            {"Gold", "Third-party identity verification"},
            {"Platinum", "Blockchain-anchored + notarized"}
        };
        int[] colors = {0xFFCD7F32, 0xFFC0C0C0, GOLD, 0xFFE5E4E2};
        for (int i = 0; i < tiers.length; i++) {
            LinearLayout card = cardContainer();
            TextView name = new TextView(this);
            name.setText(tiers[i][0]); name.setTextColor(colors[i]);
            name.setTextSize(18); name.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(name);
            TextView desc = new TextView(this);
            desc.setText(tiers[i][1]); desc.setTextColor(TEXT2); desc.setTextSize(13);
            card.addView(desc);
            col.addView(card);
        }
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showSign() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Digital Signatures"));
        col.addView(emptyText("Select a document to sign"));
        for (JSONObject doc : mDocs) {
            Button btn = new Button(this);
            btn.setText("Sign: " + doc.optString("name", "Document"));
            btn.setTextColor(Color.WHITE); btn.setBackgroundColor(ACCENT);
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            btn.setLayoutParams(lp);
            col.addView(btn);
        }
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showCredentials() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Verifiable Credentials"));
        col.addView(emptyText("W3C Verifiable Credentials linked to your vault"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void loadDocuments() {
        mExec.execute(() -> {
            try {
                String json = httpGet(API + "/documents");
                JSONArray arr = new JSONArray(json);
                mDocs.clear();
                for (int i = 0; i < arr.length(); i++) mDocs.add(arr.getJSONObject(i));
                mUi.post(() -> { if (mTab == 0) switchTab(0); });
            } catch (Exception e) { Log.d(TAG, "loadDocs failed", e); }
        });
    }

    private View docCard(JSONObject doc) {
        LinearLayout card = cardContainer();
        TextView name = new TextView(this);
        name.setText(doc.optString("name", "Document"));
        name.setTextColor(TEXT1); name.setTextSize(15); name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);
        TextView type = new TextView(this);
        type.setText(doc.optString("documentType", "Unknown") + " — " + doc.optString("verificationTier", "Bronze"));
        type.setTextColor(TEXT2); type.setTextSize(12);
        card.addView(type);
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
