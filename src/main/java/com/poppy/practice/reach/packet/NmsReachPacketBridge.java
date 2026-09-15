package com.poppy.practice.reach.packet;

import com.poppy.practice.protocol.ClientProtocolResolver;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.v1_8_R3.NetworkManager;
import net.minecraft.server.v1_8_R3.PacketPlayInEntityAction;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;
import net.minecraft.server.v1_8_R3.PacketPlayInTransaction;
import net.minecraft.server.v1_8_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_8_R3.PacketPlayOutPosition;
import net.minecraft.server.v1_8_R3.PacketPlayOutRespawn;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityExperienceOrb;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityPainting;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityWeather;
import net.minecraft.server.v1_8_R3.PacketPlayOutTransaction;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Direct v1_8_R3 packet bridge for WindSpigot.
 *
 * <p>The handler is installed after ProtocolSupport/ViaVersion translation and immediately
 * before {@code packet_handler}. Consequently both protocol 5 (1.7.10) and
 * protocol 47 (1.8.x) arrive here as v1_8_R3 NMS packets, and returning without
 * firing an inbound ATTACK prevents NetworkManager/PlayerConnection from ever
 * processing it.</p>
 */
public final class NmsReachPacketBridge implements PacketBridge {
    public static final String HANDLER_NAME = "poppy_reach_guard";
    private static final String VANILLA_DECODER = "decoder";
    private static final String VANILLA_PACKET_HANDLER = "packet_handler";
    private static final long SYNC_RETRY_DELAY_MILLIS = 50L;

    private final JavaPlugin plugin;
    private final ReachPacketSink sink;
    private final Map<UUID, Binding> bindings =
            new ConcurrentHashMap<UUID, Binding>();
    private final PacketAccess packetAccess;
    private final String packetAccessErrorType;
    private final String packetAccessErrorMessage;

    public NmsReachPacketBridge(JavaPlugin plugin, ReachPacketSink sink) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        if (sink == null) throw new IllegalArgumentException("sink");
        this.plugin = plugin;
        this.sink = sink;

        PacketAccess resolved = null;
        String errorType = null;
        String errorMessage = null;
        try {
            resolved = new PacketAccess();
        } catch (ReflectiveOperationException exception) {
            errorType = exception.getClass().getName();
            errorMessage = safeMessage(exception);
        } catch (RuntimeException exception) {
            errorType = exception.getClass().getName();
            errorMessage = safeMessage(exception);
        }
        packetAccess = resolved;
        packetAccessErrorType = errorType;
        packetAccessErrorMessage = errorMessage;
    }

    @Override
    public void register() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            install(player);
        }
    }

    @Override
    public void unregister() {
        for (Binding binding : new ArrayList<Binding>(bindings.values())) {
            removeBinding(binding);
        }
        bindings.clear();
    }

    @Override
    public void install(Player player) {
        if (player == null || !player.isOnline()) return;

        NetworkManager networkManager = ((CraftPlayer) player).getHandle()
                .playerConnection.networkManager;
        final Channel channel = networkManager.channel;
        if (channel == null || !channel.isActive()) return;

        final UUID playerId = player.getUniqueId();
        final int playerEntityId = player.getEntityId();
        final int protocol = protocolVersion(player);
        final Handler handler = new Handler(playerId, playerEntityId, protocol);
        final Binding binding = new Binding(playerId, channel, handler);
        Binding previous = bindings.put(playerId, binding);
        if (previous != null) removeBinding(previous);

        if (packetAccess == null) {
            safePacketError(playerId, playerEntityId, protocol, "PACKET_ACCESS_INIT",
                    packetAccessErrorType, packetAccessErrorMessage);
        }

        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!channel.isActive() || bindings.get(playerId) != binding) return;
                    ChannelPipeline pipeline = channel.pipeline();
                    installNormalizedPacketHandler(pipeline, handler);
                    if (packetAccess != null) {
                        sink.onBridgeInstalled(playerId, playerEntityId, protocol);
                    }
                } catch (RuntimeException exception) {
                    bindings.remove(playerId, binding);
                    safePacketError(playerId, playerEntityId, protocol,
                            "INSTALL", exception);
                }
            }
        });
    }

    /**
     * Installs after the Minecraft/ProtocolSupport decoder and before the NMS
     * packet handler. A malformed or not-yet-built pipeline is rejected so the
     * caller reports bridge failure and ReachGuard remains fail-open.
     */
    static void installNormalizedPacketHandler(ChannelPipeline pipeline,
                                               ChannelDuplexHandler handler) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline");
        if (handler == null) throw new IllegalArgumentException("handler");

        List<String> names = pipeline.names();
        int decoderIndex = names.indexOf(VANILLA_DECODER);
        int packetHandlerIndex = names.indexOf(VANILLA_PACKET_HANDLER);
        if (decoderIndex < 0) {
            throw new IllegalStateException("decoder is not present");
        }
        if (packetHandlerIndex < 0) {
            throw new IllegalStateException("packet_handler is not present");
        }
        if (decoderIndex >= packetHandlerIndex) {
            throw new IllegalStateException(
                    "decoder must precede packet_handler");
        }

        if (pipeline.get(HANDLER_NAME) != null) {
            pipeline.remove(HANDLER_NAME);
        }
        pipeline.addBefore(VANILLA_PACKET_HANDLER, HANDLER_NAME, handler);
    }

    @Override
    public void remove(Player player) {
        if (player == null) return;
        Binding binding = bindings.remove(player.getUniqueId());
        if (binding != null) removeBinding(binding);
    }

    @Override
    public void resyncPlayer(Player viewer, Player target) {
        if (!(viewer instanceof CraftPlayer) || !(target instanceof CraftPlayer)
                || !viewer.isOnline() || !target.isOnline()) return;
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(
                new PacketPlayOutEntityTeleport(
                        ((CraftPlayer) target).getHandle()));
    }

    @Override
    public boolean isInstalled(UUID playerId) {
        return playerId != null && bindings.containsKey(playerId);
    }

    private void removeBinding(final Binding binding) {
        if (binding == null || binding.channel == null) return;
        binding.channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelPipeline pipeline = binding.channel.pipeline();
                    if (pipeline.context(binding.handler) != null) {
                        pipeline.remove(binding.handler);
                    }
                } catch (RuntimeException exception) {
                    safePacketError(binding.playerId, binding.handler.playerEntityId,
                            binding.handler.protocol, "REMOVE", exception);
                }
            }
        });
    }

    /** Resolves the original client protocol from the main-thread install caller. */
    private int protocolVersion(Player player) {
        try {
            return ClientProtocolResolver.resolve(player,
                    plugin.getServer().getPluginManager());
        } catch (ReflectiveOperationException exception) {
            safePacketError(player.getUniqueId(), player.getEntityId(), -1,
                    "PROTOCOL_VERSION", exception);
            return -1;
        } catch (RuntimeException exception) {
            safePacketError(player.getUniqueId(), player.getEntityId(), -1,
                    "PROTOCOL_VERSION", exception);
            return -1;
        } catch (LinkageError error) {
            safePacketError(player.getUniqueId(), player.getEntityId(), -1,
                    "PROTOCOL_VERSION", error);
            return -1;
        }
    }

    private final class Handler extends ChannelDuplexHandler {
        private final UUID playerId;
        private final int playerEntityId;
        private final int protocol;
        private boolean syncFlushQueued;

        private Handler(UUID playerId, int playerEntityId, int protocol) {
            this.playerId = playerId;
            this.playerEntityId = playerEntityId;
            this.protocol = protocol;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message)
                throws Exception {
            // Unknown and future protocols are deliberately invisible to the
            // enforcement sink. In particular, their ATTACK and transaction
            // packets always continue to the vanilla packet handler.
            if (!ReachProtocolPolicy.supports(protocol)) {
                context.fireChannelRead(message);
                return;
            }
            final long now = System.nanoTime();
            try {
                if (message instanceof PacketPlayInFlying) {
                    PacketPlayInFlying flying = (PacketPlayInFlying) message;
                    sink.onMovement(playerId, playerEntityId, protocol,
                            flying.g(), flying.a(), flying.b(), flying.c(),
                            flying.h(), flying.d(), flying.e(), flying.f(), now);
                } else if (message instanceof PacketPlayInEntityAction) {
                    PacketPlayInEntityAction.EnumPlayerAction action =
                            ((PacketPlayInEntityAction) message).b();
                    if (action == PacketPlayInEntityAction.EnumPlayerAction.START_SNEAKING) {
                        sink.onSneaking(playerId, playerEntityId, protocol, true, now);
                    } else if (action
                            == PacketPlayInEntityAction.EnumPlayerAction.STOP_SNEAKING) {
                        sink.onSneaking(playerId, playerEntityId, protocol, false, now);
                    }
                } else if (message instanceof PacketPlayInUseEntity) {
                    PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) message;
                    if (useEntity.a()
                            == PacketPlayInUseEntity.EnumEntityUseAction.ATTACK) {
                        if (packetAccess == null) {
                            context.fireChannelRead(message);
                            return;
                        }
                        int targetEntityId = packetAccess.useEntityId.getInt(useEntity);
                        if (sink.onAttack(playerId, playerEntityId, protocol,
                                targetEntityId, now)) {
                            return;
                        }
                    }
                } else if (message instanceof PacketPlayInTransaction) {
                    PacketPlayInTransaction transaction =
                            (PacketPlayInTransaction) message;
                    int windowId = transaction.a();
                    short actionId = transaction.b();
                    if (ReachProtocolPolicy.mayBeReachTransaction(protocol,
                            windowId, actionId)
                            && sink.onTransactionResponse(playerId, playerEntityId,
                            protocol, windowId, actionId, now)) {
                        return;
                    }
                }
            } catch (Exception exception) {
                safePacketError(playerId, playerEntityId, protocol,
                        inboundStage(message), exception);
                // Fail open: the original packet continues to NetworkManager.
            }
            context.fireChannelRead(message);
        }

        @Override
        public void write(final ChannelHandlerContext context, Object message,
                          ChannelPromise promise) throws Exception {
            if (packetAccess == null || !ReachProtocolPolicy.supports(protocol)) {
                context.write(message, promise);
                return;
            }
            try {
                if (message instanceof PacketPlayOutNamedEntitySpawn) {
                    final PacketPlayOutNamedEntitySpawn spawn =
                            (PacketPlayOutNamedEntitySpawn) message;
                    final int targetId = packetAccess.namedSpawnEntityId.getInt(spawn);
                    final UUID targetUuid = (UUID) packetAccess.namedSpawnUuid.get(spawn);
                    final double x = packetAccess.namedSpawnX.getInt(spawn) / 32.0D;
                    final double y = packetAccess.namedSpawnY.getInt(spawn) / 32.0D;
                    final double z = packetAccess.namedSpawnZ.getInt(spawn) / 32.0D;
                    writeObserved(context, message, promise, "NAMED_ENTITY_SPAWN",
                            true, new SentCallback() {
                                @Override
                                public int sent(long sentNanoTime) {
                                    return sink.onNamedEntitySpawnSent(playerId,
                                            playerEntityId, protocol, targetId, targetUuid,
                                            x, y, z, sentNanoTime);
                                }
                            });
                    return;
                }

                if (message instanceof PacketPlayOutSpawnEntity
                        || message instanceof PacketPlayOutSpawnEntityLiving
                        || message instanceof PacketPlayOutSpawnEntityExperienceOrb
                        || message instanceof PacketPlayOutSpawnEntityPainting
                        || message instanceof PacketPlayOutSpawnEntityWeather) {
                    final int targetId = packetAccess.nonPlayerEntityId(message);
                    writeObserved(context, message, promise, "NON_PLAYER_SPAWN",
                            false, new SentCallback() {
                                @Override
                                public int sent(long sentNanoTime) {
                                    sink.onNonPlayerEntitySpawnSent(playerId,
                                            playerEntityId, protocol, targetId,
                                            sentNanoTime);
                                    return ReachPacketSink.NO_TRANSACTION_SYNC;
                                }
                            });
                    return;
                }

                if (message instanceof PacketPlayOutEntity.PacketPlayOutRelEntityMove
                        || message instanceof
                        PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook) {
                    final int targetId = packetAccess.relativeEntityId.getInt(message);
                    final double deltaX = packetAccess.relativeX.getByte(message) / 32.0D;
                    final double deltaY = packetAccess.relativeY.getByte(message) / 32.0D;
                    final double deltaZ = packetAccess.relativeZ.getByte(message) / 32.0D;
                    final boolean onGround = packetAccess.relativeOnGround.getBoolean(message);
                    writeObserved(context, message, promise, "RELATIVE_ENTITY_MOVE",
                            true, new SentCallback() {
                                @Override
                                public int sent(long sentNanoTime) {
                                    return sink.onRelativeEntityMoveSent(playerId,
                                            playerEntityId, protocol, targetId,
                                            deltaX, deltaY, deltaZ, onGround,
                                            sentNanoTime);
                                }
                            });
                    return;
                }

                if (message instanceof PacketPlayOutEntityTeleport) {
                    final int targetId = packetAccess.teleportEntityId.getInt(message);
                    final double x = packetAccess.teleportX.getInt(message) / 32.0D;
                    final double y = packetAccess.teleportY.getInt(message) / 32.0D;
                    final double z = packetAccess.teleportZ.getInt(message) / 32.0D;
                    final boolean onGround = packetAccess.teleportOnGround.getBoolean(message);
                    writeObserved(context, message, promise, "ENTITY_TELEPORT",
                            true, new SentCallback() {
                                @Override
                                public int sent(long sentNanoTime) {
                                    return sink.onEntityTeleportSent(playerId,
                                            playerEntityId, protocol, targetId,
                                            x, y, z, onGround, sentNanoTime);
                                }
                            });
                    return;
                }

                if (message instanceof PacketPlayOutEntityDestroy) {
                    final int[] entityIds = ((int[]) packetAccess.destroyEntityIds
                            .get(message)).clone();
                    writeObserved(context, message, promise, "ENTITY_DESTROY",
                            false, new SentCallback() {
                                @Override
                                public int sent(long sentNanoTime) {
                                    for (int entityId : entityIds) {
                                        sink.onEntityDestroyedSent(playerId,
                                                playerEntityId, protocol, entityId,
                                                sentNanoTime);
                                    }
                                    return ReachPacketSink.NO_TRANSACTION_SYNC;
                                }
                            });
                    return;
                }

                if (message instanceof PacketPlayOutPosition) {
                    writeReset(context, message, promise, "POSITION");
                    return;
                }

                if (message instanceof PacketPlayOutRespawn) {
                    writeReset(context, message, promise, "RESPAWN");
                    return;
                }
            } catch (Exception exception) {
                safePacketError(playerId, playerEntityId, protocol,
                        outboundStage(message), exception);
                // Fail open: parsing/tracking failures never suppress server packets.
            }
            context.write(message, promise);
        }

        private void writeReset(ChannelHandlerContext context, Object message,
                                ChannelPromise promise, final String reason) {
            writeObserved(context, message, promise, reason, true,
                    new SentCallback() {
                        @Override
                        public int sent(long sentNanoTime) {
                            sink.onViewerResetSent(playerId, playerEntityId, protocol,
                                    reason, sentNanoTime);
                            // resetMovement may have cleared an in-flight marker
                            // while retaining visible targets. Queue one coalesced
                            // request; the sink returns NO when reset removed all
                            // targets or no dirty target sequence remains.
                            return ReachPacketSink.REQUEST_TRANSACTION_SYNC;
                        }
                    });
        }

        private void writeObserved(final ChannelHandlerContext context,
                                   Object message, ChannelPromise originalPromise,
                                   final String stage, final boolean maySync,
                                   final SentCallback callback) {
            ChannelPromise observedPromise = originalPromise.isVoid()
                    ? context.newPromise() : originalPromise;
            observedPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (!future.isSuccess()) {
                        safePacketError(playerId, playerEntityId, protocol,
                                stage + "_WRITE", future.cause());
                        return;
                    }
                    try {
                        int actionId = callback.sent(System.nanoTime());
                        if (maySync) {
                            if (actionId == ReachPacketSink.REQUEST_TRANSACTION_SYNC) {
                                queueTransactionSync(context);
                            } else if (actionId
                                    == ReachPacketSink.DEFER_TRANSACTION_SYNC) {
                                queueDeferredTransactionSync(context);
                            } else {
                                sendTransactionSync(context, actionId);
                            }
                        }
                    } catch (Exception exception) {
                        safePacketError(playerId, playerEntityId, protocol,
                                stage + "_SINK", exception);
                    }
                }
            });
            context.write(message, observedPromise);
        }

        private void queueTransactionSync(final ChannelHandlerContext context) {
            queueTransactionSync(context, 0L);
        }

        private void queueDeferredTransactionSync(
                final ChannelHandlerContext context) {
            queueTransactionSync(context, SYNC_RETRY_DELAY_MILLIS);
        }

        private void queueTransactionSync(final ChannelHandlerContext context,
                                          long delayMillis) {
            if (syncFlushQueued) return;
            syncFlushQueued = true;
            Runnable request = new Runnable() {
                @Override
                public void run() {
                    syncFlushQueued = false;
                    if (!context.channel().isActive()) return;
                    try {
                        int actionId = sink.onTransactionSyncRequested(playerId,
                                playerEntityId, protocol, System.nanoTime());
                        if (actionId == ReachPacketSink.DEFER_TRANSACTION_SYNC) {
                            queueDeferredTransactionSync(context);
                        } else if (actionId
                                == ReachPacketSink.REQUEST_TRANSACTION_SYNC) {
                            safePacketError(playerId, playerEntityId, protocol,
                                    "TRANSACTION_SYNC_REQUEST_RESULT",
                                    IllegalArgumentException.class.getName(),
                                    "Sink returned REQUEST from the reservation callback");
                            queueDeferredTransactionSync(context);
                        } else {
                            sendTransactionSync(context, actionId);
                        }
                    } catch (RuntimeException exception) {
                        safePacketError(playerId, playerEntityId, protocol,
                                "TRANSACTION_SYNC_REQUEST_SINK", exception);
                    }
                }
            };
            try {
                if (delayMillis <= 0L) {
                    context.executor().execute(request);
                } else {
                    context.executor().schedule(request, delayMillis,
                            TimeUnit.MILLISECONDS);
                }
            } catch (RuntimeException exception) {
                syncFlushQueued = false;
                safePacketError(playerId, playerEntityId, protocol,
                        "TRANSACTION_SYNC_RETRY_SCHEDULE", exception);
            }
        }

        private void sendTransactionSync(final ChannelHandlerContext context,
                                         int actionId) {
            if (actionId == ReachPacketSink.NO_TRANSACTION_SYNC) return;
            if (actionId == ReachPacketSink.DEFER_TRANSACTION_SYNC) {
                queueDeferredTransactionSync(context);
                return;
            }
            if (actionId >= 0 || actionId < Short.MIN_VALUE
                    || actionId > Short.MAX_VALUE) {
                safePacketError(playerId, playerEntityId, protocol,
                        "TRANSACTION_SYNC_ID", IllegalArgumentException.class.getName(),
                        "Sink returned a non-negative or out-of-range action ID: "
                                + actionId);
                return;
            }

            final short encodedActionId = (short) actionId;
            ChannelPromise markerPromise = context.newPromise();
            markerPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    long now = System.nanoTime();
                    if (future.isSuccess()) {
                        try {
                            sink.onTransactionSyncSent(playerId, playerEntityId,
                                    protocol, encodedActionId, now);
                        } catch (RuntimeException exception) {
                            safePacketError(playerId, playerEntityId, protocol,
                                    "TRANSACTION_SYNC_SENT_SINK", exception);
                        }
                        return;
                    }
                    try {
                        sink.onTransactionSyncWriteFailed(playerId, playerEntityId,
                                protocol, ReachProtocolPolicy.SYNC_WINDOW_ID,
                                encodedActionId, now);
                    } catch (RuntimeException exception) {
                        safePacketError(playerId, playerEntityId, protocol,
                                "TRANSACTION_SYNC_FAILURE_SINK", exception);
                    }
                    safePacketError(playerId, playerEntityId, protocol,
                            "TRANSACTION_SYNC_WRITE", future.cause());
                    queueDeferredTransactionSync(context);
                }
            });

            // context.write starts before this handler in the outbound direction,
            // so the internally generated marker cannot recursively re-enter us.
            try {
                context.writeAndFlush(new PacketPlayOutTransaction(
                        ReachProtocolPolicy.SYNC_WINDOW_ID, encodedActionId,
                        false), markerPromise);
            } catch (RuntimeException exception) {
                // Route synchronous pipeline rejection through the same failure
                // listener so the reservation becomes dirty and is retried.
                if (!markerPromise.tryFailure(exception)) {
                    safePacketError(playerId, playerEntityId, protocol,
                            "TRANSACTION_SYNC_WRITE_THROW", exception);
                }
            }
        }
    }

    private void safePacketError(UUID playerId, int playerEntityId, int protocol,
                                 String stage, Throwable error) {
        safePacketError(playerId, playerEntityId, protocol, stage,
                error == null ? "unknown" : error.getClass().getName(),
                safeMessage(error));
    }

    private void safePacketError(UUID playerId, int playerEntityId, int protocol,
                                 String stage, String errorType, String message) {
        try {
            sink.onPacketError(playerId, playerEntityId, protocol,
                    stage == null ? "UNKNOWN" : stage,
                    errorType == null ? "unknown" : errorType,
                    message == null ? "" : message);
        } catch (RuntimeException ignored) {
            // Error reporting must never affect packet flow.
        }
    }

    private static String inboundStage(Object message) {
        return "INBOUND_" + simpleName(message);
    }

    private static String outboundStage(Object message) {
        return "OUTBOUND_" + simpleName(message);
    }

    private static String simpleName(Object value) {
        return value == null ? "NULL" : value.getClass().getSimpleName();
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "";
        String message = error.getMessage();
        return message == null ? error.toString() : message;
    }

    private interface SentCallback {
        int sent(long sentNanoTime);
    }

    private static final class Binding {
        private final UUID playerId;
        private final Channel channel;
        private final Handler handler;

        private Binding(UUID playerId, Channel channel, Handler handler) {
            this.playerId = playerId;
            this.channel = channel;
            this.handler = handler;
        }
    }

    private static final class PacketAccess {
        private final Field useEntityId;
        private final Field namedSpawnEntityId;
        private final Field namedSpawnUuid;
        private final Field namedSpawnX;
        private final Field namedSpawnY;
        private final Field namedSpawnZ;
        private final Field relativeEntityId;
        private final Field relativeX;
        private final Field relativeY;
        private final Field relativeZ;
        private final Field relativeOnGround;
        private final Field teleportEntityId;
        private final Field teleportX;
        private final Field teleportY;
        private final Field teleportZ;
        private final Field teleportOnGround;
        private final Field destroyEntityIds;
        private final Field spawnEntityId;
        private final Field spawnLivingEntityId;
        private final Field spawnExperienceOrbEntityId;
        private final Field spawnPaintingEntityId;
        private final Field spawnWeatherEntityId;

        private PacketAccess() throws ReflectiveOperationException {
            useEntityId = field(PacketPlayInUseEntity.class, "a");
            namedSpawnEntityId = field(PacketPlayOutNamedEntitySpawn.class, "a");
            namedSpawnUuid = field(PacketPlayOutNamedEntitySpawn.class, "b");
            namedSpawnX = field(PacketPlayOutNamedEntitySpawn.class, "c");
            namedSpawnY = field(PacketPlayOutNamedEntitySpawn.class, "d");
            namedSpawnZ = field(PacketPlayOutNamedEntitySpawn.class, "e");
            relativeEntityId = field(PacketPlayOutEntity.class, "a");
            relativeX = field(PacketPlayOutEntity.class, "b");
            relativeY = field(PacketPlayOutEntity.class, "c");
            relativeZ = field(PacketPlayOutEntity.class, "d");
            relativeOnGround = field(PacketPlayOutEntity.class, "g");
            teleportEntityId = field(PacketPlayOutEntityTeleport.class, "a");
            teleportX = field(PacketPlayOutEntityTeleport.class, "b");
            teleportY = field(PacketPlayOutEntityTeleport.class, "c");
            teleportZ = field(PacketPlayOutEntityTeleport.class, "d");
            teleportOnGround = field(PacketPlayOutEntityTeleport.class, "g");
            destroyEntityIds = field(PacketPlayOutEntityDestroy.class, "a");
            spawnEntityId = field(PacketPlayOutSpawnEntity.class, "a");
            spawnLivingEntityId = field(PacketPlayOutSpawnEntityLiving.class, "a");
            spawnExperienceOrbEntityId = field(
                    PacketPlayOutSpawnEntityExperienceOrb.class, "a");
            spawnPaintingEntityId = field(PacketPlayOutSpawnEntityPainting.class, "a");
            spawnWeatherEntityId = field(PacketPlayOutSpawnEntityWeather.class, "a");
        }

        private int nonPlayerEntityId(Object packet) throws IllegalAccessException {
            if (packet instanceof PacketPlayOutSpawnEntity) {
                return spawnEntityId.getInt(packet);
            }
            if (packet instanceof PacketPlayOutSpawnEntityLiving) {
                return spawnLivingEntityId.getInt(packet);
            }
            if (packet instanceof PacketPlayOutSpawnEntityExperienceOrb) {
                return spawnExperienceOrbEntityId.getInt(packet);
            }
            if (packet instanceof PacketPlayOutSpawnEntityPainting) {
                return spawnPaintingEntityId.getInt(packet);
            }
            return spawnWeatherEntityId.getInt(packet);
        }

        private static Field field(Class<?> type, String name)
                throws ReflectiveOperationException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}
