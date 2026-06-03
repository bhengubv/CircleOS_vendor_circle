/*
 * Progress + completion callback for ICirclePersonalityManager.downloadBundle.
 */
package za.co.circleos.personality;

oneway interface IBundleCallback {
    /** {@code pct} 0..100. */
    void onProgress(String modeId, int pct);

    /** Terminal signal — exactly one of (success=true) or (success=false + reason). */
    void onComplete(String modeId, boolean success, String errorMessage);
}
