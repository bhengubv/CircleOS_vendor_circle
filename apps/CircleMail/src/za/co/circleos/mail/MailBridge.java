/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Mail - bridge client. The Circle mail bridge (ro.circle.mail.url) fronts
 * the user's IMAP/SMTP account(s); this reads the inbox + a message and sends.
 *
 * Blind-to-us for Circle↔Circle mail: on send we look up the recipient's
 * published X25519 key from the bridge key directory and seal the body
 * ({@link MailCrypto}) so the bridge relays only ciphertext. Mail to addresses
 * with no published key (ordinary SMTP recipients) goes as before — you can't
 * force E2E on an arbitrary mailbox. Sealed inbound bodies are opened on read.
 *
 * Standard library only; MUST be called off the main thread.
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

    private static volatile boolean sKeyPublished;

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
        JSONObject o = getJson(base() + "/api/mail/message?id=" + enc(id));
        MailCrypto mc = MailCrypto.get();
        String body = o.optString("body", "");
        if (mc != null && MailCrypto.isSealed(body)) {
            String clear = mc.open(body);
            try {
                o.put("body", clear != null ? clear
                        : "🔒 This message is sealed for a different device — can't open it here.");
                o.put("sealed", true);
            } catch (Exception ignored) {
            }
        }
        return o;
    }

    static void send(String to, String subject, String body) throws Exception {
        String outBody = body;
        boolean sealed = false;

        MailCrypto mc = MailCrypto.get();
        if (mc != null && mc.isReady()) {
            publishKey(mc);                       // make sure peers can seal to us (idempotent)
            String recipientKey = fetchKey(to);   // null for non-Circle recipients
            if (recipientKey != null) {
                String s = mc.seal(recipientKey, body);
                if (s != null) { outBody = s; sealed = true; }
            }
        }

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
            o.put("body", outBody);
            o.put("enc", sealed ? "x25519" : "");
            try (OutputStream os = c.getOutputStream()) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) throw new Exception("HTTP " + c.getResponseCode());
        } finally {
            c.disconnect();
        }
    }

    // ── key directory ──

    /** Publish our public key once per process so Circle peers can seal mail to us. */
    private static void publishKey(MailCrypto mc) {
        if (sKeyPublished) return;
        String pub = mc.myPublicKeyB64();
        if (pub == null) return;
        try {
            HttpURLConnection c =
                    (HttpURLConnection) new URL(base() + "/api/mail/key").openConnection();
            try {
                c.setRequestMethod("POST");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                JSONObject o = new JSONObject();
                o.put("key", pub);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(o.toString().getBytes(StandardCharsets.UTF_8));
                }
                if (c.getResponseCode() / 100 == 2) sKeyPublished = true;
            } finally {
                c.disconnect();
            }
        } catch (Throwable ignored) {
            // best-effort; we just won't be sealable until this succeeds
        }
    }

    /** Fetch a recipient's published X25519 key, or null if they're not a Circle user. */
    private static String fetchKey(String to) {
        try {
            JSONObject o = getJson(base() + "/api/mail/key?user=" + enc(to));
            String k = o.optString("key", "");
            return k.isEmpty() ? null : k;
        } catch (Throwable t) {
            return null; // no key on file (or directory unreachable) -> send plaintext
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
