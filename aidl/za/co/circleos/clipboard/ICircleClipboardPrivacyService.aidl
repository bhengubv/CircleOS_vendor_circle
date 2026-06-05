/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Binder surface for the Circle Clipboard Privacy Service. Published
 * under ServiceManager.getService("circle.clipboard_privacy") by
 * com.circleos.server.clipboard.CircleClipboardPrivacyService.
 *
 * Hooks ClipboardManager.OnPrimaryClipChangedListener to keep a tally
 * of clipboard write events and expose "background-read blocking"
 * policy. The policy itself is honoured by AOSP's ClipboardService
 * which already gates background reads on api level / appop; this
 * service surfaces the state to the dashboard.
 */

package za.co.circleos.clipboard;

interface ICircleClipboardPrivacyService {

    /** Lifetime count of primary-clip-changed events observed. */
    int getClipChangeCount();

    /**
     * Unix-millis timestamp of the most recent observed clip change,
     * or 0 if none has been observed since boot.
     */
    long getLastClipChangeMs();

    /**
     * Returns true if background clipboard reads are blocked for
     * {@code packageName} -- the dashboard renders this as a per-app
     * toggle. The default is true on Circle OS (we block by default;
     * AOSP defaults to permissive).
     */
    boolean isBackgroundReadBlocked(in String packageName);

    /**
     * Toggle background-read blocking for {@code packageName}.
     * Requires MANAGE_PRIVACY.
     */
    void setBackgroundReadBlocked(in String packageName, boolean blocked);
}
