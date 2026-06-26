/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Rooms - bridge client. A room is a shared space keyed by a code;
 * messages post to and read from the Circle rooms bridge (ro.circle.rooms.url).
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
            o.put("author", author);
            o.put("text", text);
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
            return a != null ? a : new JSONArray();
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
