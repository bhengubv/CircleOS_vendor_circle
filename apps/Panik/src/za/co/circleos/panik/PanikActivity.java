/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Panik — personal safety app for Circle OS.
 *
 * Features: SOS alerts, trip tracking, safe zones, evidence recording,
 * emergency contacts, community alerts. Works offline via AetherNet mesh.
 * B! (CircleAI) integration for voice-activated SOS and smart alerts.
 *
 * Navigation: bottom tabs (Home, Trips, Zones, Contacts, History)
 * with floating SOS button always visible.
 */
package za.co.circleos.panik;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import za.co.circleos.mesh.ICircleMeshService;

public final class PanikActivity extends Activity implements LocationListener {

    private static final String TAG = "Panik";
    private static final int PERM_REQUEST = 100;

    private static final int BG_DARK    = 0xFF0A0A0A;
    private static final int ACCENT     = 0xFF2196F3;
    private static final int DANGER     = 0xFFE53935;
    private static final int TEXT_PRIMARY = 0xFFF5F0EB;
    private static final int TEXT_SECONDARY = 0x99F5F0EB;
    private static final int CARD_BG    = 0xFF1A1A2E;

    private static final String API_BASE = "https://panikapi.thegeeknetwork.co.za/api";

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final List<EmergencyContact> mContacts = new ArrayList<>();
    private final List<Trip> mTrips = new ArrayList<>();
    private final List<Alert> mAlerts = new ArrayList<>();
    private final List<SafeZone> mSafeZones = new ArrayList<>();

    private LocationManager mLocationManager;
    private Vibrator mVibrator;
    private ICircleMeshService mMesh;
    private String mAuthToken = "";

    private FrameLayout mContentFrame;
    private LinearLayout mTabBar;
    private Button mSosButton;
    private int mCurrentTab = 0;

    private Location mLastLocation;
    private boolean mSosActive = false;
    private String mActiveTripId = null;
    private boolean mRecordingEvidence = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG_DARK);
        getWindow().setNavigationBarColor(BG_DARK);

        mLocationManager = getSystemService(LocationManager.class);
        mVibrator = getSystemService(Vibrator.class);

        try {
            IBinder b = ServiceManager.getService("circle.mesh");
            if (b != null) mMesh = ICircleMeshService.Stub.asInterface(b);
        } catch (Throwable t) {
            Log.w(TAG, "Mesh service unavailable", t);
        }

        requestPermissions(new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
        }, PERM_REQUEST);

        buildUI();
        startLocationUpdates();
        loadData();
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG_DARK);

        mContentFrame = new FrameLayout(this);
        root.addView(mContentFrame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // SOS floating button
        mSosButton = new Button(this);
        mSosButton.setText("SOS");
        mSosButton.setTextColor(Color.WHITE);
        mSosButton.setTextSize(20);
        mSosButton.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable sosBg = new GradientDrawable();
        sosBg.setShape(GradientDrawable.OVAL);
        sosBg.setColor(DANGER);
        mSosButton.setBackground(sosBg);
        mSosButton.setOnClickListener(v -> toggleSos());
        mSosButton.setOnLongClickListener(v -> { triggerSilentSos(); return true; });
        FrameLayout.LayoutParams sosLp = new FrameLayout.LayoutParams(dp(80), dp(80));
        sosLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sosLp.bottomMargin = dp(100);
        root.addView(mSosButton, sosLp);

        // Tab bar
        mTabBar = new LinearLayout(this);
        mTabBar.setOrientation(LinearLayout.HORIZONTAL);
        mTabBar.setBackgroundColor(0xFF121212);
        mTabBar.setGravity(Gravity.CENTER);
        mTabBar.setPadding(0, dp(8), 0, dp(8));
        String[] tabs = {"Home", "Trips", "Zones", "Contacts", "History"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(tabs[i]);
            tab.setTextColor(i == 0 ? ACCENT : TEXT_SECONDARY);
            tab.setTextSize(11);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(4), dp(8), dp(4), dp(8));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tab.setLayoutParams(tlp);
            tab.setOnClickListener(v -> switchTab(idx));
            mTabBar.addView(tab);
        }
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        root.addView(mTabBar, barLp);

        setContentView(root);
        switchTab(0);
    }

    private void switchTab(int idx) {
        mCurrentTab = idx;
        mContentFrame.removeAllViews();
        for (int i = 0; i < mTabBar.getChildCount(); i++) {
            ((TextView) mTabBar.getChildAt(i)).setTextColor(i == idx ? ACCENT : TEXT_SECONDARY);
        }
        switch (idx) {
            case 0: showHome(); break;
            case 1: showTrips(); break;
            case 2: showSafeZones(); break;
            case 3: showContacts(); break;
            case 4: showHistory(); break;
        }
    }

    // ── Home Tab ─────────────────────────────────────────────────────
    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(16), dp(48), dp(16), dp(120));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        col.addView(sectionTitle("Panik"));
        col.addView(statusCard());
        col.addView(sectionTitle("Quick Actions"));
        col.addView(quickActions());
        col.addView(sectionTitle("Recent Activity"));
        col.addView(recentActivity());
        col.addView(sectionTitle("Community Alerts"));
        col.addView(communityAlerts());

        scroll.addView(col);
        mContentFrame.addView(scroll);
    }

    private View statusCard() {
        LinearLayout card = cardContainer();
        TextView status = new TextView(this);
        if (mSosActive) {
            status.setText("SOS ACTIVE — Help is on the way");
            status.setTextColor(DANGER);
        } else if (mActiveTripId != null) {
            status.setText("Trip in progress — tracking your location");
            status.setTextColor(ACCENT);
        } else {
            status.setText("You are safe. Mesh: " + (mMesh != null ? "connected" : "offline"));
            status.setTextColor(0xFF4CAF50);
        }
        status.setTextSize(16);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(status);

        if (mLastLocation != null) {
            TextView loc = new TextView(this);
            loc.setText(String.format(Locale.US, "Location: %.4f, %.4f",
                    mLastLocation.getLatitude(), mLastLocation.getLongitude()));
            loc.setTextColor(TEXT_SECONDARY);
            loc.setTextSize(12);
            loc.setPadding(0, dp(4), 0, 0);
            card.addView(loc);
        }
        return card;
    }

    private View quickActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(actionButton("Start Trip", ACCENT, v -> startTrip()));
        row.addView(actionButton("Check In", 0xFF4CAF50, v -> checkIn()));
        row.addView(actionButton("Record", 0xFFFF9800, v -> toggleRecording()));
        return row;
    }

    private View recentActivity() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (mAlerts.isEmpty() && mTrips.isEmpty()) {
            list.addView(emptyText("No recent activity"));
        } else {
            int count = 0;
            for (Alert a : mAlerts) {
                if (count++ >= 5) break;
                list.addView(alertRow(a));
            }
        }
        return list;
    }

    private View communityAlerts() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(emptyText("Loading community alerts..."));
        mExecutor.execute(() -> {
            try {
                String json = httpGet(API_BASE + "/community/alerts");
                JSONArray arr = new JSONArray(json);
                mUiHandler.post(() -> {
                    list.removeAllViews();
                    if (arr.length() == 0) {
                        list.addView(emptyText("No alerts nearby"));
                        return;
                    }
                    for (int i = 0; i < Math.min(arr.length(), 5); i++) {
                        try {
                            JSONObject o = arr.getJSONObject(i);
                            list.addView(communityAlertRow(
                                    o.optString("type", "SOS"),
                                    o.optString("distance", "unknown"),
                                    o.optString("timeAgo", "just now")));
                        } catch (Exception ignored) {}
                    }
                });
            } catch (Exception e) {
                mUiHandler.post(() -> {
                    list.removeAllViews();
                    list.addView(emptyText("Offline — checking mesh..."));
                    broadcastMeshSosQuery();
                });
            }
        });
        return list;
    }

    // ── Trips Tab ────────────────────────────────────────────────────
    private void showTrips() {
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(16), dp(48), dp(16), dp(120));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        col.addView(sectionTitle("Trip Tracking"));
        if (mActiveTripId != null) {
            col.addView(activeTripCard());
        } else {
            Button start = new Button(this);
            start.setText("Start New Trip");
            start.setTextColor(BG_DARK);
            start.setBackgroundColor(ACCENT);
            start.setOnClickListener(v -> startTrip());
            col.addView(start);
        }
        col.addView(sectionTitle("Trip History"));
        for (Trip t : mTrips) {
            col.addView(tripRow(t));
        }
        if (mTrips.isEmpty()) col.addView(emptyText("No trips yet"));

        scroll.addView(col);
        mContentFrame.addView(scroll);
    }

    private View activeTripCard() {
        LinearLayout card = cardContainer();
        TextView title = new TextView(this);
        title.setText("Active Trip");
        title.setTextColor(ACCENT);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        Button endTrip = new Button(this);
        endTrip.setText("End Trip");
        endTrip.setTextColor(Color.WHITE);
        endTrip.setBackgroundColor(DANGER);
        endTrip.setOnClickListener(v -> endTrip());
        card.addView(endTrip);
        return card;
    }

    // ── Safe Zones Tab ───────────────────────────────────────────────
    private void showSafeZones() {
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(16), dp(48), dp(16), dp(120));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        col.addView(sectionTitle("Safe Zones"));
        Button add = new Button(this);
        add.setText("Add Safe Zone");
        add.setTextColor(BG_DARK);
        add.setBackgroundColor(ACCENT);
        add.setOnClickListener(v -> addSafeZone());
        col.addView(add);

        for (SafeZone z : mSafeZones) {
            col.addView(safeZoneRow(z));
        }
        if (mSafeZones.isEmpty()) col.addView(emptyText("No safe zones configured"));

        scroll.addView(col);
        mContentFrame.addView(scroll);
    }

    // ── Contacts Tab ─────────────────────────────────────────────────
    private void showContacts() {
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(16), dp(48), dp(16), dp(120));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        col.addView(sectionTitle("Emergency Contacts"));
        Button add = new Button(this);
        add.setText("Add Contact");
        add.setTextColor(BG_DARK);
        add.setBackgroundColor(ACCENT);
        add.setOnClickListener(v -> addEmergencyContact());
        col.addView(add);

        for (EmergencyContact c : mContacts) {
            col.addView(contactRow(c));
        }
        if (mContacts.isEmpty()) col.addView(emptyText("No emergency contacts — add at least one"));

        scroll.addView(col);
        mContentFrame.addView(scroll);
    }

    // ── History Tab ──────────────────────────────────────────────────
    private void showHistory() {
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(16), dp(48), dp(16), dp(120));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        col.addView(sectionTitle("Alert History"));
        for (Alert a : mAlerts) {
            col.addView(alertRow(a));
        }
        if (mAlerts.isEmpty()) col.addView(emptyText("No alerts — stay safe!"));

        scroll.addView(col);
        mContentFrame.addView(scroll);
    }

    // ── SOS Logic ────────────────────────────────────────────────────
    private void toggleSos() {
        if (mSosActive) {
            cancelSos();
        } else {
            triggerSos();
        }
    }

    private void triggerSos() {
        mSosActive = true;
        mSosButton.setText("CANCEL");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF388E3C);
        mSosButton.setBackground(bg);

        if (mVibrator != null) {
            mVibrator.vibrate(new long[]{0, 200, 100, 200, 100, 500}, -1);
        }

        mExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                if (mLastLocation != null) {
                    body.put("latitude", mLastLocation.getLatitude());
                    body.put("longitude", mLastLocation.getLongitude());
                    body.put("accuracy", mLastLocation.getAccuracy());
                }
                body.put("batteryLevel", getBatteryLevel());
                httpPost(API_BASE + "/alerts/sos", body.toString());
            } catch (Exception e) {
                Log.w(TAG, "SOS API failed — falling back to mesh", e);
                broadcastMeshSos();
            }

            notifyEmergencyContacts();
        });

        switchTab(0);
    }

    private void triggerSilentSos() {
        mSosActive = true;
        mExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("silent", true);
                if (mLastLocation != null) {
                    body.put("latitude", mLastLocation.getLatitude());
                    body.put("longitude", mLastLocation.getLongitude());
                }
                httpPost(API_BASE + "/alerts/sos", body.toString());
            } catch (Exception e) {
                broadcastMeshSos();
            }
            notifyEmergencyContacts();
        });
    }

    private void cancelSos() {
        mSosActive = false;
        mSosButton.setText("SOS");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(DANGER);
        mSosButton.setBackground(bg);

        mExecutor.execute(() -> {
            try {
                httpPost(API_BASE + "/alerts/cancel", "{}");
            } catch (Exception ignored) {}
        });
        switchTab(0);
    }

    private void notifyEmergencyContacts() {
        for (EmergencyContact c : mContacts) {
            try {
                String msg = "PANIK SOS ALERT! I need help. ";
                if (mLastLocation != null) {
                    msg += "Location: https://maps.google.com/?q="
                            + mLastLocation.getLatitude() + ","
                            + mLastLocation.getLongitude();
                }
                SmsManager.getDefault().sendTextMessage(
                        c.phone, null, msg, null, null);
            } catch (Exception e) {
                Log.w(TAG, "SMS to " + c.name + " failed", e);
                sendMeshAlert(c);
            }
        }
    }

    // ── Mesh Fallback ────────────────────────────────────────────────
    private void broadcastMeshSos() {
        if (mMesh == null) return;
        try {
            JSONObject sos = new JSONObject();
            sos.put("type", "SOS");
            if (mLastLocation != null) {
                sos.put("lat", mLastLocation.getLatitude());
                sos.put("lng", mLastLocation.getLongitude());
            }
            sos.put("ts", System.currentTimeMillis());
            mMesh.sendMessage("broadcast", sos.toString().getBytes("UTF-8"), 0x30);
        } catch (Exception e) {
            Log.w(TAG, "Mesh SOS broadcast failed", e);
        }
    }

    private void broadcastMeshSosQuery() {
        if (mMesh == null) return;
        try {
            mMesh.sendMessage("broadcast",
                    "{\"type\":\"SOS_QUERY\"}".getBytes("UTF-8"), 0x31);
        } catch (Exception e) {
            Log.w(TAG, "Mesh SOS query failed", e);
        }
    }

    private void sendMeshAlert(EmergencyContact c) {
        if (mMesh == null) return;
        try {
            JSONObject alert = new JSONObject();
            alert.put("type", "EMERGENCY_CONTACT_ALERT");
            alert.put("contactPhone", c.phone);
            if (mLastLocation != null) {
                alert.put("lat", mLastLocation.getLatitude());
                alert.put("lng", mLastLocation.getLongitude());
            }
            mMesh.sendMessage("broadcast", alert.toString().getBytes("UTF-8"), 0x32);
        } catch (Exception e) {
            Log.w(TAG, "Mesh alert to contact failed", e);
        }
    }

    // ── Trip Management ──────────────────────────────────────────────
    private void startTrip() {
        mExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                if (mLastLocation != null) {
                    body.put("startLatitude", mLastLocation.getLatitude());
                    body.put("startLongitude", mLastLocation.getLongitude());
                }
                body.put("startTime", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                        Locale.US).format(new Date()));
                String resp = httpPost(API_BASE + "/trips", body.toString());
                JSONObject r = new JSONObject(resp);
                mActiveTripId = r.optString("id", "local-" + System.currentTimeMillis());
                mUiHandler.post(() -> switchTab(1));
            } catch (Exception e) {
                Log.w(TAG, "Start trip failed", e);
                mActiveTripId = "local-" + System.currentTimeMillis();
                mUiHandler.post(() -> switchTab(1));
            }
        });
    }

    private void endTrip() {
        if (mActiveTripId == null) return;
        final String tripId = mActiveTripId;
        mActiveTripId = null;
        mExecutor.execute(() -> {
            try {
                httpPost(API_BASE + "/trips/" + tripId + "/end", "{}");
            } catch (Exception ignored) {}
            mUiHandler.post(() -> switchTab(1));
        });
    }

    // ── Safe Zone Management ─────────────────────────────────────────
    private void addSafeZone() {
        if (mLastLocation == null) return;
        mExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", "My Location");
                body.put("centerLatitude", mLastLocation.getLatitude());
                body.put("centerLongitude", mLastLocation.getLongitude());
                body.put("radiusMeters", 200);
                body.put("alertOnEntry", true);
                body.put("alertOnExit", true);
                httpPost(API_BASE + "/safe-zones", body.toString());
                loadSafeZones();
                mUiHandler.post(() -> switchTab(2));
            } catch (Exception e) {
                Log.w(TAG, "Add safe zone failed", e);
            }
        });
    }

    private void checkIn() {
        mExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "safe");
                if (mLastLocation != null) {
                    body.put("latitude", mLastLocation.getLatitude());
                    body.put("longitude", mLastLocation.getLongitude());
                }
                httpPost(API_BASE + "/checkin", body.toString());
            } catch (Exception ignored) {}
        });
    }

    // ── Evidence Recording ───────────────────────────────────────────
    private void toggleRecording() {
        mRecordingEvidence = !mRecordingEvidence;
        if (mRecordingEvidence) {
            mExecutor.execute(() -> {
                try {
                    httpPost(API_BASE + "/evidence/start", "{}");
                } catch (Exception ignored) {}
            });
        } else {
            mExecutor.execute(() -> {
                try {
                    httpPost(API_BASE + "/evidence/stop", "{}");
                } catch (Exception ignored) {}
            });
        }
    }

    // ── Emergency Contact Management ─────────────────────────────────
    private void addEmergencyContact() {
        final android.widget.EditText name = new android.widget.EditText(this);
        name.setHint("Name");
        final android.widget.EditText phone = new android.widget.EditText(this);
        phone.setHint("Phone number");
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        final int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        box.addView(name);
        box.addView(phone);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add emergency contact")
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    final String n = name.getText().toString().trim();
                    final String p = phone.getText().toString().trim();
                    if (n.isEmpty() || p.isEmpty()) return;
                    mExecutor.execute(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("name", n);
                            body.put("phone", p);
                            httpPost(API_BASE + "/contacts", body.toString());
                        } catch (Exception ignored) {}
                        runOnUiThread(this::loadContacts);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Location ─────────────────────────────────────────────────────
    private void startLocationUpdates() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mLocationManager.requestLocationUpdates(
                        LocationManager.FUSED_PROVIDER, 10_000, 10f, this);
                mLastLocation = mLocationManager.getLastKnownLocation(
                        LocationManager.FUSED_PROVIDER);
            }
        } catch (Exception e) {
            Log.w(TAG, "Location updates failed", e);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        if (mActiveTripId != null) {
            mExecutor.execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("latitude", location.getLatitude());
                    body.put("longitude", location.getLongitude());
                    body.put("accuracy", location.getAccuracy());
                    httpPost(API_BASE + "/trips/" + mActiveTripId + "/location",
                            body.toString());
                } catch (Exception ignored) {}
            });
        }
        if (mSosActive) {
            mExecutor.execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("latitude", location.getLatitude());
                    body.put("longitude", location.getLongitude());
                    httpPost(API_BASE + "/alerts/location", body.toString());
                } catch (Exception ignored) {}
                broadcastMeshSos();
            });
        }
        checkSafeZones(location);
    }

    private void checkSafeZones(Location loc) {
        for (SafeZone z : mSafeZones) {
            float[] results = new float[1];
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                    z.lat, z.lng, results);
            boolean inside = results[0] <= z.radiusMeters;
            if (z.wasInside != null && z.wasInside && !inside && z.alertOnExit) {
                mExecutor.execute(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("zoneId", z.id);
                        body.put("event", "exit");
                        httpPost(API_BASE + "/safe-zones/events", body.toString());
                    } catch (Exception ignored) {}
                });
            }
            z.wasInside = inside;
        }
    }

    // ── Data Loading ─────────────────────────────────────────────────
    private void loadData() {
        loadContacts();
        loadTrips();
        loadAlerts();
        loadSafeZones();
    }

    private void loadContacts() {
        mExecutor.execute(() -> {
            try {
                String json = httpGet(API_BASE + "/emergency-contacts");
                JSONArray arr = new JSONArray(json);
                mContacts.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    mContacts.add(new EmergencyContact(
                            o.optString("id"), o.optString("name"),
                            o.optString("phoneNumber"), o.optString("relationship")));
                }
                mUiHandler.post(() -> { if (mCurrentTab == 3) switchTab(3); });
            } catch (Exception e) {
                Log.d(TAG, "loadContacts failed", e);
            }
        });
    }

    private void loadTrips() {
        mExecutor.execute(() -> {
            try {
                String json = httpGet(API_BASE + "/trips?limit=20");
                JSONArray arr = new JSONArray(json);
                mTrips.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    mTrips.add(new Trip(o.optString("id"), o.optString("status"),
                            o.optString("startTime"), o.optString("endTime")));
                }
            } catch (Exception e) {
                Log.d(TAG, "loadTrips failed", e);
            }
        });
    }

    private void loadAlerts() {
        mExecutor.execute(() -> {
            try {
                String json = httpGet(API_BASE + "/alerts?limit=20");
                JSONArray arr = new JSONArray(json);
                mAlerts.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    mAlerts.add(new Alert(o.optString("id"), o.optString("type"),
                            o.optString("status"), o.optString("createdAt")));
                }
                mUiHandler.post(() -> { if (mCurrentTab == 4) switchTab(4); });
            } catch (Exception e) {
                Log.d(TAG, "loadAlerts failed", e);
            }
        });
    }

    private void loadSafeZones() {
        mExecutor.execute(() -> {
            try {
                String json = httpGet(API_BASE + "/safe-zones");
                JSONArray arr = new JSONArray(json);
                mSafeZones.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    mSafeZones.add(new SafeZone(
                            o.optString("id"), o.optString("name"),
                            o.optDouble("centerLatitude"), o.optDouble("centerLongitude"),
                            o.optDouble("radiusMeters", 200),
                            o.optBoolean("alertOnEntry", true),
                            o.optBoolean("alertOnExit", true)));
                }
                mUiHandler.post(() -> { if (mCurrentTab == 2) switchTab(2); });
            } catch (Exception e) {
                Log.d(TAG, "loadSafeZones failed", e);
            }
        });
    }

    // ── HTTP Helpers ─────────────────────────────────────────────────
    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mAuthToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + mAuthToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private int getBatteryLevel() {
        try {
            android.os.BatteryManager bm = getSystemService(android.os.BatteryManager.class);
            return bm != null ? bm.getIntProperty(
                    android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        } catch (Exception e) { return -1; }
    }

    // ── UI Helpers ───────────────────────────────────────────────────
    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(CARD_BG);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEXT_PRIMARY);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(8));
        return t;
    }

    private TextView emptyText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEXT_SECONDARY);
        t.setTextSize(14);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private View actionButton(String text, int color, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setBackgroundColor(color);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private View alertRow(Alert a) {
        LinearLayout row = cardContainer();
        TextView type = new TextView(this);
        type.setText(a.type);
        type.setTextColor(a.type.equals("SOS") ? DANGER : ACCENT);
        type.setTextSize(14);
        type.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(type);
        TextView time = new TextView(this);
        time.setText(a.createdAt + " — " + a.status);
        time.setTextColor(TEXT_SECONDARY);
        time.setTextSize(12);
        row.addView(time);
        return row;
    }

    private View tripRow(Trip t) {
        LinearLayout row = cardContainer();
        TextView status = new TextView(this);
        status.setText("Trip " + t.status);
        status.setTextColor(ACCENT);
        status.setTextSize(14);
        row.addView(status);
        TextView time = new TextView(this);
        time.setText(t.startTime + (t.endTime != null ? " — " + t.endTime : " (ongoing)"));
        time.setTextColor(TEXT_SECONDARY);
        time.setTextSize(12);
        row.addView(time);
        return row;
    }

    private View safeZoneRow(SafeZone z) {
        LinearLayout row = cardContainer();
        TextView name = new TextView(this);
        name.setText(z.name);
        name.setTextColor(TEXT_PRIMARY);
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(name);
        TextView detail = new TextView(this);
        detail.setText(String.format(Locale.US, "Radius: %.0fm — Entry: %s Exit: %s",
                z.radiusMeters, z.alertOnEntry ? "ON" : "OFF",
                z.alertOnExit ? "ON" : "OFF"));
        detail.setTextColor(TEXT_SECONDARY);
        detail.setTextSize(12);
        row.addView(detail);
        return row;
    }

    private View contactRow(EmergencyContact c) {
        LinearLayout row = cardContainer();
        row.setOnClickListener(v -> {
            try {
                Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + c.phone));
                startActivity(call);
            } catch (Exception ignored) {}
        });
        TextView name = new TextView(this);
        name.setText(c.name + " (" + c.relationship + ")");
        name.setTextColor(TEXT_PRIMARY);
        name.setTextSize(14);
        row.addView(name);
        TextView phone = new TextView(this);
        phone.setText(c.phone);
        phone.setTextColor(ACCENT);
        phone.setTextSize(12);
        row.addView(phone);
        return row;
    }

    private View communityAlertRow(String type, String distance, String timeAgo) {
        LinearLayout row = cardContainer();
        TextView t = new TextView(this);
        t.setText(type + " — " + distance + " away, " + timeAgo);
        t.setTextColor(0xFFFF9800);
        t.setTextSize(13);
        row.addView(t);
        return row;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ── Data Classes ─────────────────────────────────────────────────
    static final class EmergencyContact {
        final String id, name, phone, relationship;
        EmergencyContact(String id, String name, String phone, String relationship) {
            this.id = id; this.name = name; this.phone = phone;
            this.relationship = relationship;
        }
    }

    static final class Trip {
        final String id, status, startTime, endTime;
        Trip(String id, String status, String startTime, String endTime) {
            this.id = id; this.status = status; this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    static final class Alert {
        final String id, type, status, createdAt;
        Alert(String id, String type, String status, String createdAt) {
            this.id = id; this.type = type; this.status = status;
            this.createdAt = createdAt;
        }
    }

    static final class SafeZone {
        final String id, name;
        final double lat, lng, radiusMeters;
        final boolean alertOnEntry, alertOnExit;
        Boolean wasInside;
        SafeZone(String id, String name, double lat, double lng,
                 double radiusMeters, boolean alertOnEntry, boolean alertOnExit) {
            this.id = id; this.name = name; this.lat = lat; this.lng = lng;
            this.radiusMeters = radiusMeters; this.alertOnEntry = alertOnEntry;
            this.alertOnExit = alertOnExit;
        }
    }
}
