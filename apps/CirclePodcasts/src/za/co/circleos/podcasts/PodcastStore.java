/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Podcasts - subscription store (a JSON file in filesDir). Feeds only;
 * episodes are fetched live. No account, no tracking.
 */
package za.co.circleos.podcasts;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

final class PodcastStore {

    static final class Feed {
        String url;
        String title;
        Feed(String url, String title) { this.url = url; this.title = title; }
    }

    private final File mFile;

    PodcastStore(Context ctx) {
        mFile = new File(ctx.getFilesDir(), "podcasts.json");
    }

    List<Feed> load() {
        List<Feed> out = new ArrayList<>();
        if (mFile.exists()) {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new FileReader(mFile))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    out.add(new Feed(o.optString("url"), o.optString("title")));
                }
            } catch (Exception e) {
                // corrupt -> empty
            }
        }
        return out;
    }

    void save(List<Feed> feeds) {
        try {
            JSONArray arr = new JSONArray();
            for (Feed f : feeds) {
                JSONObject o = new JSONObject();
                o.put("url", f.url);
                o.put("title", f.title == null ? "" : f.title);
                arr.put(o);
            }
            try (FileWriter w = new FileWriter(mFile)) {
                w.write(arr.toString());
            }
        } catch (Exception e) {
            // best-effort
        }
    }

    boolean has(List<Feed> feeds, String url) {
        for (Feed f : feeds) if (f.url.equals(url)) return true;
        return false;
    }

    void add(List<Feed> feeds, String url, String title) {
        if (!has(feeds, url)) {
            feeds.add(new Feed(url, title));
            save(feeds);
        }
    }

    void remove(List<Feed> feeds, String url) {
        for (int i = 0; i < feeds.size(); i++) {
            if (feeds.get(i).url.equals(url)) { feeds.remove(i); break; }
        }
        save(feeds);
    }
}
