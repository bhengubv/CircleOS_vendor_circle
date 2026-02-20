/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.hardware.boot.IBootControl;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import za.co.circleos.update.ICircleUpdateService;

/**
 * CircleOS main settings screen.
 *
 * Sections:
 *  1. OTA update status — current state, available version, check/install actions.
 *  2. Release channel — stable / beta / nightly selector.
 *  3. Privacy summary — permission denials, faked identifiers, network grants.
 *  4. Auto-revoke — job scheduler status and manual trigger.
 *  5. Rollback — "Revert to previous OS" (shown only when active slot is B).
 */
public class CircleSettingsActivity extends Activity {

    private static final String TAG = "CircleSettings";

    private static final int POLL_INTERVAL_MS = 3_000;

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView    mTvUpdateState;
    private TextView    mTvUpdateVersion;
    private Button      mBtnCheckNow;
    private Button      mBtnApplyUpdate;
    private RadioGroup  mRgChannel;
    private TextView    mTvPrivacyDenied;
    private TextView    mTvPrivacyFaked;
    private TextView    mTvPrivacyNetwork;
    private TextView    mTvStatus;
    private Button      mBtnRunNow;
    // Rollback section (shown only on slot B)
    private View        mRollbackSection;
    private Button      mBtnRevertOs;

    // ── Services ───────────────────────────────────────────────────────────────
    private ICircleUpdateService mUpdateService;

    // ── Polling ────────────────────────────────────────────────────────────────
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPollRunnable = this::pollUpdateState;
    private boolean mIgnoreChannelChange = false;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvUpdateState    = findViewById(R.id.tv_update_state);
        mTvUpdateVersion  = findViewById(R.id.tv_update_version);
        mBtnCheckNow      = findViewById(R.id.btn_check_now);
        mBtnApplyUpdate   = findViewById(R.id.btn_apply_update);
        mRgChannel        = findViewById(R.id.rg_channel);
        mTvPrivacyDenied  = findViewById(R.id.tv_privacy_denied);
        mTvPrivacyFaked   = findViewById(R.id.tv_privacy_faked);
        mTvPrivacyNetwork = findViewById(R.id.tv_privacy_network);
        mTvStatus         = findViewById(R.id.tv_status);
        mBtnRunNow        = findViewById(R.id.btn_run_now);
        mRollbackSection  = findViewById(R.id.section_rollback);
        mBtnRevertOs      = findViewById(R.id.btn_revert_os);

        mBtnCheckNow.setOnClickListener(v -> onCheckNow());
        mBtnApplyUpdate.setOnClickListener(v -> onApplyUpdate());
        mBtnRunNow.setOnClickListener(v -> onRunAutoRevoke());
        mBtnRevertOs.setOnClickListener(v -> onRevertOs());

        mRgChannel.setOnCheckedChangeListener((group, checkedId) -> {
            if (!mIgnoreChannelChange) onChannelSelected(checkedId);
        });

        bindUpdateService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAutoRevokeStatus();
        refreshPrivacySummary();
        refreshRollbackSection();
        mHandler.post(mPollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mPollRunnable);
    }

    // ── Update service ─────────────────────────────────────────────────────────

    private void bindUpdateService() {
        new Thread(() -> {
            IBinder b = ServiceManager.getService("circle.update");
            if (b != null) {
                mUpdateService = ICircleUpdateService.Stub.asInterface(b);
                mHandler.post(this::pollUpdateState);
            } else {
                mHandler.post(() -> {
                    mTvUpdateState.setText(R.string.update_state_unknown);
                    mTvUpdateVersion.setText("");
                });
            }
        }).start();
    }

    private void pollUpdateState() {
        if (mUpdateService == null) {
            mHandler.postDelayed(mPollRunnable, POLL_INTERVAL_MS);
            return;
        }
        new Thread(() -> {
            try {
                int    state   = mUpdateService.getState();
                String version = mUpdateService.getAvailableVersion();
                String channel = mUpdateService.getChannel();
                long   lastCheck = mUpdateService.getLastCheckTime();
                int    progress  = mUpdateService.getDownloadProgress();
                mHandler.post(() -> updateUi(state, version, channel, lastCheck, progress));
            } catch (RemoteException e) {
                Log.w(TAG, "pollUpdateState error: " + e.getMessage());
            }
            mHandler.postDelayed(mPollRunnable, POLL_INTERVAL_MS);
        }).start();
    }

    private void updateUi(int state, String version, String channel,
                          long lastCheckMs, int progress) {
        // State label
        int stateRes;
        switch (state) {
            case 1:  stateRes = R.string.update_state_checking;    break;
            case 2:  stateRes = R.string.update_state_downloading; break;
            case 3:  stateRes = R.string.update_state_ready;       break;
            case 4:  stateRes = R.string.update_state_installing;  break;
            case 5:  stateRes = R.string.update_state_failed;      break;
            default: stateRes = R.string.update_state_idle;        break;
        }
        String stateStr = getString(stateRes);
        if (state == 2 && progress >= 0) stateStr += " (" + progress + "%)";
        mTvUpdateState.setText(stateStr);

        // Version / channel / last-check line
        if (version != null && !version.isEmpty() && state >= 3) {
            mTvUpdateVersion.setText(getString(R.string.update_version_available, version));
        } else {
            String timeStr = lastCheckMs > 0
                    ? new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(lastCheckMs))
                    : "never";
            mTvUpdateVersion.setText(getString(R.string.update_channel_label,
                    channel != null ? channel : "stable", timeStr));
        }

        // Apply button visibility
        mBtnApplyUpdate.setVisibility(state == 3 ? View.VISIBLE : View.GONE);

        // Channel radio — don't trigger the listener
        mIgnoreChannelChange = true;
        if ("nightly".equals(channel)) mRgChannel.check(R.id.rb_nightly);
        else if ("beta".equals(channel)) mRgChannel.check(R.id.rb_beta);
        else mRgChannel.check(R.id.rb_stable);
        mIgnoreChannelChange = false;
    }

    private void onCheckNow() {
        if (mUpdateService == null) return;
        new Thread(() -> {
            try { mUpdateService.checkNow(); } catch (RemoteException e) {
                Log.w(TAG, "checkNow error", e);
            }
        }).start();
    }

    private void onApplyUpdate() {
        new AlertDialog.Builder(this)
            .setTitle("Install Update")
            .setMessage("The device will reboot to apply the update. Continue?")
            .setPositiveButton("Install", (d, w) -> {
                if (mUpdateService == null) return;
                new Thread(() -> {
                    try { mUpdateService.applyUpdate(); } catch (RemoteException e) {
                        Log.w(TAG, "applyUpdate error", e);
                    }
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void onChannelSelected(int checkedId) {
        if (mUpdateService == null) return;
        String channel;
        if (checkedId == R.id.rb_nightly)   channel = "nightly";
        else if (checkedId == R.id.rb_beta) channel = "beta";
        else                                channel = "stable";
        final String selected = channel;
        new Thread(() -> {
            try { mUpdateService.setChannel(selected); } catch (RemoteException e) {
                Log.w(TAG, "setChannel error", e);
            }
        }).start();
    }

    // ── Privacy summary ────────────────────────────────────────────────────────

    private void refreshPrivacySummary() {
        new Thread(() -> {
            try {
                IBinder b = ServiceManager.getService("circle.privacy");
                if (b == null) {
                    mHandler.post(() -> {
                        mTvPrivacyDenied.setText(R.string.privacy_unavailable);
                        mTvPrivacyFaked.setText("");
                        mTvPrivacyNetwork.setText("");
                    });
                    return;
                }
                android.circleos.privacy.ICirclePrivacyManagerService svc =
                        android.circleos.privacy.ICirclePrivacyManagerService.Stub.asInterface(b);
                int denied   = svc.getDeniedPermissionCount();
                int faked    = svc.getFakedIdentifierCount();
                int network  = svc.getNetworkGrantCount();
                mHandler.post(() -> {
                    mTvPrivacyDenied.setText(getString(R.string.privacy_denied,   denied));
                    mTvPrivacyFaked.setText(getString(R.string.privacy_faked,    faked));
                    mTvPrivacyNetwork.setText(getString(R.string.privacy_network, network));
                });
            } catch (Exception e) {
                Log.d(TAG, "Privacy summary: " + e.getMessage());
                mHandler.post(() -> mTvPrivacyDenied.setText(R.string.privacy_unavailable));
            }
        }).start();
    }

    // ── Auto-revoke ────────────────────────────────────────────────────────────

    private void refreshAutoRevokeStatus() {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler == null) { mTvStatus.setText(R.string.status_unavailable); return; }
        List<JobInfo> jobs = scheduler.getAllPendingJobs();
        boolean scheduled = false;
        for (JobInfo job : jobs) {
            if (job.getId() == BootReceiver.JOB_ID) { scheduled = true; break; }
        }
        mTvStatus.setText(scheduled ? R.string.status_scheduled : R.string.status_not_scheduled);
    }

    private void onRunAutoRevoke() {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo immediate = new JobInfo.Builder(
                BootReceiver.JOB_ID + 1,
                new ComponentName(this, AutoRevokeJobService.class))
                .setOverrideDeadline(0)
                .build();
        int result = scheduler.schedule(immediate);
        mTvStatus.setText(result == JobScheduler.RESULT_SUCCESS
                ? R.string.status_triggered : R.string.status_trigger_failed);
    }

    // ── Rollback — revert to slot A (stock OS) ─────────────────────────────────

    /**
     * Show the rollback section only when the active boot slot is B (CircleOS).
     * On slot A (stock or after rollback) the section is hidden entirely.
     */
    private void refreshRollbackSection() {
        new Thread(() -> {
            boolean onSlotB = isActiveSlotB();
            mHandler.post(() -> {
                if (mRollbackSection != null) {
                    mRollbackSection.setVisibility(onSlotB ? View.VISIBLE : View.GONE);
                }
            });
        }).start();
    }

    /**
     * Returns true when the device has booted from slot B (CircleOS slot).
     * Uses the IBootControl HAL AIDL interface.
     */
    private boolean isActiveSlotB() {
        try {
            IBinder b = ServiceManager.waitForDeclaredService(
                    "android.hardware.boot.IBootControl/default");
            if (b == null) return false;
            IBootControl bootControl = IBootControl.Stub.asInterface(b);
            int activeSlot = bootControl.getCurrentSlot();
            return activeSlot == 1; // 0=A, 1=B
        } catch (Exception e) {
            Log.w(TAG, "isActiveSlotB: " + e.getMessage());
            return false;
        }
    }

    /**
     * Confirm + execute rollback: set active slot to A and reboot.
     * Stock Android resumes from slot A with user data intact.
     */
    private void onRevertOs() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.rollback_title)
            .setMessage(R.string.rollback_message)
            .setPositiveButton(R.string.rollback_confirm, (d, w) -> doRollback())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void doRollback() {
        new Thread(() -> {
            try {
                IBinder b = ServiceManager.waitForDeclaredService(
                        "android.hardware.boot.IBootControl/default");
                if (b == null) {
                    Log.e(TAG, "doRollback: IBootControl not available");
                    return;
                }
                IBootControl bootControl = IBootControl.Stub.asInterface(b);
                bootControl.setActiveBootSlot(0); // slot A = stock
                Log.i(TAG, "Active slot set to A — rebooting");
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) pm.reboot(null);
            } catch (Exception e) {
                Log.e(TAG, "doRollback failed", e);
            }
        }).start();
    }
}
