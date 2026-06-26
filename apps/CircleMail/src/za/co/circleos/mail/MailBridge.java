/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Mail - bridge client. The Circle mail bridge (ro.circle.mail.url)
 * fronts the user's IMAP/SMTP account(s); this reads the inbox + a message and
 * sends. Standard library only; MUST be called off the main thread.
 */
package za.co.circleos.mail;

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

final class MailBridge {

    static String base() {
        String b = "";
        try {
            b = SystemProperties.get("ro.circle.mail.url", "");
        } catch (Throwable t) {
            // not on a Circle build
        }
        return (b == null || b.isEmpty()) ? "https://mail.circleos.co.za" : b;
    }

    static JSONArray inbox() throws Exception {
        JSONObject root = getJson(base() + "/api/mail/inbox");
        JSONArray a = root.optJSONArray("messages");
        return a != null ? a : new JSONArray();
    }

    static JSONObject message(String id) throws Exception {
        return getJson(base() + "/api/mail/message?id=" + enc(id));
    }

    static void send(String to, String subject, String body) throws Exception {
        HttpURLConnection c =
                (HttpURLConnection) new URL(base() + "/api/mail/send").openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "CircleMail/1.0");
            JSONObject o = new JSONObject();
            o.put("to", to);
            o.put("subject", subject);
            o.put("body", body);
            try (OutputStream os = c.getOutputStream()) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) throw new Exception("HTTP " + c.getResponseCode());
        } finally {
            c.disconnect();
        }
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "CircleMail/1.0");
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            return new JSONObject(sb.toString());
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
