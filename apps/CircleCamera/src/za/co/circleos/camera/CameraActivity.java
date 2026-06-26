/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Camera (WP-34..37) - Camera2 capture. Live preview, full-resolution
 * JPEG capture saved to Pictures/Circle, flash toggle, front/back switch, and a
 * PRO mode with manual ISO + shutter (live preview). Creative modes (pano,
 * refocus, living photo) layer on top of this.
 */
package za.co.circleos.camera;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Range;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public final class CameraActivity extends Activity {

    private static final int REQ = 1;

    private TextureView mTexture;
    private CameraManager mManager;
    private String mCameraId;
    private boolean mBack = true;
    private boolean mFlash = false;

    private CameraDevice mDevice;
    private CameraCaptureSession mSession;
    private ImageReader mReader;
    private Size mJpegSize;
    private int mSensorOrientation = 90;
    private HandlerThread mThread;
    private Handler mHandler;

    // Pro mode — manual ISO + shutter (WP-35..37).
    private boolean mProMode = false;
    private Range<Integer> mIsoRange;
    private Range<Long> mExpRange;
    private int mIso = 100;
    private long mShutterNs = 16_666_666L; // ~1/60s
    private LinearLayout mProPanel;
    private TextView mIsoLabel;
    private TextView mShutterLabel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        mTexture = new TextureView(this);
        root.addView(mTexture, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView shutter = circleButton("●", 26);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(72), dp(72));
        slp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        slp.bottomMargin = dp(36);
        shutter.setLayoutParams(slp);
        shutter.setOnClickListener(v -> capture());
        root.addView(shutter);

        final TextView flash = circleButton("⚡", 22);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(48), dp(48));
        flp.gravity = Gravity.TOP | Gravity.START;
        flp.setMargins(dp(20), dp(40), 0, 0);
        flash.setLayoutParams(flp);
        flash.setAlpha(0.5f);
        flash.setOnClickListener(v -> {
            mFlash = !mFlash;
            flash.setAlpha(mFlash ? 1f : 0.5f);
            createPreview();
        });
        root.addView(flash);

        TextView swap = circleButton("↻", 22);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(dp(48), dp(48));
        wlp.gravity = Gravity.TOP | Gravity.END;
        wlp.setMargins(0, dp(40), dp(20), 0);
        swap.setLayoutParams(wlp);
        swap.setOnClickListener(v -> {
            mBack = !mBack;
            closeCamera();
            openCamera();
        });
        root.addView(swap);

        final TextView pro = circleButton("PRO", 13);
        FrameLayout.LayoutParams prlp = new FrameLayout.LayoutParams(dp(58), dp(40));
        prlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        prlp.topMargin = dp(44);
        pro.setLayoutParams(prlp);
        pro.setAlpha(0.6f);
        pro.setOnClickListener(v -> {
            mProMode = !mProMode;
            pro.setAlpha(mProMode ? 1f : 0.6f);
            pro.setTextColor(mProMode ? 0xFF2196F3 : 0xFFFFFFFF);
            if (mProPanel != null) {
                mProPanel.setVisibility(mProMode ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            restartPreview();
        });
        root.addView(pro);

        mProPanel = buildProPanel();
        FrameLayout.LayoutParams pplp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pplp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pplp.setMargins(dp(24), 0, dp(24), dp(128));
        mProPanel.setLayoutParams(pplp);
        mProPanel.setVisibility(android.view.View.GONE);
        root.addView(mProPanel);

        setContentView(root);

        mManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { tryOpen(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) { }
        });
    }

    private void tryOpen() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED
                && mTexture.isAvailable()) {
            openCamera();
        } else if (rc == REQ) {
            toast("Camera permission needed");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startThread();
        if (mTexture.isAvailable()) tryOpen();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopThread();
        super.onPause();
    }

    private void startThread() {
        mThread = new HandlerThread("circle-camera");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
    }

    private void stopThread() {
        if (mThread != null) {
            mThread.quitSafely();
            try { mThread.join(); } catch (InterruptedException ignored) { }
            mThread = null;
            mHandler = null;
        }
    }

    private void openCamera() {
        try {
            mCameraId = pickCamera(mBack);
            if (mCameraId == null) { toast("No camera available"); return; }
            CameraCharacteristics ch = mManager.getCameraCharacteristics(mCameraId);
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (so != null) mSensorOrientation = so;
            mIsoRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            mExpRange = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (mIsoRange != null) {
                mIso = (mIsoRange.getLower() + mIsoRange.getUpper()) / 2;
            }
            if (mExpRange != null) {
                mShutterNs = Math.max(mExpRange.getLower(),
                        Math.min(16_666_666L, mExpRange.getUpper()));
            }
            StreamConfigurationMap map =
                    ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mJpegSize = largest(map.getOutputSizes(ImageFormat.JPEG));
            mReader = ImageReader.newInstance(
                    mJpegSize.getWidth(), mJpegSize.getHeight(), ImageFormat.JPEG, 2);
            mReader.setOnImageAvailableListener(mOnImage, mHandler);
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) return;
            mManager.openCamera(mCameraId, mStateCallback, mHandler);
        } catch (Throwable t) {
            toast("Couldn't open camera");
        }
    }

    private String pickCamera(boolean back) throws CameraAccessException {
        String[] ids = mManager.getCameraIdList();
        for (String id : ids) {
            Integer f = mManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING);
            if (f == null) continue;
            if (back && f == CameraCharacteristics.LENS_FACING_BACK) return id;
            if (!back && f == CameraCharacteristics.LENS_FACING_FRONT) return id;
        }
        return ids.length > 0 ? ids[0] : null;
    }

    private Size largest(Size[] sizes) {
        return Collections.max(Arrays.asList(sizes),
                Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()));
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice c) { mDevice = c; createPreview(); }
        @Override public void onDisconnected(CameraDevice c) { c.close(); mDevice = null; }
        @Override public void onError(CameraDevice c, int e) { c.close(); mDevice = null; }
    };

    private void createPreview() {
        try {
            SurfaceTexture st = mTexture.getSurfaceTexture();
            if (st == null || mDevice == null || mReader == null) return;
            st.setDefaultBufferSize(1920, 1080);
            final Surface preview = new Surface(st);
            final CaptureRequest.Builder req =
                    mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            req.addTarget(preview);
            mDevice.createCaptureSession(Arrays.asList(preview, mReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            mSession = s;
                            try {
                                applyManual(req);
                                req.set(CaptureRequest.FLASH_MODE, mFlash
                                        ? CameraMetadata.FLASH_MODE_TORCH
                                        : CameraMetadata.FLASH_MODE_OFF);
                                s.setRepeatingRequest(req.build(), null, mHandler);
                            } catch (Throwable t) { /* ignore */ }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            toast("Preview failed");
                        }
                    }, mHandler);
        } catch (Throwable t) {
            toast("Preview error");
        }
    }

    private void capture() {
        if (mDevice == null || mSession == null || mReader == null) return;
        try {
            CaptureRequest.Builder req =
                    mDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            req.addTarget(mReader.getSurface());
            applyManual(req);
            req.set(CaptureRequest.FLASH_MODE, mFlash
                    ? CameraMetadata.FLASH_MODE_SINGLE : CameraMetadata.FLASH_MODE_OFF);
            req.set(CaptureRequest.JPEG_ORIENTATION, mBack ? mSensorOrientation
                    : (360 - mSensorOrientation) % 360);
            mSession.capture(req.build(), null, mHandler);
        } catch (Throwable t) {
            toast("Capture failed");
        }
    }

    /** Apply manual ISO/shutter when in pro mode and the device supports it; else auto. */
    private void applyManual(CaptureRequest.Builder req) {
        if (!mProMode || mIsoRange == null || mExpRange == null) {
            req.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            return;
        }
        int iso = Math.max(mIsoRange.getLower(), Math.min(mIsoRange.getUpper(), mIso));
        long exp = Math.max(mExpRange.getLower(), Math.min(mExpRange.getUpper(), mShutterNs));
        req.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        req.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
        req.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp);
        req.set(CaptureRequest.SENSOR_FRAME_DURATION, exp);
    }

    /** Update the live preview's repeating request without rebuilding the session. */
    private void restartPreview() {
        if (mDevice == null || mSession == null) return;
        try {
            SurfaceTexture st = mTexture.getSurfaceTexture();
            if (st == null) return;
            Surface preview = new Surface(st);
            CaptureRequest.Builder req = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            req.addTarget(preview);
            applyManual(req);
            req.set(CaptureRequest.FLASH_MODE, mFlash
                    ? CameraMetadata.FLASH_MODE_TORCH : CameraMetadata.FLASH_MODE_OFF);
            mSession.setRepeatingRequest(req.build(), null, mHandler);
        } catch (Throwable ignored) {
        }
    }

    private LinearLayout buildProPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0x99000000);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));

        mIsoLabel = proLabel("ISO  auto");
        panel.addView(mIsoLabel);
        SeekBar isoBar = new SeekBar(this);
        isoBar.setMax(100);
        isoBar.setProgress(50);
        isoBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (mIsoRange == null) return;
                mIso = mIsoRange.getLower()
                        + Math.round((mIsoRange.getUpper() - mIsoRange.getLower()) * (p / 100f));
                mIsoLabel.setText("ISO  " + mIso);
                if (fromUser && mProMode) restartPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        panel.addView(isoBar);

        mShutterLabel = proLabel("Shutter  auto");
        panel.addView(mShutterLabel);
        SeekBar expBar = new SeekBar(this);
        expBar.setMax(100);
        expBar.setProgress(50);
        expBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (mExpRange == null) return;
                long lo = Math.max(1L, mExpRange.getLower());
                long hi = Math.min(mExpRange.getUpper(), 250_000_000L); // cap ~1/4 s
                if (hi <= lo) hi = mExpRange.getUpper();
                mShutterNs = (long) (lo * Math.pow((double) hi / lo, p / 100.0));
                mShutterLabel.setText("Shutter  " + shutterLabel(mShutterNs));
                if (fromUser && mProMode) restartPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        panel.addView(expBar);
        return panel;
    }

    private TextView proLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(0, dp(6), 0, dp(2));
        return t;
    }

    private String shutterLabel(long ns) {
        double sec = ns / 1e9;
        if (sec >= 1) return String.format(java.util.Locale.US, "%.1fs", sec);
        return "1/" + Math.max(1, Math.round(1.0 / sec)) + "s";
    }

    private final ImageReader.OnImageAvailableListener mOnImage = reader -> {
        Image img = null;
        try {
            img = reader.acquireLatestImage();
            if (img == null) return;
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            saveJpeg(bytes);
        } catch (Throwable t) {
            // ignore
        } finally {
            if (img != null) img.close();
        }
    };

    private void saveJpeg(byte[] bytes) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "CIRCLE_" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Circle");
            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) os.write(bytes);
                }
                toast("Saved to Pictures/Circle");
            }
        } catch (Throwable t) {
            toast("Couldn't save photo");
        }
    }

    private void closeCamera() {
        try { if (mSession != null) { mSession.close(); mSession = null; } } catch (Throwable t) { }
        try { if (mDevice != null) { mDevice.close(); mDevice = null; } } catch (Throwable t) { }
        try { if (mReader != null) { mReader.close(); mReader = null; } } catch (Throwable t) { }
    }

    private TextView circleButton(String glyph, int sp) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(0x55000000);
        t.setClickable(true);
        return t;
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
