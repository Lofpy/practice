package com.poppy.practice.combat;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityVelocity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Sends human clients a slow-fall correction without reusing the server's horizontal velocity.
 * Player movement is client-simulated: server motX/motZ are not the client's current momentum.
 */
public final class ComboSlowFallMotion {
    private static final double AIR_DRAG = 0.91D;
    private static final double PACKET_LIMIT = 3.9D;
    private static final int MAX_ACK_GRACE_TICKS = 20;
    private final ConcurrentMap<UUID, History> histories = new ConcurrentHashMap<UUID, History>();
    private final PacketSender sender;

    public ComboSlowFallMotion() {
        this(ComboSlowFallMotion::sendNativePacket);
    }

    ComboSlowFallMotion(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /** Called for accepted position changes, not look-only packets or teleports. */
    public void recordMove(UUID id, double deltaX, double deltaZ, long tick) {
        if (id == null || !finite(deltaX, deltaZ)) {
            return;
        }
        final Motion movement = new Motion(deltaX, deltaZ, tick);
        histories.compute(id, (ignored, previous) -> {
            History history = previous == null ? History.EMPTY : previous;
            if (history.movement != null && history.movement.tick > tick) {
                return history;
            }
            return new History(movement, history.velocity, history.ackGraceTicks);
        });
    }

    /** May be called by the async tracker; only immutable numeric history is touched. */
    public void recordVelocity(UUID id, double velocityX, double velocityZ, long tick, int pingMillis) {
        if (id == null || !finite(velocityX, velocityZ)) {
            return;
        }
        final Motion velocity = new Motion(velocityX, velocityZ, tick);
        final int grace = Math.min(MAX_ACK_GRACE_TICKS,
                1 + (int) Math.ceil(Math.max(0, pingMillis) / 50.0D));
        histories.compute(id, (ignored, previous) -> {
            History history = previous == null ? History.EMPTY : previous;
            if (history.velocity != null && history.velocity.tick > tick) {
                return history;
            }
            return new History(history.movement, velocity, grace);
        });
    }

    /**
     * Main-thread only. A native velocity packet contains all three axes, so preserve estimated
     * client horizontal momentum while replacing Y. No server motion or velocity flags are changed.
     */
    public boolean apply(Player player, double targetY, long tick) {
        if (player == null || !Double.isFinite(targetY)) {
            return false;
        }
        final boolean[] sent = {false};
        // Serialize choosing the source and enqueueing the correction with tracker history
        // updates. Otherwise an async hit could be followed by stale pre-hit horizontal motion.
        histories.computeIfPresent(player.getUniqueId(), (ignored, history) -> {
            sent[0] = sendCorrection(player, history, targetY, tick);
            return history;
        });
        // Without history, an invented zero horizontal velocity would stop a moving client.
        return sent[0];
    }

    private boolean sendCorrection(Player player, History history, double targetY, long tick) {
        Motion movement = history.movement;
        Motion velocity = history.velocity;
        boolean useVelocity = velocity != null && (movement == null
                || movement.tick <= velocity.tick
                || elapsed(tick, velocity.tick) < history.ackGraceTicks);
        Motion source = useVelocity ? velocity : movement;
        if (source == null) {
            return false;
        }
        // A movement delta is measured before that client tick's drag. A received KB packet
        // already contains the velocity to apply, so it must not lose drag in its sending tick.
        long age = elapsed(tick, source.tick);
        double drag = Math.pow(AIR_DRAG, age + (useVelocity ? 0.0D : 1.0D));
        return sender.send(player, clamp(source.x * drag), clamp(targetY), clamp(source.z * drag));
    }

    public void forget(UUID id) {
        if (id != null) {
            histories.remove(id);
        }
    }

    public void retain(Set<UUID> participants) {
        histories.keySet().retainAll(Objects.requireNonNull(participants, "participants"));
    }

    public void clear() {
        histories.clear();
    }

    private static boolean sendNativePacket(Player player, double x, double y, double z) {
        if (!(player instanceof CraftPlayer) || !player.isOnline()) {
            return false;
        }
        EntityPlayer handle = ((CraftPlayer) player).getHandle();
        if (handle == null || handle.playerConnection == null) {
            return false;
        }
        handle.playerConnection.sendPacket(new PacketPlayOutEntityVelocity(handle.getId(), x, y, z));
        return true;
    }

    private static long elapsed(long tick, long previousTick) {
        return tick <= previousTick ? 0L : tick - previousTick;
    }

    private static boolean finite(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z);
    }

    private static double clamp(double value) {
        return Math.max(-PACKET_LIMIT, Math.min(PACKET_LIMIT, value));
    }

    interface PacketSender {
        boolean send(Player player, double x, double y, double z);
    }

    private static final class History {
        private static final History EMPTY = new History(null, null, 1);
        private final Motion movement;
        private final Motion velocity;
        private final int ackGraceTicks;

        private History(Motion movement, Motion velocity, int ackGraceTicks) {
            this.movement = movement;
            this.velocity = velocity;
            this.ackGraceTicks = ackGraceTicks;
        }
    }

    private static final class Motion {
        private final double x;
        private final double z;
        private final long tick;

        private Motion(double x, double z, long tick) {
            this.x = x;
            this.z = z;
            this.tick = tick;
        }
    }
}
