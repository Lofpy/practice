package com.poppy.practice.duel;

import java.util.*;

/** One outgoing invitation per player; UUID identity and exact expiry avoid stale accepts. */
public final class DuelRequests {
    public static final long LIFETIME_MILLIS = 60000L;
    private final Map<UUID, Request> outgoing = new HashMap<UUID, Request>();
    public Request send(UUID sender, UUID target, String kit, long now) {
        if (sender == null || target == null || sender.equals(target) || kit == null)
            throw new IllegalArgumentException("Two distinct participants and a kit are required");
        expire(now);
        Request previous = outgoing.get(sender);
        if (previous != null && now - previous.sentAt < 3000L) return null;
        Request request = new Request(sender, target, kit, now);
        outgoing.put(sender, request);
        return request;
    }
    public Request find(UUID sender, UUID recipient, long now) {
        expire(now);
        Request request = outgoing.get(sender);
        return request != null && request.target.equals(recipient) ? request : null;
    }
    public void remove(Request request) { if (request != null) outgoing.remove(request.sender, request); }
    public void removePlayer(UUID player) {
        outgoing.values().removeIf(request -> request.sender.equals(player) || request.target.equals(player));
    }
    private void expire(long now) { outgoing.values().removeIf(request -> now - request.sentAt >= LIFETIME_MILLIS); }
    public void clear() { outgoing.clear(); }
    public static final class Request {
        public final UUID sender, target;
        public final String kit;
        public final long sentAt;
        private Request(UUID sender, UUID target, String kit, long now) {
            this.sender = sender; this.target = target; this.kit = kit; this.sentAt = now;
        }
    }
}
