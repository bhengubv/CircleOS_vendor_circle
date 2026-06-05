/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Binder surface for the Circle Camera Privacy Service. Published under
 * ServiceManager.getService("circle.camera_privacy") by
 * com.circleos.server.camera.CircleCameraPrivacyService.
 *
 * Surfaces the "is any camera in use right now?" signal that drives
 * the camera-use indicator in the status bar -- the same UX as iOS's
 * green dot. Backed by CameraManager.AvailabilityCallback so the
 * answer is authoritative against the camera HAL, not just a
 * permission/usage heuristic.
 */

package za.co.circleos.camera;

interface ICircleCameraPrivacyService {

    /** True iff any camera is currently open by some process. */
    boolean isAnyCameraInUse();

    /**
     * Most recent package observed using a camera since {@code sinceMs},
     * or empty when none. Useful for "which app just turned on the
     * camera?" surfaces. Best-effort -- CameraService doesn't expose
     * the caller package, we map UID -> primary package.
     */
    String getMostRecentCameraUser(long sinceMs);

    /**
     * Lifetime count of camera "in use" -> "available" transitions.
     * Forwarded to the Privacy Dashboard tile.
     */
    int getCameraSessionCount();
}
