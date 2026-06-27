/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.messages;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import za.co.circleos.mesh.ICircleMeshService;
import za.co.circleos.messages.db.MessageDatabase;

import java.util.List;

/**
 * Chat thread for a single mesh peer.
 *
 * Every message is end-to-end encrypted ({@link MeshCrypto}) before it touches
 * the mesh — the operator and every relay node see only ciphertext (blind-to-us).
 * On first contact a CKX key-exchange establishes the channel; the message is
 * queued until the peer's key arrives. Plaintext is never sent.
 */
public class ConversationActivity extends Activity {

    private static final String TAG = "CircleMessages";

    /** Intent extra carrying the remote device ID (16-char hex). */
    public static final String EXTRA_PEER_ID = "peer_id";

    /** MeshProtocol.TYPE_MSG_TEXT */
    private static final int TYPE_MSG_TEXT = 0x10;

    private String mPeerId;
    private MessageDatabase mDb;
    private MeshCrypto mCrypto;
    private ListView mListView;
    private EditText mInput;
    private MessageAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        mPeerId = getIntent().getStringExtra(EXTRA_PEER_ID);
        if (mPeerId == null) {
            finish();
            return;
        }

        mDb = new MessageDatabase(this);
        mCrypto = new MeshCrypto(this);

        mListView = findViewById(R.id.list_messages);
        mInput    = findViewById(R.id.et_input);
        ImageButton btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(v -> sendMessage());
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        refreshTitle();
        loadMessages();
        mDb.markRead(mPeerId);

        // Announce our key so the peer can encrypt to us (idempotent on their side).
        if (mCrypto.isReady() && !mCrypto.hasPeerKey(mPeerId)) {
            sendWire(mCrypto.keyExchangeMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        flushPending();
        loadMessages();
        refreshTitle();
        mDb.markRead(mPeerId);
        if (mCrypto != null && mCrypto.consumeKeyChanged(mPeerId)) {
            showKeyChangedWarning();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Verify security code");
        MenuItem strict = menu.add(0, 2, 1, "Block unencrypted messages");
        strict.setCheckable(true);
        strict.setChecked(mCrypto != null && mCrypto.isStrictReceive());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            showSecurityCode();
            return true;
        }
        if (item.getItemId() == 2) {
            boolean now = !item.isChecked();
            item.setChecked(now);
            if (mCrypto != null) mCrypto.setStrictReceive(now);
            Toast.makeText(this, now ? "Unencrypted messages will be blocked"
                    : "Unencrypted messages will be shown", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSecurityCode() {
        String code = (mCrypto != null) ? mCrypto.safetyNumber(mPeerId) : null;
        String msg = (code != null)
                ? "Compare this code with your contact — read it aloud or in person. If both phones "
                  + "show the same code, your chat is verified end-to-end with no one in the middle.\n\n"
                  + code
                : "No secure channel yet. Send a message first to exchange keys, then check again.";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🔒 Security code")
                .setMessage(msg)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showKeyChangedWarning() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚠ Security code changed")
                .setMessage("This contact's encryption key changed. That's normal if they reinstalled "
                        + "or switched phone — but it can also mean someone is intercepting. Verify the "
                        + "new security code with them before sharing anything sensitive.")
                .setPositiveButton("View code", (d, w) -> showSecurityCode())
                .setNegativeButton("Later", null)
                .show();
    }

    private void refreshTitle() {
        String shortId = mPeerId.length() > 12 ? mPeerId.substring(0, 12) + "…" : mPeerId;
        boolean secured = mCrypto != null && mCrypto.isReady() && mCrypto.hasPeerKey(mPeerId);
        setTitle((secured ? "🔒 " : "🔓 ") + shortId);
    }

    private void loadMessages() {
        List<MessageDatabase.Message> messages = mDb.getMessages(mPeerId);
        mAdapter = new MessageAdapter(messages);
        mListView.setAdapter(mAdapter);
        mListView.setSelection(mAdapter.getCount() - 1);
    }

    private void sendMessage() {
        String text = mInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        if (mCrypto == null || !mCrypto.isReady()) {
            Toast.makeText(this, "Secure messaging isn't available on this device", Toast.LENGTH_LONG).show();
            return;
        }

        // No secure channel yet: send our key, queue the text, never send plaintext.
        if (!mCrypto.hasPeerKey(mPeerId)) {
            sendWire(mCrypto.keyExchangeMessage());
            PendingStore.queue(this, mPeerId, text);
            mInput.setText("");
            Toast.makeText(this, "🔒 Securing channel — your message will send the moment the key arrives",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String envelope = mCrypto.encrypt(mPeerId, text);
        if (envelope == null) {
            Toast.makeText(this, "Couldn't encrypt — message not sent", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sendWire(envelope)) {
            mDb.insertMessage(mPeerId, MessageDatabase.DIR_OUTBOUND, text); // plaintext stays on-device only
            mInput.setText("");
            loadMessages();
        } else {
            Toast.makeText(this, "Could not send — peer may be offline", Toast.LENGTH_SHORT).show();
        }
    }

    /** If a message was queued during the handshake and the key has since arrived, send it now. */
    private void flushPending() {
        if (mCrypto == null || !mCrypto.isReady() || !mCrypto.hasPeerKey(mPeerId)) return;
        String pending = PendingStore.take(this, mPeerId);
        if (pending == null) return;
        String env = mCrypto.encrypt(mPeerId, pending);
        if (env != null && sendWire(env)) {
            mDb.insertMessage(mPeerId, MessageDatabase.DIR_OUTBOUND, pending);
        } else {
            PendingStore.queue(this, mPeerId, pending); // re-queue on failure
        }
    }

    private boolean sendWire(String wire) {
        if (wire == null) return false;
        try {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder == null) return false;
            ICircleMeshService mesh = ICircleMeshService.Stub.asInterface(binder);
            return mesh.sendMessage(mPeerId, wire.getBytes("UTF-8"), TYPE_MSG_TEXT);
        } catch (Exception e) {
            Log.e(TAG, "sendWire failed", e);
            return false;
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class MessageAdapter extends ArrayAdapter<MessageDatabase.Message> {
        MessageAdapter(List<MessageDatabase.Message> items) {
            super(ConversationActivity.this, R.layout.item_message, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_message, parent, false);
            }
            MessageDatabase.Message msg = getItem(position);
            if (msg == null) return convertView;

            TextView tvBody  = convertView.findViewById(R.id.tv_message_body);
            View     bubble  = convertView.findViewById(R.id.bubble);

            tvBody.setText(msg.body);

            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) bubble.getLayoutParams();
            if (msg.direction == MessageDatabase.DIR_OUTBOUND) {
                bubble.setBackgroundResource(R.drawable.bg_bubble_outbound);
                tvBody.setTextColor(0xFFFFFFFF);
                params.leftMargin  = dpToPx(48);
                params.rightMargin = dpToPx(8);
            } else {
                bubble.setBackgroundResource(R.drawable.bg_bubble_inbound);
                tvBody.setTextColor(0xFF212121);
                params.leftMargin  = dpToPx(8);
                params.rightMargin = dpToPx(48);
            }
            bubble.setLayoutParams(params);
            return convertView;
        }

        private int dpToPx(int dp) {
            float density = getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }
    }
}
