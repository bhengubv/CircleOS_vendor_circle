/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MessageAdapter extends ArrayAdapter<ChatMessage> {

    private final LayoutInflater mInflater;

    public MessageAdapter(Context ctx, List<ChatMessage> msgs) {
        super(ctx, 0, msgs);
        mInflater = LayoutInflater.from(ctx);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ChatMessage msg = getItem(position);
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_message, parent, false);
        }

        TextView tvSender = convertView.findViewById(R.id.tv_sender);
        TextView tvText   = convertView.findViewById(R.id.tv_text);
        LinearLayout container = (LinearLayout) convertView;

        tvSender.setText(msg.sender);
        tvText.setText(msg.text);

        // Align user messages right, assistant left
        if (msg.isUser()) {
            container.setGravity(Gravity.END);
            tvText.setBackgroundColor(0xFF1A1A2E);
            tvText.setTextColor(0xFFFFFFFF);
        } else {
            container.setGravity(Gravity.START);
            tvText.setBackgroundColor(0xFFE8E8F0);
            tvText.setTextColor(0xFF1A1A1A);
        }

        tvText.setAlpha(msg.isThinking ? 0.5f : 1.0f);
        return convertView;
    }
}
