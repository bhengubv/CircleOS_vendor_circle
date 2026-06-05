/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Binder surface for the Circle Analytics Service. Published under
 * ServiceManager.getService("circle.analytics") by
 * com.circleos.server.analytics.CircleAnalyticsService.
 *
 * The Privacy Dashboard in CircleSettings calls into this service to
 * answer "what did each app do today?" without any data ever leaving
 * the device. Backed by AOSP's UsageStatsManager for foreground time
 * and an on-device log table for permission events. No telemetry is
 * sent off-device.
 */

package za.co.circleos.analytics;

interface ICircleAnalyticsService {

    /**
     * Returns total foreground time (ms) for {@code packageName} since
     * the given unix-millis cutoff. Source: AOSP UsageStatsManager.
     */
    long getForegroundTimeMs(in String packageName, long sinceMs);

    /**
     * Returns the count of permission events (grants + denies + fakes
     * combined) attributed to {@code packageName} since the cutoff.
     */
    int getPermissionEventCount(in String packageName, long sinceMs);

    /**
     * Top N packages by foreground time over the last 24 h. Returned
     * as semicolon-separated "package=ms" pairs to keep the AIDL
     * surface flat -- the Settings UI parses it. Empty if usage stats
     * permission isn't granted.
     */
    String getTopForegroundPackages(int limit);
}
