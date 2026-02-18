package za.co.circleos.settings;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * Schedules the AutoRevokeJobService on device boot.
 *
 * Job runs once per day, only when the device is idle and the battery is not low.
 * Using BOOT_COMPLETED + LOCKED_BOOT_COMPLETED to ensure scheduling survives
 * direct-boot encrypted storage.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    /** Unique job ID — 0xC1F0_A001 ("CircleOS AutoRevoke 1") */
    public static final int JOB_ID = 0xC1F0A001;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }
        scheduleAutoRevoke(context);
    }

    /**
     * Schedules (or reschedules) the daily auto-revoke job.
     * Safe to call multiple times — JobScheduler deduplicates by job ID.
     */
    public static void scheduleAutoRevoke(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            Log.e(TAG, "JobScheduler not available");
            return;
        }

        ComponentName component = new ComponentName(context, AutoRevokeJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, component)
                .setPeriodic(TimeUnit.HOURS.toMillis(24))
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true) // survive reboots automatically after first schedule
                .build();

        int result = scheduler.schedule(jobInfo);
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.i(TAG, "Auto-revoke job scheduled (daily, idle, battery-not-low)");
        } else {
            Log.e(TAG, "Failed to schedule auto-revoke job: " + result);
        }
    }
}
