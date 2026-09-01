/*
 * SPDX-License-Identifier: Apache-2.0
 * Circle Start (Auto) - driving-safe car home for circle_auto. A few very large,
 * high-contrast tiles (big touch targets, minimal cognitive load). Programmatic UI,
 * brand palette. Shares the Circle core; this is the car face's home.
 */
package za.co.circleos.startauto;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import java.util.List;

public class CircleStartAutoActivity extends Activity {
    private static final int ACCENT = 0xFF2196F3;
    private static final int TILE_BG = 0xFF161616;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        GridLayout grid = new GridLayout(this);
        grid.setBackgroundColor(Color.BLACK);
        grid.setColumnCount(2);
        int pad = dp(24);
        grid.setPadding(pad, pad, pad, pad);
        for (ResolveInfo ri : launchers()) grid.addView(tile(ri));
        setContentView(grid);
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
        t.setTextSize(34);
        t.setGravity(Gravity.CENTER);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(320);
        lp.height = dp(200);
        lp.setMargins(dp(12), dp(12), dp(12), dp(12));
        t.setLayoutParams(lp);
        t.setBackgroundColor(TILE_BG);
        t.setOnClickListener(v -> {
            Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
            if (launch != null) startActivity(launch);
        });
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
