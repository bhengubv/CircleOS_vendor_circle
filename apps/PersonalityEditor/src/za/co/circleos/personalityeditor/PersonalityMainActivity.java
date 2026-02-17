/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import za.co.circleos.personality.IBundleCallback;
import za.co.circleos.personality.ICirclePersonalityManager;
import za.co.circleos.personality.ModeBundle;
import za.co.circleos.personality.PersonalityMode;
import za.co.circleos.personality.SwitchResult;

/**
 * Main screen: lists all personality modes, lets the user create, edit, clone,
 * delete custom modes, and import/export the full mode set as JSON.
 */
public class PersonalityMainActivity extends Activity {

    private static final String TAG       = "PersonalityEditor";
    static final String EXTRA_MODE_ID     = "mode_id";
    static final String EXTRA_IS_CLONE    = "is_clone";
    private static final String EXPORT_FILE = "circle_modes.json";

    private ICirclePersonalityManager mService;
    private List<PersonalityMode>     mModes = new ArrayList<>();
    private ArrayAdapter<String>      mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        ListView list = findViewById(R.id.mode_list);
        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(mAdapter);

        list.setOnItemClickListener((parent, view, pos, id) -> {
            PersonalityMode mode = mModes.get(pos);
            showModeMenu(mode);
        });

        findViewById(R.id.btn_new_mode).setOnClickListener(v -> openEditor(null, false));
        findViewById(R.id.btn_export).setOnClickListener(v -> exportModes());
        findViewById(R.id.btn_import).setOnClickListener(v -> importModes());
        // Phase 5
        findViewById(R.id.btn_suggestions).setOnClickListener(v ->
                startActivity(new Intent(this, LearningSuggestionsActivity.class)));
        findViewById(R.id.btn_community).setOnClickListener(v ->
                startActivity(new Intent(this, CommunityActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mService = ServiceConnection.get();
        loadModes();
    }

    private void loadModes() {
        mModes.clear();
        mAdapter.clear();
        if (mService == null) {
            Toast.makeText(this, "Service unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String activeId = mService.getActiveModeId();
            List<PersonalityMode> all = mService.getAvailableModes();
            if (all != null) {
                mModes.addAll(all);
                for (PersonalityMode m : mModes) {
                    String label = m.name;
                    if (m.id.equals(activeId)) {
                        label += " ✓";
                    } else if ((m.tier == 2 || m.tier == 3)
                            && !mService.isBundleDownloaded(m.id)) {
                        label += " ↓";
                    }
                    if (!m.isCustom) label += " [built-in]";
                    mAdapter.add(label);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "loadModes failed", e);
        }
    }

    private void showModeMenu(PersonalityMode mode) {
        boolean needsBundle = (mode.tier == 2 || mode.tier == 3)
                && !isBundleDownloaded(mode.id);

        List<String> options = new ArrayList<>();
        options.add(getString(R.string.action_activate));
        if (needsBundle) options.add(getString(R.string.action_download_bundle));
        options.add(getString(R.string.action_clone));
        options.add(getString(R.string.action_manage_pin));
        if (mode.isCustom) {
            options.add(getString(R.string.action_edit));
            options.add(getString(R.string.action_delete));
        }

        new AlertDialog.Builder(this)
                .setTitle(mode.name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String opt = options.get(which);
                    if (opt.equals(getString(R.string.action_activate)))        activate(mode);
                    else if (opt.equals(getString(R.string.action_download_bundle))) showBundleDownloadDialog(mode.id);
                    else if (opt.equals(getString(R.string.action_clone)))      openEditor(mode.id, true);
                    else if (opt.equals(getString(R.string.action_manage_pin))) openManagedMode(mode);
                    else if (opt.equals(getString(R.string.action_edit)))       openEditor(mode.id, false);
                    else if (opt.equals(getString(R.string.action_delete)))     confirmDelete(mode);
                })
                .show();
    }

    private boolean isBundleDownloaded(String modeId) {
        try {
            return mService != null && mService.isBundleDownloaded(modeId);
        } catch (RemoteException e) {
            return false;
        }
    }

    private void openManagedMode(PersonalityMode mode) {
        Intent intent = new Intent(this, ManagedModeActivity.class);
        intent.putExtra(ManagedModeActivity.EXTRA_MODE_ID,   mode.id);
        intent.putExtra(ManagedModeActivity.EXTRA_MODE_NAME, mode.name);
        startActivity(intent);
    }

    private void activate(PersonalityMode mode) {
        if (mService == null) return;
        try {
            SwitchResult r = mService.activateMode(mode.id);
            if (r.requiresBundle) {
                showBundleDownloadDialog(mode.id);
                return;
            }
            Toast.makeText(this, r.success
                    ? getString(R.string.activated, mode.name) : r.errorMessage,
                    Toast.LENGTH_SHORT).show();
            loadModes();
        } catch (RemoteException e) {
            Log.e(TAG, "activate failed", e);
        }
    }

    private void showBundleDownloadDialog(String modeId) {
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
                .setTitle(R.string.bundle_download_title)
                .setMessage(getString(R.string.bundle_download_message, name, sizeText))
                .setPositiveButton(R.string.bundle_download_btn, (d, w) ->
                        startBundleDownload(modeId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startBundleDownload(String modeId) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle(R.string.bundle_downloading_title);
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
                });
        progress.setOnCancelListener(d -> {
            try {
                if (mService != null) mService.cancelBundleDownload(modeId);
            } catch (RemoteException e) {
                Log.w(TAG, "cancelBundleDownload failed", e);
            }
        });
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
                        Toast.makeText(PersonalityMainActivity.this,
                                R.string.bundle_downloaded, Toast.LENGTH_SHORT).show();
                        loadModes();
                    } else {
                        Toast.makeText(PersonalityMainActivity.this,
                                R.string.bundle_download_failed, Toast.LENGTH_LONG).show();
                    }
                });
            }
        };

        try {
            if (mService != null) mService.downloadBundle(modeId, callback);
        } catch (RemoteException e) {
            Log.e(TAG, "downloadBundle failed", e);
            progress.dismiss();
            Toast.makeText(this, R.string.bundle_download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openEditor(String modeId, boolean isClone) {
        Intent intent = new Intent(this, ModeEditorActivity.class);
        if (modeId != null) intent.putExtra(EXTRA_MODE_ID, modeId);
        intent.putExtra(EXTRA_IS_CLONE, isClone);
        startActivity(intent);
    }

    private void confirmDelete(PersonalityMode mode) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + mode.name + "?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteMode(mode))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMode(PersonalityMode mode) {
        if (mService == null) return;
        try {
            SwitchResult r = mService.deleteMode(mode.id);
            Toast.makeText(this, r.success ? "Deleted" : r.errorMessage,
                    Toast.LENGTH_SHORT).show();
            loadModes();
        } catch (RemoteException e) {
            Log.e(TAG, "deleteMode failed", e);
        }
    }

    private void exportModes() {
        if (mService == null) return;
        try {
            String json = mService.exportModesJson();
            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File out = new File(downloads, EXPORT_FILE);
            try (FileWriter fw = new FileWriter(out)) {
                fw.write(json);
            }
            Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "exportModes failed", e);
            Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void importModes() {
        if (mService == null) return;
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File in = new File(downloads, EXPORT_FILE);
            if (!in.exists()) {
                Toast.makeText(this, "No " + EXPORT_FILE + " found in Downloads",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(in))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            SwitchResult r = mService.importModesJson(sb.toString());
            Toast.makeText(this, r.success
                    ? getString(R.string.import_success) : r.errorMessage,
                    Toast.LENGTH_SHORT).show();
            loadModes();
        } catch (Exception e) {
            Log.e(TAG, "importModes failed", e);
            Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show();
        }
    }
}
