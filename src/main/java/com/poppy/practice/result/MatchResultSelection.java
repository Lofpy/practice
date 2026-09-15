package com.poppy.practice.result;

import java.util.UUID;

/** Trusted menu context: inventory contents and player item lore never define actions. */
final class MatchResultSelection {
    private final UUID viewerId;
    private final MatchResult result;
    private final UUID participantId;

    MatchResultSelection(UUID viewerId, MatchResult result, UUID participantId) {
        this.viewerId = viewerId;
        this.result = result;
        this.participantId = participantId;
    }

    UUID target(UUID actualViewer, MatchResult latest, int rawSlot) {
        if (!viewerId.equals(actualViewer) || latest != result) {
            return null;
        }
        if (participantId == null) {
            if (rawSlot == 11) {
                return result.getFirst().getPlayerId();
            }
            return rawSlot == 15 ? result.getSecond().getPlayerId() : null;
        }
        if (rawSlot != 45 && rawSlot != 53) {
            return null;
        }
        MatchParticipantSnapshot other = result.getOpponent(participantId);
        return other == null ? null : other.getPlayerId();
    }
}
