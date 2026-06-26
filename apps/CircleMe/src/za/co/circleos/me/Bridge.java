/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle "Me" - social bridge client. Posts a status to the Circle social
 * bridge (which fans it out to the user's connected networks) and reads back
 * replies/mentions. The bridge base URL comes from ro.circle.social.url.
 * Standard library only; MUST be called off the main thread.
 */
package za.co.circleos.me;

import android.os.SystemProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Bridge {

    static String base() {
        String b = "";
        try {
            b = SystemProperties.get("ro.circle.social.url", "");
        } catch (Throwable t) {
            // not on a Circle build
        }
        return (b == null || b.isEmpty()) ? "https://social.circleos.co.za" : b;
    }

    /** POST the status text to the bridge for fan-out to connected networks. */
    static void post(String text) throws Exception {
        HttpURLConnection c =
                (HttpURLConnection) new URL(base() + "/api/social/post").openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "CircleMe/1.0");
            JSONObject o = new JSONObject();
            o.put("text", text);
            try (OutputStream os = c.getOutputStream()) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new Exception("HTTP " + code);
        } finally {
            c.disconnect();
        }
    }

    /** GET recent replies/mentions: a JSON array of {author, text}. */
    static JSONArray replies() throws Exception {
        HttpURLConnection c =
                (HttpURLConnection) new URL(base() + "/api/social/replies").openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "CircleMe/1.0");
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("replies");
            return arr != null ? arr : new JSONArray();
        } finally {
            c.disconnect();
        }
    }
}
