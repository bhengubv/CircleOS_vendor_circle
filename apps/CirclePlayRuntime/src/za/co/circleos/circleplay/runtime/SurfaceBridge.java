/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * JNI seam to the native render backend (libcircleplay_render). It blits the X
 * server's shared framebuffer onto the Android Surface and forwards input to the
 * server. The .so is built in-tree (loaded via loadLibrary); if a device runs the
 * runtime from the downloadable pack instead, it is loaded from there. Either way
 * the bridge disables cleanly if it can't load, rather than crashing the session.
 */
package za.co.circleos.circleplay.runtime;

import android.util.Log;
import android.view.Surface;

import java.io.File;

final class SurfaceBridge {

    private static final String TAG = "CirclePlayRuntime";
    private static volatile boolean sLoaded;

    private SurfaceBridge() {}

    /** Load the render backend once: in-tree system lib first, then the pack path. */
    static synchronized boolean init(File backendSo) {
        if (sLoaded) return true;
        try {
            System.loadLibrary("circleplay_render");
            sLoaded = true;
            return true;
        } catch (Throwable t1) {
            try {
                if (backendSo != null && backendSo.exists()) {
                    System.load(backendSo.getAbsolutePath());
                    sLoaded = true;
                    return true;
                }
                Log.w(TAG, "render backend not installed: " + backendSo);
            } catch (Throwable t2) {
                Log.e(TAG, "failed to load render backend", t2);
            }
        }
        return false;
    }

    static boolean isReady() {
        return sLoaded;
    }

    /** Tell the backend where the X server's framebuffer + input socket live. */
    static void configure(String fbPath, String inputPath) {
        if (!sLoaded) return;
        try { nativeInit(fbPath, inputPath); } catch (Throwable t) { Log.e(TAG, "configure failed", t); }
    }

    static void attach(Surface surface) {
        if (!sLoaded || surface == null) return;
        try { nativeAttach(surface); } catch (Throwable t) { Log.e(TAG, "attach failed", t); }
    }

    static void detach() {
        if (!sLoaded) return;
        try { nativeDetach(); } catch (Throwable t) { Log.e(TAG, "detach failed", t); }
    }

    static void resize(int w, int h) {
        if (!sLoaded) return;
        try { nativeResize(w, h); } catch (Throwable ignored) {}
    }

    /** Forward a touch/pointer event to the X server (action = MotionEvent action). */
    static void sendMotion(int x, int y, int action) {
        if (!sLoaded) return;
        try { nativeMotion(x, y, action); } catch (Throwable ignored) {}
    }

    /** Forward a key event to the X server. */
    static void sendKey(int keyCode, boolean down) {
        if (!sLoaded) return;
        try { nativeKey(keyCode, down ? 1 : 0); } catch (Throwable ignored) {}
    }

    // Implemented by libcircleplay_render.so.
    private static native void nativeInit(String fbPath, String inputPath);
    private static native void nativeAttach(Surface surface);
    private static native void nativeDetach();
    private static native void nativeResize(int w, int h);
    private static native void nativeMotion(int x, int y, int action);
    private static native void nativeKey(int keyCode, int down);
}
