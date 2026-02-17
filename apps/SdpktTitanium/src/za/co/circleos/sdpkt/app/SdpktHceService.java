/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.sdpkt.app;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Base64;
import android.util.Log;

import za.co.circleos.sdpkt.IShongololoWallet;

/**
 * SDPKT Titanium Host Card Emulation service.
 *
 * Registered for AID F0:43:49:52:43:4C:45:53:44:50 ("CIRCLESDP").
 * Runs on the RECEIVER side — translates NFC APDUs into SDPKT protocol
 * messages and delegates all logic to the SdpktTitaniumService system service.
 *
 * APDU framing (SDPKT over HCE):
 *   SELECT AID        → 90 00
 *   Command APDU:     CLA=00 INS=A0 P1=00 P2=00 Lc=N [base64 data bytes] Le=FF
 *   Response APDU:    [base64 response bytes] SW1=90 SW2=00
 *   Error response:   SW1=6F SW2=00
 */
public class SdpktHceService extends HostApduService {

    private static final String TAG = "SdpktHce";

    /** SDPKT proprietary AID */
    private static final byte[] SDPKT_AID = {
        (byte)0xF0, 0x43, 0x49, 0x52, 0x43, 0x4C, 0x45, 0x53, 0x44, 0x50
    };

    /** SELECT FILE (AID) command instruction bytes */
    private static final byte INS_SELECT = (byte) 0xA4;
    /** SDPKT data exchange instruction */
    private static final byte INS_DATA   = (byte) 0xA0;

    private static final byte[] OK       = { (byte) 0x90, 0x00 };
    private static final byte[] ERR      = { (byte) 0x6F, 0x00 };

    private IShongololoWallet mWallet;
    private String            mSessionId;  // active receiver session, if any

    /* ── Service lifecycle ─────────────────────────────── */

    @Override
    public void onCreate() {
        super.onCreate();
        bindWallet();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWallet = null;
    }

    private void bindWallet() {
        try {
            IBinder b = ServiceManager.getService("circle.sdpkt");
            if (b != null) {
                mWallet = IShongololoWallet.Stub.asInterface(b);
                Log.d(TAG, "Bound to circle.sdpkt");
            } else {
                Log.w(TAG, "circle.sdpkt not yet available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind circle.sdpkt", e);
        }
    }

    /* ── HostApduService callbacks ─────────────────────── */

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (apdu == null || apdu.length < 4) return ERR;

        byte ins = apdu[1];

        if (ins == INS_SELECT) {
            return handleSelect(apdu);
        } else if (ins == INS_DATA) {
            return handleData(apdu);
        }

        Log.w(TAG, "Unknown INS: " + String.format("%02X", ins));
        return ERR;
    }

    @Override
    public void onDeactivated(int reason) {
        Log.d(TAG, "NFC deactivated, reason=" + reason);
        if (mSessionId != null && mWallet != null) {
            try { mWallet.cancelNfcSession(mSessionId); } catch (RemoteException ignored) {}
            mSessionId = null;
        }
    }

    /* ── APDU handlers ─────────────────────────────────── */

    private byte[] handleSelect(byte[] apdu) {
        // SELECT AID: CLA INS P1 P2 Lc [AID bytes]
        if (apdu.length < 5) return ERR;
        int lc  = apdu[4] & 0xFF;
        int off = 5;
        if (apdu.length < off + lc) return ERR;

        // Verify AID matches ours
        if (lc != SDPKT_AID.length) return ERR;
        for (int i = 0; i < lc; i++) {
            if (apdu[off + i] != SDPKT_AID[i]) return ERR;
        }

        Log.d(TAG, "SELECT AID OK");
        return OK;
    }

    private byte[] handleData(byte[] apdu) {
        // Command: CLA INS P1 P2 Lc [data bytes]
        if (apdu.length < 5) return ERR;
        int lc = apdu[4] & 0xFF;
        if (apdu.length < 5 + lc) return ERR;

        // Extract base64-encoded SDPKT message
        String incomingBase64 = new String(apdu, 5, lc, java.nio.charset.StandardCharsets.UTF_8);

        if (mWallet == null) {
            bindWallet();
            if (mWallet == null) return ERR;
        }

        try {
            String responseBase64 = mWallet.processNfcMessage(mSessionId, incomingBase64);
            if (responseBase64 == null) {
                // Check if this was a session-creating call (receiver side)
                // The service returns null only for terminal states
                return OK;
            }

            // If this created a new receiver session, capture the session ID
            // (session ID is managed in the system service; HCE just proxies messages)
            byte[] responseBytes = responseBase64.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return appendStatus(responseBytes, OK);

        } catch (RemoteException e) {
            Log.e(TAG, "IPC error processing NFC message", e);
            return ERR;
        }
    }

    /** Appends SW1 SW2 status bytes to a response data payload. */
    private byte[] appendStatus(byte[] data, byte[] sw) {
        byte[] result = new byte[data.length + sw.length];
        System.arraycopy(data, 0, result, 0, data.length);
        System.arraycopy(sw, 0, result, data.length, sw.length);
        return result;
    }
}
