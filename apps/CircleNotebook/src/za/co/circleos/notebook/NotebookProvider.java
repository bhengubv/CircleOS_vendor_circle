/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provider over the Notebook store. Same-UID callers (the Notebook UI) reach it
 * freely; external Circle apps (B!, skills) read/write through the signature
 * permission com.circleos.permission.ACCESS_NOTEBOOK declared in the manifest.
 */
package za.co.circleos.notebook;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public final class NotebookProvider extends ContentProvider {

    static final String AUTHORITY = "za.co.circleos.notebook";
    static final Uri FACTS = Uri.parse("content://" + AUTHORITY + "/facts");

    private static final int M_FACTS = 1;
    private static final int M_FACT_ID = 2;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, "facts", M_FACTS);
        MATCHER.addURI(AUTHORITY, "facts/#", M_FACT_ID);
    }

    private NotebookStore mStore;

    @Override
    public boolean onCreate() {
        mStore = new NotebookStore(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        SQLiteDatabase db = mStore.getReadableDatabase();
        String order = sort != null ? sort
                : NotebookStore.COL_CATEGORY + " ASC, " + NotebookStore.COL_CREATED + " DESC";
        Cursor c;
        if (MATCHER.match(uri) == M_FACT_ID) {
            c = db.query(NotebookStore.TABLE, proj, NotebookStore.COL_ID + "=?",
                    new String[]{uri.getLastPathSegment()}, null, null, order);
        } else {
            c = db.query(NotebookStore.TABLE, proj, sel, args, null, null, order);
        }
        c.setNotificationUri(getContext().getContentResolver(), FACTS);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        if (v == null) return null;
        if (!v.containsKey(NotebookStore.COL_CREATED)) {
            v.put(NotebookStore.COL_CREATED, System.currentTimeMillis());
        }
        long id = mStore.getWritableDatabase().insert(NotebookStore.TABLE, null, v);
        if (id < 0) return null;
        getContext().getContentResolver().notifyChange(FACTS, null);
        return Uri.withAppendedPath(FACTS, String.valueOf(id));
    }

    @Override
    public int delete(Uri uri, String sel, String[] args) {
        SQLiteDatabase db = mStore.getWritableDatabase();
        int n;
        if (MATCHER.match(uri) == M_FACT_ID) {
            n = db.delete(NotebookStore.TABLE, NotebookStore.COL_ID + "=?",
                    new String[]{uri.getLastPathSegment()});
        } else {
            n = db.delete(NotebookStore.TABLE, sel, args);
        }
        if (n > 0) getContext().getContentResolver().notifyChange(FACTS, null);
        return n;
    }

    @Override
    public int update(Uri uri, ContentValues v, String sel, String[] args) {
        SQLiteDatabase db = mStore.getWritableDatabase();
        int n;
        if (MATCHER.match(uri) == M_FACT_ID) {
            n = db.update(NotebookStore.TABLE, v, NotebookStore.COL_ID + "=?",
                    new String[]{uri.getLastPathSegment()});
        } else {
            n = db.update(NotebookStore.TABLE, v, sel, args);
        }
        if (n > 0) getContext().getContentResolver().notifyChange(FACTS, null);
        return n;
    }

    @Override
    public String getType(Uri uri) {
        return MATCHER.match(uri) == M_FACT_ID
                ? "vnd.android.cursor.item/vnd.circle.fact"
                : "vnd.android.cursor.dir/vnd.circle.fact";
    }
}
