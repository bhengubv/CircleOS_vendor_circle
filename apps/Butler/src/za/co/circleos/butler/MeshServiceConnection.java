/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.mesh.ICircleMeshService;

/**
 * Connection to the {@code circle.mesh} system service, and the blind-to-us E2E
 * gateway for Butler's mesh traffic.
 *
 * Outbound text is end-to-end encrypted ({@link MeshCrypto}) before it touches
 * the mesh; the operator and every relay see only ciphertext. On first contact a
 * CKX handshake establishes the channel and the message queues until the peer key
 * arrives. Plaintext is never sent. Inbound wire strings are run through
 * {@link #handleIncoming} which decrypts CE1 messages and absorbs handshakes.
 */
public class MeshServiceConnection {

    private static final String TAG = "Butler.MeshConn";

    /** MeshProtocol.TYPE_MSG_TEXT */
    public static final int TYPE_MSG_TEXT = 0x10;

    private final Context mCtx;
    private final MeshCrypto mCrypto;
    private ICircleMeshService mService;

    public MeshServiceConnection(Context ctx) {
        mCtx = ctx.getApplicationContext();
        mCrypto = new MeshCrypto(mCtx);
    }

    // ── Connection ──

    public boolean connect() {
        IBinder binder = ServiceManager.getService("circle.mesh");
        if (binder == null) {
            Log.w(TAG, "circle.mesh service not available");
            return false;
        }
        mService = ICircleMeshService.Stub.asInterface(binder);
        return true;
    }

    public boolean isConnected() { return mService != null; }

    // ── Queries ──

    public int getPeerCount() {
        if (mService == null) return 0;
        try { return mService.getPeerCount(); } catch (RemoteException e) { return 0; }
    }

    public boolean isRunning() {
        if (mService == null) return false;
        try { return mService.isRunning(); } catch (RemoteException e) { return false; }
    }

    public String getDeviceId() {
        if (mService == null) return null;
        try { return mService.getDeviceId(); } catch (RemoteException e) { return null; }
    }

    // ── Messaging (E2E) ──

    /**
     * Send text to a peer, end-to-end encrypted. Never sends plaintext: if there is no peer key
     * yet it sends the CKX handshake and queues the message, flushing it once the key arrives.
     *
     * @return true if encrypted-and-dispatched, or queued behind a handshake; false on failure.
     */
    public boolean sendTextMessage(String recipientDeviceId, String text) {
        if (mService == null || recipientDeviceId == null || text == null) return false;
        if (!mCrypto.isReady()) { Log.w(TAG, "E2E unavailable on this device"); return false; }
        if (!mCrypto.hasPeerKey(recipientDeviceId)) {
            sendWire(recipientDeviceId, mCrypto.keyExchangeMessage());
            PendingStore.queue(mCtx, recipientDeviceId, text);
            return true; // securing the channel; flushes on key arrival
        }
        String env = mCrypto.encrypt(recipientDeviceId, text);
        if (env == null) return false;
        return sendWire(recipientDeviceId, env);
    }

    /**
     * Process an incoming wire string. Returns the decrypted plaintext to display, or null if it
     * was a handshake (CKX) or could not be decrypted — both handled internally, no UI for them.
     */
    public String handleIncoming(String senderId, String wire) {
        if (senderId == null || wire == null) return null;
        if (mService == null) connect();

        if (MeshCrypto.isKeyExchange(wire)) {
            int status = mCrypto.storePeerKeyStatus(senderId, wire);
            if (status == MeshCrypto.KEY_CHANGED) {
                Log.w(TAG, "Peer key changed for " + senderId + " — holding queued messages");
                return null;
            }
            if (status == MeshCrypto.KEY_NEW) {
                sendWire(senderId, mCrypto.keyExchangeMessage());
            }
            String pending = PendingStore.take(mCtx, senderId);
            if (pending != null) {
                String env = mCrypto.encrypt(senderId, pending);
                if (env == null || !sendWire(senderId, env)) {
                    PendingStore.queue(mCtx, senderId, pending);
                }
            }
            return null;
        }
        if (MeshCrypto.isEncrypted(wire)) {
            String text = mCrypto.decrypt(senderId, wire);
            if (text == null) {
                sendWire(senderId, mCrypto.keyExchangeMessage()); // ask for their key, drop
                return null;
            }
            return text;
        }
        if (mCrypto.isStrictReceive()) {
            Log.w(TAG, "Dropped unencrypted message from " + senderId + " (strict mode)");
            return null;
        }
        return wire; // legacy plaintext (back-compat, strict mode off)
    }

    /** 60-digit security code for a peer — compare out-of-band to rule out a MITM. */
    public String safetyNumber(String peerId) {
        return mCrypto.safetyNumber(peerId);
    }

    private boolean sendWire(String peerId, String wire) {
        if (mService == null || wire == null) return false;
        try {
            return mService.sendMessage(peerId, wire.getBytes("UTF-8"), TYPE_MSG_TEXT);
        } catch (Exception e) {
            Log.e(TAG, "sendWire failed", e);
            return false;
        }
    }
}
