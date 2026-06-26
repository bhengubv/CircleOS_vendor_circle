/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Backup - bridge client. Stores/reads the user's backup blob on the
 * Circle backup bridge (ro.circle.backup.url). Standard library only; MUST be
 * called off the main thread.
 */
package za.co.circleos.backup;

import android.os.SystemProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class BackupBridge {

    static String base() {
        String b = "";
        try {
            b = SystemProperties.get("ro.circle.backup.url", "");
        } catch (Throwable t) {
            // not on a Circle build
        }
        return (b == null || b.isEmpty()) ? "https://backup.circleos.co.za" : b;
    }

    static void putContacts(JSONArray contacts) throws Exception {
        HttpURLConnection c =
                (HttpURLConnection) new URL(base() + "/api/backup/contacts").openConnection();
        try {
            c.setRequestMethod("PUT");
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "CircleBackup/1.0");
            JSONObject o = new JSONObject();
            o.put("contacts", contacts);
            try (OutputStream os = c.getOutputStream()) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) throw new Exception("HTTP " + c.getResponseCode());
        } finally {
            c.disconnect();
        }
    }

    static JSONArray getContacts() throws Exception {
        HttpURLConnection c =
                (HttpURLConnection) new URL(base() + "/api/backup/contacts").openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "CircleBackup/1.0");
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray a = root.optJSONArray("contacts");
            return a != null ? a : new JSONArray();
        } finally {
            c.disconnect();
        }
    }
}
