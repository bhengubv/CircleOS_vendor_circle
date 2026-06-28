/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Drives one Windows-game session: bootstraps the Wine prefix, launches the
 * bundled X server, then runs the game's .exe through Box64 (x86_64 -> ARM64)
 * under Wine with DXVK (D3D -> Vulkan). The Java side owns process lifecycle and
 * environment; the heavy lifting is the native pack (Box64 + Wine + DXVK + the
 * X server) installed under {@link NativePack#root}.
 *
 * Honest boundary: the actual pixels are rendered by the native X server onto the
 * Surface this hands it. That bridge — and per-game compatibility — is the part
 * still maturing; everything here (prefix, process graph, env, lifecycle, logs)
 * is real and complete for its layer.
 */
package za.co.circleos.circleplay.runtime;

import android.util.Log;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RuntimeSession {

    interface Listener {
        void onStatus(String message);
        void onExit(int code);
    }

    private static final String TAG = "CirclePlayRuntime";

    private final NativePack mPack;
    private final File mDataDir;       // app files dir (writable home for the prefix)
    private final Listener mListener;

    private Process mXserver;
    private Process mGame;
    private volatile boolean mStopped;

    RuntimeSession(NativePack pack, File dataDir, Listener listener) {
        mPack = pack;
        mDataDir = dataDir;
        mListener = listener;
    }

    File winePrefix() {
        return new File(mDataDir, "prefix");
    }

    /** Launch {@code exePath} on the given {@code surface}. Blocks until the game exits. */
    void run(String exePath, Surface surface) {
        try {
            ensurePrefix();
            startXServer(surface);
            status("Starting " + new File(exePath).getName());
            mGame = exec(gameCommand(exePath), gameEnv(), winePrefix());
            pump(mGame, "wine");
            int code = mGame.waitFor();
            status(code == 0 ? "Game exited" : "Game exited (code " + code + ")");
            mListener.onExit(code);
        } catch (Throwable t) {
            Log.e(TAG, "session failed", t);
            status("Couldn't start: " + t.getMessage());
            mListener.onExit(-1);
        } finally {
            stop();
        }
    }

    void stop() {
        mStopped = true;
        killTree();
        if (mGame != null) mGame.destroy();
        if (mXserver != null) mXserver.destroy();
    }

    // ── prefix ──

    private void ensurePrefix() throws Exception {
        File prefix = winePrefix();
        if (new File(prefix, "system.reg").exists()) return; // already initialised
        status("Setting up Windows environment (first run)…");
        prefix.mkdirs();
        Process p = exec(new String[]{mPack.box64(), mPack.wineboot(), "--init"},
                gameEnv(), prefix);
        pump(p, "wineboot");
        p.waitFor();
    }

    // ── X server ──

    private void startXServer(Surface surface) throws Exception {
        // The bundled X server renders to the Surface we pass via a control socket.
        // SURFACE_HANDLE is read by the native X server's CircleOS surface backend.
        File xserver = new File(mPack.root(), "bin/circle-xserver");
        if (!xserver.exists()) {
            status("Display server not in the runtime pack");
            return;
        }
        Map<String, String> env = baseEnv();
        env.put("CIRCLE_SURFACE", "1"); // tell the X server to use the Android surface backend
        ProcessBuilder pb = new ProcessBuilder(
                xserver.getAbsolutePath(), ":0", "-noreset", "-nolisten", "tcp");
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        mXserver = pb.start();
        SurfaceBridge.attach(surface); // hand the Surface to the native backend
        pump(mXserver, "xserver");
        // Give the server a moment to come up before the game connects.
        Thread.sleep(800);
    }

    // ── command + env ──

    private String[] gameCommand(String exePath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(mPack.box64());      // x86_64 -> ARM64
        cmd.add(mPack.wine());       // Windows API layer
        cmd.add(exePath);            // the game
        return cmd.toArray(new String[0]);
    }

    private Map<String, String> baseEnv() {
        Map<String, String> e = new HashMap<>();
        e.put("HOME", mDataDir.getAbsolutePath());
        e.put("TMPDIR", new File(mDataDir, "tmp").getAbsolutePath());
        e.put("PATH", new File(mPack.root(), "bin").getAbsolutePath() + ":/system/bin");
        e.put("LD_LIBRARY_PATH", new File(mPack.root(), "lib").getAbsolutePath());
        e.put("BOX64_LD_LIBRARY_PATH", new File(mPack.root(), "lib/x86_64").getAbsolutePath());
        return e;
    }

    private Map<String, String> gameEnv() {
        Map<String, String> e = baseEnv();
        e.put("WINEPREFIX", winePrefix().getAbsolutePath());
        e.put("DISPLAY", ":0");
        e.put("WINEDEBUG", "-all");
        e.put("DXVK_HUD", "0");
        e.put("DXVK_STATE_CACHE_PATH", mDataDir.getAbsolutePath());
        e.put("MESA_VK_WSI_PRESENT_MODE", "fifo");
        return e;
    }

    private Process exec(String[] cmd, Map<String, String> env, File cwd) throws Exception {
        new File(mDataDir, "tmp").mkdirs();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().putAll(env);
        if (cwd != null) pb.directory(cwd);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private void pump(Process p, String tag) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!mStopped && (line = r.readLine()) != null) {
                    Log.i(TAG, "[" + tag + "] " + line);
                }
            } catch (Throwable ignored) {
            }
        }, "pump-" + tag);
        t.setDaemon(true);
        t.start();
    }

    private void killTree() {
        // Best-effort: Wine/Box64 spawn children; ask Wine to end the session.
        try {
            if (mPack.isReady()) {
                Process p = exec(new String[]{mPack.box64(), mPack.wineserver(), "-k"},
                        gameEnv(), winePrefix());
                p.waitFor();
            }
        } catch (Throwable ignored) {
        }
    }

    private void status(String m) {
        Log.i(TAG, m);
        mListener.onStatus(m);
    }
}
