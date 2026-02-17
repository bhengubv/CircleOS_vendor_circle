/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.inferencebridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * ContentProvider that hands out the current session token to any caller
 * that holds com.circleos.permission.ACCESS_INFERENCE.
 *
 * Android enforces the readPermission declared in AndroidManifest.xml
 * before this provider's query() is ever invoked, so no additional
 * permission check is needed here.
 *
 * URI:  content://za.co.circleos.inferencebridge/token
 * Columns: token (String)
 */
public class BridgeTokenProvider extends ContentProvider {

    private static volatile SessionTokenManager sTokenManager;

    static void setTokenManager(SessionTokenManager mgr) {
        sTokenManager = mgr;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SessionTokenManager mgr = sTokenManager;
        if (mgr == null) return null;

        MatrixCursor cursor = new MatrixCursor(new String[]{"token"});
        cursor.addRow(new Object[]{mgr.getToken()});
        return cursor;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.circleos.inference.token"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String sel, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String sel, String[] args) { return 0; }
}
