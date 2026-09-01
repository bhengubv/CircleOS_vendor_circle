/*
 * SPDX-License-Identifier: Apache-2.0
 * Circle Start (Watch) - round, glanceable home for circle_wear. Clock + a short
 * vertical list of app tiles (Panik SOS ships in core). Programmatic UI, brand
 * palette. Shares the Circle core; this is the watch face's home.
 */
package za.co.circleos.startwatch;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Date;
import java.util.List;

public class CircleStartWatchActivity extends Activity {
    private static final int TILE_BG = 0xFF111111;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView clock;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            clock.setText(DateFormat.format("HH:mm", new Date()));
            h.postDelayed(this, 10000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(Color.BLACK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(16);
        col.setPadding(pad, dp(28), pad, dp(28));
        scroller.addView(col);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(30);
        clock.setGravity(Gravity.CENTER);
        col.addView(clock);
        h.post(ticker);
        for (ResolveInfo ri : launchers()) col.addView(tile(ri));
        setContentView(scroller);
    }

    private List<ResolveInfo> launchers() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        return getPackageManager().queryIntentActivities(i, 0);
    }

    private View tile(final ResolveInfo ri) {
        final PackageManager pm = getPackageManager();
        TextView t = new TextView(this);
        t.setText(ri.loadLabel(pm));
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        t.setBackgroundColor(TILE_BG);
        t.setOnClickListener(v -> {
            Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
            if (launch != null) startActivity(launch);
        });
        return t;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(ticker);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
