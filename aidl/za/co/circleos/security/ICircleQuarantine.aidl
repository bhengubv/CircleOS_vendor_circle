/*
 * Binder surface for the Quarantine Service. Published under
 * ServiceManager.getService("circle.quarantine") by the platform's
 * security system service.
 *
 * Caller requires android.permission.CIRCLE_SECURITY_QUARANTINE
 * (platform-signature only). The TrafficLobby VPN service invokes
 * quarantineFile() when its spyware-behaviour detector decides an
 * installed app is exfiltrating data; the quarantine service moves
 * the apk out of the active install location and records the reason
 * in the security ledger so the user gets a follow-up notification.
 *
 * v0.1 — single quarantineFile() call. Restore / list / clear land
 * in a follow-up; today's one consumer (TrafficLobbyVpnService line
 * 356) only needs:
 *
 *   q.quarantineFile(info.sourceDir, pkg + ": spyware upload detected");
 */

package za.co.circleos.security;

interface ICircleQuarantine {

    /**
     * Quarantine the file at {@code sourceDir} (typically an installed
     * apk's path obtained from ApplicationInfo.sourceDir) and record
     * {@code reason} in the security ledger.
     *
     * The implementation MUST:
     *   - move the file out of any executable location;
     *   - persist the original path + reason + timestamp so the user
     *     can review or restore from CircleSettings → Security;
     *   - tolerate being called twice for the same path (idempotent
     *     no-op the second time).
     *
     * @param sourceDir absolute path to the file being quarantined.
     * @param reason short user-facing description; surfaces in the
     *               security-ledger entry and any follow-up
     *               notification. Empty allowed but discouraged.
     */
    void quarantineFile(in String sourceDir, in String reason);
}
