/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

import android.util.Log;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects C2 beaconing by analysing inter-connection timing for each destination.
 *
 * A beacon is characterised by connections at regular intervals (±15% jitter).
 * Common periods: 30s, 60s, 300s.
 */
public class BeaconDetector {

    private static final String TAG = "TrafficLobby.Beacon";

    /** Minimum samples before declaring a beacon. */
    private static final int MIN_SAMPLES = 5;
    /** Jitter tolerance — interval must be within this fraction of the mean. */
    private static final double JITTER_TOLERANCE = 0.15;
    /** Maximum history per destination (ms timestamps). */
    private static final int MAX_HISTORY = 20;

    // destination (host:port) → recent connection timestamps (ms)
    private final Map<String, Deque<Long>> mHistory = new HashMap<>();

    /** Record a new outbound connection attempt. Returns true if beaconing is detected. */
    public synchronized boolean recordConnection(String destination, long timestampMs) {
        Deque<Long> times = mHistory.computeIfAbsent(destination, k -> new ArrayDeque<>());
        times.addLast(timestampMs);
        if (times.size() > MAX_HISTORY) times.pollFirst();

        if (times.size() < MIN_SAMPLES) return false;
        return isBeaconing(times);
    }

    private boolean isBeaconing(Deque<Long> times) {
        Long[] ts = times.toArray(new Long[0]);
        double[] intervals = new double[ts.length - 1];
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = ts[i + 1] - ts[i];
        }
        double sum = 0;
        for (double v : intervals) sum += v;
        double mean = sum / intervals.length;
        if (mean < 5_000) return false; // Too fast — not a beacon, just normal traffic

        for (double v : intervals) {
            if (Math.abs(v - mean) / mean > JITTER_TOLERANCE) return false;
        }
        Log.w(TAG, "Beacon detected — interval ~" + (long)(mean / 1000) + "s");
        return true;
    }

    public synchronized void clearHistory(String destination) {
        mHistory.remove(destination);
    }

    public synchronized void clearAll() {
        mHistory.clear();
    }
}
