/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Binder surface for the Circle Permission Service. Published under
 * ServiceManager.getService("circle.permission") by
 * com.circleos.server.permission.CirclePermissionService.
 *
 * Read methods require QUERY_PRIVACY; setFakeMode requires MANAGE_PRIVACY.
 *
 * The Circle permission model extends AOSP runtime permissions with a
 * "fake response" mode -- when on, reads of stable identifiers (advertising
 * ID, IMEI, MAC, SSAID) return a synthetic value derived from a per-package
 * salt instead of the real device value. The app sees something that looks
 * real and is stable across calls within the package; the real value never
 * leaves the device.
 */

package za.co.circleos.permission;

interface ICirclePermissionService {

    /**
     * Returns the synthetic identifier for {@code packageName} +
     * {@code idType}. {@code idType} is a canonical string from
     * {@link CirclePermissionService}: "advertising_id", "ssaid",
     * "imei", "mac_wifi", "mac_bluetooth". Stable across calls for
     * the same (package, idType). Returns an empty string if no fake
     * is configured for that package.
     */
    String getFakedIdentifier(in String packageName, in String idType);

    /**
     * Turn fake-response mode on/off for one identifier class on one
     * package. {@code mode} is "real" | "fake" | "deny" -- "deny"
     * causes the framework to return null/empty for that read.
     * Requires MANAGE_PRIVACY.
     */
    void setIdentifierMode(in String packageName, in String idType, in String mode);

    /**
     * Total number of identifier reads that returned a synthetic value
     * across all packages. Visible to the CircleOsSettings dashboard
     * via ICirclePrivacyManagerService.getFakedIdentifierCount() which
     * just forwards here.
     */
    int getFakedIdentifierCount();

    /**
     * Total number of runtime permission requests denied at the gate
     * (system-wide). Forwarded to ICirclePrivacyManagerService.
     * getDeniedPermissionCount().
     */
    int getDeniedPermissionCount();
}
