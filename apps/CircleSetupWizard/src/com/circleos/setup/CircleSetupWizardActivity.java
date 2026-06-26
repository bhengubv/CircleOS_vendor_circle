/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle OS Setup Wizard — first-boot onboarding flow.
 *
 * 4 screens:
 *   1. Welcome — Shongololo logo + "Circle [logo] S" wordmark
 *   2. Privacy Setup — shield icon, explain default-deny
 *   3. Network — mesh icon, WiFi setup
 *   4. Done — checkmark, "You are protected"
 */
package com.circleos.setup;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class CircleSetupWizardActivity extends Activity {

    private static final int DEEP = 0xFF0A0A0A;
    private static final int WARM = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF2196F3;

    private static final int TOTAL_PAGES = 4;
    private int mCurrentPage = 0;

    private FrameLayout mContent;
    private LinearLayout mDots;
    private Button mNextButton;
    private Typeface mComfortaa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            mComfortaa = Typeface.createFromAsset(getAssets(), "fonts/comfortaa_bold.ttf");
        } catch (Throwable t) {
            mComfortaa = Typeface.DEFAULT_BOLD;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(DEEP);
        root.setPadding(dp(32), dp(80), dp(32), dp(40));

        mContent = new FrameLayout(this);
        root.addView(mContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mDots = new LinearLayout(this);
        mDots.setGravity(Gravity.CENTER);
        mDots.setPadding(0, dp(24), 0, dp(24));
        root.addView(mDots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mNextButton = new Button(this);
        mNextButton.setText("Next");
        mNextButton.setTextColor(DEEP);
        mNextButton.setTextSize(16);
        mNextButton.setAllCaps(false);
        mNextButton.setTypeface(mComfortaa);
        mNextButton.setBackgroundColor(ACCENT);
        mNextButton.setPadding(0, dp(14), 0, dp(14));
        mNextButton.setOnClickListener(v -> nextPage());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mNextButton.setLayoutParams(btnLp);
        mNextButton.setClipToOutline(true);
        mNextButton.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
            }
        });
        root.addView(mNextButton);

        setContentView(root);
        showPage(0);
    }

    private void nextPage() {
        if (mCurrentPage == 2) {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch (Throwable t) { /* ignore */ }
        }
        if (mCurrentPage >= TOTAL_PAGES - 1) {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEVICE_PROVISIONED, 1);
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.USER_SETUP_COMPLETE, 1);
            finish();
            return;
        }
        showPage(mCurrentPage + 1);
    }

    private void showPage(int page) {
        mCurrentPage = page;
        mContent.removeAllViews();

        switch (page) {
            case 0: mContent.addView(pageWelcome()); break;
            case 1: mContent.addView(pagePrivacy()); break;
            case 2: mContent.addView(pageNetwork()); break;
            case 3: mContent.addView(pageDone()); break;
        }

        mNextButton.setText(page == TOTAL_PAGES - 1 ? "Get Started" : "Next");
        updateDots();
    }

    private View pageWelcome() {
        LinearLayout page = pageContainer();
        page.addView(heroImage(R.drawable.setup_hero_welcome));
        page.addView(title("Welcome to Circle OS"));
        page.addView(body(
                "Circle OS puts you in control. Your data stays on your device. "
                + "Apps cannot access the internet, your contacts, or your sensors "
                + "without your explicit permission.\n\n"
                + "You’re NOT the product. Trust."));
        return page;
    }

    private View pagePrivacy() {
        LinearLayout page = pageContainer();
        page.addView(heroImage(R.drawable.setup_hero_privacy));
        page.addView(title("Privacy by Default"));
        page.addView(body(
                "Every app starts with zero permissions.\n\n"
                + "• No internet access until you grant it\n"
                + "• No contacts visible until you scope them\n"
                + "• Identifiers (IMEI, MAC) are faked per-app\n"
                + "• Unused permissions auto-revoke in 7 days\n"
                + "• Camera and microphone indicators always on\n\n"
                + "You can change any of these in Settings → Privacy."));
        return page;
    }

    private View pageNetwork() {
        LinearLayout page = pageContainer();
        page.addView(heroImage(R.drawable.setup_hero_network));
        page.addView(title("Connect to WiFi"));
        page.addView(body(
                "Tap Next to open WiFi settings. Once connected, "
                + "Circle OS will:\n\n"
                + "• Route DNS through Quad9 (encrypted)\n"
                + "• Check for system updates\n"
                + "• Sync the threat intelligence database\n\n"
                + "Your traffic is never profiled or sold."));
        return page;
    }

    private View pageDone() {
        LinearLayout page = pageContainer();
        page.addView(heroImage(R.drawable.setup_hero_done));
        page.addView(title("You’re Protected"));
        page.addView(body(
                "Circle OS is ready.\n\n"
                + "The privacy shield on your home screen shows live status. "
                + "Tap it anytime to see what your apps are doing.\n\n"
                + "Welcome to the Circle."));
        return page;
    }

    private LinearLayout pageContainer() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        return page;
    }

    private ImageView heroImage(int resId) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), dp(200));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(32);
        iv.setLayoutParams(lp);
        return iv;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(WARM);
        t.setTextSize(28);
        t.setTypeface(mComfortaa);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(16));
        return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xCCFFFFFF);
        t.setTextSize(15);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    private void updateDots() {
        mDots.removeAllViews();
        for (int i = 0; i < TOTAL_PAGES; i++) {
            View dot = new View(this);
            int size = i == mCurrentPage ? dp(10) : dp(6);
            int color = i == mCurrentPage ? ACCENT : 0x44FFFFFF;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dp(4), 0, dp(4), 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundColor(color);
            dot.setClipToOutline(true);
            dot.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            mDots.addView(dot);
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
