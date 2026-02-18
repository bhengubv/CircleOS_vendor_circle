/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.mesh.ICircleMeshService;

/**
 * Manages the connection to {@code circle.mesh} system service.
 *
 * Usage:
 * <pre>
 *   MeshServiceConnection mesh = new MeshServiceConnection();
 *   if (mesh.connect()) {
 *       mesh.sendMessage(peerId, "hello".getBytes(), 0x10);
 *   }
 * </pre>
 */
public class MeshServiceConnection {

    private static final String TAG = "Butler.MeshConn";

    /** Message type: plain text (MeshProtocol.TYPE_MSG_TEXT = 0x10) */
    public static final int TYPE_MSG_TEXT = 0x10;

    private ICircleMeshService mService;

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Acquires the Binder from ServiceManager.
     *
     * @return true if the service is available and connected.
     */
    public boolean connect() {
        IBinder binder = ServiceManager.getService("circle.mesh");
        if (binder == null) {
            Log.w(TAG, "circle.mesh service not available");
            return false;
        }
        mService = ICircleMeshService.Stub.asInterface(binder);
        Log.i(TAG, "Connected to circle.mesh");
        return true;
    }

    public boolean isConnected() { return mService != null; }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns the number of mesh peers currently visible.
     */
    public int getPeerCount() {
        if (mService == null) return 0;
        try { return mService.getPeerCount(); }
        catch (RemoteException e) { Log.e(TAG, "getPeerCount", e); return 0; }
    }

    /**
     * Returns whether the mesh stack is currently running.
     */
    public boolean isRunning() {
        if (mService == null) return false;
        try { return mService.isRunning(); }
        catch (RemoteException e) { Log.e(TAG, "isRunning", e); return false; }
    }

    /**
     * Returns this device's current rotating ID (16-char hex).
     */
    public String getDeviceId() {
        if (mService == null) return null;
        try { return mService.getDeviceId(); }
        catch (RemoteException e) { Log.e(TAG, "getDeviceId", e); return null; }
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    /**
     * Sends a text message to a peer device.
     *
     * @param recipientDeviceId 16-char hex rotating device ID of the recipient.
     * @param text              UTF-8 message text.
     * @return true if the message was dispatched or queued.
     */
    public boolean sendTextMessage(String recipientDeviceId, String text) {
        if (mService == null || text == null || recipientDeviceId == null) return false;
        try {
            byte[] payload = text.getBytes("UTF-8");
            return mService.sendMessage(recipientDeviceId, payload, TYPE_MSG_TEXT);
        } catch (Exception e) {
            Log.e(TAG, "sendTextMessage failed", e);
            return false;
        }
    }
}
