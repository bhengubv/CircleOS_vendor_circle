/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalitytile;

import android.app.Activity;
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

import za.co.circleos.personality.ICirclePersonalityManager;
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
            if (!result.success) {
                Toast.makeText(this, getString(R.string.switch_failed),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "activateMode failed", e);
        }
        finish();
    }
}
