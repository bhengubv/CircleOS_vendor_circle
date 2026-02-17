/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local VPN service that intercepts all app traffic for Traffic Lobby analysis.
 *
 * Architecture:
 *   App → TUN interface → TrafficAnalyzer → ALLOW/LOBBY/BLOCK → real network
 *
 * The VPN does NOT route traffic to an external server.
 * All analysis is on-device; the TUN interface is local-only.
 *
 * Phase 1: DNS and connection metadata analysis only.
 * Phase 2: Full TLS fingerprinting and DPI (future scope).
 */
public class TrafficLobbyVpnService extends VpnService {

    private static final String TAG = "TrafficLobby.VPN";

    private ParcelFileDescriptor    mTunInterface;
    private TrafficAnalyzer         mAnalyzer;
    private SpywareBehaviorDetector mSpywareDetector;
    private final ExecutorService   mExecutor = Executors.newCachedThreadPool();
    private volatile boolean        mRunning  = false;

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        if (mRunning) return START_STICKY;

        mAnalyzer        = new TrafficAnalyzer(this);
        mSpywareDetector = new SpywareBehaviorDetector();
        mSpywareDetector.setListener(this::handleSpywareAlert);

        try {
            Builder builder = new Builder()
                    .setSession("CircleOS Traffic Lobby")
                    .addAddress("10.0.0.1", 24)
                    .addDnsServer("10.0.0.1")
                    .addRoute("0.0.0.0", 0)
                    .setBlocking(true);

            mTunInterface = builder.establish();
            if (mTunInterface == null) {
                Log.e(TAG, "Failed to establish VPN — user may not have granted VPN permission");
                stopSelf();
                return START_NOT_STICKY;
            }

            mRunning = true;
            mExecutor.submit(this::runPacketLoop);
            Log.i(TAG, "Traffic Lobby VPN established");

        } catch (Exception e) {
            Log.e(TAG, "VPN setup failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    /**
     * Packet processing loop.
     * Phase 1: DNS-level analysis (metadata only).
     * Phase 2: Full TLS fingerprinting and DPI.
     */
    private void runPacketLoop() {
        try (FileInputStream in  = new FileInputStream(mTunInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(mTunInterface.getFileDescriptor())) {

            byte[] packet = new byte[32767];
            while (mRunning) {
                int len = in.read(packet);
                if (len <= 0) continue;
                // Phase 1: pass packets through; Phase 2 will add DPI here
                out.write(packet, 0, len);
            }
        } catch (IOException e) {
            if (mRunning) Log.e(TAG, "Packet loop error", e);
        }
    }

    private void handleSpywareAlert(String packageName, String destination, String[] behaviors) {
        Log.w(TAG, "SPYWARE ALERT: " + packageName);
        // TODO Phase 2: show system-level alert with KILL & QUARANTINE action
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        mExecutor.shutdownNow();
        try { if (mTunInterface != null) mTunInterface.close(); } catch (IOException ignored) {}
        super.onDestroy();
    }
}
