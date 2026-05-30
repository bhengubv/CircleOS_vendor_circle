/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Binder surface for the Privacy Engine running inside system_server
 * (com.circleos.server.privacy.CirclePrivacyManagerService). Apps
 * holding android.permission.MANAGE_PRIVACY look it up via
 * ServiceManager.getService("circle_privacy") and call these methods
 * to read or change per-package privacy policy.
 *
 * Read methods (getPrivacyScore, getPolicy, getUsageLog) require
 * QUERY_PRIVACY. Write methods (setPolicy) require MANAGE_PRIVACY.
 */

package android.circleos;

import android.circleos.AppPrivacyPolicy;
import android.circleos.PermissionUsageRecord;

interface ICirclePrivacyManager {

    /**
     * Composite privacy score 0..100 for the given package — derived
     * from the active policy, the recent permission-use log, and the
     * Traffic Lobby verdicts. Higher is better.
     */
    int getPrivacyScore(in String packageName);

    /**
     * Current policy for the given package. Returns a freshly-defaulted
     * policy (deny-by-default) if the package has no stored entry yet.
     */
    AppPrivacyPolicy getPolicy(in String packageName);

    /**
     * Persist {@code policy} for {@code packageName}. Subsequent
     * permission checks consult the new policy immediately.
     */
    void setPolicy(in String packageName, in AppPrivacyPolicy policy);

    /**
     * Permission-use records for {@code packageName} since the given
     * unix-millis timestamp. Returned in reverse-chronological order.
     */
    List<PermissionUsageRecord> getUsageLog(in String packageName, long since);
}
