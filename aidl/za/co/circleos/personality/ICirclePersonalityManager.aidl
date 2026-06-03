/*
 * Binder surface for the Personality Engine.
 * Published under ServiceManager.getService("circle.personality").
 * Permission: com.circleos.permission.ACCESS_PERSONALITY.
 *
 * Covers mode lookup + activation, custom-mode CRUD, community sharing
 * (URL + clipboard JSON), managed-mode PIN locking, per-mode app
 * visibility, and the learning-suggestion feedback loop.
 */
package za.co.circleos.personality;

import za.co.circleos.personality.IBundleCallback;
import za.co.circleos.personality.LearningSuggestion;
import za.co.circleos.personality.ManagedModePolicy;
import za.co.circleos.personality.ModeBundle;
import za.co.circleos.personality.PersonalityMode;
import za.co.circleos.personality.SwitchResult;

interface ICirclePersonalityManager {

    // ----- discovery + state --------------------------------------------

    /** id of the currently-active mode, or null if none active. */
    String getActiveModeId();

    /** Full PersonalityMode for the active mode (or null). */
    PersonalityMode getActiveMode();

    /** All modes installed on this device (built-in + custom). */
    List<PersonalityMode> getAvailableModes();

    /** True if the mode's bundle is downloaded (true for built-ins, may be false for tier 2/3 community modes). */
    boolean isBundleDownloaded(in String modeId);

    /** True if the active mode is currently PIN-locked. */
    boolean isManagedModeActive();

    // ----- activation ---------------------------------------------------

    /**
     * Switch to {@code modeId}. If the mode's bundle isn't downloaded
     * yet, the SwitchResult will have {@code requiresBundle=true} and
     * the caller should download it before retrying.
     */
    SwitchResult activateMode(in String modeId);

    /** Unlock a managed (PIN-locked) mode and activate it. */
    SwitchResult activateManagedMode(in String modeId, in String pin);

    // ----- bundle management --------------------------------------------

    ModeBundle getBundleInfo(in String modeId);
    void downloadBundle(in String modeId, in IBundleCallback callback);
    void cancelBundleDownload(in String modeId);

    // ----- custom modes (CRUD) ------------------------------------------

    /** Create a new user-defined mode. {@code mode.id} is assigned by the service if blank. */
    SwitchResult createCustomMode(in PersonalityMode mode);
    SwitchResult updateMode(in PersonalityMode mode);
    SwitchResult deleteMode(in String modeId);

    // ----- import / export ----------------------------------------------

    /** Serialise all custom modes to a JSON string (clipboard sharing). */
    String exportModesJson();
    /** Restore custom modes from a JSON blob. */
    SwitchResult importModesJson(in String json);

    /** Returns a sharable URL hosted at the community endpoint for one mode. */
    String getModeShareUrl(in String modeId);
    /** Fetch a mode from a community URL and add it to the local library. */
    SwitchResult importModeFromUrl(in String url);

    /** Fetch the community-curated mode list. */
    List<PersonalityMode> fetchCommunityModes();

    // ----- learning suggestions -----------------------------------------

    List<LearningSuggestion> getLearningSuggestions();
    void acceptLearningSuggestion(in String id);
    void dismissLearningSuggestion(in String id);

    // ----- managed-mode policy ------------------------------------------

    ManagedModePolicy getManagedModePolicy(in String modeId);
    SwitchResult setManagedModePolicy(in ManagedModePolicy policy);
    void clearManagedModePolicy(in String modeId);

    // ----- per-mode app visibility --------------------------------------

    List<String> getModeHiddenApps(in String modeId);
    void setModeHiddenApps(in String modeId, in List<String> packageNames);
}
