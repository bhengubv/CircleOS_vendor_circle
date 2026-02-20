/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.messages;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import za.co.circleos.mesh.ICircleMeshService;
import za.co.circleos.messages.db.MessageDatabase;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * CircleMessages main screen — conversation list.
 *
 * Shows one row per unique peer that has sent or received a message,
 * ordered by most recent activity. Displays peer device ID (abbreviated),
 * last message preview, and unread count badge.
 *
 * The toolbar subtitle shows active peer count from the mesh service.
 */
public class MainActivity extends Activity {

    private static final String TAG = "CircleMessages";

    private TextView mPeerCountView;
    private ListView mListView;
    private MessageDatabase mDb;
    private ConversationAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDb = new MessageDatabase(this);

        mPeerCountView = findViewById(R.id.tv_peer_count);
        mListView      = findViewById(R.id.list_conversations);

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            MessageDatabase.Conversation conv = mAdapter.getItem(position);
            if (conv == null) return;
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra(ConversationActivity.EXTRA_PEER_ID, conv.peerId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<MessageDatabase.Conversation> convs = mDb.getConversations();
        mAdapter = new ConversationAdapter(convs);
        mListView.setAdapter(mAdapter);

        // Show connected peer count
        int peers = 0;
        try {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder != null) {
                ICircleMeshService mesh = ICircleMeshService.Stub.asInterface(binder);
                peers = mesh.getPeerCount();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not reach circle.mesh: " + e.getMessage());
        }
        mPeerCountView.setText(peers == 0 ? "No peers nearby" :
                peers + (peers == 1 ? " peer nearby" : " peers nearby"));
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class ConversationAdapter extends ArrayAdapter<MessageDatabase.Conversation> {
        ConversationAdapter(List<MessageDatabase.Conversation> items) {
            super(MainActivity.this, R.layout.item_conversation, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_conversation, parent, false);
            }
            MessageDatabase.Conversation conv = getItem(position);
            if (conv == null) return convertView;

            TextView tvPeer    = convertView.findViewById(R.id.tv_peer_id);
            TextView tvPreview = convertView.findViewById(R.id.tv_preview);
            TextView tvTime    = convertView.findViewById(R.id.tv_time);
            TextView tvUnread  = convertView.findViewById(R.id.tv_unread);

            String shortId = conv.peerId.length() > 12
                    ? conv.peerId.substring(0, 12) + "…" : conv.peerId;
            tvPeer.setText(shortId);
            tvPreview.setText(conv.lastMessage != null ? conv.lastMessage : "");
            tvTime.setText(DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(new Date(conv.lastTimestampMs)));

            if (conv.unreadCount > 0) {
                tvUnread.setVisibility(View.VISIBLE);
                tvUnread.setText(String.valueOf(conv.unreadCount));
            } else {
                tvUnread.setVisibility(View.GONE);
            }
            return convertView;
        }
    }
}
