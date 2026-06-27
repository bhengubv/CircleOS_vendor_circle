/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Device-rooted ownership ledger for Circle Play (#150).
 *
 * Unlike a store account whose licences can be revoked, geo-locked, or lost with
 * the account, a game you claim here is YOURS: the entitlement lives on the
 * device, bound to a per-device owner key, and proves itself with no server in
 * the loop. Entitlements ride along when a game is shared peer-to-peer over the
 * mesh, so ownership survives even if every store goes dark — own once, keep
 * forever, uncensorable. Ownership is anchored to the device/SDPKT identity
 * (the wallet spine), not a remote account that can ban you.
 */
package za.co.circleos.circleplay;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

final class Ownership {

    static final String SRC_CLAIMED   = "claimed";    // self-issued (drop-in / free)
    static final String SRC_PURCHASED = "purchased";  // bought via the SDPKT wallet
    static final String SRC_MESH      = "mesh";        // received from a peer over AetherNet

    private static final String PREFS    = "circle_play_owned";
    private static final String K_OWNER  = "owner_key";
    private static final String K_LEDGER = "ledger";

    private final SharedPreferences p;

    Ownership(Context c) {
        p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Stable id for a game from its title (folder/exe name). */
    static String gameId(String name) {
        return sha256("circle-play:" + name).substring(0, 16);
    }

    boolean isOwned(String gameId) {
        return find(gameId) != null;
    }

    int count() {
        return ledger().length();
    }

    JSONObject get(String gameId) {
        return find(gameId);
    }

    /**
     * Claim ownership (own-once). Idempotent: re-claiming returns the existing record.
     * {@code source} is one of SRC_CLAIMED / SRC_PURCHASED / SRC_MESH.
     */
    JSONObject claim(String gameId, String title, String source) {
        JSONObject ex = find(gameId);
        if (ex != null) return ex;
        try {
            JSONObject o = new JSONObject();
            o.put("id", gameId);
            o.put("title", title);
            o.put("source", source);
            o.put("owner", ownerFingerprint());
            o.put("proof", proof(gameId));
            JSONArray arr = ledger();
            arr.put(o);
            p.edit().putString(K_LEDGER, arr.toString()).apply();
            return o;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Record an entitlement received from a peer over the mesh, preserving the original
     * owner fingerprint as provenance. Returns the stored record (or existing, if already owned).
     */
    JSONObject receiveFromMesh(String gameId, String title, String originOwner) {
        JSONObject ex = find(gameId);
        if (ex != null) return ex;
        try {
            JSONObject o = new JSONObject();
            o.put("id", gameId);
            o.put("title", title);
            o.put("source", SRC_MESH);
            o.put("owner", ownerFingerprint());
            o.put("origin", originOwner);   // who shared it
            o.put("proof", proof(gameId));
            JSONArray arr = ledger();
            arr.put(o);
            p.edit().putString(K_LEDGER, arr.toString()).apply();
            return o;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A device-bound proof token over (ownerKey + gameId). Only this device can recompute it. */
    String proof(String gameId) {
        return sha256(ownerKey() + ":" + gameId);
    }

    /** Short, shareable fingerprint of this device's owner identity (the wallet spine). */
    String ownerFingerprint() {
        return sha256(ownerKey()).substring(0, 12);
    }

    // ── internals ──

    private JSONObject find(String gameId) {
        JSONArray arr = ledger();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && gameId.equals(o.optString("id"))) return o;
        }
        return null;
    }

    private JSONArray ledger() {
        try {
            return new JSONArray(p.getString(K_LEDGER, "[]"));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private String ownerKey() {
        String k = p.getString(K_OWNER, null);
        if (k != null) return k;
        byte[] r = new byte[32];
        new SecureRandom().nextBytes(r);
        k = hex(r);
        p.edit().putString(K_OWNER, k).apply();
        return k;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable t) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }
}
