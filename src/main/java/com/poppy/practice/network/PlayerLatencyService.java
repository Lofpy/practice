package com.poppy.practice.network;

import com.poppy.practice.PracticePlugin;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.server.v1_8_R3.NetworkManager;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class PlayerLatencyService {
    public static final int MAX_ADDITIONAL_PING_MS = 2000;
    private static final String HANDLER_NAME = "poppy_artificial_latency";
    private static final String REACH_GUARD_HANDLER_NAME = "poppy_reach_guard";

    private final PracticePlugin plugin;
    private final Map<UUID, ArtificialLatencyHandler> handlers =
            new ConcurrentHashMap<UUID, ArtificialLatencyHandler>();

    public PlayerLatencyService(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setAdditionalPing(Player player, int additionalPingMs) {
        if (player == null || !player.isOnline() || !isValidAdditionalPing(additionalPingMs)) {
            return false;
        }
        if (additionalPingMs == 0) {
            remove(player);
            return true;
        }

        NetworkManager networkManager = ((CraftPlayer) player).getHandle()
                .playerConnection.networkManager;
        final Channel channel = networkManager.channel;
        if (channel == null || !channel.isActive()
                || channel.pipeline().get("packet_handler") == null) {
            return false;
        }

        ArtificialLatencyHandler existing = handlers.get(player.getUniqueId());
        if (existing != null) {
            existing.setAdditionalPingMs(additionalPingMs);
            return true;
        }

        final UUID playerId = player.getUniqueId();
        final ArtificialLatencyHandler handler = new ArtificialLatencyHandler(
                playerId, additionalPingMs);
        ArtificialLatencyHandler raced = handlers.putIfAbsent(playerId, handler);
        if (raced != null) {
            raced.setAdditionalPingMs(additionalPingMs);
            return true;
        }

        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!channel.isActive() || handlers.get(playerId) != handler) {
                        return;
                    }
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get(HANDLER_NAME) != null) {
                        pipeline.remove(HANDLER_NAME);
                    }
                    String anchor = pipeline.get(REACH_GUARD_HANDLER_NAME) == null
                            ? "packet_handler" : REACH_GUARD_HANDLER_NAME;
                    pipeline.addBefore(anchor, HANDLER_NAME, handler);
                } catch (RuntimeException exception) {
                    handlers.remove(playerId, handler);
                    plugin.getLogger().warning("Could not add artificial latency for "
                            + playerId + ": " + exception.getMessage());
                }
            }
        });
        return true;
    }

    public int getAdditionalPing(Player player) {
        if (player == null) {
            return 0;
        }
        ArtificialLatencyHandler handler = handlers.get(player.getUniqueId());
        return handler == null ? 0 : handler.getAdditionalPingMs();
    }

    public void handleQuit(Player player) {
        remove(player);
    }

    public void shutdown() {
        for (Player player : new ArrayList<Player>(plugin.getServer().getOnlinePlayers())) {
            remove(player);
        }
    }

    private void remove(Player player) {
        if (player == null) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        final ArtificialLatencyHandler handler = handlers.get(playerId);
        if (handler == null) {
            return;
        }
        final Channel channel = ((CraftPlayer) player).getHandle()
                .playerConnection.networkManager.channel;
        if (channel == null) {
            return;
        }
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (handlers.get(playerId) != handler) return;
                handler.requestRemoval();
                tryRemoveWhenDrained(channel, playerId, handler);
            }
        });
    }

    private void tryRemoveWhenDrained(final Channel channel, final UUID playerId,
                                      final ArtificialLatencyHandler handler) {
        synchronized (handler) {
            if (!handler.isRemovalRequested()) return;
            if (!channel.isActive() || !handler.hasPendingMessages()) {
                handlers.remove(playerId, handler);
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.context(handler) != null) pipeline.remove(handler);
            }
        }
    }

    public static boolean isValidAdditionalPing(int milliseconds) {
        return milliseconds >= 0 && milliseconds <= MAX_ADDITIONAL_PING_MS;
    }

    static long oneWayDelay(int additionalPingMs) {
        return (additionalPingMs + 1L) / 2L;
    }

    static long monotonicDueNanoTime(long nowNanoTime, long delayNanos,
                                     long previousTailNanoTime) {
        long requested = nowNanoTime + Math.max(0L, delayNanos);
        if (previousTailNanoTime > nowNanoTime
                && requested <= previousTailNanoTime) {
            return previousTailNanoTime + 1L;
        }
        return requested;
    }

    private final class ArtificialLatencyHandler extends ChannelDuplexHandler {
        private final UUID playerId;
        private volatile int additionalPingMs;
        private volatile boolean removalRequested;
        private long inboundTailNanoTime;
        private long outboundTailNanoTime;
        private int pendingInbound;
        private int pendingOutbound;

        private ArtificialLatencyHandler(UUID playerId, int additionalPingMs) {
            this.playerId = playerId;
            this.additionalPingMs = additionalPingMs;
        }

        private int getAdditionalPingMs() {
            return additionalPingMs;
        }

        private synchronized void setAdditionalPingMs(int additionalPingMs) {
            this.additionalPingMs = additionalPingMs;
            removalRequested = false;
        }

        private synchronized void requestRemoval() {
            additionalPingMs = 0;
            removalRequested = true;
        }

        private synchronized boolean isRemovalRequested() {
            return removalRequested;
        }

        private synchronized boolean hasPendingMessages() {
            return pendingInbound > 0 || pendingOutbound > 0;
        }

        private synchronized long reserveDelayNanos(boolean inbound) {
            long now = System.nanoTime();
            long tail = inbound ? inboundTailNanoTime : outboundTailNanoTime;
            long due = monotonicDueNanoTime(now,
                    TimeUnit.MILLISECONDS.toNanos(oneWayDelay(additionalPingMs)),
                    tail);
            int pending = inbound ? pendingInbound : pendingOutbound;
            if (pending > 0 && due <= now) due = now + 1L;
            if (inbound) inboundTailNanoTime = due;
            else outboundTailNanoTime = due;
            long delay = Math.max(0L, due - now);
            if (delay > 0L) {
                if (inbound) pendingInbound++;
                else pendingOutbound++;
            }
            return delay;
        }

        private void completeScheduled(boolean inbound, ChannelHandlerContext context) {
            synchronized (this) {
                if (inbound) pendingInbound = Math.max(0, pendingInbound - 1);
                else pendingOutbound = Math.max(0, pendingOutbound - 1);
            }
            tryRemoveWhenDrained(context.channel(), playerId, this);
        }

        @Override
        public void channelRead(final ChannelHandlerContext context, final Object message) {
            long delay = reserveDelayNanos(true);
            if (delay <= 0L) {
                context.fireChannelRead(message);
                return;
            }
            context.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (context.channel().isActive()) {
                            context.fireChannelRead(message);
                        } else {
                            ReferenceCountUtil.release(message);
                        }
                    } finally {
                        completeScheduled(true, context);
                    }
                }
            }, delay, TimeUnit.NANOSECONDS);
        }

        @Override
        public void write(final ChannelHandlerContext context, final Object message,
                          final ChannelPromise promise) {
            long delay = reserveDelayNanos(false);
            if (delay <= 0L) {
                context.write(message, promise);
                return;
            }
            context.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (context.channel().isActive()) {
                            // The original flush may already have happened while this write
                            // was delayed, so flush together with the delayed message.
                            context.writeAndFlush(message, promise);
                        } else {
                            ReferenceCountUtil.release(message);
                            promise.tryFailure(new ClosedChannelException());
                        }
                    } finally {
                        completeScheduled(false, context);
                    }
                }
            }, delay, TimeUnit.NANOSECONDS);
        }
    }
}
