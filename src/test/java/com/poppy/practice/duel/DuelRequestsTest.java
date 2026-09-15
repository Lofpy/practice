package com.poppy.practice.duel;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class DuelRequestsTest {
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test public void invitationBelongsToExactRecipientAndKit() {
        DuelRequests requests = new DuelRequests();
        DuelRequests.Request request = requests.send(first, second, "boxing", 1000L);
        assertSame(request, requests.find(first, second, 1000L));
        assertNull(requests.find(first, third, 1000L));
        assertNull(requests.find(second, first, 1000L));
        assertEquals("boxing", request.kit);
    }

    @Test public void expiryIsExactlySixtySeconds() {
        DuelRequests requests = new DuelRequests();
        DuelRequests.Request request = requests.send(first, second, "nodebuff", 1000L);
        assertSame(request, requests.find(first, second, 60999L));
        assertNull(requests.find(first, second, 61000L));
    }

    @Test public void spamCooldownDoesNotReplaceOriginalInvitation() {
        DuelRequests requests = new DuelRequests();
        DuelRequests.Request firstRequest = requests.send(first, second, "nodebuff", 1000L);
        assertNull(requests.send(first, third, "combo", 3999L));
        assertSame(firstRequest, requests.find(first, second, 3999L));
        assertNotNull(requests.send(first, third, "combo", 4000L));
        assertNull(requests.find(first, second, 4000L));
        assertEquals("combo", requests.find(first, third, 4000L).kit);
    }

    @Test public void staleRemovalCannotEraseAReplacementRequest() {
        DuelRequests requests = new DuelRequests();
        DuelRequests.Request previous = requests.send(first, second, "boxing", 1000L);
        DuelRequests.Request current = requests.send(first, second, "combo", 4000L);
        requests.remove(previous);
        assertSame(current, requests.find(first, second, 4000L));
        requests.remove(current);
        assertNull(requests.find(first, second, 4000L));
    }

    @Test public void quittingClearsBothIncomingAndOutgoingWithoutAffectingOthers() {
        DuelRequests requests = new DuelRequests();
        requests.send(first, second, "boxing", 1000L);
        requests.send(third, first, "combo", 1000L);
        DuelRequests.Request unaffected = requests.send(second, third, "nodebuff", 1000L);
        requests.removePlayer(first);
        assertNull(requests.find(first, second, 1001L));
        assertNull(requests.find(third, first, 1001L));
        assertSame(unaffected, requests.find(second, third, 1001L));
    }

    @Test public void shutdownClearsEveryInvitation() {
        DuelRequests requests = new DuelRequests();
        requests.send(first, second, "boxing", 1000L);
        requests.clear();
        assertNull(requests.find(first, second, 1000L));
    }

    @Test(expected = IllegalArgumentException.class) public void cannotInviteYourself() {
        new DuelRequests().send(first, first, "boxing", 1000L);
    }

    @Test(expected = IllegalArgumentException.class) public void missingKitCannotBeInvited() {
        new DuelRequests().send(first, second, null, 1000L);
    }
}
