/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

public class ChatMessage {
    public String sender;
    public String text;
    public boolean isThinking;

    public ChatMessage(String sender, String text, boolean isThinking) {
        this.sender = sender;
        this.text = text;
        this.isThinking = isThinking;
    }

    public boolean isUser() {
        return !isThinking && sender != null && sender.equals("You");
    }
}
