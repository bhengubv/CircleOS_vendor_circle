package za.co.circleos.bruh;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import za.co.circleos.mesh.ICircleMeshService;

public final class BruhActivity extends Activity {

    private static final String TAG = "Bruh";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int CARD   = 0xFF1A1A2E;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;

    private static final String[][] CIRCLE_APPS = {
        {"za.co.circleos.messages",    "Messages",    "Messaging & calls"},
        {"za.co.circleos.sdpkt",       "Sdpkt",       "Wallet & payments"},
        {"za.co.circleos.butler",      "B! AI",       "Your AI assistant"},
        {"za.co.circleos.homecinema",  "HomeCinema",  "Media player"},
        {"za.co.circleos.panik",       "Panik",       "Personal safety"},
        {"za.co.circleos.jobcenter",   "Job Center",  "Jobs & careers"},
        {"za.co.circleos.trustseal",   "TrustSeal",   "Document vault"},
        {"za.co.circleos.bidbaas",     "BidBaas",     "Auctions"},
        {"za.co.circleos.slepton",     "SleptOn",     "App store & creators"},
        {"za.co.circleos.whatwewant",  "WhatWeWant",  "News & stories"},
        {"za.co.circleos.takemehome",  "Takemehome",  "Travel & bookings"},
        {"za.co.circleos.tagme",       "TagMe",       "Search & discover"},
        {"za.co.circleos.circlemaps",  "Maps",        "Navigation"},
        {"za.co.circleos.trafficlobby","TrafficLobby","Traffic fines"},
    };

    private final ExecutorService mExec = Executors.newFixedThreadPool(2);
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
        String[] tabs = {"Home", "Services", "Wallet", "Messages", "Profile"};
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
            case 0: showHome(); break;
            case 1: showServices(); break;
            case 2: launchApp("za.co.circleos.sdpkt"); break;
            case 3: launchApp("za.co.circleos.messages"); break;
            case 4: showProfile(); break;
        }
    }

    private void showHome() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("Bruh!"));
        col.addView(emptyText("Everything in one place. No app-switching."));

        col.addView(sectionTitle("Quick Access"));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < CIRCLE_APPS.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, CIRCLE_APPS.length); j++) {
                final String pkg = CIRCLE_APPS[j][0];
                final String name = CIRCLE_APPS[j][1];
                final String desc = CIRCLE_APPS[j][2];
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(CARD);
                card.setPadding(dp(12), dp(12), dp(12), dp(12));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                clp.setMargins(dp(4), dp(4), dp(4), dp(4));
                card.setLayoutParams(clp);
                card.setOnClickListener(v -> launchApp(pkg));

                TextView n = new TextView(this);
                n.setText(name); n.setTextColor(ACCENT); n.setTextSize(14);
                n.setTypeface(Typeface.DEFAULT_BOLD);
                card.addView(n);
                TextView d = new TextView(this);
                d.setText(desc); d.setTextColor(TEXT2); d.setTextSize(11);
                card.addView(d);
                row.addView(card);
            }
            grid.addView(row);
        }
        col.addView(grid);

        col.addView(sectionTitle("Mesh Status"));
        TextView mesh = emptyText(mMesh != null ? "CircleAether: connected" : "CircleAether: offline");
        mesh.setTextColor(mMesh != null ? 0xFF4CAF50 : 0xFFE53935);
        col.addView(mesh);

        sv.addView(col);
        mContent.addView(sv);
    }

    private void showServices() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("All Services"));
        for (String[] app : CIRCLE_APPS) {
            LinearLayout card = cardContainer();
            final String pkg = app[0];
            TextView name = new TextView(this);
            name.setText(app[1]); name.setTextColor(TEXT1); name.setTextSize(16);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(name);
            TextView desc = new TextView(this);
            desc.setText(app[2]); desc.setTextColor(TEXT2); desc.setTextSize(13);
            card.addView(desc);
            card.setOnClickListener(v -> launchApp(pkg));
            col.addView(card);
        }
        sv.addView(col);
        mContent.addView(sv);
    }

    private void showProfile() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(48), dp(16), dp(80));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionTitle("My Profile"));
        col.addView(emptyText("Sign in to access all services"));
        sv.addView(col);
        mContent.addView(sv);
    }

    private void launchApp(String pkg) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                startActivity(launch);
            } else {
                Log.w(TAG, "App not installed: " + pkg);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch " + pkg, e);
        }
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
