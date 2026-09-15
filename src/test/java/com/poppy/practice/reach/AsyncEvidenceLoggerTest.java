package com.poppy.practice.reach;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class AsyncEvidenceLoggerTest {
    @Test
    public void evictsOldestDebugBeforeNormalAllow() {
        AsyncEvidenceLogger.PriorityEvidenceQueue queue = queue(4);
        EvidenceRecord firstDebug = record(ReachDecision.ALLOW, "DEBUG_MOVEMENT");
        EvidenceRecord normalAllow = record(ReachDecision.ALLOW, "WITHIN_REACH");
        EvidenceRecord secondDebug = record(ReachDecision.ALLOW_UNVERIFIED,
                "SAMPLE_DEBUG_TRACE");
        EvidenceRecord cancel = record(ReachDecision.CANCEL_REACH, "REACH_CANCEL");
        queue.offer(firstDebug);
        queue.offer(normalAllow);
        queue.offer(secondDebug);
        queue.offer(cancel);

        EvidenceRecord flag = record(ReachDecision.FLAG, "REACH_FLAG");
        assertEquals(AsyncEvidenceLogger.AdmissionResult.REPLACED,
                AsyncEvidenceLogger.offerPrioritized(queue, flag));

        assertFalse(queue.contains(firstDebug));
        assertTrue(queue.contains(secondDebug));
        assertTrue(queue.contains(normalAllow));
        assertTrue(queue.contains(cancel));
        assertTrue(queue.contains(flag));
    }

    @Test
    public void evictsNormalAllowBeforeProtectedEvidence() {
        AsyncEvidenceLogger.PriorityEvidenceQueue queue = queue(3);
        EvidenceRecord normalAllow = record(ReachDecision.ALLOW, "WITHIN_REACH");
        EvidenceRecord cancel = record(ReachDecision.CANCEL_STALE_ATTACK,
                "STALE_ATTACK");
        EvidenceRecord sync = record(ReachDecision.ALLOW_UNVERIFIED,
                "SYNC_WRITE_FAILED");
        queue.offer(normalAllow);
        queue.offer(cancel);
        queue.offer(sync);

        EvidenceRecord packetError = record(ReachDecision.ALLOW_UNVERIFIED,
                "PACKET_ERROR_DECODE");
        assertEquals(AsyncEvidenceLogger.AdmissionResult.REPLACED,
                AsyncEvidenceLogger.offerPrioritized(queue, packetError));

        assertFalse(queue.contains(normalAllow));
        assertTrue(queue.contains(cancel));
        assertTrue(queue.contains(sync));
        assertTrue(queue.contains(packetError));
    }

    @Test
    public void doesNotEvictProtectedEvidenceForNewLowPriorityRecord() {
        AsyncEvidenceLogger.PriorityEvidenceQueue queue = queue(4);
        EvidenceRecord cancel = record(ReachDecision.CANCEL_INVALID_ENTITY,
                "INVALID_ENTITY");
        EvidenceRecord flag = record(ReachDecision.FLAG, "REACH_FLAG");
        EvidenceRecord sync = record(ReachDecision.ALLOW_UNVERIFIED,
                "SYNC_WRITE_FAILED");
        EvidenceRecord packetError = record(ReachDecision.ALLOW_UNVERIFIED,
                "RECENT_PACKET_ERROR");
        queue.offer(cancel);
        queue.offer(flag);
        queue.offer(sync);
        queue.offer(packetError);

        EvidenceRecord normalAllow = record(ReachDecision.ALLOW, "WITHIN_REACH");
        assertEquals(AsyncEvidenceLogger.AdmissionResult.REJECTED,
                AsyncEvidenceLogger.offerPrioritized(queue, normalAllow));
        EvidenceRecord debug = record(ReachDecision.ALLOW, "DEBUG_SAMPLE");
        assertEquals(AsyncEvidenceLogger.AdmissionResult.REJECTED,
                AsyncEvidenceLogger.offerPrioritized(queue, debug));

        assertEquals(4, queue.size());
        assertTrue(queue.contains(cancel));
        assertTrue(queue.contains(flag));
        assertTrue(queue.contains(sync));
        assertTrue(queue.contains(packetError));
    }

    @Test
    public void refreshesEqualPriorityByDroppingOldestRecord() {
        AsyncEvidenceLogger.PriorityEvidenceQueue queue = queue(2);
        EvidenceRecord oldest = record(ReachDecision.ALLOW, "WITHIN_REACH");
        EvidenceRecord newer = record(ReachDecision.BYPASS, "EXEMPT_CONTEXT");
        EvidenceRecord newest = record(ReachDecision.ALLOW_UNVERIFIED,
                "NO_CONFIRMED_TARGET_FRAME");
        queue.offer(oldest);
        queue.offer(newer);

        assertEquals(AsyncEvidenceLogger.AdmissionResult.REPLACED,
                AsyncEvidenceLogger.offerPrioritized(queue, newest));
        assertSame(newer, queue.pollNow());
        assertSame(newest, queue.pollNow());
    }

    @Test
    public void full8192AdmissionChecksOnlyConstantPriorityLaneHeads() {
        AsyncEvidenceLogger.PriorityEvidenceQueue queue = queue(8192);
        EvidenceRecord debug = record(ReachDecision.ALLOW, "DEBUG_SAMPLE");
        for (int index = 0; index < 8192; index++) {
            assertEquals(AsyncEvidenceLogger.AdmissionResult.ACCEPTED,
                    queue.offer(debug));
        }

        EvidenceRecord protectedRecord = record(ReachDecision.FLAG, "REACH_FLAG");
        assertEquals(AsyncEvidenceLogger.AdmissionResult.REPLACED,
                queue.offer(protectedRecord));
        assertEquals(1, queue.getLastAdmissionLaneChecks());
        assertEquals(8192, queue.size());
        assertTrue(queue.contains(protectedRecord));
    }

    @Test
    public void writerSelectionIsWeightedAndEachPriorityLaneRemainsFifo() {
        AsyncEvidenceLogger.PriorityEvidenceQueue queue = queue(36);
        List<EvidenceRecord> protectedRecords = new ArrayList<EvidenceRecord>();
        List<EvidenceRecord> normalRecords = new ArrayList<EvidenceRecord>();
        List<EvidenceRecord> debugRecords = new ArrayList<EvidenceRecord>();
        for (int index = 0; index < 12; index++) {
            EvidenceRecord protectedRecord = record(ReachDecision.FLAG,
                    "REACH_FLAG_" + index);
            EvidenceRecord normalRecord = record(ReachDecision.ALLOW,
                    "WITHIN_REACH_" + index);
            EvidenceRecord debugRecord = record(ReachDecision.ALLOW,
                    "DEBUG_SAMPLE_" + index);
            protectedRecords.add(protectedRecord);
            normalRecords.add(normalRecord);
            debugRecords.add(debugRecord);
            queue.offer(protectedRecord);
            queue.offer(normalRecord);
            queue.offer(debugRecord);
        }

        int protectedCount = 0;
        int normalCount = 0;
        int debugCount = 0;
        for (int index = 0; index < 12; index++) {
            EvidenceRecord selected = queue.pollNow();
            AsyncEvidenceLogger.RetentionPriority priority =
                    AsyncEvidenceLogger.retentionPriority(selected);
            if (priority == AsyncEvidenceLogger.RetentionPriority.PROTECTED) {
                assertSame(protectedRecords.get(protectedCount++), selected);
            } else if (priority
                    == AsyncEvidenceLogger.RetentionPriority.NORMAL_ALLOW) {
                assertSame(normalRecords.get(normalCount++), selected);
            } else {
                assertSame(debugRecords.get(debugCount++), selected);
            }
        }

        assertEquals(8, protectedCount);
        assertEquals(3, normalCount);
        assertEquals(1, debugCount);
    }

    @Test
    public void queueRecoveryWritesOneAggregatedSyntheticNoticeBeforeEvidence()
            throws Exception {
        Path directory = Files.createTempDirectory("reachguard-queue-test-");
        AsyncEvidenceLogger logger = null;
        try {
            logger = new AsyncEvidenceLogger(directory.toFile(), 2, 1, false);
            assertTrue(logger.log(record(ReachDecision.ALLOW, "WITHIN_REACH")));
            assertTrue(logger.log(record(ReachDecision.CANCEL_REACH, "REACH_CANCEL")));
            assertTrue(logger.log(record(ReachDecision.FLAG, "REACH_FLAG")));
            assertFalse(logger.log(record(ReachDecision.ALLOW, "DEBUG_SAMPLE")));
            assertEquals(2L, logger.getDroppedCount());

            logger.shutdown();
            File evidence = new File(directory.toFile(),
                    "reachguard-2026-08-20.jsonl");
            List<String> lines = Files.readAllLines(evidence.toPath(),
                    StandardCharsets.UTF_8);

            assertEquals(3, lines.size());
            assertTrue(lines.get(0).contains("\"reason\":\"LOG_QUEUE_DROPPED\""));
            assertTrue(lines.get(0).contains("\"droppedEvidenceCount\":2"));
            assertFalse(lines.get(1).contains("LOG_QUEUE_DROPPED"));
            assertFalse(lines.get(2).contains("LOG_QUEUE_DROPPED"));
            assertEquals(3L, logger.getProcessedCount());
            assertEquals(0L, logger.getErrorCount());
            assertEquals(0, logger.getQueuedCount());
        } finally {
            if (logger != null) logger.shutdown();
            File[] files = directory.toFile().listFiles();
            if (files != null) {
                for (File file : files) Files.deleteIfExists(file.toPath());
            }
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void classifiesRequiredProtectedAndLowPriorityRecords() {
        assertEquals(AsyncEvidenceLogger.RetentionPriority.DEBUG,
                AsyncEvidenceLogger.retentionPriority(
                        record(ReachDecision.ALLOW, "DEBUG_SAMPLE")));
        assertEquals(AsyncEvidenceLogger.RetentionPriority.NORMAL_ALLOW,
                AsyncEvidenceLogger.retentionPriority(
                        record(ReachDecision.ALLOW, "WITHIN_REACH")));
        assertEquals(AsyncEvidenceLogger.RetentionPriority.PROTECTED,
                AsyncEvidenceLogger.retentionPriority(
                        record(ReachDecision.FLAG, "REACH_FLAG")));
        assertEquals(AsyncEvidenceLogger.RetentionPriority.PROTECTED,
                AsyncEvidenceLogger.retentionPriority(
                        record(ReachDecision.CANCEL_REACH, "DEBUG_CANCEL")));
        assertEquals(AsyncEvidenceLogger.RetentionPriority.PROTECTED,
                AsyncEvidenceLogger.retentionPriority(
                        record(ReachDecision.ALLOW_UNVERIFIED, "SYNC_WRITE_FAILED")));
        assertEquals(AsyncEvidenceLogger.RetentionPriority.PROTECTED,
                AsyncEvidenceLogger.retentionPriority(
                        record(ReachDecision.ALLOW_UNVERIFIED,
                                "RECENT_PACKET_ERROR")));
        assertEquals(AsyncEvidenceLogger.RetentionPriority.PROTECTED,
                AsyncEvidenceLogger.retentionPriority(
                        record(ReachDecision.ALLOW_UNVERIFIED,
                                "LOG_QUEUE_DROPPED")));

        EvidenceRecord confirmationAnomaly = EvidenceRecord.builder(
                        OffsetDateTime.parse("2026-08-20T00:00:00Z"))
                .decision(ReachDecision.ALLOW_UNVERIFIED)
                .reason("LOW_RELIABILITY")
                .unverifiedContext(false, false,
                        Arrays.asList("NO_CONFIRMED_TARGET_STATE"))
                .build();
        assertEquals(AsyncEvidenceLogger.RetentionPriority.PROTECTED,
                AsyncEvidenceLogger.retentionPriority(confirmationAnomaly));
    }

    private static AsyncEvidenceLogger.PriorityEvidenceQueue queue(int capacity) {
        return new AsyncEvidenceLogger.PriorityEvidenceQueue(capacity);
    }

    private static EvidenceRecord record(ReachDecision decision, String reason) {
        return EvidenceRecord.builder(OffsetDateTime.parse("2026-08-20T00:00:00Z"))
                .decision(decision)
                .reason(reason)
                .build();
    }
}
