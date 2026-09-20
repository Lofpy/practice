package com.poppy.practice.spectator;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchState;

import java.util.UUID;

/** A spectator is bound to one match, never to a player's subsequent match. */
final class SpectatorTarget {
    private final UUID participantId;
    private final Match match;
    private final BotMatch botMatch;

    SpectatorTarget(UUID participantId, Match match) {
        this.participantId = participantId;
        this.match = match;
        this.botMatch = null;
    }

    SpectatorTarget(UUID participantId, BotMatch match) {
        this.participantId = participantId;
        this.match = null;
        this.botMatch = match;
    }

    UUID getParticipantId() { return participantId; }
    UUID getMatchId() { return match == null ? botMatch.getId() : match.getId(); }
    Match getMatch() { return match; }
    BotMatch getBotMatch() { return botMatch; }
    MatchState getState() { return match == null ? botMatch.getState() : match.getState(); }

    boolean canJoin() {
        return getState() == MatchState.STARTING || getState() == MatchState.FIGHTING;
    }

    boolean canContinue() { return canJoin() || getState() == MatchState.ENDING; }

    boolean contains(UUID playerId) {
        return match == null ? botMatch.getPlayerId().equals(playerId)
                : match.getFirstPlayerId().equals(playerId) || match.getSecondPlayerId().equals(playerId);
    }
}
