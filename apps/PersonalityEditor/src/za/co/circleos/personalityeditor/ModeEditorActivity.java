/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import java.util.List;

import za.co.circleos.personality.ICirclePersonalityManager;
import za.co.circleos.personality.ModeConfig;
import za.co.circleos.personality.PersonalityMode;
import za.co.circleos.personality.SwitchResult;

/**
 * Full mode configuration editor. Handles create, edit, and clone.
 */
public class ModeEditorActivity extends Activity {

    private static final String TAG = "PersonalityEditor";

    private ICirclePersonalityManager mService;
    private PersonalityMode           mOriginalMode;
    private boolean                   mIsClone;
    private boolean                   mIsNew;
    private String                    mEditModeId;

    private EditText mEditName, mEditDescription;
    private Switch   mSwitchDnd, mSwitchWifi, mSwitchData,
                     mSwitchBluetooth, mSwitchLocation, mSwitchVpn, mSwitchCloud;
    private Spinner  mSpinnerNotif, mSpinnerTheme, mSpinnerPrivacy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        mService    = ServiceConnection.get();
        mEditModeId = getIntent().getStringExtra(PersonalityMainActivity.EXTRA_MODE_ID);
        mIsClone    = getIntent().getBooleanExtra(PersonalityMainActivity.EXTRA_IS_CLONE, false);
        mIsNew      = (mEditModeId == null);

        setTitle(mIsNew ? R.string.editor_title_new : R.string.editor_title);

        bindViews();

        if (!mIsNew && mService != null) {
            loadExistingMode();
        } else {
            populateDefaults();
        }

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_app_visibility).setOnClickListener(v -> openAppVisibility());
    }

    private void bindViews() {
        mEditName        = findViewById(R.id.edit_name);
        mEditDescription = findViewById(R.id.edit_description);
        mSwitchDnd       = findViewById(R.id.switch_dnd);
        mSwitchWifi      = findViewById(R.id.switch_wifi);
        mSwitchData      = findViewById(R.id.switch_data);
        mSwitchBluetooth = findViewById(R.id.switch_bluetooth);
        mSwitchLocation  = findViewById(R.id.switch_location);
        mSwitchVpn       = findViewById(R.id.switch_vpn);
        mSwitchCloud     = findViewById(R.id.switch_cloud_sync);
        mSpinnerNotif    = findViewById(R.id.spinner_notif);
        mSpinnerTheme    = findViewById(R.id.spinner_theme);
        mSpinnerPrivacy  = findViewById(R.id.spinner_privacy);
    }

    private void loadExistingMode() {
        try {
            List<PersonalityMode> all = mService.getAvailableModes();
            if (all == null) return;
            for (PersonalityMode m : all) {
                if (m.id.equals(mEditModeId)) {
                    mOriginalMode = m;
                    break;
                }
            }
            if (mOriginalMode == null) return;

            ModeConfig cfg = mOriginalMode.config != null
                    ? mOriginalMode.config : new ModeConfig();

            if (mIsClone) {
                mEditName.setText(mOriginalMode.name + " (copy)");
            } else {
                mEditName.setText(mOriginalMode.name);
            }
            mEditDescription.setText(mOriginalMode.description);
            mSwitchDnd.setChecked(cfg.dndEnabled);
            mSpinnerNotif.setSelection(cfg.notificationLevel);
            mSwitchWifi.setChecked(cfg.wifiEnabled);
            mSwitchData.setChecked(cfg.dataEnabled);
            mSwitchBluetooth.setChecked(cfg.bluetoothEnabled);
            mSwitchLocation.setChecked(cfg.locationEnabled);
            mSpinnerTheme.setSelection(cfg.themeMode);
            mSwitchVpn.setChecked(cfg.enforceVpn);
            mSpinnerPrivacy.setSelection(cfg.privacyLevel);
            mSwitchCloud.setChecked(cfg.cloudSyncEnabled);
        } catch (RemoteException e) {
            Log.e(TAG, "loadExistingMode failed", e);
        }
    }

    private void populateDefaults() {
        ModeConfig def = new ModeConfig();
        mSwitchDnd.setChecked(def.dndEnabled);
        mSpinnerNotif.setSelection(def.notificationLevel);
        mSwitchWifi.setChecked(def.wifiEnabled);
        mSwitchData.setChecked(def.dataEnabled);
        mSwitchBluetooth.setChecked(def.bluetoothEnabled);
        mSwitchLocation.setChecked(def.locationEnabled);
        mSpinnerTheme.setSelection(def.themeMode);
        mSwitchVpn.setChecked(def.enforceVpn);
        mSpinnerPrivacy.setSelection(def.privacyLevel);
        mSwitchCloud.setChecked(def.cloudSyncEnabled);
    }

    private void save() {
        String name = mEditName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.error_name_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        ModeConfig cfg = new ModeConfig();
        cfg.dndEnabled        = mSwitchDnd.isChecked();
        cfg.notificationLevel = mSpinnerNotif.getSelectedItemPosition();
        cfg.wifiEnabled       = mSwitchWifi.isChecked();
        cfg.dataEnabled       = mSwitchData.isChecked();
        cfg.bluetoothEnabled  = mSwitchBluetooth.isChecked();
        cfg.locationEnabled   = mSwitchLocation.isChecked();
        cfg.themeMode         = mSpinnerTheme.getSelectedItemPosition();
        cfg.enforceVpn        = mSwitchVpn.isChecked();
        cfg.privacyLevel      = mSpinnerPrivacy.getSelectedItemPosition();
        cfg.cloudSyncEnabled  = mSwitchCloud.isChecked();
        cfg.screenBrightness  = -1;

        PersonalityMode mode = new PersonalityMode();
        mode.name        = name;
        mode.description = mEditDescription.getText().toString().trim();
        mode.tier        = 1;
        mode.isCustom    = true;
        mode.config      = cfg;

        if (mIsNew || mIsClone) {
            // Generate a unique id from name
            mode.id = "custom_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_")
                    + "_" + System.currentTimeMillis() % 10000;
        } else {
            mode.id = mEditModeId;
        }

        if (mService == null) {
            Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SwitchResult r;
            if (mIsNew || mIsClone) {
                r = mService.createCustomMode(mode);
            } else {
                r = mService.updateMode(mode);
            }
            if (r.success) {
                finish();
            } else {
                Toast.makeText(this, r.errorMessage != null
                        ? r.errorMessage : getString(R.string.error_save_failed),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "save failed", e);
            Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppVisibility() {
        if (mEditModeId == null && mIsNew) {
            Toast.makeText(this, "Save the mode first to manage app visibility",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, AppVisibilityActivity.class);
        intent.putExtra(AppVisibilityActivity.EXTRA_MODE_ID,
                mIsClone ? null : mEditModeId);
        startActivity(intent);
    }
}
