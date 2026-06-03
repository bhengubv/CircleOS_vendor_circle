/*
 * Server-side binder of the Privacy Engine. Published under
 * ServiceManager.getService("circle.privacy") by
 * com.circleos.server.privacy.CirclePrivacyManagerService.
 *
 * Distinct from the per-app android.circleos.ICirclePrivacyManager
 * (which exposes policy CRUD scoped to one package). This service
 * surface reports system-wide counters used by the vendor
 * CircleOsSettings dashboard:
 *
 *   svc.getDeniedPermissionCount()
 *   svc.getFakedIdentifierCount()
 *   svc.getNetworkGrantCount()
 *
 * Callers must hold za.co.circleos.permission.QUERY_PRIVACY.
 */

package android.circleos.privacy;

interface ICirclePrivacyManagerService {

    /** All-time tally of permission requests denied at the runtime gate. */
    int getDeniedPermissionCount();

    /**
     * All-time tally of identifier reads (advertising id, IMEI,
     * SSAID, …) where the Fake Response Provider returned synthetic
     * data instead of the real value.
     */
    int getFakedIdentifierCount();

    /**
     * Number of packages currently holding a network grant
     * (INTERNET permission allowed).
     */
    int getNetworkGrantCount();
}
