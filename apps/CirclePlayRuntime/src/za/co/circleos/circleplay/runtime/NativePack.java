/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Locates the Circle Play runtime native pack — Box64, Wine, DXVK, the X server,
 * and the render backend .so. The pack is large (hundreds of MB) and downloaded
 * once, separately from this APK, into the app's files dir. {@link #isReady}
 * tells the activity whether games can run yet; if not, it points the user at the
 * one-tap pack download.
 */
package za.co.circleos.circleplay.runtime;

import android.content.Context;

import java.io.File;

final class NativePack {

    /** Bump when the pack layout/ABI changes so old packs are re-fetched. */
    static final int VERSION = 1;

    private final File mRoot;

    NativePack(Context ctx) {
        mRoot = new File(ctx.getFilesDir(), "runtime");
    }

    File root() { return mRoot; }

    File bin(String name) { return new File(mRoot, "bin/" + name); }

    String box64() { return bin("box64").getAbsolutePath(); }
    String wine() { return bin("wine").getAbsolutePath(); }
    String wineboot() { return bin("wineboot").getAbsolutePath(); }
    String wineserver() { return bin("wineserver").getAbsolutePath(); }
    File xserver() { return bin("circle-xserver"); }

    File renderBackendSo() { return new File(mRoot, "lib/libcircleplay_render.so"); }

    File versionMarker() { return new File(mRoot, "PACK_VERSION"); }

    /** True when the essential binaries are present and the pack version matches. */
    boolean isReady() {
        if (!bin("box64").exists() || !bin("wine").exists() || !xserver().exists()) return false;
        try {
            String v = new String(java.nio.file.Files.readAllBytes(versionMarker().toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            return Integer.parseInt(v) == VERSION;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Human-readable reason the pack isn't usable, for the install prompt. */
    String status() {
        if (isReady()) return "Runtime ready";
        if (!mRoot.exists()) return "Runtime pack not installed";
        return "Runtime pack incomplete or outdated — re-download";
    }
}
