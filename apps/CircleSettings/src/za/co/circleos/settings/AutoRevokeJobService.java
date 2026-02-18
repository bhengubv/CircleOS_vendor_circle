package za.co.circleos.settings;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JobService that auto-revokes runtime permissions from apps unused for 90+ days.
 *
 * Runs daily (via BootReceiver schedule), requires device idle + battery not low.
 * Uses UsageStatsManager to detect last use time, then revokes PROTECTION_DANGEROUS
 * permissions for qualifying apps via PackageManager.revokeRuntimePermission().
 */
public class AutoRevokeJobService extends JobService {

    private static final String TAG = "AutoRevokeJobService";

    /** Apps unused for longer than this threshold get permissions revoked. */
    private static final long UNUSED_THRESHOLD_MS = TimeUnit.DAYS.toMillis(90);

    private AsyncTask<JobParameters, Void, Boolean> mTask;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Auto-revoke job started");
        mTask = new RevokeTask(params).execute(params);
        return true; // work continues on background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mTask != null) {
            mTask.cancel(true);
        }
        return true; // reschedule
    }

    // ── Background worker ──────────────────────────────────────────────────────

    private class RevokeTask extends AsyncTask<JobParameters, Void, Boolean> {
        private final JobParameters mParams;

        RevokeTask(JobParameters params) {
            mParams = params;
        }

        @Override
        protected Boolean doInBackground(JobParameters... params) {
            try {
                runAutoRevoke();
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Auto-revoke failed", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            jobFinished(mParams, !success); // reschedule on failure
        }
    }

    // ── Core logic ─────────────────────────────────────────────────────────────

    private void runAutoRevoke() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        PackageManager pm = getPackageManager();

        long now = System.currentTimeMillis();
        long queryStart = now - TimeUnit.DAYS.toMillis(365); // 1-year window
        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(queryStart, now);

        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        int revokeCount = 0;

        for (PackageInfo pi : packages) {
            if (isCancelled()) break;
            if (pi.requestedPermissions == null) continue;
            if (isSystemPackage(pm, pi)) continue;

            long lastUsed = getLastUsedTime(statsMap, pi.packageName);
            long idleMs = now - lastUsed;

            if (lastUsed == 0 || idleMs < UNUSED_THRESHOLD_MS) {
                // App was used recently (or never installed runtime-stats) — skip
                continue;
            }

            // Revoke all dangerous permissions
            for (String perm : pi.requestedPermissions) {
                if (isDangerous(pm, perm)) {
                    try {
                        pm.revokeRuntimePermission(pi.packageName, perm,
                                android.os.Process.myUserHandle());
                        Log.d(TAG, "Revoked " + perm + " from " + pi.packageName
                                + " (idle " + TimeUnit.MILLISECONDS.toDays(idleMs) + "d)");
                        revokeCount++;
                    } catch (Exception e) {
                        Log.w(TAG, "Could not revoke " + perm + " from " + pi.packageName
                                + ": " + e.getMessage());
                    }
                }
            }
        }

        Log.i(TAG, "Auto-revoke complete: " + revokeCount + " permissions revoked");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static boolean isSystemPackage(PackageManager pm, PackageInfo pi) {
        return (pi.applicationInfo.flags
                & (android.content.pm.ApplicationInfo.FLAG_SYSTEM
                   | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    private static long getLastUsedTime(Map<String, UsageStats> statsMap, String pkg) {
        UsageStats stats = statsMap.get(pkg);
        return stats != null ? stats.getLastTimeUsed() : 0L;
    }

    private static boolean isDangerous(PackageManager pm, String permission) {
        try {
            PermissionInfo pi = pm.getPermissionInfo(permission, 0);
            return (pi.getProtection() & PermissionInfo.PROTECTION_DANGEROUS)
                    == PermissionInfo.PROTECTION_DANGEROUS;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns whether the last auto-revoke ran successfully (used by UI).
     * Stored in shared prefs to survive process death.
     */
    public static boolean isCancelled(android.content.Context ctx) {
        return ctx.getSharedPreferences("auto_revoke", MODE_PRIVATE)
                .getBoolean("cancelled", false);
    }
}
