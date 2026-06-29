/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package android.circleos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CircleEndpoints — the single source of truth for the central Circle OS &lt;-&gt;
 * The Geek Network API endpoint.
 *
 * <p>The URL is <b>configuration, not code</b>: it lives in a JSON file the OS
 * reads at runtime, so it can be repointed via a config / OTA push <b>without an
 * OS rebuild</b>.
 *
 * <pre>
 *   override (writable, runtime)  /data/system/circle/endpoints.json
 *   default  (shipped, fallback)  /system/etc/circle/endpoints.json
 * </pre>
 *
 * The reader prefers the override, falls back to the shipped default, and to a
 * compiled-in last resort if both are missing. Nothing in the OS should hardcode
 * a TGN URL — ask {@link #getCentralApi()}.
 */
public final class CircleEndpoints {

    private static final String OVERRIDE = "/data/system/circle/endpoints.json";
    private static final String DEFAULT  = "/system/etc/circle/endpoints.json";

    /** Last-resort default if both config files are missing/unreadable. */
    private static final String FALLBACK_CENTRAL = "https://media.circleos.co.za";

    private static volatile String sCentral;
    private static volatile List<String> sFallbacks;
    private static volatile long sLoadedAt;

    private CircleEndpoints() {}

    /** The single endpoint all Circle OS &lt;-&gt; TGN traffic goes through. Never null. */
    public static synchronized String getCentralApi() {
        ensureLoaded();
        return sCentral;
    }

    /** Optional ordered fallback endpoints for resilience; may be empty, never null. */
    public static synchronized List<String> getFallbacks() {
        ensureLoaded();
        return sFallbacks;
    }

    /** Force a re-read (e.g. after a config/OTA push rewrote the override). */
    public static synchronized void refresh() {
        sLoadedAt = 0;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (sCentral != null && sLoadedAt != 0) return;
        String central = FALLBACK_CENTRAL;
        List<String> fb = new ArrayList<>();
        String raw = readFirst(OVERRIDE, DEFAULT);
        if (raw != null) {
            try {
                JSONObject o = new JSONObject(raw);
                String c = o.optString("central_api", "").trim();
                if (!c.isEmpty()) central = c;
                JSONArray arr = o.optJSONArray("fallbacks");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String f = arr.optString(i, "").trim();
                        if (!f.isEmpty()) fb.add(f);
                    }
                }
            } catch (Throwable ignored) {
                // malformed config -> keep the safe compiled-in fallback
            }
        }
        sCentral = stripTrailingSlash(central);
        sFallbacks = Collections.unmodifiableList(fb);
        sLoadedAt = System.currentTimeMillis();
    }

    private static String readFirst(String... paths) {
        for (String p : paths) {
            String s = readFile(p);
            if (s != null && !s.trim().isEmpty()) return s;
        }
        return null;
    }

    private static String readFile(String path) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String stripTrailingSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}
