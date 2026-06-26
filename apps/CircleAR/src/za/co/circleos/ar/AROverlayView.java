/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Draws POI labels over the camera preview. Each place is placed horizontally by
 * the difference between its compass bearing and the device heading; labels are
 * staggered vertically so they don't overlap. Nearest draws on top.
 */
package za.co.circleos.ar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.location.Location;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class AROverlayView extends View {

    private static final float FOV = 60f; // approx horizontal field of view

    private float mAzimuth = 0f;
    private Location mMe;
    private final List<Poi> mPois = new ArrayList<>();

    private final Paint mBox = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mName = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDist = new Paint(Paint.ANTI_ALIAS_FLAG);

    AROverlayView(Context c) {
        super(c);
        mBox.setColor(0xCC161616);
        mName.setColor(0xFFFFFFFF);
        mName.setTextSize(sp(14));
        mName.setFakeBoldText(true);
        mDist.setColor(0xFF2196F3);
        mDist.setTextSize(sp(12));
    }

    void setAzimuth(float deg) {
        mAzimuth = deg;
        invalidate();
    }

    void setPois(List<Poi> pois, Location me) {
        mMe = me;
        mPois.clear();
        if (pois != null) mPois.addAll(pois);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mMe == null || mPois.isEmpty()) return;
        int w = getWidth(), h = getHeight();

        List<Poi> list = new ArrayList<>(mPois);
        for (Poi p : list) {
            p.dist = mMe.distanceTo(p.loc);
            p.bearing = mMe.bearingTo(p.loc);
        }
        Collections.sort(list, (a, b) -> Float.compare(b.dist, a.dist)); // far first

        int shown = 0;
        for (Poi p : list) {
            float diff = wrap(p.bearing - mAzimuth);
            if (Math.abs(diff) > FOV / 2f) continue;
            float x = w / 2f + (diff / (FOV / 2f)) * (w / 2f);
            float y = h * 0.40f + (shown % 4) * sp(58);
            drawLabel(canvas, x, y, p);
            if (++shown >= 14) break;
        }
    }

    private void drawLabel(Canvas c, float cx, float y, Poi p) {
        String dist = fmtDist(p.dist);
        float tw = Math.max(mName.measureText(p.name), mDist.measureText(dist));
        float pad = sp(10);
        RectF r = new RectF(cx - tw / 2 - pad, y - sp(18), cx + tw / 2 + pad, y + sp(22));
        c.drawRoundRect(r, sp(8), sp(8), mBox);
        c.drawText(p.name, cx - tw / 2, y, mName);
        c.drawText(dist, cx - tw / 2, y + sp(16), mDist);
    }

    private float wrap(float deg) {
        while (deg > 180) deg -= 360;
        while (deg < -180) deg += 360;
        return deg;
    }

    private String fmtDist(float m) {
        return m < 1000 ? Math.round(m) + " m"
                : String.format(Locale.US, "%.1f km", m / 1000f);
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
