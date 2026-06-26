/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * CircleNotes - on-device note storage (a JSON file in the app's filesDir).
 * No cloud, no account: notes never leave the device. Circle's "you're not
 * the product" promise applies to your notes too.
 */
package za.co.circleos.notes;

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

final class NoteStore {

    static final class Note {
        long id;
        String title;
        String body;
        long updated;

        Note(long id, String title, String body, long updated) {
            this.id = id;
            this.title = title;
            this.body = body;
            this.updated = updated;
        }
    }

    private final File mFile;

    NoteStore(Context ctx) {
        mFile = new File(ctx.getFilesDir(), "notes.json");
    }

    List<Note> load() {
        List<Note> out = new ArrayList<>();
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
                    out.add(new Note(o.getLong("id"), o.optString("title"),
                            o.optString("body"), o.optLong("updated")));
                }
            } catch (Exception e) {
                // corrupt / unreadable -> start empty rather than crash
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated)); // newest first
        return out;
    }

    void save(List<Note> notes) {
        try {
            JSONArray arr = new JSONArray();
            for (Note n : notes) {
                JSONObject o = new JSONObject();
                o.put("id", n.id);
                o.put("title", n.title == null ? "" : n.title);
                o.put("body", n.body == null ? "" : n.body);
                o.put("updated", n.updated);
                arr.put(o);
            }
            try (FileWriter w = new FileWriter(mFile)) {
                w.write(arr.toString());
            }
        } catch (Exception e) {
            // best-effort persistence
        }
    }

    Note find(List<Note> notes, long id) {
        for (Note n : notes) if (n.id == id) return n;
        return null;
    }

    /** Insert (id == 0) or update an existing note; persists; returns the note. */
    Note upsert(List<Note> notes, long id, String title, String body) {
        Note n = (id != 0) ? find(notes, id) : null;
        if (n == null) {
            n = new Note(System.currentTimeMillis(), title, body, System.currentTimeMillis());
            notes.add(n);
        } else {
            n.title = title;
            n.body = body;
            n.updated = System.currentTimeMillis();
        }
        save(notes);
        return n;
    }

    void delete(List<Note> notes, long id) {
        for (Iterator<Note> it = notes.iterator(); it.hasNext(); ) {
            if (it.next().id == id) {
                it.remove();
                break;
            }
        }
        save(notes);
    }
}
