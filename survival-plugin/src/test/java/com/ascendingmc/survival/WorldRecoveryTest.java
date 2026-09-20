package com.ascendingmc.survival;

import org.junit.Test;
import static org.junit.Assert.*;

public final class WorldRecoveryTest {
    @Test public void interruptedDeleteBeforeMoveRemainsActive() {
        assertEquals("ACTIVE", WorldRecovery.finishPending("DELETE_PENDING", true, false));
    }
    @Test public void interruptedDeleteAfterMoveRemainsRecoverablyDeleted() {
        assertEquals("DELETED", WorldRecovery.finishPending("DELETE_PENDING", false, true));
    }
    @Test public void interruptedRestoreBeforeMoveRemainsDeleted() {
        assertEquals("DELETED", WorldRecovery.finishPending("RESTORE_PENDING", false, true));
    }
    @Test public void interruptedRestoreAfterMoveRemainsActive() {
        assertEquals("ACTIVE", WorldRecovery.finishPending("RESTORE_PENDING", true, false));
    }
    @Test public void bothMissingOrBothPresentRequireManualRecovery() {
        assertThrows(IllegalStateException.class, () -> WorldRecovery.finishPending("DELETE_PENDING", false, false));
        assertThrows(IllegalStateException.class, () -> WorldRecovery.finishPending("RESTORE_PENDING", true, true));
    }
    @Test public void cannotRecoverNonPendingState() {
        assertThrows(IllegalArgumentException.class, () -> WorldRecovery.finishPending("ACTIVE", true, false));
    }
}
