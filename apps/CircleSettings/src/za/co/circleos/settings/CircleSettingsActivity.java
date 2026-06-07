/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * CircleSettings main activity — HyperOS-inspired card-based layout.
 *
 * Design elements from Xiaomi HyperOS Settings:
 *   - Profile/device header card at top with avatar + device name
 *   - Category sections with rounded card backgrounds
 *   - Each setting row: icon + title + subtitle + chevron
 *   - Circle brand colors (Deep navy, Warm cream, Sage green, Gold accent)
 *   - Privacy Dashboard prominent as first category
 */
package za.co.circleos.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class CircleSettingsActivity extends Activity {

    private static final int DEEP    = 0xFF1A1F36;
    private static final int WARM    = 0xFFF5F0EB;
    private static final int GOLD    = 0xFFD4A574;
    private static final int SAGE    = 0xFF7D9B8A;
    private static final int TERRA   = 0xFFC17B5D;
    private static final int CARD    = 0xFF243047;
    private static final int DIVIDER = 0xFF2D3A52;
    private static final int SUBTITLE = 0x99F5F0EB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(DEEP);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(48), dp(16), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Device header card ----
        root.addView(buildHeaderCard());

        // ---- Privacy & Security (primary category) ----
        root.addView(sectionLabel("Privacy & Security"));
        LinearLayout privacyCard = cardContainer();
        privacyCard.addView(settingRow("Privacy Dashboard",
                "View all privacy activity", SAGE, v -> openDashboard()));
        privacyCard.addView(divider());
        privacyCard.addView(settingRow("Network Permissions",
                "Default deny — apps must ask", SAGE, null));
        privacyCard.addView(divider());
        privacyCard.addView(settingRow("Contact Scoping",
                "Choose which contacts each app sees", SAGE, null));
        privacyCard.addView(divider());
        privacyCard.addView(settingRow("Fake Identifiers",
                "Synthetic IMEI, MAC, Ad ID per app", SAGE, null));
        privacyCard.addView(divider());
        privacyCard.addView(settingRow("Auto-Revoke",
                "Unused permissions revoked after 7 days", SAGE, null));
        root.addView(privacyCard);

        // ---- Network & Internet ----
        root.addView(sectionLabel("Network & Internet"));
        LinearLayout netCard = cardContainer();
        netCard.addView(settingRow("DNS over HTTPS",
                "Quad9 (dns.quad9.net) — encrypted", GOLD, null));
        netCard.addView(divider());
        netCard.addView(settingRow("Traffic Lobby",
                "Quarantine suspicious connections", GOLD, null));
        netCard.addView(divider());
        netCard.addView(settingRow("Mesh Network",
                "Phone-to-phone via WiFi Direct + BLE", GOLD, null));
        root.addView(netCard);

        // ---- AI & Personalisation ----
        root.addView(sectionLabel("AI & Personalisation"));
        LinearLayout aiCard = cardContainer();
        aiCard.addView(settingRow("B! AI Assistant",
                "On-device inference via CircleInference", TERRA, null));
        aiCard.addView(divider());
        aiCard.addView(settingRow("Personality Modes",
                "Work, Personal, Kids — per-profile rules", TERRA, null));
        root.addView(aiCard);

        // ---- System ----
        root.addView(sectionLabel("System"));
        LinearLayout sysCard = cardContainer();
        sysCard.addView(settingRow("Software Update",
                "Check ota.circleos.co.za", WARM, null));
        sysCard.addView(divider());
        sysCard.addView(settingRow("Backup & Restore",
                "Encrypted local backup (AES-256-GCM)", WARM, null));
        sysCard.addView(divider());
        sysCard.addView(settingRow("About Circle OS",
                getVersionString(), WARM, null));
        root.addView(sysCard);

        setContentView(scroll);
    }

    // ------------------------------------------------------------------
    //  Header card
    // ------------------------------------------------------------------

    private View buildHeaderCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundColor(CARD);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        card.setLayoutParams(lp);
        // Round corners via clip
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });

        // Avatar placeholder — Circle logo circle
        View avatar = new View(this);
        avatar.setBackgroundColor(SAGE);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        avLp.rightMargin = dp(16);
        avatar.setLayoutParams(avLp);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        card.addView(avatar);

        // Text block
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText("Circle OS Device");
        name.setTextColor(WARM);
        name.setTextSize(18);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        text.addView(name);

        TextView sub = new TextView(this);
        sub.setText(getVersionString());
        sub.setTextColor(SUBTITLE);
        sub.setTextSize(13);
        sub.setPadding(0, dp(2), 0, 0);
        text.addView(sub);

        card.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Chevron
        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(SUBTITLE);
        chevron.setTextSize(24);
        card.addView(chevron);

        return card;
    }

    // ------------------------------------------------------------------
    //  UI building blocks
    // ------------------------------------------------------------------

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(SUBTITLE);
        t.setTextSize(12);
        t.setAllCaps(true);
        t.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(CARD);
        card.setPadding(dp(16), dp(4), dp(16), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        return card;
    }

    private View settingRow(String title, String subtitle, int accentColor,
                            View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        if (onClick != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(onClick);
        }

        // Accent dot
        View dot = new View(this);
        dot.setBackgroundColor(accentColor);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(4), dp(32));
        dotLp.rightMargin = dp(14);
        dot.setLayoutParams(dotLp);
        dot.setClipToOutline(true);
        dot.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(2));
            }
        });
        row.addView(dot);

        // Text
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(WARM);
        t.setTextSize(15);
        text.addView(t);
        if (subtitle != null) {
            TextView s = new TextView(this);
            s.setText(subtitle);
            s.setTextColor(SUBTITLE);
            s.setTextSize(12);
            s.setPadding(0, dp(2), 0, 0);
            text.addView(s);
        }
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Chevron
        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(SUBTITLE);
        chevron.setTextSize(20);
        row.addView(chevron);

        return row;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(18);
        d.setLayoutParams(lp);
        return d;
    }

    // ------------------------------------------------------------------
    //  Navigation
    // ------------------------------------------------------------------

    private void openDashboard() {
        Intent i = new Intent("com.circleos.action.OPEN_PRIVACY_DASHBOARD");
        if (i.resolveActivity(getPackageManager()) != null) {
            startActivity(i);
        }
    }

    private String getVersionString() {
        String ver = SystemProperties.get("ro.circle.version", "0.1.0-alpha");
        return "CircleOS " + ver + " — Android " + Build.VERSION.RELEASE;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
