/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

import android.content.Context;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core traffic analysis engine.
 * Combines threat matching, beacon detection, and upload volume analysis.
 */
public class TrafficAnalyzer {

    private static final String TAG = "TrafficLobby.Analyzer";

    /** Exfiltration threshold — uploads >1MB to unknown host trigger a lobby verdict. */
    private static final long EXFIL_THRESHOLD_BYTES = 1024 * 1024L;

    private final ThreatMatcher  mThreatMatcher;
    private final BeaconDetector mBeaconDetector;
    private int mMode = TrafficMode.BALANCED;

    // Track upload bytes per destination
    private final ConcurrentHashMap<String, AtomicLong> mUploadBytes = new ConcurrentHashMap<>();

    public TrafficAnalyzer(Context context) {
        mThreatMatcher  = new ThreatMatcher(context);
        mBeaconDetector = new BeaconDetector();
    }

    public void setMode(int mode) { mMode = mode; }

    /**
     * Evaluate an outbound connection.
     * @param packageName  App making the connection
     * @param host         Destination hostname
     * @param ip           Resolved IP address
     * @param port         Destination port
     * @param timestampMs  Current time
     */
    public ConnectionVerdict evaluate(String packageName, String host,
                                      String ip, int port, long timestampMs) {
        String dest = (host != null ? host : ip) + ":" + port;

        // Always check threat feeds first
        ConnectionVerdict feedVerdict = mThreatMatcher.evaluate(host, ip);
        if (feedVerdict.verdict == ConnectionVerdict.BLOCK) return feedVerdict;

        // Beacon detection
        if (mBeaconDetector.recordConnection(dest, timestampMs)) {
            Log.w(TAG, "Beacon from " + packageName + " to " + dest);
            return ConnectionVerdict.lobby("Regular-interval beaconing detected");
        }

        // Paranoid mode: lobby all first-time connections
        if (mMode == TrafficMode.PARANOID && !mUploadBytes.containsKey(dest)) {
            return ConnectionVerdict.lobby("First-time connection (Paranoid mode)");
        }

        return feedVerdict; // ALLOW or LOBBY from threat feed
    }

    /** Record bytes uploaded to a destination. Returns true if exfiltration threshold crossed. */
    public boolean recordUpload(String destination, long bytes) {
        AtomicLong total = mUploadBytes.computeIfAbsent(destination, k -> new AtomicLong(0));
        long newTotal = total.addAndGet(bytes);
        if (newTotal > EXFIL_THRESHOLD_BYTES) {
            total.set(0); // Reset counter after alert
            return true;
        }
        return false;
    }

    public void allowDestination(String host) { mThreatMatcher.addToUserWhitelist(host); }
    public void blockDestination(String host)  { mThreatMatcher.addToUserBlacklist(host); }
    public void reloadFeeds() { mThreatMatcher.reload(); }
}
