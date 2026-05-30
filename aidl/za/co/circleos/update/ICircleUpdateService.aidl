/*
 * Binder surface for the Update Service. Published under
 * ServiceManager.getService("circle_update") by
 * com.circleos.server.update.CircleUpdateService.
 *
 * Read methods require QUERY_OTA; checkNow + applyUpdate require
 * TRIGGER_OTA; setChannel requires MANAGE_OTA (signature|privileged).
 */

package za.co.circleos.update;

interface ICircleUpdateService {

    /**
     * OTA state machine. Concrete int values defined in the service
     * impl; the Settings pane treats them opaquely as "tag the state
     * row with this label" and asks the service for a localised name
     * if needed.
     */
    int getState();

    /**
     * Semver of the latest build the origin advertised. Empty when no
     * successful check has happened yet.
     */
    String getAvailableVersion();

    /** Bytes-fetched/total expressed as 0..100. -1 when not downloading. */
    int getDownloadProgress();

    /** Unix-millis of the last successful availability check, or 0. */
    long getLastCheckTime();

    /**
     * Update channel — "stable" | "beta" | "dev" | "internal". Empty
     * before enrolment.
     */
    String getChannel();

    /**
     * Switch channel. Triggers an immediate check against the new
     * channel's manifest. Requires MANAGE_OTA.
     */
    void setChannel(in String channel);

    /** Ask the origin "is there anything newer than current?". */
    void checkNow();

    /**
     * Stage the most recently-verified build into the inactive A/B
     * slot and reboot. Requires fresh lock-screen auth.
     */
    void applyUpdate();
}
