/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import za.co.circleos.personality.ICirclePersonalityManager;

/**
 * Shows all installed user apps with checkboxes. Checked = hidden in this mode.
 */
public class AppVisibilityActivity extends Activity {

    private static final String TAG = "PersonalityEditor";
    static final String EXTRA_MODE_ID = "mode_id";

    private ICirclePersonalityManager mService;
    private String mModeId;

    private final List<String> mPackageNames = new ArrayList<>();
    private final List<String> mAppLabels    = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_visibility);
        setTitle(R.string.app_visibility_title);

        mService = ServiceConnection.get();
        mModeId  = getIntent().getStringExtra(EXTRA_MODE_ID);

        if (mModeId == null) {
            Toast.makeText(this, "No mode selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ListView listView = findViewById(R.id.app_list);

        // Load installed user apps
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> currentHidden = new ArrayList<>();
        try {
            if (mService != null) {
                currentHidden = mService.getModeHiddenApps(mModeId);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getModeHiddenApps failed", e);
        }

        final List<String> hidden = currentHidden;
        for (ApplicationInfo info : apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                mPackageNames.add(info.packageName);
                mAppLabels.add(pm.getApplicationLabel(info).toString());
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_multiple_choice, mAppLabels);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // Pre-check currently hidden apps
        for (int i = 0; i < mPackageNames.size(); i++) {
            if (hidden.contains(mPackageNames.get(i))) {
                listView.setItemChecked(i, true);
            }
        }

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            List<String> toHide = new ArrayList<>();
            for (int i = 0; i < mPackageNames.size(); i++) {
                if (listView.isItemChecked(i)) {
                    toHide.add(mPackageNames.get(i));
                }
            }
            saveHiddenApps(toHide);
        });
    }

    private void saveHiddenApps(List<String> packages) {
        if (mService == null) { finish(); return; }
        try {
            mService.setModeHiddenApps(mModeId, packages);
            finish();
        } catch (RemoteException e) {
            Log.e(TAG, "setModeHiddenApps failed", e);
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }
}
