/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correlates sensor access (mic/camera/location) with network uploads
 * to detect spyware exfiltration patterns.
 *
 * Detection: sensor active + upload to unknown host > threshold = spyware alert.
 */
public class SpywareBehaviorDetector {

    private static final String TAG = "TrafficLobby.Spyware";

    public interface Listener {
        void onSpywareDetected(String packageName, String destination, String[] behaviors);
    }

    // Sensor flags
    public static final int SENSOR_MIC      = 1;
    public static final int SENSOR_CAMERA   = 2;
    public static final int SENSOR_LOCATION = 4;

    // packageName → currently active sensor flags (bitfield)
    private final Map<String, Integer> mActiveSensors = new HashMap<>();

    private Listener mListener;

    public void setListener(Listener l) { mListener = l; }

    public synchronized void onSensorActive(String packageName, int sensorFlag) {
        int current = mActiveSensors.getOrDefault(packageName, 0);
        mActiveSensors.put(packageName, current | sensorFlag);
        Log.d(TAG, packageName + " sensor active: " + sensorFlag);
    }

    public synchronized void onSensorInactive(String packageName, int sensorFlag) {
        int current = mActiveSensors.getOrDefault(packageName, 0);
        mActiveSensors.put(packageName, current & ~sensorFlag);
    }

    /**
     * Called when an app uploads data to a destination that isn't whitelisted.
     * If the app also has active sensors, this is a spyware pattern.
     */
    public synchronized void onSuspiciousUpload(String packageName, String destination) {
        int sensors = mActiveSensors.getOrDefault(packageName, 0);
        if (sensors == 0) return; // No sensors active — not a spyware pattern

        List<String> behaviors = new ArrayList<>();
        behaviors.add("Uploading data to: " + destination);
        if ((sensors & SENSOR_MIC)      != 0) behaviors.add("Accessing microphone in background");
        if ((sensors & SENSOR_CAMERA)   != 0) behaviors.add("Accessing camera in background");
        if ((sensors & SENSOR_LOCATION) != 0) behaviors.add("Accessing location in background");

        Log.w(TAG, "SPYWARE PATTERN: " + packageName + " → " + destination);

        if (mListener != null) {
            mListener.onSpywareDetected(packageName, destination,
                    behaviors.toArray(new String[0]));
        }
    }
}
