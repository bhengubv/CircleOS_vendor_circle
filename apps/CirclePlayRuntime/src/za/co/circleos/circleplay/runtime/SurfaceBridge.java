/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * JNI seam between the Android Surface and the native render backend that the
 * bundled X server draws into. The backend .so ships in the runtime native pack
 * (it is large and downloaded separately, not baked into this APK), so it is
 * loaded from an absolute path at runtime. If the pack — or this device's GPU
 * support — isn't present, the bridge disables itself cleanly rather than
 * crashing the session.
 */
package za.co.circleos.circleplay.runtime;

import android.util.Log;
import android.view.Surface;

import java.io.File;

final class SurfaceBridge {

    private static final String TAG = "CirclePlayRuntime";
    private static volatile boolean sLoaded;

    private SurfaceBridge() {}

    /** Load the native render backend from the pack, once. Safe to call repeatedly. */
    static synchronized boolean init(File backendSo) {
        if (sLoaded) return true;
        try {
            if (backendSo == null || !backendSo.exists()) {
                Log.w(TAG, "render backend not installed: " + backendSo);
                return false;
            }
            System.load(backendSo.getAbsolutePath());
            sLoaded = true;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "failed to load render backend", t);
            return false;
        }
    }

    static boolean isReady() {
        return sLoaded;
    }

    /** Hand the Surface to the native X server backend. No-op if the backend isn't loaded. */
    static void attach(Surface surface) {
        if (!sLoaded || surface == null) return;
        try {
            nativeAttach(surface);
        } catch (Throwable t) {
            Log.e(TAG, "attach failed", t);
        }
    }

    static void detach() {
        if (!sLoaded) return;
        try {
            nativeDetach();
        } catch (Throwable t) {
            Log.e(TAG, "detach failed", t);
        }
    }

    static void resize(int w, int h) {
        if (!sLoaded) return;
        try {
            nativeResize(w, h);
        } catch (Throwable ignored) {
        }
    }

    // Implemented by libcircleplay_render.so in the runtime native pack.
    private static native void nativeAttach(Surface surface);
    private static native void nativeDetach();
    private static native void nativeResize(int w, int h);
}
