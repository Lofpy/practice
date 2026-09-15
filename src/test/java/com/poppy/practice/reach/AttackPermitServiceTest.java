package com.poppy.practice.reach;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class AttackPermitServiceTest {
    private static final long NOW = 1000000000L;

    private final UUID attacker = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    public void permitCanOnlyBeConsumedOnce() {
        AttackPermitService service = new AttackPermitService(150L);
        service.issue(attacker, target, 41L, 100, NOW);

        AttackPermit consumed = service.consumePermit(attacker, target, 100, NOW);

        assertNotNull(consumed);
        assertEquals(41L, consumed.getAttackSequence());
        assertFalse(service.consume(attacker, target, 100, NOW));
    }

    @Test
    public void fifoPermitsAllowSameAndNextServerTick() {
        AttackPermitService service = new AttackPermitService(150L);
        service.issue(attacker, target, 1L, 100, NOW);
        service.issue(attacker, target, 2L, 100, NOW + 1L);

        AttackPermit first = service.consumePermit(attacker, target, 100, NOW + 2L);
        AttackPermit second = service.consumePermit(attacker, target, 101, NOW + 3L);

        assertEquals(1L, first.getAttackSequence());
        assertEquals(2L, second.getAttackSequence());
        assertEquals(0, service.pendingPermitCount());
    }

    @Test
    public void permitIsRejectedTwoTicksAfterIssue() {
        AttackPermitService service = new AttackPermitService(150L);
        service.issue(attacker, target, 1L, 100, NOW);

        assertFalse(service.consume(attacker, target, 102, NOW + 1L));
        assertEquals(0, service.pendingPermitCount());
    }

    @Test
    public void damageBeforePermitTickDoesNotConsumeFuturePermit() {
        AttackPermitService service = new AttackPermitService(150L);
        service.issue(attacker, target, 1L, 101, NOW);

        assertFalse(service.consume(attacker, target, 100, NOW + 1L));
        assertTrue(service.consume(attacker, target, 101, NOW + 2L));
    }

    @Test
    public void expiryBoundaryIsInclusiveAndLaterTimeIsRejected() {
        AttackPermitService service = new AttackPermitService(150L);
        long expiresAt = NOW + 150000000L;
        service.issue(attacker, target, 1L, 100, NOW);

        assertTrue(service.consume(attacker, target, 100, expiresAt));

        service.issue(attacker, target, 2L, 100, NOW);
        assertFalse(service.consume(attacker, target, 100, expiresAt + 1L));
    }

    @Test
    public void targetMismatchCannotConsumeAnotherPairsPermit() {
        AttackPermitService service = new AttackPermitService(150L);
        UUID otherTarget = UUID.randomUUID();
        service.issue(attacker, target, 1L, 100, NOW);

        assertNull(service.consumePermit(attacker, otherTarget, 100, NOW));
        assertTrue(service.consume(attacker, target, 100, NOW));
    }

    @Test
    public void staleHeadDoesNotBlockNextValidPermit() {
        AttackPermitService service = new AttackPermitService(150L);
        service.issue(attacker, target, 1L, 99, NOW);
        service.issue(attacker, target, 2L, 100, NOW + 1L);

        AttackPermit consumed = service.consumePermit(attacker, target, 101, NOW + 2L);

        assertNotNull(consumed);
        assertEquals(2L, consumed.getAttackSequence());
    }

    @Test
    public void concurrentConsumersStillConsumeExactlyOnce() throws InterruptedException {
        final AttackPermitService service = new AttackPermitService(150L);
        service.issue(attacker, target, 1L, 100, NOW);
        final int workerCount = 8;
        final CountDownLatch ready = new CountDownLatch(workerCount);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger consumed = new AtomicInteger();
        Thread[] workers = new Thread[workerCount];
        for (int index = 0; index < workerCount; index++) {
            workers[index] = new Thread(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        if (service.consume(attacker, target, 100, NOW)) {
                            consumed.incrementAndGet();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            workers[index].start();
        }
        ready.await();
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals(1, consumed.get());
    }

    @Test
    public void clearingPlayerRemovesPermitsInBothDirections() {
        AttackPermitService service = new AttackPermitService(150L);
        UUID third = UUID.randomUUID();
        service.issue(attacker, target, 1L, 100, NOW);
        service.issue(third, attacker, 2L, 100, NOW);
        service.issue(third, target, 3L, 100, NOW);

        service.clearPlayer(attacker);

        assertFalse(service.consume(attacker, target, 100, NOW));
        assertFalse(service.consume(third, attacker, 100, NOW));
        assertTrue(service.consume(third, target, 100, NOW));
    }

    @Test
    public void packetSpamCannotGrowOnePairWithoutBound() {
        AttackPermitService service = new AttackPermitService(150L);
        for (int index = 0; index < 1000; index++) {
            service.issue(attacker, target, index, 100, NOW + index);
        }

        assertEquals(32, service.pendingPermitCount());
    }

    @Test
    public void cleanupRemovesPermitsThatNeverProducedDamageEvents() {
        AttackPermitService service = new AttackPermitService(150L);
        service.issue(attacker, target, 1L, 100, NOW);

        service.cleanup(102, NOW + 1L);

        assertEquals(0, service.pendingPermitCount());
    }
}
