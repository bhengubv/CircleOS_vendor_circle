package za.co.circleos.settings;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

/**
 * Main settings activity for CircleOS privacy controls.
 *
 * Shows the current status of the auto-revoke job scheduler and lets
 * administrators trigger an immediate run for testing.
 */
public class CircleSettingsActivity extends Activity {

    private TextView mStatusText;
    private Button mRunNowButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusText  = findViewById(R.id.tv_status);
        mRunNowButton = findViewById(R.id.btn_run_now);

        mRunNowButton.setOnClickListener(v -> onRunNowClicked());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void refreshStatus() {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            mStatusText.setText(R.string.status_unavailable);
            return;
        }

        List<JobInfo> jobs = scheduler.getAllPendingJobs();
        boolean scheduled = false;
        for (JobInfo job : jobs) {
            if (job.getId() == BootReceiver.JOB_ID) {
                scheduled = true;
                break;
            }
        }

        mStatusText.setText(scheduled ? R.string.status_scheduled : R.string.status_not_scheduled);
    }

    private void onRunNowClicked() {
        // Schedule immediately (no constraints) for a one-off run
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        android.app.job.JobInfo immediate = new android.app.job.JobInfo.Builder(
                BootReceiver.JOB_ID + 1,
                new android.content.ComponentName(this, AutoRevokeJobService.class))
                .setOverrideDeadline(0) // run immediately
                .build();

        int result = scheduler.schedule(immediate);
        mStatusText.setText(result == JobScheduler.RESULT_SUCCESS
                ? R.string.status_triggered
                : R.string.status_trigger_failed);
    }
}
