package com.poppy.practice.bot;

import com.poppy.practice.match.MatchState;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.result.MatchParticipantStats;

import java.util.UUID;

public final class BotMatch {
    private final UUID id = UUID.randomUUID();
    private final UUID playerId;
    private final UUID botEntityId;
    private final String kitId;
    private final String arenaId;
    private final boolean placement;
    private final long createdAt = System.currentTimeMillis();
    private final MatchParticipantStats playerStats = new MatchParticipantStats();
    private final MatchParticipantStats botStats = new MatchParticipantStats();
    private MatchState state = MatchState.STARTING;
    private long startedAt;
    private Integer countdownTaskId;
    private Integer aiTaskId;
    private Integer finishTaskId;
    private int botRemainingHealingPotions;
    private UUID boxingWinnerId;

    public BotMatch(UUID playerId, UUID botEntityId, String kitId, String arenaId) {
        this(playerId, botEntityId, kitId, arenaId, false);
    }

    public BotMatch(UUID playerId, UUID botEntityId, String kitId, String arenaId, boolean placement) {
        if (playerId == null || botEntityId == null || kitId == null || arenaId == null) {
            throw new IllegalArgumentException("Bot match values cannot be null");
        }
        if (playerId.equals(botEntityId)) {
            throw new IllegalArgumentException("A bot match requires two different participants");
        }
        this.playerId = playerId;
        this.botEntityId = botEntityId;
        this.kitId = kitId;
        this.arenaId = arenaId;
        this.placement = placement;
    }

    public UUID getId() {
        return id;
    }

    public boolean isPlacement() {
        return placement;
    }

    public Integer getFinishTaskId() {
        return finishTaskId;
    }

    public void setFinishTaskId(Integer finishTaskId) {
        this.finishTaskId = finishTaskId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getBotEntityId() {
        return botEntityId;
    }

    public String getKitId() {
        return kitId;
    }

    public boolean isBoxing() {
        return BoxingRules.isBoxing(kitId);
    }

    public boolean allowsConsumables() {
        return !isBoxing();
    }

    public boolean isCombo() {
        return ComboRules.isCombo(kitId);
    }

    public boolean allowsHealingPotions() {
        return allowsConsumables() && !isCombo();
    }

    public UUID getOpponent(UUID participantId) {
        if (playerId.equals(participantId)) {
            return botEntityId;
        }
        return botEntityId.equals(participantId) ? playerId : null;
    }

    public boolean canScoreBoxingHit(UUID attackerId, UUID victimId) {
        return isBoxing() && state == MatchState.FIGHTING && boxingWinnerId == null
                && attackerId != null && victimId != null
                && victimId.equals(getOpponent(attackerId));
    }

    public UUID getBoxingWinnerId() {
        return boxingWinnerId;
    }

    public synchronized boolean claimBoxingWinner(UUID participantId) {
        MatchParticipantStats stats = getStats(participantId);
        if (!isBoxing() || state != MatchState.FIGHTING || boxingWinnerId != null
                || stats == null || stats.getHits() < BoxingRules.HITS_TO_WIN) {
            return false;
        }
        boxingWinnerId = participantId;
        return true;
    }

    public String getArenaId() {
        return arenaId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public MatchState getState() {
        return state;
    }

    public synchronized void markFighting() {
        if (state != MatchState.STARTING) {
            return;
        }
        startedAt = System.currentTimeMillis();
        state = MatchState.FIGHTING;
    }

    public Integer getCountdownTaskId() {
        return countdownTaskId;
    }

    public void setCountdownTaskId(Integer countdownTaskId) {
        this.countdownTaskId = countdownTaskId;
    }

    public Integer getAiTaskId() {
        return aiTaskId;
    }

    public void setAiTaskId(Integer aiTaskId) {
        this.aiTaskId = aiTaskId;
    }

    public MatchParticipantStats getStats(UUID participantId) {
        if (playerId.equals(participantId)) {
            return playerStats;
        }
        if (botEntityId.equals(participantId)) {
            return botStats;
        }
        return null;
    }

    public int getBotRemainingHealingPotions() {
        return botRemainingHealingPotions;
    }

    public void setBotRemainingHealingPotions(int botRemainingHealingPotions) {
        this.botRemainingHealingPotions = allowsHealingPotions()
                ? Math.max(0, botRemainingHealingPotions) : 0;
    }

    public synchronized boolean beginEnding() {
        if (state == MatchState.ENDING || state == MatchState.FINISHED) {
            return false;
        }
        state = MatchState.ENDING;
        return true;
    }

    public void markFinished() {
        state = MatchState.FINISHED;
    }
}
