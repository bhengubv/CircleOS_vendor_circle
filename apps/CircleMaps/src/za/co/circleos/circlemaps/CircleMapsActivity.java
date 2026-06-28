/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Maps (WP-38/39/40) — DataAcuity map embed with real place search and
 * directions (drive / transit / walk). Per the maps rule this drives the
 * DataAcuity embed only (no MapLibre/OSM/Google): search loads ?q=, directions
 * load ?from=<here>&to=<dest>&mode=, so turn-by-turn and transit are rendered by
 * the embed. True-black + brand accent only.
 */
package za.co.circleos.circlemaps;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URLEncoder;

public final class CircleMapsActivity extends Activity implements LocationListener {

    private static final String TAG = "CircleMaps";
    private static final String MAPS_BASE = "https://maps.dataacuity.co.za";

    private static final int BG      = 0xFF000000; // true black
    private static final int SURFACE = 0xFF161616;
    private static final int ACCENT  = 0xFF2196F3;
    private static final int TEXT    = 0xFFFFFFFF;
    private static final int DIM     = 0xFF9AA0A6;

    // travel modes; "search" = plain place lookup
    private static final String MODE_SEARCH  = "search";
    private static final String MODE_DRIVE   = "drive";
    private static final String MODE_TRANSIT = "transit";
    private static final String MODE_WALK    = "walk";

    private LocationManager mLocMgr;
    private Location mLastLoc;
    private WebView mMapView;
    private EditText mInput;
    private String mMode = MODE_SEARCH;
    private final java.util.Map<String, TextView> mChips = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        mLocMgr = getSystemService(LocationManager.class);
        buildUI();
        startLocation();
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        mMapView = new WebView(this);
        WebSettings ws = mMapView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setGeolocationEnabled(true);
        mMapView.setWebViewClient(new WebViewClient());
        mMapView.setBackgroundColor(BG);
        root.addView(mMapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(0xE6000000);
        top.setPadding(dp(16), dp(44), dp(16), dp(10));

        mInput = new EditText(this);
        mInput.setHint("Search places or a destination…");
        mInput.setTextColor(TEXT);
        mInput.setHintTextColor(DIM);
        mInput.setBackgroundColor(SURFACE);
        mInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        mInput.setSingleLine(true);
        mInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mInput.setOnEditorActionListener((v, actionId, ev) -> { submit(); return true; });
        top.addView(mInput);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(10), 0, 0);
        chips.addView(chip("Search", MODE_SEARCH));
        chips.addView(chip("Drive", MODE_DRIVE));
        chips.addView(chip("Transit", MODE_TRANSIT));
        chips.addView(chip("Walk", MODE_WALK));
        top.addView(chips);

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        root.addView(top, tlp);

        // Recenter button
        TextView recenter = new TextView(this);
        recenter.setText("◎");
        recenter.setTextColor(ACCENT);
        recenter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        recenter.setBackgroundColor(SURFACE);
        recenter.setPadding(dp(14), dp(10), dp(14), dp(10));
        recenter.setOnClickListener(v -> loadMap());
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.BOTTOM | Gravity.END;
        rlp.rightMargin = dp(16);
        rlp.bottomMargin = dp(24);
        root.addView(recenter, rlp);

        setContentView(root);
        setMode(MODE_SEARCH);
        loadMap();
    }

    private TextView chip(String label, final String mode) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        c.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        c.setLayoutParams(lp);
        c.setOnClickListener(v -> { setMode(mode); if (mInput.getText().length() > 0) submit(); });
        mChips.put(mode, c);
        return c;
    }

    private void setMode(String mode) {
        mMode = mode;
        for (java.util.Map.Entry<String, TextView> e : mChips.entrySet()) {
            boolean on = e.getKey().equals(mode);
            e.getValue().setBackgroundColor(on ? ACCENT : SURFACE);
            e.getValue().setTextColor(on ? 0xFF000000 : TEXT);
        }
        mInput.setHint(mode.equals(MODE_SEARCH) ? "Search places…" : "Destination…");
    }

    private void submit() {
        String q = mInput.getText().toString().trim();
        if (q.isEmpty()) { loadMap(); return; }
        String here = (mLastLoc != null)
                ? mLastLoc.getLatitude() + "," + mLastLoc.getLongitude() : "";
        String url;
        if (MODE_SEARCH.equals(mMode)) {
            url = MAPS_BASE + "/embed?q=" + enc(q) + (here.isEmpty() ? "" : "&near=" + here) + "&z=14";
        } else {
            url = MAPS_BASE + "/embed?from=" + enc(here) + "&to=" + enc(q) + "&mode=" + mMode + "&nav=1";
        }
        mMapView.loadUrl(url);
    }

    private void loadMap() {
        String url = MAPS_BASE + "/embed";
        if (mLastLoc != null) {
            url += "?lat=" + mLastLoc.getLatitude() + "&lng=" + mLastLoc.getLongitude() + "&z=14";
        }
        mMapView.loadUrl(url);
    }

    private void startLocation() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                mLocMgr.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 10_000, 10f, this);
                mLastLoc = mLocMgr.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
                if (mLastLoc != null) loadMap();
            } else {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        } catch (Exception e) {
            Log.w(TAG, "location failed", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == 1 && g.length > 0 && g[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startLocation();
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        boolean first = mLastLoc == null;
        mLastLoc = loc;
        if (first) loadMap();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
