/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * CircleSettings AutoRevokeJobService -- the actual auto-revoke pass.
 *
 * Runs in the CircleSettings app process so it has
 * REVOKE_RUNTIME_PERMISSIONS + PACKAGE_USAGE_STATS without the
 * system_server having to plumb anything special. Scheduled by
 * {@code com.circleos.server.privacy.CircleAutoRevokeScheduler} at boot.
 *
 * Window: 7 days of no observed access. Mirrors the alpha_checklist.md
 * "auto-revoke: unused permissions revoked after 7-day simulation"
 * test exactly.
 */
package za.co.circleos.settings;

import android.app.AppOpsManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AutoRevokeJobService extends JobService {

    private static final String TAG = "CircleAutoRevoke";

    private static final long DEFAULT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

    private static final Set<String> ESSENTIAL_PACKAGES = essentials();

    private Thread mWorker;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "AutoRevoke job started, jobId=" + params.getJobId());
        mWorker = new Thread(() -> {
            int revoked = 0;
            try {
                revoked = runRevokePass();
            } finally {
                Log.i(TAG, "AutoRevoke pass complete; revoked=" + revoked);
                jobFinished(params, false);
            }
        }, "CircleAutoRevoke-Worker");
        mWorker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mWorker != null) mWorker.interrupt();
        return true;
    }

    private int runRevokePass() {
        final Context ctx = getApplicationContext();
        if (ctx == null) return 0;
        final PackageManager pm = ctx.getPackageManager();
        final AppOpsManager ops = ctx.getSystemService(AppOpsManager.class);
        if (pm == null || ops == null) return 0;

        final long cutoff = System.currentTimeMillis() - DEFAULT_WINDOW_MS;
        int revoked = 0;

        for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (pkg.packageName == null) continue;
            if (ESSENTIAL_PACKAGES.contains(pkg.packageName)) continue;
            if (pkg.requestedPermissions == null) continue;

            for (int i = 0; i < pkg.requestedPermissions.length; i++) {
                final String perm = pkg.requestedPermissions[i];
                if (perm == null) continue;
                if (!isRuntimePermission(pm, perm)) continue;
                final int flagsBits = pkg.requestedPermissionsFlags != null
                        && i < pkg.requestedPermissionsFlags.length
                        ? pkg.requestedPermissionsFlags[i] : 0;
                if ((flagsBits & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) continue;

                if (isStale(ops, pkg.packageName, perm, cutoff)) {
                    if (revoke(pm, pkg.packageName, perm)) {
                        revoked++;
                    }
                }
                if (Thread.currentThread().isInterrupted()) return revoked;
            }
        }
        return revoked;
    }

    private static boolean isRuntimePermission(PackageManager pm, String perm) {
        try {
            return pm.getPermissionInfo(perm, 0).getProtection()
                    == PermissionInfo.PROTECTION_DANGEROUS;
        } catch (PackageManager.NameNotFoundException nf) {
            return false;
        }
    }

    private static boolean isStale(AppOpsManager ops, String pkg, String perm, long cutoff) {
        final String op = AppOpsManager.permissionToOp(perm);
        if (op == null) return false;
        try {
            List<AppOpsManager.PackageOps> list = ops.getOpsForPackage(
                    android.os.Process.myUid(), pkg, new String[]{op});
            if (list == null || list.isEmpty()) return true;
            for (AppOpsManager.PackageOps po : list) {
                for (AppOpsManager.OpEntry oe : po.getOps()) {
                    if (op.equals(oe.getOpStr())) {
                        try {
                            final long ts = oe.getLastAccessTime(
                                    AppOpsManager.OP_FLAGS_ALL);
                            return ts > 0 && ts < cutoff;
                        } catch (Throwable ignored) { }
                        try {
                            final long ts = oe.getLastAccessForegroundTime(
                                    AppOpsManager.OP_FLAGS_ALL);
                            return ts > 0 && ts < cutoff;
                        } catch (Throwable ignored) { }
                        return false;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean revoke(PackageManager pm, String pkg, String perm) {
        try {
            pm.getClass()
                    .getMethod("revokeRuntimePermission", String.class, String.class,
                            android.os.UserHandle.class)
                    .invoke(pm, pkg, perm, android.os.UserHandle.SYSTEM);
            Log.i(TAG, "Revoked " + perm + " from " + pkg);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Set<String> essentials() {
        final Set<String> s = new HashSet<>();
        s.add("com.circleos.settings");
        s.add("za.co.circleos.settings");
        s.add("com.circleos.launcher");
        s.add("za.co.circleos.butler");
        s.add("za.co.circleos.messages");
        s.add("za.co.circleos.titanium");
        s.add("android");
        s.add("com.android.systemui");
        s.add("com.android.settings");
        return s;
    }
}
