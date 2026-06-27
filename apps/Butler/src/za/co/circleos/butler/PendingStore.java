/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Holds one outbound message per peer while the E2E channel is being
 * established (the CKX handshake). Flushed once the peer's key arrives.
 */
package za.co.circleos.butler;

import android.content.Context;
import android.content.SharedPreferences;

final class PendingStore {

    private static final String PREFS = "mesh_pending";

    private PendingStore() {}

    static void queue(Context c, String peerId, String text) {
        prefs(c).edit().putString(peerId, text).apply();
    }

    /** Returns and clears the pending text for a peer, or null if none. */
    static String take(Context c, String peerId) {
        SharedPreferences p = prefs(c);
        String t = p.getString(peerId, null);
        if (t != null) p.edit().remove(peerId).apply();
        return t;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
