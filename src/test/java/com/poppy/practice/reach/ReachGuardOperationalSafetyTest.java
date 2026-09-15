package com.poppy.practice.reach;

import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReachGuardOperationalSafetyTest {
    @Test
    public void expiredResyncCooldownsArePurgedWithoutRemovingFutureEntries() {
        ConcurrentMap<String, Long> cooldowns =
                new ConcurrentHashMap<String, Long>();
        cooldowns.put("expired", 999L);
        cooldowns.put("boundary", 1_000L);
        cooldowns.put("future", 1_001L);

        assertEquals(2, ReachGuardService.purgeExpiredCooldowns(
                cooldowns, 1_000L));
        assertFalse(cooldowns.containsKey("expired"));
        assertFalse(cooldowns.containsKey("boundary"));
        assertEquals(Long.valueOf(1_001L), cooldowns.get("future"));
    }

    @Test
    public void normalAllowsStayOutOfInspectionButUnverifiedAndViolationsRemain() {
        assertFalse(ReachGuardService.shouldRetainForInspection(
                result(ReachDecision.ALLOW)));
        assertTrue(ReachGuardService.shouldRetainForInspection(
                result(ReachDecision.ALLOW_UNVERIFIED)));
        assertTrue(ReachGuardService.shouldRetainForInspection(
                result(ReachDecision.FLAG)));
        assertTrue(ReachGuardService.shouldRetainForInspection(
                result(ReachDecision.CANCEL_REACH)));
    }

    @Test
    public void inspectSummaryShowsEveryIndependentViolationLevel() {
        EvidenceRecord record = EvidenceRecord.builder(
                        OffsetDateTime.parse("2026-08-20T12:34:56Z"))
                .decision(ReachDecision.CANCEL_INVALID_ENTITY)
                .reason("DAMAGE_WITHOUT_PERMIT")
                .reliability(Reliability.HIGH)
                .reach(3.40D, 3.08D, 0.32D)
                .violationLevels(6.40D, 1.25D, 2.75D)
                .build();

        String summary = ReachGuardCommand.inspectionSummary(record);

        assertTrue(summary.contains("12:34:56 CANCEL_INVALID_ENTITY"));
        assertTrue(summary.contains("VL(R/S/I)=6.400/1.250/2.750"));
    }

    @Test
    public void operationalStatusShowsBothClientProtocolsAsSupported() {
        ReachGuardConfig config = ReachGuardConfig.load(null);

        assertEquals("SUPPORTED/OBSERVE",
                ReachGuardCommand.protocolProtectionStatus(config, 5));
        assertEquals("SUPPORTED/OBSERVE",
                ReachGuardCommand.protocolProtectionStatus(config, 47));
        assertEquals("UNSUPPORTED/FAIL_OPEN",
                ReachGuardCommand.protocolProtectionStatus(config, 754));
    }

    private static ReachResult result(ReachDecision decision) {
        return new ReachResult(decision, 3.0D, 3.03D, Reliability.HIGH,
                "TEST", 1L, 2L, 1, 1, 0L);
    }
}
