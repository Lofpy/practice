package com.poppy.practice.match;

import com.poppy.practice.result.MatchParticipantStats;

import java.util.UUID;

public final class Match {
    private final UUID id;
    private final UUID firstPlayerId;
    private final UUID secondPlayerId;
    private final String kitId;
    private final String arenaId;
    private final MatchType type;
    private final long createdAt;
    private final MatchParticipantStats firstPlayerStats = new MatchParticipantStats();
    private final MatchParticipantStats secondPlayerStats = new MatchParticipantStats();
    private MatchState state;
    private long startedAt;
    private Integer countdownTaskId;
    private UUID boxingWinnerId;

    public Match(UUID firstPlayerId, UUID secondPlayerId, String kitId, String arenaId) {
        this(firstPlayerId, secondPlayerId, kitId, arenaId, MatchType.RANKED);
    }

    public Match(UUID firstPlayerId, UUID secondPlayerId, String kitId, String arenaId, MatchType type) {
        if (firstPlayerId == null || secondPlayerId == null || kitId == null || arenaId == null) {
            throw new IllegalArgumentException("Match participants, kit and arena are required");
        }
        if (firstPlayerId.equals(secondPlayerId)) {
            throw new IllegalArgumentException("A match requires two different players");
        }
        this.id = UUID.randomUUID();
        this.firstPlayerId = firstPlayerId;
        this.secondPlayerId = secondPlayerId;
        this.kitId = kitId;
        this.arenaId = arenaId;
        if (type == null) throw new IllegalArgumentException("Match type is required");
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.state = MatchState.STARTING;
    }

    public UUID getId() {
        return id;
    }

    public MatchType getType() { return type; }

    public UUID getFirstPlayerId() {
        return firstPlayerId;
    }

    public UUID getSecondPlayerId() {
        return secondPlayerId;
    }

    public String getKitId() {
        return kitId;
    }

    public String getArenaId() {
        return arenaId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public MatchState getState() {
        return state;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public synchronized boolean markFighting() {
        if (state != MatchState.STARTING) {
            return false;
        }
        this.startedAt = System.currentTimeMillis();
        this.state = MatchState.FIGHTING;
        return true;
    }

    public synchronized void markFinished() {
        if (state != MatchState.ENDING && state != MatchState.FINISHED) {
            throw new IllegalStateException("A match must begin ending before it can finish");
        }
        state = MatchState.FINISHED;
    }

    public Integer getCountdownTaskId() {
        return countdownTaskId;
    }

    public void setCountdownTaskId(Integer countdownTaskId) {
        this.countdownTaskId = countdownTaskId;
    }

    public UUID getOpponent(UUID playerId) {
        if (firstPlayerId.equals(playerId)) {
            return secondPlayerId;
        }
        if (secondPlayerId.equals(playerId)) {
            return firstPlayerId;
        }
        return null;
    }

    public MatchParticipantStats getStats(UUID playerId) {
        if (firstPlayerId.equals(playerId)) {
            return firstPlayerStats;
        }
        if (secondPlayerId.equals(playerId)) {
            return secondPlayerStats;
        }
        return null;
    }

    public UUID getBoxingWinnerId() {
        return boxingWinnerId;
    }

    /** Locks the result while the final hit finishes its normal server physics. */
    public synchronized boolean claimBoxingWinner(UUID playerId) {
        MatchParticipantStats stats = getStats(playerId);
        if (!BoxingRules.isBoxing(kitId) || state != MatchState.FIGHTING
                || boxingWinnerId != null || stats == null
                || stats.getHits() < BoxingRules.HITS_TO_WIN) {
            return false;
        }
        boxingWinnerId = playerId;
        return true;
    }

    public synchronized boolean beginEnding() {
        if (state == MatchState.ENDING || state == MatchState.FINISHED) {
            return false;
        }
        state = MatchState.ENDING;
        return true;
    }
}
