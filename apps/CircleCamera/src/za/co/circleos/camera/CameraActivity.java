/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Camera (WP-34..37) - Camera2 capture. Live preview, full-resolution
 * JPEG capture saved to Pictures/Circle, flash toggle, front/back switch.
 * Manual ISO/shutter "pro" controls and creative modes layer on top of this.
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
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
                                req.set(CaptureRequest.CONTROL_MODE,
                                        CameraMetadata.CONTROL_MODE_AUTO);
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
            req.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            req.set(CaptureRequest.FLASH_MODE, mFlash
                    ? CameraMetadata.FLASH_MODE_SINGLE : CameraMetadata.FLASH_MODE_OFF);
            req.set(CaptureRequest.JPEG_ORIENTATION, mBack ? mSensorOrientation
                    : (360 - mSensorOrientation) % 360);
            mSession.capture(req.build(), null, mHandler);
        } catch (Throwable t) {
            toast("Capture failed");
        }
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
