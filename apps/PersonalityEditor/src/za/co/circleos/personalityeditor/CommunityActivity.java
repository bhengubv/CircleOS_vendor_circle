/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
 * Community mode sharing: share a mode via URL, import from URL, browse store.
 */
public class CommunityActivity extends Activity {

    private static final String TAG = "PersonalityEditor";

    private ICirclePersonalityManager mService;
    private List<PersonalityMode>     mCommunityModes = new ArrayList<>();
    private ArrayAdapter<String>      mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);
        setTitle(R.string.community_title);

        mService = ServiceConnection.get();

        Button btnShare  = findViewById(R.id.btn_share_mode);
        Button btnImport = findViewById(R.id.btn_import_url);
        Button btnBrowse = findViewById(R.id.btn_browse_store);

        ListView list = findViewById(R.id.community_list);
        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(mAdapter);

        list.setOnItemClickListener((parent, view, pos, id) ->
                importCommunityMode(mCommunityModes.get(pos)));

        btnShare.setOnClickListener(v -> showSharePicker());
        btnImport.setOnClickListener(v -> showImportDialog());
        btnBrowse.setOnClickListener(v -> loadCommunityStore());
    }

    // ---- Share --------------------------------------------------------------

    private void showSharePicker() {
        if (mService == null) return;
        try {
            List<PersonalityMode> modes = mService.getAvailableModes();
            if (modes == null || modes.isEmpty()) {
                Toast.makeText(this, "No modes available", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[modes.size()];
            for (int i = 0; i < modes.size(); i++) names[i] = modes.get(i).name;

            new AlertDialog.Builder(this)
                    .setTitle(R.string.community_pick_mode)
                    .setItems(names, (d, which) -> shareModeUrl(modes.get(which).id))
                    .show();
        } catch (RemoteException e) {
            Log.e(TAG, "showSharePicker failed", e);
        }
    }

    private void shareModeUrl(String modeId) {
        if (mService == null) return;
        try {
            String url = mService.getModeShareUrl(modeId);
            if (url == null) {
                Toast.makeText(this, R.string.community_share_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Mode URL", url));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.community_share_title)
                    .setMessage(url)
                    .setPositiveButton(R.string.community_copied, null)
                    .show();
        } catch (RemoteException e) {
            Log.e(TAG, "shareModeUrl failed", e);
        }
    }

    // ---- Import from URL ----------------------------------------------------

    private void showImportDialog() {
        EditText et = new EditText(this);
        et.setHint(R.string.community_url_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.community_import_title)
                .setView(et)
                .setPositiveButton(R.string.community_import_btn, (d, w) -> {
                    String url = et.getText().toString().trim();
                    if (!url.isEmpty()) importFromUrl(url);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void importFromUrl(String url) {
        if (mService == null) return;
        Toast.makeText(this, R.string.community_importing, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                SwitchResult r = mService.importModeFromUrl(url);
                runOnUiThread(() -> {
                    if (r.success) {
                        Toast.makeText(this,
                                getString(R.string.community_imported, r.newModeId),
                                Toast.LENGTH_SHORT).show();
                        // If the imported mode needs a bundle, offer to download it now
                        offerBundleDownloadIfNeeded(r.newModeId);
                    } else {
                        Toast.makeText(this, r.errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "importFromUrl failed", e);
            }
        }).start();
    }

    // ---- Community store ----------------------------------------------------

    private void loadCommunityStore() {
        if (mService == null) return;
        Toast.makeText(this, R.string.community_loading, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<PersonalityMode> modes = mService.fetchCommunityModes();
                runOnUiThread(() -> {
                    mCommunityModes.clear();
                    mAdapter.clear();
                    if (modes == null || modes.isEmpty()) {
                        mAdapter.add(getString(R.string.community_no_modes));
                        return;
                    }
                    mCommunityModes.addAll(modes);
                    for (PersonalityMode m : mCommunityModes) {
                        String label = m.name;
                        if (m.tier == 3)      label += " [Tier 3]";
                        else if (m.tier == 2) label += " [Tier 2]";
                        if (m.description != null) label += " — " + m.description;
                        mAdapter.add(label);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "fetchCommunityModes failed", e);
            }
        }).start();
    }

    private void importCommunityMode(PersonalityMode mode) {
        String tierNote = (mode.tier == 2 || mode.tier == 3)
                ? "\n\n" + getString(R.string.community_tier_bundle_note, mode.tier) : "";
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.community_import_mode_title, mode.name))
                .setMessage((mode.description != null ? mode.description : "") + tierNote)
                .setPositiveButton(R.string.community_import_btn, (d, w) ->
                        doImportCommunityMode(mode))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Encodes the mode as a minimal JSON list and calls importModesJson on the service.
     *
     * <p>We cannot call getModeShareUrl() here because the mode is from the remote store
     * and has not been added to the local mode list yet — getModeShareUrl looks up mModes
     * by id and would return null, silently failing the import.</p>
     */
    private void doImportCommunityMode(PersonalityMode mode) {
        if (mService == null) return;
        new Thread(() -> {
            try {
                String json = encodeModeJson(mode);
                SwitchResult r = mService.importModesJson(json);
                runOnUiThread(() -> {
                    if (r.success) {
                        offerBundleDownloadIfNeeded(mode.id);
                    } else {
                        Toast.makeText(this, r.errorMessage != null
                                ? r.errorMessage : getString(R.string.import_failed),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "doImportCommunityMode failed", e);
            }
        }).start();
    }

    // ---- Bundle download (Tier-2 / Tier-3) ----------------------------------

    /**
     * Checks whether the given mode needs a bundle download and, if so, shows an
     * offer dialog.  Used after a successful import so the user can immediately
     * download the bundle without leaving this screen.
     */
    private void offerBundleDownloadIfNeeded(String modeId) {
        if (mService == null || modeId == null) return;
        try {
            if (!mService.isBundleDownloaded(modeId)) {
                ModeBundle bundle = mService.getBundleInfo(modeId);
                if (bundle != null) {
                    showBundleDownloadDialog(modeId, bundle);
                    return;
                }
            }
            // Bundle already downloaded or no bundle required — just confirm import
            Toast.makeText(this, getString(R.string.community_imported, modeId),
                    Toast.LENGTH_SHORT).show();
        } catch (RemoteException e) {
            Log.w(TAG, "offerBundleDownloadIfNeeded failed", e);
        }
    }

    private void showBundleDownloadDialog(String modeId, ModeBundle bundle) {
        long   sizeMb   = bundle.sizeBytes / (1024 * 1024);
        String sizeText = sizeMb > 0 ? " (" + sizeMb + " MB)" : "";

        new AlertDialog.Builder(this)
                .setTitle(R.string.bundle_download_title)
                .setMessage(getString(R.string.bundle_download_message,
                        bundle.displayName, sizeText))
                .setPositiveButton(R.string.bundle_download_btn, (d, w) ->
                        startBundleDownload(modeId))
                .setNegativeButton(android.R.string.cancel, (d, w) ->
                        Toast.makeText(this, getString(R.string.community_imported, modeId),
                                Toast.LENGTH_SHORT).show())
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
                    Toast.makeText(CommunityActivity.this,
                            success ? getString(R.string.bundle_downloaded)
                                    : getString(R.string.bundle_download_failed),
                            Toast.LENGTH_LONG).show();
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

    // ---- Helpers ------------------------------------------------------------

    /**
     * Encodes a community PersonalityMode as a JSON list compatible with
     * ModeSerializer.decodeModeList().  Only the fields the server can provide
     * are encoded; config defaults to null (service fills in defaults).
     */
    private static String encodeModeJson(PersonalityMode mode) {
        StringBuilder sb = new StringBuilder("[{");
        sb.append("\"id\":\"").append(escape(mode.id)).append('"');
        sb.append(",\"name\":\"").append(escape(mode.name)).append('"');
        if (mode.description != null)
            sb.append(",\"description\":\"").append(escape(mode.description)).append('"');
        sb.append(",\"tier\":").append(mode.tier);
        sb.append(",\"custom\":true");
        sb.append("}]");
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
