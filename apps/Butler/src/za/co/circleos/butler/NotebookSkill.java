/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.Locale;

/**
 * Butler notebook skill — lets you tell B! what to remember, ask what it knows,
 * and forget things, all in plain language. Facts are written to the user-owned
 * Circle Notebook (content://za.co.circleos.notebook/facts), never to the LLM,
 * so everything stays visible and deletable in the Notebook app.
 *
 * Handled (keyword-based, case-insensitive):
 *   "remember (that) X" / "note that X" / "jot down X"  -> store X
 *   "what do you know about me" / "my notebook"          -> list stored facts
 *   "forget X" / "forget about X"                        -> delete matching facts
 *
 * Returns null if the message isn't a notebook request (falls through to the LLM).
 * Mirrors {@link WalletSkill}: deterministic, accurate, and keeps personal facts
 * out of the inference model.
 */
public final class NotebookSkill {

    private static final String TAG = "Butler.NotebookSkill";

    private static final Uri FACTS = Uri.parse("content://za.co.circleos.notebook/facts");
    private static final String C_ID = "_id";
    private static final String C_CATEGORY = "category";
    private static final String C_LABEL = "label";
    private static final String C_VALUE = "value";
    private static final String C_SOURCE = "source";
    private static final String C_CREATED = "created_at";

    private NotebookSkill() {}

    public static String tryHandle(Context ctx, String input) {
        if (ctx == null || input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase(Locale.US);

        // ── Recall ──
        if (lower.contains("what do you know about me")
                || lower.contains("what do you remember")
                || lower.contains("my notebook")
                || lower.contains("in my notebook")
                || lower.contains("what have you noted")) {
            return list(ctx);
        }

        // ── Forget ──
        if (lower.startsWith("forget ") || lower.startsWith("please forget ")) {
            return forget(ctx, stripPrefix(trimmed,
                    "please forget about ", "please forget ", "forget about ", "forget "));
        }

        // ── Remember ──
        if (lower.startsWith("remember ") || lower.startsWith("please remember ")
                || lower.startsWith("note that ") || lower.startsWith("make a note ")
                || lower.startsWith("take a note ") || lower.startsWith("jot down ")) {
            return remember(ctx, stripPrefix(trimmed,
                    "please remember that ", "please remember ",
                    "remember that ", "remember ",
                    "note that ", "make a note that ", "make a note ",
                    "take a note that ", "take a note ", "jot down "));
        }

        return null;
    }

    private static String remember(Context ctx, String fact) {
        if (fact == null || fact.trim().isEmpty()) {
            return "What would you like me to remember?";
        }
        fact = fact.trim();
        try {
            ContentValues cv = new ContentValues();
            cv.put(C_CATEGORY, categorise(fact));
            cv.put(C_LABEL, "");
            cv.put(C_VALUE, capitalise(fact));
            cv.put(C_SOURCE, "You told B!");
            cv.put(C_CREATED, System.currentTimeMillis());
            Uri u = ctx.getContentResolver().insert(FACTS, cv);
            if (u == null) return "I couldn't save that to your Notebook just now.";
            return "Noted — I'll remember that. You can see or remove it anytime in your **Notebook**.";
        } catch (Throwable t) {
            Log.e(TAG, "remember failed", t);
            return "I couldn't reach your Notebook just now.";
        }
    }

    private static String list(Context ctx) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(FACTS, null, null, null, null);
            if (c == null || c.getCount() == 0) {
                return "I haven't noted anything about you yet. Tell me to \"remember\" something "
                        + "and it'll appear in your Notebook.";
            }
            int iVal = c.getColumnIndex(C_VALUE);
            int iCat = c.getColumnIndex(C_CATEGORY);
            int iLbl = c.getColumnIndex(C_LABEL);
            StringBuilder sb = new StringBuilder("**Here's what I have in your Notebook**\n");
            String lastCat = null;
            while (c.moveToNext()) {
                String cat = iCat >= 0 ? c.getString(iCat) : "Other";
                String val = iVal >= 0 ? c.getString(iVal) : "";
                String lbl = iLbl >= 0 ? c.getString(iLbl) : "";
                if (cat == null) cat = "Other";
                if (val == null) val = "";
                if (!cat.equals(lastCat)) {
                    sb.append("\n_").append(cat).append("_\n");
                    lastCat = cat;
                }
                sb.append("• ");
                if (lbl != null && !lbl.isEmpty()) sb.append(lbl).append(": ");
                sb.append(val).append("\n");
            }
            sb.append("\nYou can edit or delete any of these in the **Notebook** app.");
            return sb.toString().trim();
        } catch (Throwable t) {
            Log.e(TAG, "list failed", t);
            return "I couldn't reach your Notebook just now.";
        } finally {
            if (c != null) c.close();
        }
    }

    private static String forget(Context ctx, String what) {
        if (what == null || what.trim().isEmpty()) {
            return "What would you like me to forget?";
        }
        what = what.trim();
        while (what.endsWith(".") || what.endsWith("?") || what.endsWith("!")) {
            what = what.substring(0, what.length() - 1).trim();
        }
        if (what.isEmpty()) return "What would you like me to forget?";
        Cursor c = null;
        try {
            String needle = what.toLowerCase(Locale.US);
            c = ctx.getContentResolver().query(FACTS, null, null, null, null);
            if (c == null) return "I couldn't reach your Notebook just now.";
            int iId = c.getColumnIndex(C_ID);
            int iVal = c.getColumnIndex(C_VALUE);
            int iLbl = c.getColumnIndex(C_LABEL);
            int removed = 0;
            while (c.moveToNext()) {
                if (iId < 0) break;
                String val = iVal >= 0 ? c.getString(iVal) : "";
                String lbl = iLbl >= 0 ? c.getString(iLbl) : "";
                String hay = ((val == null ? "" : val) + " " + (lbl == null ? "" : lbl))
                        .toLowerCase(Locale.US);
                if (hay.contains(needle)) {
                    long id = c.getLong(iId);
                    removed += ctx.getContentResolver().delete(
                            Uri.withAppendedPath(FACTS, String.valueOf(id)), null, null);
                }
            }
            if (removed == 0) {
                return "I couldn't find anything about \"" + what + "\" in your Notebook.";
            }
            return removed == 1
                    ? "Done — I've forgotten that."
                    : "Done — I've removed " + removed + " notes about that.";
        } catch (Throwable t) {
            Log.e(TAG, "forget failed", t);
            return "I couldn't reach your Notebook just now.";
        } finally {
            if (c != null) c.close();
        }
    }

    /* ── Helpers ── */

    private static String categorise(String fact) {
        String l = " " + fact.toLowerCase(Locale.US) + " ";
        if (l.contains(" like ") || l.contains(" likes ") || l.contains(" prefer")
                || l.contains(" favourite") || l.contains(" favorite")
                || l.contains(" love ") || l.contains(" loves ") || l.contains(" hate ")) {
            return "Preferences";
        }
        if (l.contains(" lives ") || l.contains(" address") || l.contains(" street")
                || l.contains(" city ") || l.contains(" work at") || l.contains(" office")) {
            return "Places";
        }
        if (l.contains(" birthday") || l.contains(" wife") || l.contains(" husband")
                || l.contains(" son ") || l.contains(" daughter") || l.contains(" friend")
                || l.contains(" mum") || l.contains(" mom") || l.contains(" dad")
                || l.contains(" partner") || l.contains(" colleague")) {
            return "People";
        }
        if (l.startsWith(" i ") || l.startsWith(" my ") || l.startsWith(" me ")) {
            return "About you";
        }
        return "Other";
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String stripPrefix(String input, String... prefixes) {
        String lower = input.toLowerCase(Locale.US);
        for (String p : prefixes) {
            if (lower.startsWith(p)) {
                return input.substring(p.length());
            }
        }
        int sp = input.indexOf(' ');
        return sp >= 0 ? input.substring(sp + 1) : input;
    }
}
