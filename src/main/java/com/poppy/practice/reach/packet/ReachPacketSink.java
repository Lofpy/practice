package com.poppy.practice.reach.packet;

import java.util.UUID;

/**
 * Thread-safe destination for protocol-neutral ReachGuard packet data.
 *
 * <p>No Bukkit, CraftBukkit, NMS, Netty, mutable packet, or mutable collection
 * is exposed through this interface. Implementations must expect callbacks for
 * different players to run concurrently, while callbacks for one player retain
 * that player's channel order.</p>
 */
public interface ReachPacketSink {
    /** Sentinel returned by target callbacks when no transaction marker is needed. */
    int NO_TRANSACTION_SYNC = Integer.MIN_VALUE;
    int REQUEST_TRANSACTION_SYNC = Integer.MIN_VALUE + 1;
    /** Dirty target state exists, but its configured server-tick interval is not due. */
    int DEFER_TRANSACTION_SYNC = Integer.MIN_VALUE + 2;

    /** Confirms that a functional bridge handler is attached to the channel. */
    void onBridgeInstalled(UUID playerId, int playerEntityId, int protocol);

    void onMovement(UUID playerId, int playerEntityId, int protocol,
                    boolean hasPosition, double x, double y, double z,
                    boolean hasRotation, float yaw, float pitch,
                    boolean onGround, long receiveNanoTime);

    void onSneaking(UUID playerId, int playerEntityId, int protocol,
                    boolean sneaking, long receiveNanoTime);

    /**
     * Validates an ATTACK before it reaches the server packet handler.
     *
     * @return {@code true} when the packet must be dropped
     */
    boolean onAttack(UUID playerId, int playerEntityId, int protocol,
                     int targetEntityId, long receiveNanoTime);

    /**
     * Handles a client transaction response.
     *
     * @return {@code true} only for a ReachGuard-owned marker that must not be
     * forwarded to the vanilla inventory transaction handler
     */
    boolean onTransactionResponse(UUID playerId, int playerEntityId, int protocol,
                                  int windowId, short actionId,
                                  long receiveNanoTime);

    /**
     * Reserves one marker after all target updates queued in this event-loop
     * turn. Returns a negative signed-short action ID, {@link
     * #NO_TRANSACTION_SYNC}, or {@link #DEFER_TRANSACTION_SYNC}. A deferred
     * result must be retried later on the same channel event loop.
     */
    int onTransactionSyncRequested(UUID viewerId, int viewerEntityId,
                                   int protocol, long requestNanoTime);

    /** Activates the reserved marker only after its outbound write succeeds. */
    void onTransactionSyncSent(UUID viewerId, int viewerEntityId, int protocol,
                               short actionId, long sentNanoTime);

    /**
     * Records a successfully sent player spawn and optionally allocates a sync
     * action ID. A returned action ID must be a negative signed-short value.
     */
    int onNamedEntitySpawnSent(UUID viewerId, int viewerEntityId, int protocol,
                               int targetEntityId, UUID targetUuid,
                               double x, double y, double z,
                               long sentNanoTime);

    /** Records a viewer-visible entity that is outside player-v-player scope. */
    void onNonPlayerEntitySpawnSent(UUID viewerId, int viewerEntityId,
                                    int protocol, int targetEntityId,
                                    long sentNanoTime);

    /**
     * Records a successfully sent relative player movement and optionally
     * allocates a sync action ID. Unknown/non-player IDs should return
     * {@link #NO_TRANSACTION_SYNC}.
     */
    int onRelativeEntityMoveSent(UUID viewerId, int viewerEntityId, int protocol,
                                 int targetEntityId,
                                 double deltaX, double deltaY, double deltaZ,
                                 boolean onGround, long sentNanoTime);

    /**
     * Records a successfully sent player teleport and optionally allocates a
     * sync action ID. Unknown/non-player IDs should return
     * {@link #NO_TRANSACTION_SYNC}.
     */
    int onEntityTeleportSent(UUID viewerId, int viewerEntityId, int protocol,
                             int targetEntityId, double x, double y, double z,
                             boolean onGround, long sentNanoTime);

    void onEntityDestroyedSent(UUID viewerId, int viewerEntityId, int protocol,
                               int targetEntityId, long sentNanoTime);

    /** Called after a server position correction or respawn packet is sent. */
    void onViewerResetSent(UUID viewerId, int viewerEntityId, int protocol,
                           String reason, long sentNanoTime);

    /** Allows a pending marker to be discarded if its outbound write failed. */
    void onTransactionSyncWriteFailed(UUID viewerId, int viewerEntityId,
                                      int protocol, int windowId,
                                      short actionId, long failureNanoTime);

    /**
     * Receives a rate-limitable error description. Packet objects and Throwable
     * instances are deliberately not exposed across this boundary.
     */
    void onPacketError(UUID playerId, int playerEntityId, int protocol,
                       String stage, String errorType, String message);
}
