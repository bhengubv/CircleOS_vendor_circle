/*
 * Binder surface for the Mesh Service. Published under
 * ServiceManager.getService("circle_mesh") by
 * com.circleos.server.mesh.CircleMeshService.
 *
 * Read-only methods (status) require QUERY_MESH; send methods require
 * CIRCLE_MESH_SEND. This v0 surface covers the Settings UI status pane
 * only — message send/receive is added in a follow-up alongside the
 * MeshMessage / PeerInfo Parcelables.
 */

package za.co.circleos.mesh;

interface ICircleMeshService {

    /** True if the mesh transports are up and at least one is healthy. */
    boolean isRunning();

    /** Currently-reachable peer count across all transports. */
    int getPeerCount();

    /** Local Ed25519 fingerprint, hex (64 chars). */
    String getDeviceId();
}
