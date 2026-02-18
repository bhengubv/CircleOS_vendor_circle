/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.sdpkt.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import za.co.circleos.sdpkt.IShongololoWallet;
import za.co.circleos.sdpkt.LocationContext;
import za.co.circleos.sdpkt.NfcTransferRequest;
import za.co.circleos.sdpkt.ShongololoTransaction;
import za.co.circleos.sdpkt.SyncStatus;
import za.co.circleos.sdpkt.TransactionResult;
import za.co.circleos.sdpkt.WalletBalance;
import za.co.circleos.sdpkt.WalletKey;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main Shongololo Wallet Activity.
 *
 * Sender flow (TAP TO PAY):
 *   1. User enters amount → taps phones together
 *   2. Activity enables NFC reader mode (IsoDep)
 *   3. On tag discovered: SELECT AID → exchange SDPKT protocol messages
 *   4. System service handles signing/verification; we display result
 *
 * Receiver flow (passive — handled by SdpktHceService):
 *   Activity receives broadcast when an incoming transfer is pending,
 *   shows accept/decline dialog, and calls acceptIncomingTransfer().
 */
public class WalletActivity extends Activity {

    private static final String TAG = "SdpktWallet";

    /** SDPKT AID bytes */
    private static final byte[] SDPKT_AID = {
        (byte)0xF0, 0x43, 0x49, 0x52, 0x43, 0x4C, 0x45, 0x53, 0x44, 0x50
    };

    /* APDU constants */
    private static final byte[] SELECT_APDU_HEADER = { 0x00, (byte)0xA4, 0x04, 0x00 };
    private static final byte   INS_DATA           = (byte) 0xA0;
    private static final byte[] SW_OK              = { (byte)0x90, 0x00 };

    private IShongololoWallet mWallet;
    private NfcAdapter        mNfcAdapter;
    private boolean           mNfcReaderActive;
    private String            mActiveSendSessionId;
    private long              mSendAmountCents;

    private TextView  mTvBalance;
    private TextView  mTvDailyRemaining;
    private TextView  mTvAddress;
    private TextView  mTvSync;
    private TextView  mTvStatus;
    private TextView  mTvLocationContext;
    private Button    mBtnPay;
    private Button    mBtnRequest;
    private ListView  mLvTransactions;

    private TransactionAdapter         mAdapter;
    private final Handler              mUiHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat     mDateFmt   =
            new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());

    /* ── Activity lifecycle ────────────────────────────── */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        mTvBalance       = findViewById(R.id.tv_balance);
        mTvDailyRemaining= findViewById(R.id.tv_daily_remaining);
        mTvAddress       = findViewById(R.id.tv_address);
        mTvSync            = findViewById(R.id.tv_sync);
        mTvStatus          = findViewById(R.id.tv_status);
        mTvLocationContext = findViewById(R.id.tv_location_context);
        mBtnPay          = findViewById(R.id.btn_pay);
        mBtnRequest      = findViewById(R.id.btn_request);
        mLvTransactions  = findViewById(R.id.lv_transactions);

        mAdapter = new TransactionAdapter(this, new ArrayList<>());
        mLvTransactions.setAdapter(mAdapter);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            showStatus(getString(R.string.nfc_not_available));
        }

        mBtnPay.setOnClickListener(v -> showPayDialog());
        mBtnRequest.setOnClickListener(v -> showRequestInfo());
        // Long-press sync indicator to force sync
        if (mTvSync != null) {
            mTvSync.setOnLongClickListener(v -> {
                new Thread(() -> {
                    try { if (mWallet != null) mWallet.forceSyncNow(); } catch (RemoteException e) { Log.e(TAG, "forceSyncNow", e); }
                }).start();
                Toast.makeText(this, "Sync triggered", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        bindWallet();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNfcAdapter != null && !mNfcAdapter.isEnabled()) {
            showStatus(getString(R.string.nfc_disabled));
        }
        refreshWallet();
        // Phase 4: Quick Pay tile launched us — start NFC reader immediately
        handleQuickPayIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleQuickPayIntent(intent);
    }

    private void handleQuickPayIntent(android.content.Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra(QuickPayTileService.EXTRA_QUICK_PAY, false)) {
            // Clear the flag so re-resume doesn't re-trigger
            intent.removeExtra(QuickPayTileService.EXTRA_QUICK_PAY);
            // Use lock-screen limit for quick pay from tile
            new Thread(() -> {
                try {
                    if (mWallet == null) { bindWallet(); }
                    if (mWallet == null || !mWallet.hasWallet()) return;
                    za.co.circleos.sdpkt.NfcTransferRequest req =
                            new za.co.circleos.sdpkt.NfcTransferRequest();
                    // Quick pay: use the lock-screen per-tap limit as amount ceiling
                    long limit = mWallet.getEffectivePerTapLimitCents(true);
                    req.amountCents   = 0; // 0 = ask user; amount dialog shown below
                    req.lockScreenMode = true;
                    mUiHandler.post(() -> showPayDialog(/*lockScreen=*/true));
                } catch (Exception e) {
                    Log.e(TAG, "Quick pay intent error", e);
                }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableNfcReader();
    }

    /* ── Wallet binding ────────────────────────────────── */

    private void bindWallet() {
        try {
            IBinder b = ServiceManager.getService("circle.sdpkt");
            if (b != null) {
                mWallet = IShongololoWallet.Stub.asInterface(b);
                Log.d(TAG, "Bound to circle.sdpkt");
            } else {
                Log.w(TAG, "circle.sdpkt not available");
                mTvBalance.setText(getString(R.string.wallet_initializing));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind", e);
        }
    }

    /* ── UI refresh ────────────────────────────────────── */

    private void refreshWallet() {
        if (mWallet == null) {
            bindWallet();
            return;
        }
        new Thread(() -> {
            try {
                if (!mWallet.hasWallet()) {
                    mWallet.initializeWallet();
                }
                WalletKey key  = mWallet.getWalletKey();
                WalletBalance b = mWallet.getBalance();
                List<ShongololoTransaction> txs = mWallet.getTransactions(50, 0);
                SyncStatus sync = mWallet.getSyncStatus();
                int pendingCount = mWallet.getPendingCount();
                LocationContext loc = mWallet.getLocationContext();
                boolean protectionActive = mWallet.isProtectionActive();

                mUiHandler.post(() -> {
                    mTvBalance.setText(formatCents(b.availableCents));
                    mTvDailyRemaining.setText(
                        getString(R.string.wallet_daily_label) + ": "
                        + formatCents(b.dailyRemainingCents()));
                    if (key != null) {
                        mTvAddress.setText(key.shortAddress());
                    }
                    // Sync indicator
                    if (mTvSync != null) {
                        if (pendingCount > 0) {
                            String syncLabel = sync.state == SyncStatus.STATE_SYNCING
                                    ? "Syncing…"
                                    : sync.state == SyncStatus.STATE_OFFLINE
                                    ? "Offline — " + pendingCount + " pending"
                                    : pendingCount + " pending";
                            mTvSync.setText(syncLabel);
                            mTvSync.setVisibility(View.VISIBLE);
                        } else {
                            mTvSync.setVisibility(View.GONE);
                        }
                    }
                    // Location context chip (Phase 3)
                    if (mTvLocationContext != null && loc != null) {
                        String locLabel = loc.locationLabel != null && !loc.locationLabel.isEmpty()
                                ? loc.locationLabel : loc.typeName();
                        String limitStr = formatCents(loc.perTapLimitCents) + "/tap";
                        mTvLocationContext.setText(locLabel + " · " + limitStr);
                        int chipColor;
                        switch (loc.type) {
                            case LocationContext.TYPE_HOME:   chipColor = 0xFF69F0AE; break;
                            case LocationContext.TYPE_KNOWN:  chipColor = 0xFF80CFFF; break;
                            case LocationContext.TYPE_RISKY:  chipColor = 0xFFFF5252; break;
                            case LocationContext.TYPE_MOVING: chipColor = 0xFFFFD740; break;
                            default:                          chipColor = 0xFFB0B0B0; break;
                        }
                        mTvLocationContext.setTextColor(chipColor);
                        mTvLocationContext.setVisibility(View.VISIBLE);
                    }

                    // Protection active banner
                    if (protectionActive) {
                        showStatus(getString(R.string.protection_active));
                    } else if (mTvStatus.getVisibility() == View.VISIBLE
                            && getString(R.string.protection_active)
                               .equals(mTvStatus.getText().toString())) {
                        hideStatus();
                    }

                    mAdapter.clear();
                    mAdapter.addAll(txs);
                    mAdapter.notifyDataSetChanged();
                });
            } catch (RemoteException e) {
                Log.e(TAG, "Refresh error", e);
            }
        }).start();
    }

    private String formatCents(long cents) {
        return String.format(Locale.US, "₷%,d.%02d", cents / 100, Math.abs(cents % 100));
    }

    private void showStatus(String msg) {
        mTvStatus.setText(msg);
        mTvStatus.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        mTvStatus.setVisibility(View.GONE);
    }

    /* ── PAY dialog ────────────────────────────────────── */

    private void showPayDialog() { showPayDialog(false); }

    private void showPayDialog(boolean lockScreen) {
        if (mWallet == null) { bindWallet(); return; }

        View dialogView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        final EditText etAmount = new EditText(this);
        etAmount.setHint(getString(R.string.transfer_amount_hint));
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etMemo = new EditText(this);
        etMemo.setHint(getString(R.string.transfer_memo_hint));

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);
        layout.addView(etAmount);
        layout.addView(etMemo);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.wallet_tap_to_pay))
            .setView(layout)
            .setPositiveButton(getString(R.string.transfer_send), (d, w) -> {
                String amtStr = etAmount.getText().toString().trim();
                if (amtStr.isEmpty()) return;
                try {
                    double amtDouble = Double.parseDouble(amtStr);
                    long amtCents = Math.round(amtDouble * 100);
                    mSendAmountCents = amtCents;
                    String memo = etMemo.getText().toString().trim();
                    startSendSession(amtCents, memo, lockScreen);
                } catch (NumberFormatException ignored) {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.transfer_cancel), null)
            .show();
    }

    private void startSendSession(long amountCents, String memo) {
        startSendSession(amountCents, memo, false);
    }

    private void startSendSession(long amountCents, String memo, boolean lockScreen) {
        if (mWallet == null) return;
        new Thread(() -> {
            try {
                NfcTransferRequest req = new NfcTransferRequest();
                req.amountCents = amountCents;
                req.memo        = memo;
                req.lockScreenMode = lockScreen;

                String sessionId = mWallet.beginNfcSession(req);
                if (sessionId == null) {
                    mUiHandler.post(() -> Toast.makeText(this,
                        getString(R.string.nfc_failed), Toast.LENGTH_SHORT).show());
                    return;
                }
                mActiveSendSessionId = sessionId;
                mUiHandler.post(() -> {
                    showStatus(getString(R.string.nfc_searching));
                    enableNfcReader();
                });
            } catch (RemoteException e) {
                Log.e(TAG, "beginNfcSession error", e);
            }
        }).start();
    }

    private void showRequestInfo() {
        if (mWallet == null) return;
        new Thread(() -> {
            try {
                WalletKey key = mWallet.getWalletKey();
                if (key == null) return;
                mUiHandler.post(() -> {
                    new AlertDialog.Builder(this)
                        .setTitle("Your Wallet Address")
                        .setMessage(key.walletAddress)
                        .setPositiveButton("OK", null)
                        .show();
                });
            } catch (RemoteException e) {
                Log.e(TAG, "getWalletKey error", e);
            }
        }).start();
    }

    /* ── NFC reader mode (SENDER side) ────────────────── */

    private void enableNfcReader() {
        if (mNfcAdapter == null || mNfcReaderActive) return;
        mNfcAdapter.enableReaderMode(this,
            tag -> onTagDiscovered(tag),
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null);
        mNfcReaderActive = true;
        Log.d(TAG, "NFC reader mode enabled");
    }

    private void disableNfcReader() {
        if (mNfcAdapter != null && mNfcReaderActive) {
            mNfcAdapter.disableReaderMode(this);
            mNfcReaderActive = false;
        }
    }

    private void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) return;

        mUiHandler.post(() -> showStatus(getString(R.string.nfc_peer_found)));

        new Thread(() -> performSenderExchange(isoDep)).start();
    }

    /**
     * Sender-side NFC APDU exchange.
     * Runs the SDPKT handshake: SELECT AID, then iterative APDU data exchange.
     */
    private void performSenderExchange(IsoDep isoDep) {
        String sessionId = mActiveSendSessionId;
        if (sessionId == null || mWallet == null) return;

        try {
            isoDep.connect();
            isoDep.setTimeout(5000);

            // 1. SELECT AID
            byte[] selectApdu = buildSelectApdu(SDPKT_AID);
            byte[] resp       = isoDep.transceive(selectApdu);
            if (!endsWith(resp, SW_OK)) {
                Log.w(TAG, "SELECT AID rejected");
                onTransferFailed(sessionId);
                return;
            }

            // 2. Send DISCOVER — the system service built it when we called beginSenderSession
            //    but we need to fetch the first message. We encode DISCOVER directly here.
            String discoverB64 = buildDiscoverMessage(sessionId);
            if (discoverB64 == null) {
                onTransferFailed(sessionId);
                return;
            }

            String nextOutgoing = discoverB64;
            int    steps        = 0;

            while (nextOutgoing != null && steps++ < 10) {
                byte[] dataApdu = buildDataApdu(nextOutgoing);
                byte[] dataResp = isoDep.transceive(dataApdu);

                // Extract response data (strip SW1 SW2)
                if (dataResp == null || dataResp.length < 2) {
                    onTransferFailed(sessionId);
                    return;
                }
                if (!endsWith(dataResp, SW_OK)) {
                    Log.w(TAG, "APDU error at step " + steps);
                    onTransferFailed(sessionId);
                    return;
                }

                if (dataResp.length == 2) {
                    // No data — session done
                    break;
                }

                String incomingB64 = new String(dataResp, 0, dataResp.length - 2, StandardCharsets.UTF_8);

                // Feed into system service state machine
                nextOutgoing = mWallet.processNfcMessage(sessionId, incomingB64);
            }

            // Done — refresh balance
            mUiHandler.post(() -> {
                showStatus(getString(R.string.nfc_confirmed));
                disableNfcReader();
                mActiveSendSessionId = null;
                refreshWallet();
                mUiHandler.postDelayed(this::hideStatus, 3000);
            });

        } catch (Exception e) {
            Log.e(TAG, "NFC exchange error", e);
            onTransferFailed(sessionId);
        } finally {
            try { isoDep.close(); } catch (Exception ignored) {}
        }
    }

    private void onTransferFailed(String sessionId) {
        try {
            if (mWallet != null) mWallet.cancelNfcSession(sessionId);
        } catch (RemoteException ignored) {}
        mActiveSendSessionId = null;
        mUiHandler.post(() -> {
            showStatus(getString(R.string.nfc_failed));
            disableNfcReader();
            mUiHandler.postDelayed(this::hideStatus, 3000);
        });
    }

    /* ── Incoming transfer dialog ─────────────────────── */

    /** Called when SdpktHceService signals an incoming transfer pending confirmation. */
    public void showIncomingTransferDialog(String sessionId, ShongololoTransaction tx) {
        String peer   = tx.senderDeviceId != null
                ? tx.senderDeviceId.substring(0, Math.min(8, tx.senderDeviceId.length())) + "..."
                : "unknown";
        String prompt = String.format(getString(R.string.nfc_incoming_prompt),
                                      formatCents(tx.amountCents), peer);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.nfc_receiving))
            .setMessage(prompt)
            .setPositiveButton(getString(R.string.nfc_accept), (d, w) -> {
                new Thread(() -> {
                    try {
                        TransactionResult res = mWallet.acceptIncomingTransfer(sessionId);
                        mUiHandler.post(() -> {
                            if (res.success) {
                                showStatus(getString(R.string.nfc_confirmed));
                            } else {
                                showStatus(getString(R.string.nfc_failed) + ": " + res.errorMessage);
                            }
                            refreshWallet();
                            mUiHandler.postDelayed(this::hideStatus, 3000);
                        });
                    } catch (RemoteException e) {
                        Log.e(TAG, "acceptIncomingTransfer error", e);
                    }
                }).start();
            })
            .setNegativeButton(getString(R.string.nfc_decline), (d, w) -> {
                new Thread(() -> {
                    try {
                        if (mWallet != null) mWallet.declineIncomingTransfer(sessionId);
                    } catch (RemoteException ignored) {}
                }).start();
            })
            .setCancelable(false)
            .show();
    }

    /* ── APDU builders ─────────────────────────────────── */

    private byte[] buildSelectApdu(byte[] aid) {
        byte[] apdu = new byte[SELECT_APDU_HEADER.length + 1 + aid.length];
        System.arraycopy(SELECT_APDU_HEADER, 0, apdu, 0, SELECT_APDU_HEADER.length);
        apdu[SELECT_APDU_HEADER.length] = (byte) aid.length;
        System.arraycopy(aid, 0, apdu, SELECT_APDU_HEADER.length + 1, aid.length);
        return apdu;
    }

    private byte[] buildDataApdu(String base64Data) {
        byte[] data = base64Data.getBytes(StandardCharsets.UTF_8);
        byte[] apdu = new byte[5 + data.length];
        apdu[0] = 0x00;
        apdu[1] = INS_DATA;
        apdu[2] = 0x00;
        apdu[3] = 0x00;
        apdu[4] = (byte) data.length;
        System.arraycopy(data, 0, apdu, 5, data.length);
        return apdu;
    }

    /**
     * Build the DISCOVER message by calling beginSenderSession's initial state.
     * We encode it directly here since the system service manages the session state
     * but doesn't expose a separate buildDiscoverMessage IPC call.
     */
    private String buildDiscoverMessage(String sessionId) {
        // MSG_DISCOVER = 0x01; payload: {"type":"discover","version":1,"sid":"<sessionId>"}
        String json = "{\"type\":\"discover\",\"version\":1,\"sid\":\"" + sessionId + "\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] msg       = new byte[1 + jsonBytes.length];
        msg[0] = 0x01; // MSG_DISCOVER
        System.arraycopy(jsonBytes, 0, msg, 1, jsonBytes.length);
        return android.util.Base64.encodeToString(msg, android.util.Base64.NO_WRAP);
    }

    private boolean endsWith(byte[] data, byte[] suffix) {
        if (data == null || data.length < suffix.length) return false;
        int off = data.length - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if (data[off + i] != suffix[i]) return false;
        }
        return true;
    }

    /* ── Transaction list adapter ─────────────────────── */

    private class TransactionAdapter extends ArrayAdapter<ShongololoTransaction> {
        TransactionAdapter(Context ctx, List<ShongololoTransaction> txs) {
            super(ctx, R.layout.item_transaction, txs);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_transaction, parent, false);
            }
            ShongololoTransaction tx = getItem(position);
            if (tx == null) return convertView;

            TextView tvDir    = convertView.findViewById(R.id.tv_direction);
            TextView tvPeer   = convertView.findViewById(R.id.tv_peer);
            TextView tvDate   = convertView.findViewById(R.id.tv_date);
            TextView tvAmount = convertView.findViewById(R.id.tv_amount);

            boolean isSend = tx.type == ShongololoTransaction.TYPE_SEND;
            tvDir.setText(isSend ? "↑" : "↓");
            tvDir.setTextColor(isSend ? 0xFFFF5252 : 0xFF69F0AE);

            String peer = isSend ? tx.receiverPubkey : tx.senderDeviceId;
            if (peer != null && peer.length() > 12) {
                peer = peer.substring(0, 8) + "…" + peer.substring(peer.length() - 4);
            }
            tvPeer.setText(peer != null ? peer : "—");
            tvDate.setText(mDateFmt.format(new Date(tx.createdAtMs)));

            tvAmount.setText((isSend ? "−" : "+") + formatCents(tx.amountCents));
            tvAmount.setTextColor(isSend ? 0xFFFF5252 : 0xFF69F0AE);

            return convertView;
        }
    }
}
