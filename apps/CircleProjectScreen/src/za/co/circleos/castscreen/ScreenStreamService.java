/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Project My Screen (WP-68) — phone-side screen streamer. Captures the
 * display via MediaProjection, H.264-encodes it with MediaCodec, and serves the
 * encoded stream over TCP.
 *
 * Wire protocol (so any viewer can be written): each access unit is sent as a
 * 4-byte big-endian length followed by that many bytes of raw H.264 (Annex-B).
 * The first frame a client receives is the codec config (SPS/PPS). Feed the
 * stream straight into any H.264 decoder (ffmpeg: `ffplay tcp://ip:7842`).
 */
package za.co.circleos.castscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ScreenStreamService extends Service {

    static final int PORT = 7842;
    static final String EXTRA_RESULT = "result";
    static final String EXTRA_DATA = "data";
    static final String ACTION_STOP = "za.co.circleos.castscreen.STOP";
    private static final String CHANNEL = "cast";

    private MediaProjection mProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaCodec mEncoder;
    private Surface mInputSurface;
    private ServerSocket mServerSocket;
    private volatile boolean mRunning = false;
    private final CopyOnWriteArrayList<Socket> mClients = new CopyOnWriteArrayList<>();
    private volatile byte[] mConfig; // SPS/PPS sent to each new client first

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNotice();
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        int result = intent.getIntExtra(EXTRA_RESULT, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
        if (data == null) { stopSelf(); return START_NOT_STICKY; }

        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mProjection = mpm.getMediaProjection(result, data);
        if (mProjection == null) { stopSelf(); return START_NOT_STICKY; }
        mProjection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, null);

        try {
            mRunning = true;
            startEncoderAndDisplay();
            startServer();
        } catch (Throwable t) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startEncoderAndDisplay() throws Exception {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int dpi = dm.densityDpi;
        int w = dm.widthPixels, h = dm.heightPixels;
        // Cap the long edge for a smooth stream while keeping aspect ratio.
        float scale = Math.min(1f, 1280f / Math.max(w, h));
        w = even((int) (w * scale));
        h = even((int) (h * scale));

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", w, h);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        mEncoder = MediaCodec.createEncoderByType("video/avc");
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        mVirtualDisplay = mProjection.createVirtualDisplay("circle-cast", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mInputSurface, null, null);

        new Thread(this::drainEncoder, "cast-drain").start();
    }

    private void drainEncoder() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (mRunning) {
            MediaCodec enc = mEncoder;
            if (enc == null) break;
            int idx;
            try {
                idx = enc.dequeueOutputBuffer(info, 50000);
            } catch (Throwable t) {
                break;
            }
            if (idx >= 0) {
                ByteBuffer buf = enc.getOutputBuffer(idx);
                if (buf != null && info.size > 0) {
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    byte[] frame = new byte[info.size];
                    buf.get(frame);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        mConfig = frame;
                    } else {
                        broadcast(frame);
                    }
                }
                try { enc.releaseOutputBuffer(idx, false); } catch (Throwable ignored) {}
            }
        }
    }

    private void startServer() {
        new Thread(() -> {
            try {
                mServerSocket = new ServerSocket(PORT);
                while (mRunning) {
                    Socket s = mServerSocket.accept();
                    s.setTcpNoDelay(true);
                    byte[] cfg = mConfig;
                    if (cfg != null) writeFrame(s, cfg);
                    mClients.add(s);
                }
            } catch (Throwable ignored) {
            }
        }, "cast-accept").start();
    }

    private void broadcast(byte[] frame) {
        for (Socket s : mClients) {
            if (!writeFrame(s, frame)) {
                try { s.close(); } catch (Throwable ignored) {}
                mClients.remove(s);
            }
        }
    }

    private boolean writeFrame(Socket s, byte[] frame) {
        try {
            OutputStream os = s.getOutputStream();
            int n = frame.length;
            os.write((n >>> 24) & 0xFF);
            os.write((n >>> 16) & 0xFF);
            os.write((n >>> 8) & 0xFF);
            os.write(n & 0xFF);
            os.write(frame);
            os.flush();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void startForegroundNotice() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Screen projection",
                NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle("Projecting your screen")
                .setContentText("Streaming on port " + PORT)
                .setOngoing(true)
                .build();
        startForeground(7842, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    private int even(int v) {
        return (v % 2 == 0) ? v : v - 1;
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        for (Socket s : mClients) {
            try { s.close(); } catch (Throwable ignored) {}
        }
        mClients.clear();
        try { if (mServerSocket != null) { mServerSocket.close(); mServerSocket = null; } } catch (Throwable ignored) {}
        try { if (mVirtualDisplay != null) { mVirtualDisplay.release(); mVirtualDisplay = null; } } catch (Throwable ignored) {}
        try { if (mEncoder != null) { mEncoder.stop(); mEncoder.release(); mEncoder = null; } } catch (Throwable ignored) {}
        try { if (mInputSurface != null) { mInputSurface.release(); mInputSurface = null; } } catch (Throwable ignored) {}
        try { if (mProjection != null) { mProjection.stop(); mProjection = null; } } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
