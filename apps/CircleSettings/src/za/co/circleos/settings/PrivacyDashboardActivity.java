/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * CircleSettings -- Privacy Dashboard.
 *
 * Queries every Circle privacy binder published by system_server
 * (circle.privacy, circle.permission, circle.analytics,
 * circle.camera_privacy, circle.clipboard_privacy,
 * circle.notification_privacy, circle.backup, circle.update) and
 * surfaces:
 *
 *  - system-wide privacy counters (denied perms, faked ids, network grants)
 *  - camera-in-use indicator + last-use timestamp
 *  - clipboard change count + last change timestamp
 *  - notification listener count + revocation count
 *  - backup configured?
 *  - update state + last check + available version
 *  - per-app list with computed privacy score (0..100)
 *
 * UI is built programmatically -- no XML layouts to drift out of sync
 * with the binder shapes. True-black Circle OS theme
 * (black background, white text, brand
 * blue #2196F3 for protected status).
 */
package za.co.circleos.settings;

import android.app.Activity;
import android.app.ListActivity;
import android.circleos.AppPrivacyPolicy;
import android.circleos.ICirclePrivacyManager;
import android.circleos.privacy.ICirclePrivacyManagerService;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import za.co.circleos.analytics.ICircleAnalyticsService;
import za.co.circleos.backup.ICircleBackupService;
import za.co.circleos.camera.ICircleCameraPrivacyService;
import za.co.circleos.clipboard.ICircleClipboardPrivacyService;
import za.co.circleos.notification.ICircleNotificationPrivacyService;
import za.co.circleos.permission.ICirclePermissionService;
import za.co.circleos.update.ICircleUpdateService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PrivacyDashboardActivity extends Activity {

    private static final String TAG = "CirclePrivacyDash";

    private static final int CIRCLE_DEEP        = 0xFF000000;
    private static final int CIRCLE_WARM        = 0xFFFFFFFF;
    private static final int CIRCLE_GOLD        = 0xFF2196F3;
    private static final int CIRCLE_SAGE        = 0xFF2196F3;
    private static final int CIRCLE_TERRACOTTA  = 0xFF2196F3;
    private static final int CIRCLE_BLOCKED     = 0xFFC45C5C;
    private static final int CIRCLE_CARD        = 0xFF161616;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(CIRCLE_DEEP);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(header("Circle Privacy"));
        root.addView(subhead("You're not the product. Trust."));

        // -------- System-wide counters --------
        root.addView(sectionTitle("System counters"));
        root.addView(counterCard(querySystemCounters()));

        // -------- Camera --------
        root.addView(sectionTitle("Camera"));
        root.addView(textCard(queryCameraStatus()));

        // -------- Clipboard --------
        root.addView(sectionTitle("Clipboard"));
        root.addView(textCard(queryClipboardStatus()));

        // -------- Notification listeners --------
        root.addView(sectionTitle("Notification listeners"));
        root.addView(textCard(queryNotificationStatus()));

        // -------- Backup --------
        root.addView(sectionTitle("Backup"));
        root.addView(textCard(queryBackupStatus()));

        // -------- Update --------
        root.addView(sectionTitle("OS Update"));
        root.addView(textCard(queryUpdateStatus()));

        // -------- Per-app scores --------
        root.addView(sectionTitle("Per-app privacy scores"));
        final ListView apps = new ListView(this);
        apps.setBackgroundColor(CIRCLE_CARD);
        apps.setDivider(null);
        apps.setAdapter(new AppScoreAdapter(loadApps()));
        // Cap the inline height so the scroll view scrolls the whole page
        // rather than the inner list -- single-scroll UX matches the brief.
        apps.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        root.addView(apps);

        setContentView(scroll);
    }

    // ------------------------------------------------------------------
    //  Binder queries
    // ------------------------------------------------------------------

    private String querySystemCounters() {
        final IBinder b = ServiceManager.getService("circle.privacy");
        if (b == null) return "circle.privacy service not running";
        try {
            final ICirclePrivacyManagerService svc =
                    ICirclePrivacyManagerService.Stub.asInterface(b);
            return "Denied permissions: " + svc.getDeniedPermissionCount() + "\n"
                    + "Faked identifiers: " + svc.getFakedIdentifierCount() + "\n"
                    + "Network grants: "    + svc.getNetworkGrantCount();
        } catch (RemoteException re) {
            return "circle.privacy: " + re.getMessage();
        }
    }

    private String queryCameraStatus() {
        final IBinder b = ServiceManager.getService("circle.camera_privacy");
        if (b == null) return "circle.camera_privacy: not running";
        try {
            final ICircleCameraPrivacyService svc =
                    ICircleCameraPrivacyService.Stub.asInterface(b);
            return (svc.isAnyCameraInUse() ? "ACTIVE -- some app has the camera open" : "idle")
                    + "\nSessions since boot: " + svc.getCameraSessionCount();
        } catch (RemoteException re) {
            return "circle.camera_privacy: " + re.getMessage();
        }
    }

    private String queryClipboardStatus() {
        final IBinder b = ServiceManager.getService("circle.clipboard_privacy");
        if (b == null) return "circle.clipboard_privacy: not running";
        try {
            final ICircleClipboardPrivacyService svc =
                    ICircleClipboardPrivacyService.Stub.asInterface(b);
            final long ts = svc.getLastClipChangeMs();
            return "Clip changes: " + svc.getClipChangeCount()
                    + "\nLast change: " + (ts == 0 ? "never" : fmt(ts));
        } catch (RemoteException re) {
            return "circle.clipboard_privacy: " + re.getMessage();
        }
    }

    private String queryNotificationStatus() {
        final IBinder b = ServiceManager.getService("circle.notification_privacy");
        if (b == null) return "circle.notification_privacy: not running";
        try {
            final ICircleNotificationPrivacyService svc =
                    ICircleNotificationPrivacyService.Stub.asInterface(b);
            final String enabled = svc.getEnabledListenerPackages();
            return "Apps reading notifications: "
                    + (enabled.isEmpty() ? "none" : enabled.replace(";", ", "))
                    + "\nRevocations this session: " + svc.getRevocationCount();
        } catch (RemoteException re) {
            return "circle.notification_privacy: " + re.getMessage();
        }
    }

    private String queryBackupStatus() {
        final IBinder b = ServiceManager.getService("circle.backup");
        if (b == null) return "circle.backup: not running";
        try {
            final ICircleBackupService svc = ICircleBackupService.Stub.asInterface(b);
            return (svc.isConfigured()
                    ? "Configured -- " + svc.getBackupCount() + " backup(s) on disk"
                    : "Not configured -- set a backup PIN in Settings");
        } catch (RemoteException re) {
            return "circle.backup: " + re.getMessage();
        }
    }

    private String queryUpdateStatus() {
        final IBinder b = ServiceManager.getService("circle.update");
        if (b == null) return "circle.update: not running";
        try {
            final ICircleUpdateService svc = ICircleUpdateService.Stub.asInterface(b);
            final long last = svc.getLastCheckTime();
            return "State: "    + stateLabel(svc.getState())
                    + "\nChannel: " + svc.getChannel()
                    + "\nLast check: " + (last == 0 ? "never" : fmt(last))
                    + "\nAvailable: "  + (svc.getAvailableVersion().isEmpty()
                            ? "up to date" : svc.getAvailableVersion());
        } catch (RemoteException re) {
            return "circle.update: " + re.getMessage();
        }
    }

    private static String stateLabel(int s) {
        switch (s) {
            case 0: return "idle";
            case 1: return "checking";
            case 2: return "downloading";
            case 3: return "staged -- reboot to apply";
            case 4: return "error";
            default: return "unknown(" + s + ")";
        }
    }

    private static String fmt(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(ms));
    }

    // ------------------------------------------------------------------
    //  Per-app list
    // ------------------------------------------------------------------

    private List<AppScore> loadApps() {
        final PackageManager pm = getPackageManager();
        final List<ApplicationInfo> raw = pm.getInstalledApplications(0);
        final IBinder pb = ServiceManager.getService("circle_privacy");
        final ICirclePrivacyManager perApp = pb == null
                ? null : ICirclePrivacyManager.Stub.asInterface(pb);
        final List<AppScore> out = new ArrayList<>();
        for (ApplicationInfo ai : raw) {
            if (ai.packageName == null) continue;
            if (ai.uid < android.os.Process.FIRST_APPLICATION_UID) continue;
            int score = 100;
            try {
                if (perApp != null) {
                    score = perApp.getPrivacyScore(ai.packageName);
                }
            } catch (Throwable t) {
                Log.w(TAG, "getPrivacyScore(" + ai.packageName + ") failed", t);
            }
            out.add(new AppScore(
                    ai.packageName,
                    String.valueOf(pm.getApplicationLabel(ai)),
                    score));
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.score, a.score));
        return out;
    }

    private final class AppScoreAdapter extends BaseAdapter {
        private final List<AppScore> mRows;
        AppScoreAdapter(List<AppScore> rows) { mRows = rows; }
        @Override public int getCount()         { return mRows.size(); }
        @Override public AppScore getItem(int p){ return mRows.get(p); }
        @Override public long getItemId(int p)  { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LinearLayout row = (LinearLayout) (convertView != null ? convertView
                    : newRow());
            final AppScore a = mRows.get(position);
            ((TextView) row.findViewById(1)).setText(a.label);
            ((TextView) row.findViewById(2)).setText(a.pkg);
            final TextView score = (TextView) row.findViewById(3);
            score.setText(String.valueOf(a.score));
            score.setTextColor(a.score >= 80 ? CIRCLE_SAGE
                    : (a.score >= 50 ? CIRCLE_GOLD : CIRCLE_BLOCKED));
            return row;
        }

        private LinearLayout newRow() {
            final LinearLayout row = new LinearLayout(PrivacyDashboardActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            final LinearLayout text = new LinearLayout(PrivacyDashboardActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            final LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(text, textLp);
            final TextView label = new TextView(PrivacyDashboardActivity.this);
            label.setId(1); label.setTextColor(CIRCLE_WARM); label.setTextSize(15);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            final TextView pkg = new TextView(PrivacyDashboardActivity.this);
            pkg.setId(2); pkg.setTextColor(0x88FFFFFF); pkg.setTextSize(11);
            text.addView(label); text.addView(pkg);
            final TextView score = new TextView(PrivacyDashboardActivity.this);
            score.setId(3); score.setTextSize(22);
            score.setTypeface(Typeface.DEFAULT_BOLD);
            score.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(score, new LinearLayout.LayoutParams(
                    dp(60), ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }

    private static final class AppScore {
        final String pkg;
        final String label;
        final int    score;
        AppScore(String pkg, String label, int score) {
            this.pkg   = pkg;
            this.label = label;
            this.score = score;
        }
    }

    // ------------------------------------------------------------------
    //  View builders
    // ------------------------------------------------------------------

    private TextView header(String s) {
        final TextView t = new TextView(this);
        t.setText(s); t.setTextColor(CIRCLE_WARM); t.setTextSize(28);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView subhead(String s) {
        final TextView t = new TextView(this);
        t.setText(s); t.setTextColor(CIRCLE_GOLD); t.setTextSize(14);
        t.setPadding(0, dp(4), 0, dp(20));
        return t;
    }

    private TextView sectionTitle(String s) {
        final TextView t = new TextView(this);
        t.setText(s); t.setTextColor(CIRCLE_SAGE); t.setTextSize(12);
        t.setAllCaps(true); t.setLetterSpacing(0.15f);
        t.setPadding(0, dp(20), 0, dp(8));
        return t;
    }

    private TextView counterCard(String body) { return textCard(body); }

    private TextView textCard(String body) {
        final TextView t = new TextView(this);
        t.setText(body); t.setTextColor(CIRCLE_WARM); t.setTextSize(15);
        t.setBackgroundColor(CIRCLE_CARD);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        return t;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
