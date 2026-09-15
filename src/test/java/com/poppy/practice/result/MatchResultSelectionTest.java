package com.poppy.practice.result;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class MatchResultSelectionTest {
    private final UUID viewer = UUID.randomUUID();
    private final MatchParticipantSnapshot first = MatchParticipantSnapshot.capture(
            UUID.randomUUID(), "First", null, new MatchParticipantStats());
    private final MatchParticipantSnapshot second = MatchParticipantSnapshot.capture(
            UUID.randomUUID(), "Second", null, new MatchParticipantStats());
    private final MatchResult result = new MatchResult(first.getPlayerId(), first, second, 60);

    @Test
    public void navigationTargetsOnlyTheOtherParticipantUsingBothArrows() {
        MatchResultSelection selection = new MatchResultSelection(viewer, result, first.getPlayerId());
        assertEquals(second.getPlayerId(), selection.target(viewer, result, 45));
        assertEquals(second.getPlayerId(), selection.target(viewer, result, 53));
        MatchResultSelection reverse = new MatchResultSelection(viewer, result, second.getPlayerId());
        assertEquals(first.getPlayerId(), reverse.target(viewer, result, 45));
    }

    @Test
    public void inventoryContentsAndPlayerInventorySlotsCannotTriggerNavigation() {
        MatchResultSelection selection = new MatchResultSelection(viewer, result, first.getPlayerId());
        for (int slot = -1; slot < 90; slot++) {
            if (slot != 45 && slot != 53) {
                assertNull(selection.target(viewer, result, slot));
            }
        }
    }

    @Test
    public void rejectsAnotherViewerAndResultReplacedWhileMenuWasOpen() {
        MatchResultSelection selection = new MatchResultSelection(viewer, result, first.getPlayerId());
        assertNull(selection.target(UUID.randomUUID(), result, 45));
        assertNull(selection.target(viewer, null, 45));
        assertNull(selection.target(viewer,
                new MatchResult(second.getPlayerId(), first, second, 120), 45));
    }

    @Test
    public void overviewAcceptsOnlyParticipantSlots() {
        MatchResultSelection selection = new MatchResultSelection(viewer, result, null);
        assertEquals(first.getPlayerId(), selection.target(viewer, result, 11));
        assertEquals(second.getPlayerId(), selection.target(viewer, result, 15));
        assertNull(selection.target(viewer, result, 13));
        assertNull(selection.target(viewer, result, 38));
    }
}
