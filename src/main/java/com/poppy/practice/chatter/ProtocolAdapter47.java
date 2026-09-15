package com.poppy.practice.chatter;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.protocol.ClientProtocolResolver;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.v1_8_R3.NetworkManager;
import net.minecraft.server.v1_8_R3.PacketPlayInEntityAction;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;
import net.minecraft.server.v1_8_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityVelocity;
import net.minecraft.server.v1_8_R3.PacketPlayOutPosition;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtocolAdapter47 {
    private static final String HANDLER_NAME = "poppy_chatter_kb_protocol_47";
    private static final Field USE_ENTITY_ID = field(PacketPlayInUseEntity.class, "a");
    private static final Field VELOCITY_ENTITY_ID = field(PacketPlayOutEntityVelocity.class, "a");
    private static final Field VELOCITY_X = field(PacketPlayOutEntityVelocity.class, "b");
    private static final Field VELOCITY_Y = field(PacketPlayOutEntityVelocity.class, "c");
    private static final Field VELOCITY_Z = field(PacketPlayOutEntityVelocity.class, "d");

    private final PracticePlugin plugin;
    private final ChatterKbService service;
    private final Map<UUID, Handler> handlers = new ConcurrentHashMap<UUID, Handler>();

    public ProtocolAdapter47(PracticePlugin plugin, ChatterKbService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void register() {
        for (Player player : Bukkit.getOnlinePlayers()) install(player);
    }

    public void unregister() {
        for (Player player : new ArrayList<Player>(Bukkit.getOnlinePlayers())) remove(player);
        handlers.clear();
    }

    public void install(Player player) {
        if (player == null || !player.isOnline()) return;
        NetworkManager networkManager = ((CraftPlayer) player).getHandle()
                .playerConnection.networkManager;
        final Channel channel = networkManager.channel;
        if (channel == null || !channel.isActive()) return;
        final UUID playerId = player.getUniqueId();
        final int entityId = player.getEntityId();
        final int protocol = protocolVersion(player);
        final Handler handler = new Handler(playerId, entityId, protocol);
        Handler previous = handlers.put(playerId, handler);
        if (previous != null) remove(channel, previous);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!channel.isActive() || handlers.get(playerId) != handler) return;
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME);
                    String anchor = pipeline.get("poppy_artificial_latency") != null
                            ? "poppy_artificial_latency"
                            : pipeline.get("poppy_reach_guard") != null
                            ? "poppy_reach_guard" : "packet_handler";
                    pipeline.addBefore(anchor, HANDLER_NAME, handler);
                } catch (RuntimeException exception) {
                    handlers.remove(playerId, handler);
                    plugin.getLogger().warning("Could not install ChatterKB packet monitor for "
                            + playerId + ": " + exception.getMessage());
                }
            }
        });
    }

    public void remove(Player player) {
        if (player == null) return;
        Handler handler = handlers.remove(player.getUniqueId());
        if (handler == null) return;
        Channel channel = ((CraftPlayer) player).getHandle()
                .playerConnection.networkManager.channel;
        remove(channel, handler);
    }

    private void remove(final Channel channel, final Handler handler) {
        if (channel == null) return;
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.context(handler) != null) pipeline.remove(handler);
            }
        });
    }

    private int protocolVersion(Player player) {
        try {
            return ClientProtocolResolver.resolve(player, Bukkit.getPluginManager());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not resolve client protocol for "
                    + player.getName() + "; ChatterKB will fail safe: " + exception.getMessage());
            return -1;
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final class Handler extends ChannelDuplexHandler {
        private final UUID playerId;
        private final int entityId;
        private final int protocol;

        private Handler(UUID playerId, int entityId, int protocol) {
            this.playerId = playerId;
            this.entityId = entityId;
            this.protocol = protocol;
        }

        @Override
        public void channelRead(final ChannelHandlerContext context, Object message)
                throws Exception {
            long now = System.nanoTime();
            if (message instanceof PacketPlayInFlying) {
                PacketPlayInFlying flying = (PacketPlayInFlying) message;
                Double x = flying.g() ? flying.a() : null;
                Double y = flying.g() ? flying.b() : null;
                Double z = flying.g() ? flying.c() : null;
                service.handleMovement(playerId, entityId, protocol, x, y, z,
                        flying.f(), now);
            } else if (message instanceof PacketPlayInEntityAction) {
                PacketPlayInEntityAction action = (PacketPlayInEntityAction) message;
                if (action.b() == PacketPlayInEntityAction.EnumPlayerAction.START_SPRINTING) {
                    service.handleSprint(playerId, entityId, true, now);
                } else if (action.b()
                        == PacketPlayInEntityAction.EnumPlayerAction.STOP_SPRINTING) {
                    service.handleSprint(playerId, entityId, false, now);
                }
            } else if (message instanceof PacketPlayInUseEntity) {
                PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) message;
                if (useEntity.a() == PacketPlayInUseEntity.EnumEntityUseAction.ATTACK) {
                    int targetEntityId = USE_ENTITY_ID.getInt(useEntity);
                    ChatterKbService.AttackDecision decision = service.handleAttack(
                            playerId, entityId, protocol, targetEntityId, now,
                            new ChatterKbService.CorrectionTransport() {
                                @Override
                                public boolean send(int targetId, Vec3 velocity) {
                                    context.channel().writeAndFlush(
                                            new PacketPlayOutEntityVelocity(targetId,
                                            velocity.getX(), velocity.getY(), velocity.getZ()));
                                    return true;
                                }
                            });
                    if (decision.shouldCancel()) return;
                }
            }
            context.fireChannelRead(message);
        }

        @Override
        public void write(ChannelHandlerContext context, Object message,
                          ChannelPromise promise) throws Exception {
            if (message instanceof PacketPlayOutEntityVelocity
                    && VELOCITY_ENTITY_ID.getInt(message) == entityId) {
                service.handleVelocity(playerId, entityId, protocol,
                        new Vec3(VELOCITY_X.getInt(message) / 8000.0D,
                                VELOCITY_Y.getInt(message) / 8000.0D,
                                VELOCITY_Z.getInt(message) / 8000.0D),
                        System.nanoTime());
            } else if (message instanceof PacketPlayOutPosition) {
                service.reset(playerId);
            }
            context.write(message, promise);
        }
    }
}
