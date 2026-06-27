/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Persists the Circle Start tile layout — which apps are pinned, their order and
 * size — in app-private storage. Two built-in live tiles (clock, battery) are
 * seeded on first run alongside a few common apps.
 */
package za.co.circleos.start;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class TileStore {

    // Tile sizes (column x row span on a 4-wide grid).
    static final int SMALL = 0;   // 1x1
    static final int MEDIUM = 1;  // 2x2
    static final int WIDE = 2;    // 4x2

    static final String TYPE_APP = "app";
    static final String TYPE_CLOCK = "clock";
    static final String TYPE_BATTERY = "battery";

    private static final String PREFS = "circle_start";
    private static final String K_TILES = "tiles";
    private static final String K_SEEDED = "seeded";

    static final class Tile {
        String type;
        String pkg;   // for app tiles
        String cls;   // for app tiles
        int size;
        Tile(String type, String pkg, String cls, int size) {
            this.type = type; this.pkg = pkg; this.cls = cls; this.size = size;
        }
    }

    private final SharedPreferences p;

    TileStore(Context c) {
        p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isSeeded() { return p.getBoolean(K_SEEDED, false); }
    void markSeeded() { p.edit().putBoolean(K_SEEDED, true).apply(); }

    List<Tile> load() {
        List<Tile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(K_TILES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                out.add(new Tile(
                        o.optString("type", TYPE_APP),
                        o.optString("pkg", ""),
                        o.optString("cls", ""),
                        o.optInt("size", SMALL)));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    void save(List<Tile> tiles) {
        JSONArray a = new JSONArray();
        try {
            for (Tile t : tiles) {
                JSONObject o = new JSONObject();
                o.put("type", t.type);
                o.put("pkg", t.pkg);
                o.put("cls", t.cls);
                o.put("size", t.size);
                a.put(o);
            }
        } catch (Throwable ignored) {
        }
        p.edit().putString(K_TILES, a.toString()).apply();
    }

    boolean isPinned(List<Tile> tiles, String pkg) {
        for (Tile t : tiles) if (TYPE_APP.equals(t.type) && pkg.equals(t.pkg)) return true;
        return false;
    }
}
