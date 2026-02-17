/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import za.co.circleos.personality.ICirclePersonalityManager;
import za.co.circleos.personality.ManagedModePolicy;
import za.co.circleos.personality.SwitchResult;

/**
 * Allows the user to set or clear a PIN lock on a specific personality mode.
 *
 * When a PIN is set, the OS will refuse to leave that mode without the correct PIN,
 * enabling parental or enterprise lock-down scenarios.
 */
public class ManagedModeActivity extends Activity {

    private static final String TAG = "PersonalityEditor";
    static final String EXTRA_MODE_ID   = "mode_id";
    static final String EXTRA_MODE_NAME = "mode_name";

    private ICirclePersonalityManager mService;
    private String mModeId;
    private String mModeName;

    private TextView mTvStatus;
    private EditText mEditPin, mEditPinConfirm;
    private Button   mBtnSet, mBtnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_managed_mode);

        mService   = ServiceConnection.get();
        mModeId    = getIntent().getStringExtra(EXTRA_MODE_ID);
        mModeName  = getIntent().getStringExtra(EXTRA_MODE_NAME);

        if (mModeId == null) { finish(); return; }

        setTitle(getString(R.string.managed_title, mModeName != null ? mModeName : mModeId));

        mTvStatus       = findViewById(R.id.tv_managed_status);
        mEditPin        = findViewById(R.id.edit_pin);
        mEditPinConfirm = findViewById(R.id.edit_pin_confirm);
        mBtnSet         = findViewById(R.id.btn_set_pin);
        mBtnClear       = findViewById(R.id.btn_clear_pin);

        mBtnSet.setOnClickListener(v -> setPin());
        mBtnClear.setOnClickListener(v -> clearPin());

        refreshStatus();
    }

    private void refreshStatus() {
        if (mService == null) return;
        try {
            ManagedModePolicy p = mService.getManagedModePolicy(mModeId);
            mTvStatus.setText(p != null
                    ? getString(R.string.managed_status_locked,
                            p.isEnterpriseManaged ? " (enterprise)" : "")
                    : getString(R.string.managed_status_unlocked));
            mBtnClear.setEnabled(p != null && !p.isEnterpriseManaged);
        } catch (RemoteException e) {
            Log.e(TAG, "getManagedModePolicy failed", e);
        }
    }

    private void setPin() {
        String pin     = mEditPin.getText().toString();
        String confirm = mEditPinConfirm.getText().toString();

        if (pin.length() < 4) {
            Toast.makeText(this, R.string.managed_pin_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pin.equals(confirm)) {
            Toast.makeText(this, R.string.managed_pin_mismatch, Toast.LENGTH_SHORT).show();
            return;
        }

        ManagedModePolicy policy = new ManagedModePolicy();
        policy.modeId  = mModeId;
        policy.pinHash = sha256(pin);
        policy.isEnterpriseManaged = false;

        try {
            SwitchResult r = mService.setManagedModePolicy(policy);
            if (r.success) {
                mEditPin.setText("");
                mEditPinConfirm.setText("");
                Toast.makeText(this, R.string.managed_pin_set, Toast.LENGTH_SHORT).show();
                refreshStatus();
            } else {
                Toast.makeText(this, r.errorMessage, Toast.LENGTH_SHORT).show();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "setManagedModePolicy failed", e);
        }
    }

    private void clearPin() {
        try {
            mService.clearManagedModePolicy(mModeId);
            Toast.makeText(this, R.string.managed_pin_cleared, Toast.LENGTH_SHORT).show();
            refreshStatus();
        } catch (RemoteException e) {
            Log.e(TAG, "clearManagedModePolicy failed", e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
