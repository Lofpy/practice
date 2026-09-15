package com.poppy.practice.result;

import java.util.UUID;

public final class MatchResult {
    private final UUID winnerId;
    private final MatchParticipantSnapshot first;
    private final MatchParticipantSnapshot second;
    private final long durationSeconds;

    public MatchResult(UUID winnerId, MatchParticipantSnapshot first,
                       MatchParticipantSnapshot second, long durationSeconds) {
        this.winnerId = winnerId;
        this.first = first;
        this.second = second;
        this.durationSeconds = Math.max(0L, durationSeconds);
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public MatchParticipantSnapshot getFirst() {
        return first;
    }

    public MatchParticipantSnapshot getSecond() {
        return second;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public MatchParticipantSnapshot getParticipant(UUID playerId) {
        if (first.getPlayerId().equals(playerId)) {
            return first;
        }
        if (second.getPlayerId().equals(playerId)) {
            return second;
        }
        return null;
    }

    public MatchParticipantSnapshot getOpponent(UUID playerId) {
        if (first.getPlayerId().equals(playerId)) {
            return second;
        }
        return second.getPlayerId().equals(playerId) ? first : null;
    }
}
