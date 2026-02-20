/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.messages;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
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
 * Inbound messages appear on the left; outbound on the right.
 * Sends via ICircleMeshService.sendMessage() using msgType 0x10 (TYPE_MSG_TEXT).
 */
public class ConversationActivity extends Activity {

    private static final String TAG = "CircleMessages";

    /** Intent extra carrying the remote device ID (16-char hex). */
    public static final String EXTRA_PEER_ID = "peer_id";

    /** MeshProtocol.TYPE_MSG_TEXT */
    private static final int TYPE_MSG_TEXT = 0x10;

    private String mPeerId;
    private MessageDatabase mDb;
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

        // Action bar title
        String shortId = mPeerId.length() > 12 ? mPeerId.substring(0, 12) + "…" : mPeerId;
        setTitle(shortId);

        mListView = findViewById(R.id.list_messages);
        mInput    = findViewById(R.id.et_input);
        ImageButton btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(v -> sendMessage());
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        loadMessages();
        mDb.markRead(mPeerId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMessages();
        mDb.markRead(mPeerId);
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

        boolean sent = false;
        try {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder != null) {
                ICircleMeshService mesh = ICircleMeshService.Stub.asInterface(binder);
                byte[] payload = text.getBytes("UTF-8");
                sent = mesh.sendMessage(mPeerId, payload, TYPE_MSG_TEXT);
            }
        } catch (Exception e) {
            Log.e(TAG, "sendMessage failed", e);
        }

        if (sent) {
            mDb.insertMessage(mPeerId, MessageDatabase.DIR_OUTBOUND, text);
            mInput.setText("");
            loadMessages();
        } else {
            Toast.makeText(this, "Could not send — peer may be offline", Toast.LENGTH_SHORT).show();
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
                // Right-align: outbound
                bubble.setBackgroundResource(R.drawable.bg_bubble_outbound);
                tvBody.setTextColor(0xFFFFFFFF);
                params.leftMargin  = dpToPx(48);
                params.rightMargin = dpToPx(8);
            } else {
                // Left-align: inbound
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
