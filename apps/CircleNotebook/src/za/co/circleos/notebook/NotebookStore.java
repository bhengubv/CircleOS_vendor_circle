/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Notebook (WP-27) — the on-device store of everything B! knows about
 * you. Owned by the user-facing Notebook app (not the assistant) so the data
 * is the user's; B! writes into it through the guarded provider. SQLite-backed.
 */
package za.co.circleos.notebook;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class NotebookStore extends SQLiteOpenHelper {

    static final String DB = "circle_notebook.db";
    static final int VERSION = 1;
    static final String TABLE = "facts";

    static final String COL_ID = "_id";
    static final String COL_CATEGORY = "category";
    static final String COL_LABEL = "label";
    static final String COL_VALUE = "value";
    static final String COL_SOURCE = "source";
    static final String COL_CREATED = "created_at";

    NotebookStore(Context c) {
        super(c, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CATEGORY + " TEXT NOT NULL DEFAULT 'Other', "
                + COL_LABEL + " TEXT NOT NULL DEFAULT '', "
                + COL_VALUE + " TEXT NOT NULL, "
                + COL_SOURCE + " TEXT NOT NULL DEFAULT 'You added this', "
                + COL_CREATED + " INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Forward-only for v1; preserve data on future migrations.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }
}
