/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Binder surface for Circle Notification Privacy. Published under
 * ServiceManager.getService("circle.notification_privacy") by
 * com.circleos.server.notification.CircleNotificationPrivacyService.
 *
 * Lists the active NotificationListenerService bindings and lets the
 * user revoke individual ones. NotificationListenerService is the most
 * powerful "read every notification" surface on Android; the dashboard
 * surfaces every binding so the user knows which apps can read theirs.
 */

package za.co.circleos.notification;

interface ICircleNotificationPrivacyService {

    /**
     * Semicolon-separated list of package names currently holding an
     * enabled NotificationListenerService binding. Empty when none.
     */
    String getEnabledListenerPackages();

    /**
     * Revoke {@code packageName}'s NotificationListenerService access.
     * Equivalent to the user toggling it off in Settings -> Apps ->
     * Special access -> Notification access. Requires MANAGE_PRIVACY.
     */
    void revokeListener(in String packageName);

    /**
     * Lifetime count of revocations triggered through this service --
     * a proxy metric for "how many times has the user pruned their
     * notification surveillance footprint?". Surfaced on the dashboard.
     */
    int getRevocationCount();
}
