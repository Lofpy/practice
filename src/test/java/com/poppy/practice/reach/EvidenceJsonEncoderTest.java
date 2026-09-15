package com.poppy.practice.reach;

import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class EvidenceJsonEncoderTest {
    private final EvidenceJsonEncoder encoder = new EvidenceJsonEncoder();

    @Test
    public void encodesFieldsAndEscapesJsonControlCharacters() {
        EvidenceRecord record = EvidenceRecord.builder(
                        OffsetDateTime.parse("2026-08-20T21:00:00.123+09:00"))
                .attacker(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "A\"\\\n\t\u0001\uD83D\uDE00")
                .target(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "Target")
                .protocol(47)
                .mode(ReachGuardMode.PROTECT)
                .decision(ReachDecision.CANCEL_REACH)
                .reason("line\u2028break")
                .reliability(Reliability.HIGH)
                .reach(3.27D, 3.08D, 0.19D)
                .violationLevels(6.4D, 1.25D)
                .network(74, 11)
                .server(20.0D, 49.5D, 12345L)
                .candidateCounts(2, 4)
                .sequences(18421L, 41882L, 9281L)
                .snapshotAgeMs(42L)
                .build();

        String json = encoder.encode(record);

        assertTrue(json.startsWith("{\"timestamp\":\"2026-08-20T21:00:00.123+09:00\""));
        assertTrue(json.contains("\"attackerName\":\"A\\\"\\\\\\n\\t\\u0001"
                + "\\uD83D\\uDE00\""));
        assertTrue(json.contains("\"reason\":\"line\\u2028break\""));
        assertTrue(json.contains("\"decision\":\"CANCEL_REACH\",\"cancelled\":true"));
        assertTrue(json.contains("\"measuredReach\":3.27"));
        assertTrue(json.contains("\"vl\":6.4,\"staleVl\":1.25"));
        assertTrue(json.contains("\"attackerCandidateCount\":2,\"targetCandidateCount\":4"));
        assertTrue(json.endsWith("\"snapshotAgeMs\":42}"));
    }

    @Test
    public void writesNonFiniteFloatingPointValuesAsNull() {
        EvidenceRecord record = EvidenceRecord.builder(
                        OffsetDateTime.parse("2026-08-20T12:00:00Z"))
                .reach(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
                .violationLevels(Double.NaN, Double.POSITIVE_INFINITY)
                .server(Double.NEGATIVE_INFINITY, Double.NaN, 1L)
                .build();

        String json = encoder.encode(record);

        assertTrue(json.contains("\"measuredReach\":null"));
        assertTrue(json.contains("\"allowedReach\":null"));
        assertTrue(json.contains("\"excess\":null"));
        assertTrue(json.contains("\"vl\":null"));
        assertTrue(json.contains("\"staleVl\":null"));
        assertTrue(json.contains("\"tps\":null"));
        assertTrue(json.contains("\"tickDurationMs\":null"));
        assertFalse(json.contains("NaN"));
        assertFalse(json.contains("Infinity"));
    }

    @Test
    public void capturesImmutableReplayContextForEveryCandidate() {
        long now = 2_000_000_000L;
        MovementFrame attacker = new MovementFrame(7L, now - 10_000_000L,
                100, 10.0D, 64.0D, -3.0D, 90.0F, 12.5F,
                true, true, false, true, true);
        UUID targetId = UUID.fromString(
                "00000000-0000-0000-0000-000000000003");
        Aabb selectedBox = new Aabb(1.0D, 2.0D, 3.0D,
                2.0D, 4.0D, 5.0D);
        TargetFrame selected = new TargetFrame(44L, now - 25_000_000L,
                now - 5_000_000L, 99, 73, targetId, selectedBox,
                TargetFrameStatus.CONFIRMED, true, true, 9L);
        TargetFrame stale = new TargetFrame(40L, now - 500_000_000L,
                now - 400_000_000L, 90, 73, targetId,
                new Aabb(-1.0D, 1.0D, -1.0D, 0.0D, 3.0D, 0.0D),
                TargetFrameStatus.CONFIRMED, false, true, 8L);
        List<MovementFrame> attackers = new ArrayList<MovementFrame>(
                Arrays.asList(attacker));
        List<TargetFrame> targets = new ArrayList<TargetFrame>(
                Arrays.asList(selected));
        List<String> exceptions = new ArrayList<String>(Arrays.asList(
                "TARGET_TELEPORT_GRACE", "line\nexception"));

        EvidenceRecord record = EvidenceRecord.builder(
                        OffsetDateTime.parse("2026-08-20T12:00:00Z"))
                .protocol(5)
                .mode(ReachGuardMode.OBSERVE)
                .configuredMode(ReachGuardMode.STRICT)
                .decision(ReachDecision.ALLOW_UNVERIFIED)
                .reason("LOW_RELIABILITY")
                .reliability(Reliability.LOW)
                .reach(3.27D, 3.08D, 0.19D)
                .geometry(attacker, selectedBox, 0.25D)
                .reachConfiguration(3.0D, 0.20D, 0.05D)
                .violationComputation(0.27D, 3.7D, 1.0D, 10.0D, 4.0D)
                .attackerCandidates(attackers, now)
                .targetCandidates(targets, now)
                .newestTargetState(selected, now)
                .staleAnchor(stale, now)
                .currentRuntimeCandidate(73, targetId,
                        Aabb.player(8.0D, 64.0D, 9.0D))
                .unverifiedContext(false, true, exceptions)
                .targetTeleportGrace(true)
                .context(73, false, true, 82L, 301L)
                .droppedEvidenceCount(17L)
                .build();

        attackers.clear();
        targets.clear();
        exceptions.clear();

        assertEquals(1, record.getAttackerCandidates().size());
        assertEquals(1, record.getTargetCandidates().size());
        assertEquals(2, record.getUnverifiedExceptions().size());
        assertEquals(65.54D, record.getAttackerEyeY(), 0.000001D);
        assertEquals(0.75D, record.getSelectedExpandedMinX(), 0.000001D);
        assertTrue(record.isAttackerSneaking());
        assertTrue(record.isTargetTeleportGrace());
        try {
            record.getTargetCandidates().clear();
            fail("candidate list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: evidence may be handed to the asynchronous logger safely.
        }

        String json = encoder.encode(record);
        assertTrue(json.contains("\"configuredMode\":\"STRICT\""));
        assertTrue(json.contains("\"unsupportedProtocol\":false"));
        assertTrue(json.contains("\"attackerEyeY\":65.54"));
        assertTrue(json.contains("\"selectedExpandedMinX\":0.75"));
        assertTrue(json.contains("\"vlExcess\":0.27,\"vlAdded\":3.7"));
        assertTrue(json.contains("\"attackerCandidates\":[{\"sequence\":7"));
        assertTrue(json.contains("\"targetCandidates\":[{\"sequence\":44,"
                + "\"status\":\"CONFIRMED\",\"ageMs\":25"));
        assertTrue(json.contains("\"teleportEpoch\":9"));
        assertTrue(json.contains("\"newestTargetState\":{\"sequence\":44"));
        assertTrue(json.contains("\"staleAnchor\":{\"sequence\":40"));
        assertTrue(json.contains("\"currentRuntimeCandidate\":{\"sequence\":-10,"
                + "\"status\":\"CURRENT_RUNTIME\""));
        assertTrue(json.contains("\"targetTeleportGrace\":true"));
        assertTrue(json.contains("\"droppedEvidenceCount\":17"));
        assertTrue(json.contains("\"unverifiedExceptions\":["
                + "\"TARGET_TELEPORT_GRACE\",\"line\\nexception\"]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullRecords() {
        encoder.encode(null);
    }
}
