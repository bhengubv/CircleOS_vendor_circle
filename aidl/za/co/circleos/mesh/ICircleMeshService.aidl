/*
 * Binder surface for the Mesh Service. Published under
 * ServiceManager.getService("circle.mesh") by the mesh system service.
 *
 * Read-only status methods require android.permission.CIRCLE_MESH_QUERY;
 * sendMessage requires android.permission.CIRCLE_MESH_SEND.
 *
 * v0.1 — status + opaque-payload sendMessage. Richer MeshMessage /
 * PeerInfo / subscriber callbacks land in a follow-up; today's two
 * consumers (Butler.MeshServiceConnection + CircleMessages.ConversationActivity)
 * already invoke this exact shape:
 *
 *   sent = mesh.sendMessage(peerId, text.getBytes("UTF-8"), TYPE_MSG_TEXT);
 */

package za.co.circleos.mesh;

interface ICircleMeshService {

    /** True if the mesh transports are up and at least one is healthy. */
    boolean isRunning();

    /** Currently-reachable peer count across all transports. */
    int getPeerCount();

    /**
     * Local rotating device id. 16-char hex (so 8 bytes / 64 bits) —
     * the size CircleMessages comments document at the call site.
     * Rotates per the mesh privacy spec; do NOT cache it across boots.
     */
    String getDeviceId();

    /**
     * Send {@code payload} to {@code recipientDeviceId} with the given
     * {@code msgType}. msgType is opaque to the mesh layer — the spec
     * reserves the high nibble for service classes (0x1_ = direct
     * messaging, 0x2_ = file transfer, …). Butler defines
     * TYPE_MSG_TEXT = 0x10 locally.
     *
     * @return true if the message was dispatched or queued for relay;
     *         false if the mesh layer rejected it (peer unknown,
     *         payload over MTU, no transport up).
     */
    boolean sendMessage(in String recipientDeviceId, in byte[] payload, int msgType);
}
