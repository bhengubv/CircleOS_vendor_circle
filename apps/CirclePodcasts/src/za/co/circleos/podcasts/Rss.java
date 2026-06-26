/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Podcasts - minimal RSS fetch + parse (channel title + episode
 * enclosures). Standard library only. MUST be called off the main thread.
 */
package za.co.circleos.podcasts;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class Rss {

    static final class Episode {
        String title;
        String audioUrl;
        String date;
    }

    static final class Channel {
        String title;
        final List<Episode> episodes = new ArrayList<>();
    }

    static Channel fetch(String feedUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(feedUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "CirclePodcasts/1.0");
        conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            try (InputStream in = conn.getInputStream()) {
                return parse(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static Channel parse(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, null);

        Channel ch = new Channel();
        Episode cur = null;
        boolean inItem = false;
        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = p.getName();
                if ("item".equals(name) || "entry".equals(name)) {
                    inItem = true;
                    cur = new Episode();
                } else if ("title".equals(name)) {
                    String text = p.nextText();
                    if (inItem && cur != null) {
                        if (cur.title == null) cur.title = text;
                    } else if (ch.title == null) {
                        ch.title = text;
                    }
                } else if ("enclosure".equals(name) && cur != null) {
                    if (cur.audioUrl == null) {
                        cur.audioUrl = p.getAttributeValue(null, "url");
                    }
                } else if ("pubDate".equals(name) && inItem && cur != null) {
                    cur.date = p.nextText();
                }
            } else if (event == XmlPullParser.END_TAG) {
                String name = p.getName();
                if ("item".equals(name) || "entry".equals(name)) {
                    if (cur != null && cur.audioUrl != null && cur.title != null) {
                        ch.episodes.add(cur);
                    }
                    inItem = false;
                    cur = null;
                    if (ch.episodes.size() >= 100) break; // cap
                }
            }
            event = p.next();
        }
        return ch;
    }
}
