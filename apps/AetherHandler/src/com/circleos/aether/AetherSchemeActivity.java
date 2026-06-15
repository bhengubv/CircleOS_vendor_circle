/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * aether:// URI scheme handler.
 *
 * Handles URIs of the form:
 *   aether://<peerId>/<resource>
 *   aether://broadcast/<topic>
 *   aether://stream/<streamId>
 *   aether://content/<sha256hash>
 *
 * Routes to the appropriate Aether service (mesh send, content fetch,
 * stream join) via the circle.mesh binder. For alpha-1 this is a
 * debug view that parses and displays the URI components + shows
 * mesh status. Real content rendering lands when the Aether content
 * layer is wired to the UI.
 */
package com.circleos.aether;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import za.co.circleos.mesh.ICircleMeshService;

public final class AetherSchemeActivity extends Activity {

    private static final int DEEP = 0xFF1A1F36;
    private static final int WARM = 0xFFF5F0EB;
    private static final int GOLD = 0xFFD4A574;
    private static final int SAGE = 0xFF7D9B8A;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri uri = getIntent().getData();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(DEEP);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(60), dp(24), dp(24));
        scroll.addView(root);

        root.addView(label("Aether Protocol", GOLD, 24, true));
        root.addView(spacer(16));

        if (uri != null) {
            root.addView(label("URI", SAGE, 12, false));
            root.addView(value(uri.toString()));
            root.addView(spacer(12));
            root.addView(label("Scheme", SAGE, 12, false));
            root.addView(value(uri.getScheme()));
            root.addView(spacer(12));
            root.addView(label("Host (Peer / Command)", SAGE, 12, false));
            root.addView(value(uri.getHost() != null ? uri.getHost() : "(none)"));
            root.addView(spacer(12));
            root.addView(label("Path (Resource)", SAGE, 12, false));
            root.addView(value(uri.getPath() != null ? uri.getPath() : "(none)"));
            root.addView(spacer(12));

            if (uri.getQueryParameterNames() != null && !uri.getQueryParameterNames().isEmpty()) {
                root.addView(label("Query Parameters", SAGE, 12, false));
                for (String key : uri.getQueryParameterNames()) {
                    root.addView(value(key + " = " + uri.getQueryParameter(key)));
                }
                root.addView(spacer(12));
            }
        } else {
            root.addView(label("No URI provided", WARM, 16, false));
        }

        root.addView(spacer(24));
        root.addView(label("Mesh Status", GOLD, 18, true));
        root.addView(spacer(8));

        String meshStatus = queryMeshStatus();
        root.addView(value(meshStatus));

        root.addView(spacer(24));
        root.addView(label("This handler will route to:", SAGE, 12, false));
        root.addView(spacer(4));

        if (uri != null && uri.getHost() != null) {
            String host = uri.getHost();
            switch (host) {
                case "broadcast":
                    root.addView(value("SOS / topic broadcast via mesh flood"));
                    break;
                case "stream":
                    root.addView(value("Live stream join via AetherMedia"));
                    break;
                case "content":
                    root.addView(value("Content fetch via SHA-256 address"));
                    break;
                default:
                    root.addView(value("Direct message to peer: " + host));
                    break;
            }
        }

        setContentView(scroll);
    }

    private String queryMeshStatus() {
        try {
            IBinder b = ServiceManager.getService("circle.mesh");
            if (b == null) return "circle.mesh: not running";
            ICircleMeshService mesh = ICircleMeshService.Stub.asInterface(b);
            return "Running: " + mesh.isRunning()
                    + "\nPeers: " + mesh.getPeerCount()
                    + "\nDevice ID: " + mesh.getDeviceId();
        } catch (RemoteException e) {
            return "circle.mesh: " + e.getMessage();
        } catch (Throwable t) {
            return "circle.mesh: " + t.getMessage();
        }
    }

    private TextView label(String text, int color, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView value(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(WARM);
        t.setTextSize(15);
        t.setPadding(0, dp(2), 0, 0);
        return t;
    }

    private android.view.View spacer(int dpHeight) {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(dpHeight)));
        return v;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
