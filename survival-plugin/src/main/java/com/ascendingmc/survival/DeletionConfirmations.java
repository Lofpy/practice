package com.ascendingmc.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Single-use confirmations are bound to the initiating administrator and expire. */
public final class DeletionConfirmations {
    private final Map<String, Pending> pending = new HashMap<>();
    private final LongSupplier clock;
    private final long lifetimeMillis;

    public DeletionConfirmations(LongSupplier clock, long lifetimeMillis) {
        if (lifetimeMillis <= 0) throw new IllegalArgumentException("Confirmation lifetime must be positive.");
        this.clock = clock;
        this.lifetimeMillis = lifetimeMillis;
    }

    public String request(String administrator, String world) {
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt <= clock.getAsLong());
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        pending.put(administrator, new Pending(world, token, clock.getAsLong() + lifetimeMillis));
        return token;
    }

    public String consume(String administrator, String token) {
        Pending request = pending.get(administrator);
        if (request == null) return null;
        if (request.expiresAt <= clock.getAsLong()) {
            pending.remove(administrator);
            return null;
        }
        if (!request.token.equals(token)) return null;
        pending.remove(administrator);
        return request.world;
    }

    public void forget(String administrator) {
        pending.remove(administrator);
    }

    private record Pending(String world, String token, long expiresAt) { }
}
