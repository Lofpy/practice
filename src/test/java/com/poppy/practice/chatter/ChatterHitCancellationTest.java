package com.poppy.practice.chatter;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatterHitCancellationTest {
    @Test
    public void confirmedChatterHitIsDisabledInBothActiveModes() {
        assertTrue(ChatterKbService.shouldDisableConfirmedDuplicate(
                ChatterKbMode.CHATTER_ONLY, true, true));
        assertTrue(ChatterKbService.shouldDisableConfirmedDuplicate(
                ChatterKbMode.STRICT_NORMALIZE, true, true));
        assertFalse(ChatterKbService.shouldDisableConfirmedDuplicate(
                ChatterKbMode.DETECT_ONLY, true, true));
        assertFalse(ChatterKbService.shouldDisableConfirmedDuplicate(
                ChatterKbMode.CHATTER_ONLY, false, true));
        assertFalse(ChatterKbService.shouldDisableConfirmedDuplicate(
                ChatterKbMode.CHATTER_ONLY, true, false));
    }
}
