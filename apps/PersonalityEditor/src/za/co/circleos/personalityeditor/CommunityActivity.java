/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.app.Activity;
import android.app.AlertDialog;
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

import za.co.circleos.personality.ICirclePersonalityManager;
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
                runOnUiThread(() -> Toast.makeText(this,
                        r.success ? getString(R.string.community_imported, r.newModeId)
                                  : r.errorMessage,
                        Toast.LENGTH_LONG).show());
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
                        mAdapter.add(m.name + (m.description != null
                                ? " — " + m.description : ""));
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "fetchCommunityModes failed", e);
            }
        }).start();
    }

    private void importCommunityMode(PersonalityMode mode) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.community_import_mode_title, mode.name))
                .setMessage(mode.description)
                .setPositiveButton(R.string.community_import_btn, (d, w) -> {
                    if (mService == null) return;
                    try {
                        // Re-use importModeFromUrl with the mode's share URL if available,
                        // or encode + import inline via the existing import flow.
                        String url = mService.getModeShareUrl(mode.id);
                        if (url != null) {
                            importFromUrl(url);
                        } else {
                            Toast.makeText(this, R.string.community_share_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "importCommunityMode failed", e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
