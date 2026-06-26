/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle AR Places (WP-41) — point the camera down a street and see nearby
 * places labelled over the live view. Heading comes from the rotation-vector
 * sensor; the places come from OpenStreetMap's open Overpass API. Projection +
 * sensor smoothing benefit from on-device calibration (follow-up).
 */
package za.co.circleos.ar;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public final class CircleARActivity extends Activity implements SensorEventListener {

    private static final int REQ = 1;

    private TextureView mTexture;
    private AROverlayView mOverlay;
    private TextView mHint;

    private CameraManager mCam;
    private CameraDevice mDevice;
    private CameraCaptureSession mSession;
    private String mCamId;
    private HandlerThread mThread;
    private Handler mHandler;

    private SensorManager mSensors;
    private Sensor mRotation;
    private final float[] mRot = new float[9];
    private final float[] mOut = new float[9];
    private final float[] mOrient = new float[3];

    private boolean mFetched = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        mTexture = new TextureView(this);
        root.addView(mTexture, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mOverlay = new AROverlayView(this);
        root.addView(mOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mHint = new TextView(this);
        mHint.setText("Point your camera down a street");
        mHint.setTextColor(0xFFFFFFFF);
        mHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mHint.setBackgroundColor(0x88000000);
        mHint.setPadding(dp(16), dp(10), dp(16), dp(10));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(40);
        mHint.setLayoutParams(hlp);
        root.addView(mHint);

        setContentView(root);

        mCam = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mSensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensors != null) mRotation = mSensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        mTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { tryStart(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
    }

    private void tryStart() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION}, REQ);
            return;
        }
        openCamera();
        fetchPois();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ && mTexture.isAvailable()) {
            boolean ok = true;
            for (int r : g) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) { openCamera(); fetchPois(); }
            else toast("Camera + location are needed for AR");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startThread();
        if (mRotation != null) mSensors.registerListener(this, mRotation, SensorManager.SENSOR_DELAY_GAME);
        if (mTexture.isAvailable()) tryStart();
    }

    @Override
    protected void onPause() {
        if (mSensors != null) mSensors.unregisterListener(this);
        closeCamera();
        stopThread();
        super.onPause();
    }

    /* ── sensors ── */

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(mRot, e.values);
        // Device held upright, looking through the back camera.
        SensorManager.remapCoordinateSystem(mRot, SensorManager.AXIS_X, SensorManager.AXIS_Z, mOut);
        SensorManager.getOrientation(mOut, mOrient);
        float az = (float) Math.toDegrees(mOrient[0]);
        az = (az + 360f) % 360f;
        mOverlay.setAzimuth(az);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /* ── camera preview ── */

    private void startThread() {
        mThread = new HandlerThread("ar-cam");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
    }

    private void stopThread() {
        if (mThread != null) {
            mThread.quitSafely();
            try { mThread.join(); } catch (InterruptedException ignored) {}
            mThread = null; mHandler = null;
        }
    }

    private void openCamera() {
        try {
            mCamId = backCamera();
            if (mCamId == null) { toast("No camera"); return; }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
            mCam.openCamera(mCamId, mState, mHandler);
        } catch (Throwable t) {
            toast("Couldn't open camera");
        }
    }

    private String backCamera() throws CameraAccessException {
        for (String id : mCam.getCameraIdList()) {
            Integer f = mCam.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (f != null && f == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        String[] ids = mCam.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    private final CameraDevice.StateCallback mState = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice c) { mDevice = c; preview(); }
        @Override public void onDisconnected(CameraDevice c) { c.close(); mDevice = null; }
        @Override public void onError(CameraDevice c, int e) { c.close(); mDevice = null; }
    };

    private void preview() {
        try {
            SurfaceTexture st = mTexture.getSurfaceTexture();
            if (st == null || mDevice == null) return;
            st.setDefaultBufferSize(1920, 1080);
            Surface surface = new Surface(st);
            CaptureRequest.Builder req = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            req.addTarget(surface);
            mDevice.createCaptureSession(java.util.Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            mSession = s;
                            try { s.setRepeatingRequest(req.build(), null, mHandler); }
                            catch (Throwable ignored) {}
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {}
                    }, mHandler);
        } catch (Throwable ignored) {
        }
    }

    private void closeCamera() {
        try { if (mSession != null) { mSession.close(); mSession = null; } } catch (Throwable ignored) {}
        try { if (mDevice != null) { mDevice.close(); mDevice = null; } } catch (Throwable ignored) {}
    }

    /* ── places from OpenStreetMap ── */

    private void fetchPois() {
        if (mFetched) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        LocationManager lm = getSystemService(LocationManager.class);
        if (lm == null) return;
        Location me = null;
        for (String prov : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(prov);
                if (l != null && (me == null || l.getTime() > me.getTime())) me = l;
            } catch (Throwable ignored) {}
        }
        if (me == null) { mHint.setText("Waiting for a location fix… (go outdoors)"); return; }
        mFetched = true;
        final Location origin = me;
        new Thread(() -> {
            List<Poi> pois = queryOverpass(origin.getLatitude(), origin.getLongitude(), 600);
            runOnUiThread(() -> {
                if (pois.isEmpty()) {
                    mHint.setText("No labelled places nearby");
                } else {
                    mHint.setText(pois.size() + " places around you");
                    mOverlay.setPois(pois, origin);
                }
            });
        }, "ar-overpass").start();
    }

    private List<Poi> queryOverpass(double lat, double lng, int radius) {
        List<Poi> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String q = "[out:json][timeout:20];(node(around:" + radius + "," + lat + "," + lng
                    + ")[name][amenity];node(around:" + radius + "," + lat + "," + lng
                    + ")[name][shop];);out " + 80 + ";";
            URL url = new URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8"));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "CircleOS-AR/1.0");
            if (conn.getResponseCode() != 200) return out;
            String body = read(conn.getInputStream());
            JSONArray els = new JSONObject(body).optJSONArray("elements");
            if (els == null) return out;
            for (int i = 0; i < els.length(); i++) {
                JSONObject el = els.getJSONObject(i);
                JSONObject tags = el.optJSONObject("tags");
                String name = tags != null ? tags.optString("name", "") : "";
                if (name.isEmpty() || !el.has("lat") || !el.has("lon")) continue;
                out.add(new Poi(name, el.getDouble("lat"), el.getDouble("lon")));
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    private String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }

    private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show()); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
