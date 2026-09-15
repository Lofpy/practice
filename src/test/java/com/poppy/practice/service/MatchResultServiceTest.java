package com.poppy.practice.service;

import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchParticipantStats;
import com.poppy.practice.result.MatchResult;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class MatchResultServiceTest {
    @Test
    public void commandAcceptsParticipantNamesAndChatUuidButRejectsOtherPlayers() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        MatchResult result = new MatchResult(firstId,
                MatchParticipantSnapshot.capture(firstId, "First", null, new MatchParticipantStats()),
                MatchParticipantSnapshot.capture(secondId, "Second", null, new MatchParticipantStats()), 60);

        assertEquals(firstId, MatchResultService.participantId(result, "fIrSt"));
        assertEquals(secondId, MatchResultService.participantId(result, secondId.toString()));
        assertNull(MatchResultService.participantId(result, "SomeoneElse"));
        assertNull(MatchResultService.participantId(result, UUID.randomUUID().toString()));
        assertNull(MatchResultService.participantId(null, "First"));
    }
}
