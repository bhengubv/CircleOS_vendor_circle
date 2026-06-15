package za.co.circleos.circlemaps;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ServiceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import za.co.circleos.mesh.ICircleMeshService;

public final class CircleMapsActivity extends Activity implements LocationListener {

    private static final String TAG = "CircleMaps";
    private static final String MAPS_BASE = "https://maps.dataacuity.co.za";

    private static final int BG     = 0xFF0A0A0A;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT1  = 0xFFF5F0EB;
    private static final int TEXT2  = 0x99F5F0EB;

    private LocationManager mLocMgr;
    private Location mLastLoc;
    private ICircleMeshService mMesh;
    private WebView mMapView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        mLocMgr = getSystemService(LocationManager.class);
        try {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder != null) mMesh = ICircleMeshService.Stub.asInterface(binder);
        } catch (Throwable ignored) {}

        buildUI();
        startLocation();
    }

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        // Search bar overlay
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setBackgroundColor(0xCC1A1A2E);
        searchBar.setPadding(dp(16), dp(48), dp(16), dp(12));
        EditText input = new EditText(this);
        input.setHint("Search places..."); input.setTextColor(TEXT1);
        input.setHintTextColor(TEXT2); input.setBackgroundColor(0xFF1A1A2E);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchBar.addView(input);

        // DataAcuity map WebView
        mMapView = new WebView(this);
        WebSettings ws = mMapView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setGeolocationEnabled(true);
        mMapView.setWebViewClient(new WebViewClient());
        mMapView.setBackgroundColor(BG);

        root.addView(mMapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.gravity = Gravity.TOP;
        root.addView(searchBar, searchLp);

        setContentView(root);
        loadMap();
    }

    private void loadMap() {
        String url = MAPS_BASE + "/embed";
        if (mLastLoc != null) {
            url += "?lat=" + mLastLoc.getLatitude() + "&lng=" + mLastLoc.getLongitude() + "&z=14";
        }
        mMapView.loadUrl(url);
    }

    private void startLocation() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                mLocMgr.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 10_000, 10f, this);
                mLastLoc = mLocMgr.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
            } else {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        } catch (Exception e) { Log.w(TAG, "location failed", e); }
    }

    @Override
    public void onLocationChanged(Location loc) {
        mLastLoc = loc;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
