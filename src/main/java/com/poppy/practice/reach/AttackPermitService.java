package com.poppy.practice.reach;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Issues and consumes short-lived, one-shot melee attack permits.
 *
 * <p>Permits are FIFO per attacker/target pair. Each pair has an independently
 * synchronized deque, so issuing or consuming a permit for one fight does not
 * serialize unrelated fights.</p>
 */
public final class AttackPermitService {
    private static final long NANOS_PER_MILLISECOND = 1000000L;
    private static final int MAX_PERMITS_PER_PAIR = 32;

    private final ConcurrentMap<PermitKey, PermitBucket> permits =
            new ConcurrentHashMap<PermitKey, PermitBucket>();
    private volatile long expireNanos;

    public AttackPermitService(long expireMilliseconds) {
        setExpireMilliseconds(expireMilliseconds);
    }

    public void setExpireMilliseconds(long expireMilliseconds) {
        if (expireMilliseconds <= 0L) {
            throw new IllegalArgumentException("expireMilliseconds must be positive");
        }
        expireNanos = saturatedMultiply(expireMilliseconds, NANOS_PER_MILLISECOND);
    }

    public AttackPermit issue(UUID attacker, UUID target, long attackSequence,
                              int serverTick, long nowNanoTime) {
        PermitKey key = new PermitKey(attacker, target);
        AttackPermit permit = new AttackPermit(attacker, target, attackSequence,
                serverTick, saturatedAdd(nowNanoTime, expireNanos));
        while (true) {
            PermitBucket bucket = permits.get(key);
            if (bucket == null) {
                PermitBucket created = new PermitBucket();
                PermitBucket raced = permits.putIfAbsent(key, created);
                bucket = raced == null ? created : raced;
            }
            synchronized (bucket) {
                if (permits.get(key) != bucket) continue;
                bucket.add(permit, serverTick, nowNanoTime);
                return permit;
            }
        }
    }

    /**
     * Consumes one matching permit and returns it, or returns {@code null} if
     * the damage event is not covered by an accepted attack packet.
     */
    public AttackPermit consumePermit(UUID attacker, UUID target,
                                      int damageServerTick, long nowNanoTime) {
        if (attacker == null || target == null) {
            return null;
        }
        PermitKey key = new PermitKey(attacker, target);
        PermitBucket bucket = permits.get(key);
        if (bucket == null) {
            return null;
        }
        synchronized (bucket) {
            if (permits.get(key) != bucket) return null;
            AttackPermit consumed = bucket.consume(damageServerTick, nowNanoTime);
            if (bucket.isEmpty()) permits.remove(key, bucket);
            return consumed;
        }
    }

    public boolean consume(UUID attacker, UUID target,
                           int damageServerTick, long nowNanoTime) {
        return consumePermit(attacker, target, damageServerTick, nowNanoTime) != null;
    }

    /** Removes every permit involving the supplied player. */
    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (Map.Entry<PermitKey, PermitBucket> entry : permits.entrySet()) {
            if (entry.getKey().involves(playerId)) {
                permits.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public void clear() {
        permits.clear();
    }

    /** Prunes expired buckets even when no Bukkit damage event is produced. */
    public void cleanup(int serverTick, long nowNanoTime) {
        for (Map.Entry<PermitKey, PermitBucket> entry : permits.entrySet()) {
            PermitBucket bucket = entry.getValue();
            synchronized (bucket) {
                if (permits.get(entry.getKey()) != bucket) continue;
                bucket.prune(serverTick, nowNanoTime);
                if (bucket.isEmpty()) permits.remove(entry.getKey(), bucket);
            }
        }
    }

    public int pendingPermitCount() {
        int total = 0;
        for (PermitBucket bucket : permits.values()) {
            total += bucket.size();
        }
        return total;
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private static long saturatedAdd(long value, long addition) {
        if (addition > 0L && value > Long.MAX_VALUE - addition) {
            return Long.MAX_VALUE;
        }
        return value + addition;
    }

    private static final class PermitBucket {
        private final ArrayDeque<AttackPermit> queue = new ArrayDeque<AttackPermit>();

        void add(AttackPermit permit, int serverTick, long nowNanoTime) {
            prune(serverTick, nowNanoTime);
            queue.addLast(permit);
            while (queue.size() > MAX_PERMITS_PER_PAIR) queue.removeFirst();
        }

        AttackPermit consume(int damageServerTick, long nowNanoTime) {
            while (!queue.isEmpty()) {
                AttackPermit permit = queue.peekFirst();
                long lastValidTick = (long) permit.getServerTick() + 1L;
                if (nowNanoTime > permit.getExpiresAtNanoTime()
                        || (long) damageServerTick > lastValidTick) {
                    queue.removeFirst();
                    continue;
                }
                if (damageServerTick < permit.getServerTick()) {
                    return null;
                }
                queue.removeFirst();
                return permit;
            }
            return null;
        }

        void prune(int serverTick, long nowNanoTime) {
            while (!queue.isEmpty()) {
                AttackPermit permit = queue.peekFirst();
                if (nowNanoTime <= permit.getExpiresAtNanoTime()
                        && (long) serverTick <= (long) permit.getServerTick() + 1L) {
                    break;
                }
                queue.removeFirst();
            }
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }

        synchronized int size() {
            return queue.size();
        }
    }

    private static final class PermitKey {
        private final UUID attacker;
        private final UUID target;

        private PermitKey(UUID attacker, UUID target) {
            if (attacker == null) {
                throw new IllegalArgumentException("attacker must not be null");
            }
            if (target == null) {
                throw new IllegalArgumentException("target must not be null");
            }
            this.attacker = attacker;
            this.target = target;
        }

        boolean involves(UUID playerId) {
            return attacker.equals(playerId) || target.equals(playerId);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PermitKey)) {
                return false;
            }
            PermitKey that = (PermitKey) other;
            return attacker.equals(that.attacker) && target.equals(that.target);
        }

        @Override
        public int hashCode() {
            return 31 * attacker.hashCode() + target.hashCode();
        }
    }
}
