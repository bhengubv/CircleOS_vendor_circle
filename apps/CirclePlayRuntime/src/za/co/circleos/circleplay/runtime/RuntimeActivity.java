/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Circle Play runtime's front door. Circle Play's library fires
 * {@code za.co.circleos.circleplay.LAUNCH} with the game's exe path + title; this
 * activity hosts the game's display Surface, drives a {@link RuntimeSession}
 * (Box64 + Wine + DXVK), and shows a true-black status/HUD layer. If the runtime
 * native pack isn't installed yet, it says so plainly instead of failing — the
 * library keeps working and games run once the pack lands.
 */
package za.co.circleos.circleplay.runtime;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;

public final class RuntimeActivity extends Activity implements SurfaceHolder.Callback {

    public static final String ACTION_LAUNCH = "za.co.circleos.circleplay.LAUNCH";

    private static final int BG = 0xFF000000;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;

    private final Handler mUi = new Handler(Looper.getMainLooper());

    private NativePack mPack;
    private RuntimeSession mSession;
    private Thread mSessionThread;

    private SurfaceView mSurface;
    private TextView mStatus;
    private String mExe, mTitle;
    private boolean mStarted;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mExe = getIntent().getStringExtra("exe");
        mTitle = getIntent().getStringExtra("title");
        if (mExe == null) { finish(); return; }

        mPack = new NativePack(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        mSurface = new SurfaceView(this);
        mSurface.getHolder().addCallback(this);
        root.addView(mSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mStatus = new TextView(this);
        mStatus.setTextColor(TEXT);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mStatus.setPadding(dp(24), dp(24), dp(24), dp(24));
        mStatus.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER;
        root.addView(mStatus, slp);

        TextView exit = new TextView(this);
        exit.setText("✕");
        exit.setTextColor(ACCENT);
        exit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        exit.setPadding(dp(18), dp(14), dp(18), dp(14));
        exit.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.gravity = Gravity.TOP | Gravity.END;
        root.addView(exit, elp);

        setContentView(root);

        if (!mPack.isReady()) {
            showStatus(mPack.status()
                    + "\n\nCircle Play needs its compatibility runtime (Box64 + Wine).\n"
                    + "Install it from Circle Play → Runtime, then relaunch "
                    + (mTitle != null ? mTitle : "the game") + ".");
        } else {
            showStatus("Preparing " + (mTitle != null ? mTitle : "game") + "…");
        }
    }

    // ── Surface lifecycle ──

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mStarted || !mPack.isReady()) return;
        mStarted = true;
        SurfaceBridge.init(mPack.renderBackendSo());
        File tmp = new File(getFilesDir(), "tmp");
        SurfaceBridge.configure(new File(tmp, "circle_fb").getAbsolutePath(),
                new File(tmp, "circle_input").getAbsolutePath());
        startSession(holder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        SurfaceBridge.sendMotion((int) e.getX(), (int) e.getY(), e.getActionMasked());
        return true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        SurfaceBridge.resize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        SurfaceBridge.detach();
    }

    private void startSession(SurfaceHolder holder) {
        mSession = new RuntimeSession(mPack, getFilesDir(), new RuntimeSession.Listener() {
            @Override public void onStatus(String m) { mUi.post(() -> showStatus(m)); }
            @Override public void onExit(int code) { mUi.post(() -> { if (!isFinishing()) finish(); }); }
        });
        final android.view.Surface surface = holder.getSurface();
        mSessionThread = new Thread(() -> mSession.run(mExe, surface), "circle-play-session");
        mSessionThread.start();
    }

    private void showStatus(String m) {
        // Hide the status text once the game is actually drawing to the surface.
        mStatus.setText(m);
        mStatus.setVisibility(m == null || m.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSession != null) mSession.stop();
        SurfaceBridge.detach();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
