/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.personalityeditor;

import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.personality.ICirclePersonalityManager;

/** Acquires the ICirclePersonalityManager binder from ServiceManager. */
class ServiceConnection {
    private static final String TAG          = "PersonalityEditor";
    private static final String SERVICE_NAME = "circle.personality";

    private ServiceConnection() {}

    static ICirclePersonalityManager get() {
        try {
            IBinder binder = ServiceManager.getService(SERVICE_NAME);
            if (binder == null) {
                Log.w(TAG, "circle.personality not found");
                return null;
            }
            return ICirclePersonalityManager.Stub.asInterface(binder);
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect: " + e.getMessage());
            return null;
        }
    }
}
