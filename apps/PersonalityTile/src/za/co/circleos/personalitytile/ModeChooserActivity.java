/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalitytile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import za.co.circleos.personality.IBundleCallback;
import za.co.circleos.personality.ICirclePersonalityManager;
import za.co.circleos.personality.ModeBundle;
import za.co.circleos.personality.PersonalityMode;
import za.co.circleos.personality.SwitchResult;

/**
 * Dialog-style activity listing all available personality modes.
 * Tapping a mode activates it immediately and finishes the activity.
 */
public class ModeChooserActivity extends Activity {

    private static final String TAG = "PersonalityTile";

    private ICirclePersonalityManager mService;
    private List<PersonalityMode>     mModes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_chooser);
        setTitle(R.string.chooser_title);

        connectService();
        populateList();
    }

    private void connectService() {
        try {
            IBinder binder = ServiceManager.getService("circle.personality");
            if (binder != null) {
                mService = ICirclePersonalityManager.Stub.asInterface(binder);
            }
        } catch (Exception e) {
            Log.w(TAG, "Cannot connect: " + e.getMessage());
        }
    }

    private void populateList() {
        if (mService == null) {
            finish();
            return;
        }

        try {
            mModes = mService.getAvailableModes();
            String activeModeId = mService.getActiveModeId();

            List<String> labels = new ArrayList<>();
            for (PersonalityMode mode : mModes) {
                String label = mode.name;
                if (mode.id.equals(activeModeId)) {
                    label += getString(R.string.mode_active_suffix);
                } else if ((mode.tier == 2 || mode.tier == 3)
                        && !mService.isBundleDownloaded(mode.id)) {
                    label += getString(R.string.mode_download_suffix);
                }
                labels.add(label);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_list_item_1, labels);

            ListView list = findViewById(R.id.mode_list);
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, view, position, id) -> {
                activateMode(mModes.get(position).id);
            });

        } catch (RemoteException e) {
            Log.e(TAG, "Failed to load modes", e);
            finish();
        }
    }

    private void activateMode(String modeId) {
        if (mService == null) return;
        try {
            SwitchResult result = mService.activateMode(modeId);
            if (result.requiresBundle) {
                showBundleDownloadDialog(modeId, result.pendingBundleId);
                return;
            }
            if (!result.success) {
                Toast.makeText(this, getString(R.string.switch_failed),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "activateMode failed", e);
        }
        finish();
    }

    private void showBundleDownloadDialog(String modeId, String bundleId) {
        ModeBundle bundle = null;
        try {
            if (mService != null) bundle = mService.getBundleInfo(modeId);
        } catch (RemoteException e) {
            Log.w(TAG, "getBundleInfo failed", e);
        }

        String name     = bundle != null ? bundle.displayName : modeId;
        long   sizeMb   = bundle != null ? bundle.sizeBytes / (1024 * 1024) : 0;
        String sizeText = sizeMb > 0 ? " (" + sizeMb + " MB)" : "";

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.bundle_download_title))
                .setMessage(getString(R.string.bundle_download_message, name, sizeText))
                .setPositiveButton(getString(R.string.bundle_download_btn), (d, w) ->
                        startBundleDownload(modeId))
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void startBundleDownload(String modeId) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle(getString(R.string.bundle_downloading_title));
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(true);
        progress.setButton(ProgressDialog.BUTTON_NEGATIVE,
                getString(android.R.string.cancel), (d, w) -> {
                    try {
                        if (mService != null) mService.cancelBundleDownload(modeId);
                    } catch (RemoteException e) {
                        Log.w(TAG, "cancelBundleDownload failed", e);
                    }
                    finish();
                });
        progress.setOnCancelListener(d -> finish());
        progress.show();

        IBundleCallback callback = new IBundleCallback.Stub() {
            @Override
            public void onProgress(String id, int pct) {
                runOnUiThread(() -> progress.setProgress(pct));
            }

            @Override
            public void onComplete(String id, boolean success, String errorMessage) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (success) {
                        // Bundle ready — activate the mode now
                        activateMode(modeId);
                    } else {
                        Toast.makeText(ModeChooserActivity.this,
                                getString(R.string.bundle_download_failed),
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        };

        try {
            if (mService != null) mService.downloadBundle(modeId, callback);
        } catch (RemoteException e) {
            Log.e(TAG, "downloadBundle failed", e);
            progress.dismiss();
            Toast.makeText(this, getString(R.string.bundle_download_failed),
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
