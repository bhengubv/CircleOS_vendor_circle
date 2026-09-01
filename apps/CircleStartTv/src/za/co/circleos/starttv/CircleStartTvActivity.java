/*
 * SPDX-License-Identifier: Apache-2.0
 * Circle Start (TV) - leanback 10-foot home for circle_tv. D-pad navigable row of
 * large app tiles. Programmatic UI, brand palette (black / #2196F3 / white). Shares
 * the Circle core; this is just the TV face's home. One codebase, every device.
 */
package za.co.circleos.starttv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class CircleStartTvActivity extends Activity {
    private static final int ACCENT = 0xFF2196F3;
    private static final int TILE_BG = 0xFF111111;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setBackgroundColor(Color.BLACK);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(48);
        row.setPadding(pad, pad, pad, pad);
        scroller.addView(row);
        for (ResolveInfo ri : launchers()) row.addView(tile(ri));
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
        t.setTextSize(28);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        int s = dp(180);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.rightMargin = dp(24);
        t.setLayoutParams(lp);
        t.setBackgroundColor(TILE_BG);
        t.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? ACCENT : TILE_BG));
        t.setOnClickListener(v -> {
            Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
            if (launch != null) startActivity(launch);
        });
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
