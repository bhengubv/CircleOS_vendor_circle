/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.messages.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite store for mesh messages.
 *
 * Schema:
 *   messages(id, peer_id, direction, body, timestamp_ms, read)
 *   direction: 0 = inbound (from peer), 1 = outbound (sent by us)
 */
public class MessageDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME    = "circle_messages.db";
    private static final int    DB_VERSION = 1;

    public static final int DIR_INBOUND  = 0;
    public static final int DIR_OUTBOUND = 1;

    private static final String CREATE_MESSAGES =
            "CREATE TABLE IF NOT EXISTS messages (" +
            "  id           INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  peer_id      TEXT    NOT NULL," +
            "  direction    INTEGER NOT NULL," +
            "  body         TEXT    NOT NULL," +
            "  timestamp_ms INTEGER NOT NULL," +
            "  read         INTEGER NOT NULL DEFAULT 0" +
            ");";

    private static final String CREATE_IDX_PEER =
            "CREATE INDEX IF NOT EXISTS idx_messages_peer ON messages(peer_id, timestamp_ms DESC);";

    // ── Model ─────────────────────────────────────────────────────────────────

    public static class Message {
        public long   id;
        public String peerId;
        public int    direction;
        public String body;
        public long   timestampMs;
        public boolean read;
    }

    public static class Conversation {
        public String peerId;
        public String lastMessage;
        public long   lastTimestampMs;
        public int    unreadCount;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public MessageDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_MESSAGES);
        db.execSQL(CREATE_IDX_PEER);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS messages");
        onCreate(db);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public long insertMessage(String peerId, int direction, String body) {
        ContentValues cv = new ContentValues();
        cv.put("peer_id",      peerId);
        cv.put("direction",    direction);
        cv.put("body",         body);
        cv.put("timestamp_ms", System.currentTimeMillis());
        cv.put("read",         direction == DIR_OUTBOUND ? 1 : 0);
        return getWritableDatabase().insert("messages", null, cv);
    }

    public void markRead(String peerId) {
        ContentValues cv = new ContentValues();
        cv.put("read", 1);
        getWritableDatabase().update("messages", cv,
                "peer_id = ? AND read = 0", new String[]{peerId});
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns all messages for a peer, oldest first. */
    public List<Message> getMessages(String peerId) {
        List<Message> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "messages", null,
                "peer_id = ?", new String[]{peerId},
                null, null, "timestamp_ms ASC")) {
            while (c.moveToNext()) {
                Message m = new Message();
                m.id          = c.getLong(c.getColumnIndexOrThrow("id"));
                m.peerId      = c.getString(c.getColumnIndexOrThrow("peer_id"));
                m.direction   = c.getInt(c.getColumnIndexOrThrow("direction"));
                m.body        = c.getString(c.getColumnIndexOrThrow("body"));
                m.timestampMs = c.getLong(c.getColumnIndexOrThrow("timestamp_ms"));
                m.read        = c.getInt(c.getColumnIndexOrThrow("read")) != 0;
                list.add(m);
            }
        }
        return list;
    }

    /** Returns one Conversation summary row per peer, ordered by most recent message. */
    public List<Conversation> getConversations() {
        List<Conversation> list = new ArrayList<>();
        String sql =
                "SELECT peer_id, " +
                "  (SELECT body FROM messages m2 WHERE m2.peer_id = m.peer_id ORDER BY timestamp_ms DESC LIMIT 1) AS last_body," +
                "  MAX(timestamp_ms) AS last_ts," +
                "  SUM(CASE WHEN read = 0 AND direction = 0 THEN 1 ELSE 0 END) AS unread " +
                "FROM messages m GROUP BY peer_id ORDER BY last_ts DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Conversation conv = new Conversation();
                conv.peerId          = c.getString(0);
                conv.lastMessage     = c.getString(1);
                conv.lastTimestampMs = c.getLong(2);
                conv.unreadCount     = c.getInt(3);
                list.add(conv);
            }
        }
        return list;
    }
}
