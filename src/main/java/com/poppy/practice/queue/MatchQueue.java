package com.poppy.practice.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MatchQueue {
    private final String kitId;
    private final Deque<UUID> waitingPlayers = new ArrayDeque<UUID>();
    private final Set<UUID> membership = new HashSet<UUID>();

    public MatchQueue(String kitId) {
        this.kitId = kitId;
    }

    public String getKitId() {
        return kitId;
    }

    public boolean add(UUID playerId) {
        requirePlayer(playerId);
        if (!membership.add(playerId)) {
            return false;
        }
        waitingPlayers.addLast(playerId);
        return true;
    }

    public boolean addFirst(UUID playerId) {
        requirePlayer(playerId);
        if (!membership.add(playerId)) {
            return false;
        }
        waitingPlayers.addFirst(playerId);
        return true;
    }

    private void requirePlayer(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
    }

    public boolean remove(UUID playerId) {
        if (!membership.remove(playerId)) {
            return false;
        }
        waitingPlayers.remove(playerId);
        return true;
    }

    public UUID poll() {
        UUID playerId = waitingPlayers.pollFirst();
        if (playerId != null) {
            membership.remove(playerId);
        }
        return playerId;
    }

    public boolean contains(UUID playerId) {
        return membership.contains(playerId);
    }

    public int size() {
        return membership.size();
    }

    public Collection<UUID> snapshot() {
        return new ArrayList<UUID>(waitingPlayers);
    }

    public void clear() {
        waitingPlayers.clear();
        membership.clear();
    }
}
