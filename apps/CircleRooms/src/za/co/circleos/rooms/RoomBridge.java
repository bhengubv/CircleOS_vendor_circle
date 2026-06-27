/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Rooms - bridge client. A room is a shared space keyed by a code;
 * messages post to and read from the Circle rooms bridge (ro.circle.rooms.url).
 *
 * Blind-to-us: every message body is sealed on-device with a key derived from
 * the room code ({@link RoomCrypto}) before it leaves the phone. The bridge
 * stores only "CE1:<ciphertext>" with a non-identifying author placeholder, so
 * neither the operator nor any relay can read who said what. Reads are decrypted
 * here, so the UI is unchanged. Legacy plaintext rows pass through untouched.
 *
 * Standard library only; MUST be called off the main thread.
 */
package za.co.circleos.rooms;

import android.os.SystemProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class RoomBridge {

    /** Author field sent to the bridge in place of the real name (which is sealed inside the body). */
    private static final String AUTHOR_PLACEHOLDER = "•"; // •

    static String base() {
        String b = "";
        try {
            b = SystemProperties.get("ro.circle.rooms.url", "");
        } catch (Throwable t) {
            // not on a Circle build
        }
        return (b == null || b.isEmpty()) ? "https://rooms.circleos.co.za" : b;
    }

    static void post(String code, String author, String text) throws Exception {
        // Seal author+text together under the room-code key; the bridge never sees either in clear.
        JSONObject inner = new JSONObject();
        inner.put("a", author);
        inner.put("t", text);
        String sealed = RoomCrypto.seal(code, inner.toString());
        if (sealed == null) {
            throw new Exception("Could not secure the message — not sent");
        }

        HttpURLConnection c = (HttpURLConnection) new URL(
                base() + "/api/rooms/" + enc(code) + "/message").openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "CircleRooms/1.0");
            JSONObject o = new JSONObject();
            o.put("author", AUTHOR_PLACEHOLDER);
            o.put("text", sealed);
            try (OutputStream os = c.getOutputStream()) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) throw new Exception("HTTP " + c.getResponseCode());
        } finally {
            c.disconnect();
        }
    }

    static JSONArray messages(String code) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(
                base() + "/api/rooms/" + enc(code) + "/messages").openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "CircleRooms/1.0");
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray a = root.optJSONArray("messages");
            return a != null ? decryptAll(code, a) : new JSONArray();
        } finally {
            c.disconnect();
        }
    }

    /** Decrypt sealed bodies back to {author,text}; legacy plaintext rows pass through unchanged. */
    private static JSONArray decryptAll(String code, JSONArray a) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String text = o.optString("text", "");
            if (!RoomCrypto.isSealed(text)) continue; // legacy plaintext room
            try {
                String clear = RoomCrypto.open(code, text);
                if (clear == null) {
                    o.put("author", "⚠"); // ⚠
                    o.put("text", "Can't read this message — check the room code");
                    continue;
                }
                JSONObject inner = new JSONObject(clear);
                o.put("author", inner.optString("a", "?"));
                o.put("text", inner.optString("t", ""));
            } catch (Exception e) {
                // Leave the row as-is rather than dropping it.
            }
        }
        return a;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
