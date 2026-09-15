package com.poppy.practice.reach;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReachValidationEngineTest {
    private static final long NOW = 1000000000L;
    private static final double ERROR = 0.000001D;

    private final ReachGuardConfig config = ReachGuardConfig.load(null);
    private final ReachValidationEngine engine = new ReachValidationEngine();

    @Test
    public void measurementSelectsMinimumAcrossEveryCombination() {
        MovementFrame nearAttacker = movement(11L, 0.0D);
        MovementFrame farAttacker = movement(12L, 20.0D);
        TargetFrame farTarget = target(21L, 10.0D);
        TargetFrame nearTarget = target(22L, 3.0D);

        ReachValidationEngine.Measurement measured = ReachValidationEngine.measure(
                Arrays.asList(nearAttacker, farAttacker),
                Arrays.asList(farTarget, nearTarget), config.getTotalExpansion());

        assertEquals(3.0D - 0.3D - config.getTotalExpansion(),
                measured.distance, ERROR);
        assertEquals(11L, measured.attacker.getSequence());
        assertEquals(22L, measured.target.getStateSequence());
    }

    @Test
    public void oneReachableCandidateAllowsAttack() {
        Fixture fixture = fixtureWithOldAndNewTarget(2.8D, 8.0D, 47, 10);

        ReachResult result = validate(fixture, ReachGuardMode.PROTECT, 10);

        assertEquals(ReachDecision.ALLOW, result.getDecision());
        assertEquals(2.8D, result.getMeasuredReach(), ERROR);
        assertEquals(5, result.getTargetCandidateCount());
    }

    @Test
    public void observeNeverCancelsReachExcess() {
        Fixture fixture = fixture(8.0D, 47, 10, false);

        ReachResult result = validate(fixture, ReachGuardMode.OBSERVE, 10);

        assertEquals(ReachDecision.FLAG, result.getDecision());
        assertFalse(result.getDecision().isCancelled());
    }

    @Test
    public void defaultModeObservesExcessOnBothSupportedProtocolsAndReliabilities() {
        for (int protocol : new int[] { 5, 47 }) {
            for (boolean medium : new boolean[] { false, true }) {
                for (double reach : new double[] { 3.0D, 3.03D, 8.0D }) {
                    ReachResult result = validate(fixture(reach, protocol, 10, medium),
                            config.getMode(), 10);

                    assertEquals(ReachDecision.FLAG, result.getDecision());
                    assertFalse(result.getDecision().isCancelled());
                    assertEquals(medium ? Reliability.MEDIUM : Reliability.HIGH,
                            result.getReliability());
                    assertEquals("OBSERVE_ONLY", result.getReason());
                }
            }
        }
    }

    @Test
    public void defaultModeAllowsLegalReachOnBothSupportedProtocols() {
        for (int protocol : new int[] { 5, 47 }) {
            ReachResult result = validate(fixture(2.999D, protocol, 10, false),
                    config.getMode(), 10);

            assertEquals(ReachDecision.ALLOW, result.getDecision());
            assertFalse(result.getDecision().isCancelled());
            assertFalse(ReachViolationPolicy.records(result));
        }
    }

    @Test
    public void defaultModeObservesStaleAttacksOnBothSupportedProtocols() {
        for (int protocol : new int[] { 5, 47 }) {
            ReachResult result = validate(staleFixture(protocol), config.getMode(), 10);

            assertEquals(ReachDecision.FLAG, result.getDecision());
            assertEquals("STALE_ATTACK", result.getReason());
            assertFalse(result.getDecision().isCancelled());
            assertTrue(ReachViolationPolicy.records(result));
            assertEquals(ViolationCategory.STALE, ReachViolationPolicy.category(result));
        }
    }

    @Test
    public void defaultModeObservesUnknownAndDestroyedTargetsOnBothSupportedProtocols() {
        for (int protocol : new int[] { 5, 47 }) {
            PlayerSession session = new PlayerSession(UUID.randomUUID(), protocol,
                    config, runtime(0), NOW);
            UUID targetId = UUID.randomUUID();
            session.spawnTarget(7, targetId, 3.0D, 0.0D, 0.0D,
                    0, NOW + 1L, config);
            session.destroyTarget(7);
            for (int targetEntityId : new int[] { 7, 999999 }) {
                PlayerSession.AttackSnapshot attack = session.createAttackSnapshot(
                        targetEntityId, null, 10, NOW + 2L,
                        config, new CandidateStateResolver());
                ReachResult result = engine.validate(attack, config, config.getMode(),
                        new ServerHealthSnapshot(10, 20.0D, 50L, false),
                        null, NOW + 2L);

                assertEquals(ReachDecision.FLAG, result.getDecision());
                assertEquals("INVALID_ENTITY", result.getReason());
                assertFalse(result.getDecision().isCancelled());
                assertTrue(ReachViolationPolicy.records(result));
                assertEquals(ViolationCategory.INVALID_ENTITY,
                        ReachViolationPolicy.category(result));
            }
        }
    }

    @Test
    public void defaultObservedReachStillAccumulatesEvidenceAndViolationLevels() {
        for (int protocol : new int[] { 5, 47 }) {
            ReachResult result = validate(fixture(3.5D, protocol, 10, false),
                    config.getMode(), 10);
            ViolationTracker tracker = new ViolationTracker(config);
            ViolationSnapshot violation = tracker.record(UUID.randomUUID(), result,
                    config.getBaseReach(), NOW);

            assertFalse(result.getDecision().isCancelled());
            assertTrue(ReachGuardService.shouldRetainForInspection(result));
            assertTrue(violation.getReachVl() >= config.getAlertMinimumVl());
            assertEquals(0.0D, violation.getStaleVl(), ERROR);
            assertEquals(0.0D, violation.getInvalidEntityVl(), ERROR);
        }
    }

    @Test
    public void nullInternalContextAlwaysFailsOpenAtLowReliability() {
        ServerHealthSnapshot health = new ServerHealthSnapshot(
                10, 20.0D, 50L, false);
        for (ReachGuardMode mode : ReachGuardMode.values()) {
            ReachResult result = engine.validate(null, config, mode,
                    health, null, NOW);
            assertEquals(ReachDecision.ALLOW_UNVERIFIED, result.getDecision());
            assertEquals(Reliability.LOW, result.getReliability());
            assertFalse(result.getDecision().isCancelled());
        }

        ReachResult missingConfig = engine.validate(null, null,
                ReachGuardMode.PROTECT, health, null, NOW);
        assertEquals(ReachDecision.ALLOW_UNVERIFIED,
                missingConfig.getDecision());
        assertEquals(Reliability.LOW, missingConfig.getReliability());

        Fixture fixture = fixture(8.0D, 47, 10, false);
        ReachResult missingMode = engine.validate(fixture.attack, config,
                null, health, fixture.currentTargetBox, fixture.now);
        assertEquals(ReachDecision.ALLOW_UNVERIFIED,
                missingMode.getDecision());
        assertEquals(Reliability.LOW, missingMode.getReliability());
    }

    @Test
    public void destroyedPlayerEntityIsCancelledOnlyInEnforcingMode() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, 3.0D, 0.0D, 0.0D,
                0, NOW + 1L, config);
        session.destroyTarget(7);
        PlayerSession.AttackSnapshot attack = session.createAttackSnapshot(
                7, null, 10, NOW + 2L, config, new CandidateStateResolver());
        ServerHealthSnapshot health = new ServerHealthSnapshot(
                10, 20.0D, 50L, false);

        ReachResult observed = engine.validate(attack, config,
                ReachGuardMode.OBSERVE, health, null, NOW + 2L);
        ReachResult protectedResult = engine.validate(attack, config,
                ReachGuardMode.PROTECT, health, null, NOW + 2L);

        assertEquals(ReachDecision.FLAG, observed.getDecision());
        assertEquals(ReachDecision.CANCEL_INVALID_ENTITY,
                protectedResult.getDecision());
    }

    @Test
    public void unknownEntityIdIsInvalidExceptDuringFailOpenConditions() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        PlayerSession.AttackSnapshot attack = session.createAttackSnapshot(
                999999, null, 10, NOW + 1L, config,
                new CandidateStateResolver());
        ServerHealthSnapshot health = new ServerHealthSnapshot(
                10, 20.0D, 50L, false);

        assertEquals(ReachDecision.FLAG, engine.validate(attack, config,
                ReachGuardMode.OBSERVE, health, null, NOW + 1L).getDecision());
        assertEquals(ReachDecision.CANCEL_INVALID_ENTITY,
                engine.validate(attack, config, ReachGuardMode.PROTECT,
                        health, null, NOW + 1L).getDecision());
    }

    @Test
    public void capacityEvictedKnownPlayerFailsOpenInsteadOfBecomingInvalid() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID evictedPlayer = UUID.randomUUID();
        session.spawnTarget(7, evictedPlayer, 3.0D, 0.0D, 0.0D,
                0, NOW + 1L, config);
        for (int index = 0; index < config.getMaxTargetsPerViewer(); index++) {
            session.spawnTarget(100 + index, UUID.randomUUID(),
                    3.0D, 0.0D, 0.0D, 0, NOW + 2L + index, config);
        }

        PlayerSession.AttackSnapshot attack = session.createAttackSnapshot(
                7, null, 10, NOW + 100L, config,
                new CandidateStateResolver());
        ReachResult result = engine.validate(attack, config,
                ReachGuardMode.PROTECT, new ServerHealthSnapshot(
                10, 20.0D, 50L, false), null, NOW + 100L);

        assertEquals(ReachDecision.ALLOW_UNVERIFIED, result.getDecision());
        assertEquals(evictedPlayer, attack.getTargetUuid());
        assertFalse(attack.isInvalidTarget());

        session.destroyTarget(7);
        PlayerSession.AttackSnapshot destroyed = session.createAttackSnapshot(
                7, null, 11, NOW + 101L, config,
                new CandidateStateResolver());
        assertTrue(destroyed.isInvalidTarget());
        assertEquals(ReachDecision.CANCEL_INVALID_ENTITY,
                engine.validate(destroyed, config, ReachGuardMode.PROTECT,
                        new ServerHealthSnapshot(11, 20.0D, 50L, false),
                        null, NOW + 101L).getDecision());
    }

    @Test
    public void reusedEntityIdPrefersCurrentFallbackUuidAndResyncs() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID oldPlayer = UUID.randomUUID();
        UUID currentPlayer = UUID.randomUUID();
        session.spawnTarget(7, oldPlayer, targetX(8.0D), 0.0D, 0.0D,
                0, NOW + 1L, config);
        confirmCurrentTargets(session, NOW + 2L, NOW + 3L);

        PlayerSession.AttackSnapshot reused = session.createAttackSnapshot(
                7, currentPlayer, 10, NOW + 4L, config,
                new CandidateStateResolver());
        ReachResult result = engine.validate(reused, config,
                ReachGuardMode.PROTECT,
                new ServerHealthSnapshot(10, 20.0D, 50L, false),
                Aabb.player(targetX(8.0D), 0.0D, 0.0D), NOW + 4L);

        assertEquals(currentPlayer, reused.getTargetUuid());
        assertFalse(reused.isInvalidTarget());
        assertTrue(reused.getTargetResolution().getCandidates().isEmpty());
        assertTrue(session.needsTargetResync(7));
        assertEquals(ReachDecision.ALLOW_UNVERIFIED, result.getDecision());
        assertEquals(Reliability.LOW, result.getReliability());

        session.seedTarget(7, currentPlayer, targetX(3.0D), 0.0D, 0.0D, 11);
        assertTrue(session.needsTargetResync(7));
        assertTrue(session.teleportTarget(7, targetX(3.0D), 0.0D, 0.0D,
                12, NOW + 5L, config));
        assertFalse(session.needsTargetResync(7));
        PlayerSession.AttackSnapshot recovered = session.createAttackSnapshot(
                7, currentPlayer, 20, NOW + 6L, config,
                new CandidateStateResolver());
        assertEquals(currentPlayer, recovered.getTargetUuid());
        assertFalse(recovered.getTargetResolution().getCandidates().isEmpty());
    }

    @Test
    public void protocolFiveEnforcesProtectAndStrictAtHighReliability() {
        Fixture fixture = fixture(8.0D, 5, 10, false);

        ReachResult protectedResult = validate(
                fixture, ReachGuardMode.PROTECT, 10);
        ReachResult strictResult = validate(
                fixture, ReachGuardMode.STRICT, 10);

        assertEquals(Reliability.HIGH, protectedResult.getReliability());
        assertEquals(ReachDecision.CANCEL_REACH,
                protectedResult.getDecision());
        assertEquals(Reliability.HIGH, strictResult.getReliability());
        assertEquals(ReachDecision.CANCEL_REACH, strictResult.getDecision());
    }

    @Test
    public void protocolFiveCancelsExactThreeAtEveryEnforcingBoundary() {
        Fixture high = fixture(3.0D, 5, 10, false);
        Fixture medium = fixture(3.0D, 5, 10, true);

        ReachResult protectHigh = validate(high, ReachGuardMode.PROTECT, 10);
        ReachResult strictHigh = validate(high, ReachGuardMode.STRICT, 10);
        ReachResult protectMedium = validate(medium, ReachGuardMode.PROTECT, 10);
        ReachResult strictMedium = validate(medium, ReachGuardMode.STRICT, 10);

        assertExactThreeCancellation(protectHigh, Reliability.HIGH);
        assertExactThreeCancellation(strictHigh, Reliability.HIGH);
        assertExactThreeCancellation(protectMedium, Reliability.MEDIUM);
        assertExactThreeCancellation(strictMedium, Reliability.MEDIUM);
    }

    @Test
    public void protocolFiveCanCancelVerifiedStaleAttack() {
        Fixture fixture = staleFixture(5);

        ReachResult result = validate(fixture, ReachGuardMode.PROTECT, 10);

        assertEquals(ReachDecision.CANCEL_STALE_ATTACK, result.getDecision());
    }

    @Test
    public void unknownProtocolFailsOpenForReachAndStaleAttacks() {
        Fixture reach = fixture(8.0D, 4, 10, false);
        Fixture stale = staleFixture(4);

        ReachResult reachResult = validate(reach, ReachGuardMode.STRICT, 10);
        ReachResult staleResult = validate(stale, ReachGuardMode.PROTECT, 10);

        assertEquals(Reliability.LOW, reachResult.getReliability());
        assertEquals(ReachDecision.ALLOW_UNVERIFIED,
                reachResult.getDecision());
        assertFalse(reachResult.getDecision().isCancelled());
        assertEquals(Reliability.LOW, staleResult.getReliability());
        assertEquals(ReachDecision.ALLOW_UNVERIFIED,
                staleResult.getDecision());
        assertFalse(staleResult.getDecision().isCancelled());
    }

    @Test
    public void supportedProtocolCanStillCancelVerifiedStaleAttack() {
        Fixture fixture = staleFixture(47);

        ReachResult result = validate(fixture, ReachGuardMode.PROTECT, 10);

        assertEquals(ReachDecision.CANCEL_STALE_ATTACK, result.getDecision());
    }

    @Test
    public void pendingSyncBeyondHistoryRetentionKeepsTheStaleAnchor() {
        Fixture fixture = staleFixture(47, 1500000000L);

        ReachResult result = validate(fixture, ReachGuardMode.PROTECT, 10);

        assertEquals(ReachDecision.CANCEL_STALE_ATTACK, result.getDecision());
    }

    @Test
    public void evictedUnconfirmedFrameStillSupersedesOldConfirmedState() {
        double oldX = targetX(2.0D);
        double currentX = targetX(8.0D);
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, oldX, 0.0D, 0.0D,
                0, NOW + 1L, config);
        confirmCurrentTargets(session, NOW + 2L, NOW + 3L);
        short delayed = session.reserveSyncId();
        session.markSyncSent(delayed, NOW + 4L);
        session.moveTarget(7, currentX - oldX, 0.0D, 0.0D,
                2, NOW + 5L, config);
        long attackNow = NOW + 1500000004L;
        session.handleMovement(false, 0.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 10, attackNow);
        Fixture fixture = new Fixture(session.createAttackSnapshot(7, targetId,
                10, attackNow, config, new CandidateStateResolver()),
                Aabb.player(currentX, 0.0D, 0.0D), attackNow);

        assertEquals(ReachDecision.CANCEL_STALE_ATTACK,
                validate(fixture, ReachGuardMode.PROTECT, 10).getDecision());
    }

    @Test
    public void oldButCurrentConfirmedStateStillProtectsStaticTarget() {
        double targetX = targetX(3.50D);
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, targetX, 0.0D, 0.0D,
                0, NOW + 1L, config);
        confirmCurrentTargets(session, NOW + 2L, NOW + 3L);
        long attackNow = NOW + 1200000000L;
        session.handleMovement(false, 0.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 10, attackNow);
        Fixture fixture = new Fixture(session.createAttackSnapshot(7, targetId,
                10, attackNow, config, new CandidateStateResolver()),
                Aabb.player(targetX, 0.0D, 0.0D), attackNow);

        assertEquals(ReachDecision.CANCEL_REACH,
                validate(fixture, ReachGuardMode.PROTECT, 10).getDecision());
    }

    @Test
    public void transactionOnlyConfirmsFramesWrittenBeforeItsReservation() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, 3.0D, 0.0D, 0.0D,
                0, NOW + 1L, config);

        short marker = session.reserveSyncId();
        assertFalse(session.confirmSync(marker, NOW + 2L));
        session.moveTarget(7, 1.0D, 0.0D, 0.0D,
                0, NOW + 3L, config);
        session.markSyncSent(marker, NOW + 4L);
        assertTrue(session.confirmSync(marker, NOW + 5L));

        PlayerSession.AttackSnapshot snapshot = session.createAttackSnapshot(
                7, targetId, 1, NOW + 6L, config,
                new CandidateStateResolver());
        assertEquals(TargetFrameStatus.SENT_UNCONFIRMED,
                snapshot.getTargetResolution().getNewest().getStatus());
    }

    @Test
    public void transactionReservationsUseUniqueNegativeActionIds() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        Set<Short> actionIds = new HashSet<Short>();

        for (int index = 0; index < 64; index++) {
            short actionId = session.reserveSyncId();
            assertTrue(actionId < 0);
            assertTrue(actionIds.add(actionId));
        }
    }

    @Test
    public void markerReservedAfterBatchConfirmsEverySpawnInTheBatch() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        session.spawnTarget(7, first, targetX(2.0D), 0.0D, 0.0D,
                0, NOW + 1L, config);
        session.spawnTarget(8, second, targetX(2.0D), 0.0D, 0.0D,
                0, NOW + 2L, config);
        confirmCurrentTargets(session, NOW + 3L, NOW + 4L);

        assertEquals(true, session.createAttackSnapshot(7, first, 10,
                NOW + 5L, config, new CandidateStateResolver())
                .getTargetResolution().hasConfirmed());
        assertEquals(true, session.createAttackSnapshot(8, second, 10,
                NOW + 5L, config, new CandidateStateResolver())
                .getTargetResolution().hasConfirmed());
    }

    @Test
    public void oldCompletedTransactionRttAloneDoesNotProveStaleAttack() {
        Fixture fixture = completedSlowTransactionFixture(47);

        ReachResult result = validate(fixture, ReachGuardMode.PROTECT, 10);

        assertEquals(ReachDecision.ALLOW_UNVERIFIED, result.getDecision());
    }

    @Test
    public void playerAndTargetSpawnGraceFailOpenThroughConfiguredBoundary() {
        Fixture duringGrace = fixture(8.0D, 47, config.getSpawnGraceTicks(), false);
        Fixture afterGrace = fixture(8.0D, 47, config.getSpawnGraceTicks() + 1, false);

        ReachResult low = validate(duringGrace, ReachGuardMode.PROTECT,
                config.getSpawnGraceTicks());
        ReachResult enforced = validate(afterGrace, ReachGuardMode.PROTECT,
                config.getSpawnGraceTicks() + 1);

        assertEquals(Reliability.LOW, low.getReliability());
        assertEquals(ReachDecision.ALLOW_UNVERIFIED, low.getDecision());
        assertEquals(Reliability.HIGH, enforced.getReliability());
        assertEquals(ReachDecision.CANCEL_REACH, enforced.getDecision());
    }

    @Test
    public void targetTeleportGraceSurvivesFollowingRelativeMovement() {
        double targetX = targetX(8.0D);
        PlayerSession session = new PlayerSession(UUID.randomUUID(), 47,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, targetX, 0.0D, 0.0D,
                0, NOW + 1L, config);
        confirmCurrentTargets(session, NOW + 2L, NOW + 3L);
        session.teleportTarget(7, targetX, 0.0D, 0.0D,
                20, NOW + 4L, config);
        confirmCurrentTargets(session, NOW + 5L, NOW + 6L);
        session.moveTarget(7, 0.0D, 0.0D, 0.0D,
                21, NOW + 7L, config);
        confirmCurrentTargets(session, NOW + 8L, NOW + 9L);

        long duringNow = NOW + 10L;
        session.handleMovement(false, 0.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 25, duringNow);
        PlayerSession.AttackSnapshot during = session.createAttackSnapshot(
                7, targetId, 25, duringNow, config, new CandidateStateResolver());
        ReachResult allowed = engine.validate(during, config, ReachGuardMode.PROTECT,
                new ServerHealthSnapshot(25, 20.0D, 50L, false),
                Aabb.player(targetX, 0.0D, 0.0D), duringNow);

        long afterNow = NOW + 11L;
        session.handleMovement(false, 0.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 26, afterNow);
        PlayerSession.AttackSnapshot after = session.createAttackSnapshot(
                7, targetId, 26, afterNow, config, new CandidateStateResolver());
        ReachResult cancelled = engine.validate(after, config, ReachGuardMode.PROTECT,
                new ServerHealthSnapshot(26, 20.0D, 50L, false),
                Aabb.player(targetX, 0.0D, 0.0D), afterNow);

        assertEquals(true, during.isTargetTeleportGrace());
        assertEquals(Reliability.LOW, allowed.getReliability());
        assertEquals(ReachDecision.ALLOW_UNVERIFIED, allowed.getDecision());
        assertFalse(after.isTargetTeleportGrace());
        assertEquals(Reliability.HIGH, cancelled.getReliability());
        assertEquals(ReachDecision.CANCEL_REACH, cancelled.getDecision());
    }

    @Test
    public void stalledMainHeartbeatFailsOpenDuringTheStall() {
        Fixture fixture = fixture(8.0D, 47, 10, false);
        long oldHeartbeat = fixture.now
                - config.getMaximumTickTimeMs() * 1000000L;

        ReachResult result = engine.validate(fixture.attack, config,
                ReachGuardMode.PROTECT,
                new ServerHealthSnapshot(10, 20.0D, 50L, false, oldHeartbeat),
                fixture.currentTargetBox, fixture.now);

        assertEquals(Reliability.LOW, result.getReliability());
        assertEquals(ReachDecision.ALLOW_UNVERIFIED, result.getDecision());
    }

    @Test
    public void protectHighBoundaryIsInclusive() {
        assertEquals(ReachDecision.ALLOW, validate(
                fixture(config.getProtectHighCancel() - 0.0001D, 47, 10, false),
                ReachGuardMode.PROTECT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getProtectHighCancel(), 47, 10, false),
                ReachGuardMode.PROTECT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getProtectHighCancel() + 0.0001D, 47, 10, false),
                ReachGuardMode.PROTECT, 10).getDecision());
    }

    @Test
    public void protectMediumBoundaryIsInclusive() {
        assertEquals(ReachDecision.ALLOW, validate(
                fixture(config.getProtectMediumCancel() - 0.0001D, 47, 10, true),
                ReachGuardMode.PROTECT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getProtectMediumCancel(), 47, 10, true),
                ReachGuardMode.PROTECT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getProtectMediumCancel() + 0.0001D, 47, 10, true),
                ReachGuardMode.PROTECT, 10).getDecision());
    }

    @Test
    public void strictHighBoundaryIsInclusive() {
        assertEquals(ReachDecision.ALLOW, validate(
                fixture(config.getStrictHighCancel() - 0.0001D, 47, 10, false),
                ReachGuardMode.STRICT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getStrictHighCancel(), 47, 10, false),
                ReachGuardMode.STRICT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getStrictHighCancel() + 0.0001D, 47, 10, false),
                ReachGuardMode.STRICT, 10).getDecision());
    }

    @Test
    public void strictMediumBoundaryIsInclusive() {
        assertEquals(ReachDecision.ALLOW, validate(
                fixture(config.getStrictMediumCancel() - 0.0001D, 47, 10, true),
                ReachGuardMode.STRICT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getStrictMediumCancel(), 47, 10, true),
                ReachGuardMode.STRICT, 10).getDecision());
        assertEquals(ReachDecision.CANCEL_REACH, validate(
                fixture(config.getStrictMediumCancel() + 0.0001D, 47, 10, true),
                ReachGuardMode.STRICT, 10).getDecision());
    }

    @Test
    public void exactThreeIsObservedButNotCancelledInObserveMode() {
        ReachResult result = validate(fixture(3.0D, 47, 10, false),
                ReachGuardMode.OBSERVE, 10);

        assertEquals(3.0D, result.getMeasuredReach(), ERROR);
        assertEquals(ReachDecision.FLAG, result.getDecision());
        assertFalse(result.getDecision().isCancelled());
    }

    @Test
    public void lowReliabilityStillFailsOpenAtExactThree() {
        ReachResult result = validate(fixture(3.0D, 4, 10, false),
                ReachGuardMode.STRICT, 10);

        assertEquals(3.0D, result.getMeasuredReach(), ERROR);
        assertEquals(Reliability.LOW, result.getReliability());
        assertEquals(ReachDecision.ALLOW_UNVERIFIED, result.getDecision());
    }

    @Test
    public void staleAttackBoundaryIsInclusiveAtExactThree() {
        ReachResult result = validate(staleFixture(47, 400000000L, 3.0D),
                ReachGuardMode.PROTECT, 10);

        assertEquals(3.0D, result.getMeasuredReach(), ERROR);
        assertEquals(ReachDecision.CANCEL_STALE_ATTACK, result.getDecision());
    }

    private ReachResult validate(Fixture fixture, ReachGuardMode mode, int healthTick) {
        return engine.validate(fixture.attack, config, mode,
                new ServerHealthSnapshot(healthTick, 20.0D, 50L, false),
                fixture.currentTargetBox, fixture.now);
    }

    private static void assertExactThreeCancellation(ReachResult result,
                                                     Reliability reliability) {
        assertEquals(3.0D, result.getMeasuredReach(), ERROR);
        assertEquals(reliability, result.getReliability());
        assertEquals(ReachDecision.CANCEL_REACH, result.getDecision());
    }

    private Fixture fixture(double measuredReach, int protocol, int attackTick,
                            boolean mediumReliability) {
        double targetX = targetX(measuredReach);
        PlayerRuntimeSnapshot runtime = runtime(0);
        PlayerSession session = new PlayerSession(UUID.randomUUID(), protocol,
                config, runtime, NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, targetX, 0.0D, 0.0D,
                0, NOW + 1L, config);
        confirmCurrentTargets(session, NOW + 2L, NOW + 3L);
        if (mediumReliability) {
            session.moveTarget(7, 0.0D, 0.0D, 0.0D,
                    1, NOW + 4L, config);
        }
        long attackNow = NOW + 5L;
        return new Fixture(session.createAttackSnapshot(7, targetId, attackTick,
                attackNow, config, new CandidateStateResolver()),
                Aabb.player(targetX, 0.0D, 0.0D), attackNow);
    }

    private Fixture fixtureWithOldAndNewTarget(double oldReach, double newReach,
                                                int protocol, int attackTick) {
        double oldX = targetX(oldReach);
        double newX = targetX(newReach);
        PlayerSession session = new PlayerSession(UUID.randomUUID(), protocol,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, oldX, 0.0D, 0.0D,
                0, NOW + 1L, config);
        confirmCurrentTargets(session, NOW + 2L, NOW + 3L);
        session.moveTarget(7, newX - oldX, 0.0D, 0.0D,
                1, NOW + 4L, config);
        long attackNow = NOW + 5L;
        return new Fixture(session.createAttackSnapshot(7, targetId, attackTick,
                attackNow, config, new CandidateStateResolver()),
                Aabb.player(newX, 0.0D, 0.0D), attackNow);
    }

    private Fixture staleFixture(int protocol) {
        return staleFixture(protocol, 400000000L);
    }

    private Fixture staleFixture(int protocol, long pendingDelayNanos) {
        return staleFixture(protocol, pendingDelayNanos, 8.0D);
    }

    private Fixture staleFixture(int protocol, long pendingDelayNanos,
                                 double currentReach) {
        double oldX = targetX(2.0D);
        double currentX = targetX(currentReach);
        PlayerSession session = new PlayerSession(UUID.randomUUID(), protocol,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, oldX, 0.0D, 0.0D,
                0, NOW + 1L, config);
        confirmCurrentTargets(session, NOW + 2L, NOW + 3L);
        short delayedSync = session.reserveSyncId();
        long delayedSentAt = NOW + 4L;
        session.markSyncSent(delayedSync, delayedSentAt);
        long attackNow = delayedSentAt + pendingDelayNanos;
        session.moveTarget(7, currentX - oldX, 0.0D, 0.0D,
                9, attackNow - 10000000L, config);
        session.handleMovement(false, 0.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 10, attackNow);
        return new Fixture(session.createAttackSnapshot(7, targetId, 10,
                attackNow, config, new CandidateStateResolver()),
                Aabb.player(currentX, 0.0D, 0.0D), attackNow);
    }

    private Fixture completedSlowTransactionFixture(int protocol) {
        double oldX = targetX(2.0D);
        double currentX = targetX(8.0D);
        PlayerSession session = new PlayerSession(UUID.randomUUID(), protocol,
                config, runtime(0), NOW);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, oldX, 0.0D, 0.0D,
                0, NOW + 1L, config);
        short sync = session.reserveSyncId();
        session.markSyncSent(sync, NOW + 2L);
        long confirmedAt = NOW + 400000002L;
        session.confirmSync(sync, confirmedAt);
        long attackNow = confirmedAt + 1L;
        session.handleMovement(false, 0.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 10, attackNow);
        return new Fixture(session.createAttackSnapshot(7, targetId, 10,
                attackNow, config, new CandidateStateResolver()),
                Aabb.player(currentX, 0.0D, 0.0D), attackNow);
    }

    private double targetX(double measuredReach) {
        return measuredReach + 0.3D + config.getTotalExpansion();
    }

    private static PlayerRuntimeSnapshot runtime(int serverTick) {
        return new PlayerRuntimeSnapshot(1, UUID.randomUUID(),
                0.0D, 0.0D, 0.0D, true, false, false,
                false, 0, serverTick);
    }

    private static void confirmCurrentTargets(PlayerSession session,
                                              long sentAt, long confirmedAt) {
        short sync = session.reserveSyncId();
        session.markSyncSent(sync, sentAt);
        session.confirmSync(sync, confirmedAt);
    }

    private static MovementFrame movement(long sequence, double x) {
        return new MovementFrame(sequence, NOW, 0, x, 0.0D, 0.0D,
                0.0F, 0.0F, true, true, true, false, true);
    }

    private static TargetFrame target(long sequence, double x) {
        return new TargetFrame(sequence, NOW, NOW, 0, 7, UUID.randomUUID(),
                Aabb.player(x, 0.0D, 0.0D), TargetFrameStatus.CONFIRMED,
                false, true);
    }

    private static final class Fixture {
        private final PlayerSession.AttackSnapshot attack;
        private final Aabb currentTargetBox;
        private final long now;

        private Fixture(PlayerSession.AttackSnapshot attack,
                        Aabb currentTargetBox, long now) {
            this.attack = attack;
            this.currentTargetBox = currentTargetBox;
            this.now = now;
        }
    }
}
