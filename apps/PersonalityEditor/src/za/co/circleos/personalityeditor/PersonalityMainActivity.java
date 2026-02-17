/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.app.Activity;
import android.app.AlertDialog;
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

import za.co.circleos.personality.ICirclePersonalityManager;
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
                    if (m.id.equals(activeId)) label += " ✓";
                    if (!m.isCustom) label += " [built-in]";
                    mAdapter.add(label);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "loadModes failed", e);
        }
    }

    private void showModeMenu(PersonalityMode mode) {
        List<String> options = new ArrayList<>();
        options.add("Activate");
        options.add(getString(R.string.action_clone));
        options.add(getString(R.string.action_manage_pin));  // Phase 5
        if (mode.isCustom) {
            options.add(getString(R.string.action_edit));
            options.add(getString(R.string.action_delete));
        }

        new AlertDialog.Builder(this)
                .setTitle(mode.name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String opt = options.get(which);
                    if (opt.equals("Activate")) activate(mode);
                    else if (opt.equals(getString(R.string.action_clone))) openEditor(mode.id, true);
                    else if (opt.equals(getString(R.string.action_manage_pin))) openManagedMode(mode);
                    else if (opt.equals(getString(R.string.action_edit)))  openEditor(mode.id, false);
                    else if (opt.equals(getString(R.string.action_delete))) confirmDelete(mode);
                })
                .show();
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
            Toast.makeText(this, r.success ? "Activated: " + mode.name : r.errorMessage,
                    Toast.LENGTH_SHORT).show();
            loadModes();
        } catch (RemoteException e) {
            Log.e(TAG, "activate failed", e);
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
