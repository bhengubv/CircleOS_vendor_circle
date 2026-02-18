/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesh P2P chat screen for Butler.
 *
 * Shows:
 *  - Header: local device ID, peer count, mesh status indicator
 *  - Chat history: messages to/from this session's selected peer
 *  - Input row: recipient field + text field + Send button
 *
 * Incoming messages are received via the broadcast action
 * {@code za.co.circleos.mesh.action.MESSAGE_RECEIVED}, which the mesh
 * service fires whenever a MSG_TEXT frame arrives.
 *
 * The recipient field is pre-filled when launched via Intent extra
 * {@link #EXTRA_PEER_ID}.
 */
public class MeshActivity extends Activity {

    private static final String TAG = "Butler.Mesh";

    /** Intent extra key for pre-filling the recipient device ID. */
    public static final String EXTRA_PEER_ID = "peer_id";

    /** Broadcast action fired by CircleMeshService on incoming MSG_TEXT. */
    public static final String ACTION_MSG_RECEIVED =
            "za.co.circleos.mesh.action.MESSAGE_RECEIVED";
    public static final String EXTRA_SENDER_ID = "sender_id";
    public static final String EXTRA_MSG_TEXT  = "msg_text";

    private MeshServiceConnection mMesh;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    // UI
    private TextView  mTvDeviceId;
    private TextView  mTvPeerCount;
    private TextView  mTvStatus;
    private ListView  mLvMessages;
    private EditText  mEtRecipient;
    private EditText  mEtMessage;
    private Button    mBtnSend;

    private final List<ChatMessage> mMessages = new ArrayList<>();
    private MessageAdapter mAdapter;

    // Peer polling
    private final Runnable mPollPeers = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) {
                updatePeerCount();
                mUiHandler.postDelayed(this, 5_000);
            }
        }
    };

    // Incoming message receiver
    private final BroadcastReceiver mMsgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String senderId = intent.getStringExtra(EXTRA_SENDER_ID);
            String text     = intent.getStringExtra(EXTRA_MSG_TEXT);
            if (text == null) return;
            String label = (senderId != null && senderId.length() >= 8)
                    ? senderId.substring(0, 8) + "…"
                    : "Peer";
            addMessage(new ChatMessage(label, text, false));
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());

        // Pre-fill recipient if launched with an ID
        String peerId = getIntent().getStringExtra(EXTRA_PEER_ID);
        if (peerId != null) mEtRecipient.setText(peerId);

        mAdapter = new MessageAdapter(this, mMessages);
        mLvMessages.setAdapter(mAdapter);

        mBtnSend.setOnClickListener(v -> sendMessage());

        // Connect to mesh service
        mMesh = new MeshServiceConnection();
        new Thread(() -> {
            boolean ok = mMesh.connect();
            mUiHandler.post(() -> {
                if (ok) {
                    String id = mMesh.getDeviceId();
                    mTvDeviceId.setText("ID: " + (id != null && id.length() >= 8
                            ? id.substring(0, 8) + "…" : id));
                    updatePeerCount();
                } else {
                    mTvStatus.setText("● Mesh offline");
                    mTvStatus.setTextColor(0xFFCC0000);
                }
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mMsgReceiver, new IntentFilter(ACTION_MSG_RECEIVED));
        mUiHandler.post(mPollPeers);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mMsgReceiver);
        mUiHandler.removeCallbacks(mPollPeers);
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private void sendMessage() {
        String recipient = mEtRecipient.getText().toString().trim();
        String text      = mEtMessage.getText().toString().trim();
        if (recipient.isEmpty() || text.isEmpty()) return;

        mEtMessage.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mEtMessage.getWindowToken(), 0);

        // Add to local history immediately
        addMessage(new ChatMessage("You", text, false));

        // Dispatch on background thread
        new Thread(() -> {
            boolean sent = mMesh.isConnected()
                    && mMesh.sendTextMessage(recipient, text);
            if (!sent) {
                mUiHandler.post(() ->
                    addMessage(new ChatMessage("System",
                            "Message queued — peer not currently reachable.", false)));
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updatePeerCount() {
        if (!mMesh.isConnected()) return;
        new Thread(() -> {
            int count    = mMesh.getPeerCount();
            boolean running = mMesh.isRunning();
            mUiHandler.post(() -> {
                mTvPeerCount.setText(count + (count == 1 ? " peer" : " peers"));
                mTvStatus.setText(running ? "● Mesh online" : "● Mesh offline");
                mTvStatus.setTextColor(running ? 0xFF006600 : 0xFFCC0000);
            });
        }).start();
    }

    private void addMessage(ChatMessage msg) {
        mUiHandler.post(() -> {
            mMessages.add(msg);
            mAdapter.notifyDataSetChanged();
            mLvMessages.setSelection(mAdapter.getCount() - 1);
        });
    }

    // ── Programmatic layout ───────────────────────────────────────────────────

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F5F5);

        // Header bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(12), dp(12), dp(12), dp(8));
        header.setBackgroundColor(0xFF1A1A2E);

        mTvDeviceId = makeText("ID: —", 12, 0xFFCCCCCC);
        mTvPeerCount = makeText("0 peers", 12, 0xFFCCCCCC);
        mTvStatus    = makeText("● Connecting…", 12, 0xFFAAAAAA);

        LinearLayout.LayoutParams fillWeight = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(mTvDeviceId,  fillWeight);
        header.addView(mTvPeerCount, fillWeight);
        header.addView(mTvStatus,    fillWeight);
        root.addView(header);

        // Title
        TextView title = makeText("Mesh Chat", 18, 0xFF1A1A2E);
        title.setPadding(dp(16), dp(12), dp(16), dp(4));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        // Message list
        mLvMessages = new ListView(this);
        mLvMessages.setDivider(null);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(mLvMessages, listParams);

        // Recipient row
        LinearLayout recipientRow = new LinearLayout(this);
        recipientRow.setOrientation(LinearLayout.HORIZONTAL);
        recipientRow.setPadding(dp(8), dp(4), dp(8), 0);

        TextView recipLabel = makeText("To:", 13, 0xFF444444);
        recipLabel.setPadding(0, 0, dp(6), 0);
        mEtRecipient = new EditText(this);
        mEtRecipient.setHint("Device ID (hex)");
        mEtRecipient.setTextSize(13);
        mEtRecipient.setSingleLine(true);

        LinearLayout.LayoutParams recipFill = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        recipientRow.addView(recipLabel);
        recipientRow.addView(mEtRecipient, recipFill);
        root.addView(recipientRow);

        // Input row
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(dp(8), dp(4), dp(8), dp(8));

        mEtMessage = new EditText(this);
        mEtMessage.setHint("Message…");
        mEtMessage.setTextSize(15);
        mEtMessage.setMaxLines(3);

        mBtnSend = new Button(this);
        mBtnSend.setText("Send");
        mBtnSend.setTextColor(0xFFFFFFFF);
        mBtnSend.setBackgroundColor(0xFF1A1A2E);

        LinearLayout.LayoutParams inputFill = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(mEtMessage, inputFill);
        inputRow.addView(mBtnSend);
        root.addView(inputRow);

        return root;
    }

    private TextView makeText(String text, int spSize, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(spSize);
        tv.setTextColor(color);
        return tv;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
