/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Reminders - on-device reminder store (JSON in filesDir). Reminders
 * never leave the device.
 */
package za.co.circleos.reminders;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class ReminderStore {

    static final class Reminder {
        int id;
        String text;
        long time; // epoch millis to fire
        Reminder(int id, String text, long time) {
            this.id = id;
            this.text = text;
            this.time = time;
        }
    }

    private final File mFile;

    ReminderStore(Context ctx) {
        mFile = new File(ctx.getFilesDir(), "reminders.json");
    }

    List<Reminder> load() {
        List<Reminder> out = new ArrayList<>();
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
                    out.add(new Reminder(o.getInt("id"), o.optString("text"), o.optLong("time")));
                }
            } catch (Exception e) {
                // corrupt -> empty
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.time, b.time)); // soonest first
        return out;
    }

    void save(List<Reminder> reminders) {
        try {
            JSONArray arr = new JSONArray();
            for (Reminder r : reminders) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("text", r.text == null ? "" : r.text);
                o.put("time", r.time);
                arr.put(o);
            }
            try (FileWriter w = new FileWriter(mFile)) {
                w.write(arr.toString());
            }
        } catch (Exception e) {
            // best-effort
        }
    }

    int nextId(List<Reminder> reminders) {
        int max = 0;
        for (Reminder r : reminders) if (r.id > max) max = r.id;
        return max + 1;
    }

    void add(List<Reminder> reminders, Reminder r) {
        reminders.add(r);
        save(reminders);
    }

    void remove(List<Reminder> reminders, int id) {
        for (Iterator<Reminder> it = reminders.iterator(); it.hasNext(); ) {
            if (it.next().id == id) { it.remove(); break; }
        }
        save(reminders);
    }
}
