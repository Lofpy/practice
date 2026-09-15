package com.poppy.practice.reach;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, non-blocking producer queue with a single JSONL writer thread.
 * Files rotate by the record's local calendar day and are retained for the
 * configured number of days.
 */
public final class AsyncEvidenceLogger implements AutoCloseable {
    private static final String FILE_PREFIX = "reachguard-";
    private static final String FILE_SUFFIX = ".jsonl";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long WRITER_WAIT_MILLIS = 250L;
    private static final long MAX_FLUSH_INTERVAL_NANOS =
            TimeUnit.MILLISECONDS.toNanos(250L);
    private static final int MAX_FLUSH_BATCH_SIZE = 64;

    private final File directory;
    private final int retentionDays;
    private final PriorityEvidenceQueue queue;
    private final EvidenceJsonEncoder encoder = new EvidenceJsonEncoder();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean workerStarted = new AtomicBoolean();
    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicLong pendingDroppedEvidenceCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong processedCount = new AtomicLong();
    private final Object lifecycleLock = new Object();
    private final Thread worker;

    private BufferedWriter writer;
    private LocalDate writerDate;
    private int unflushedLineCount;
    private long lastFlushNanoTime = System.nanoTime();
    private volatile File currentFile;

    enum RetentionPriority {
        DEBUG,
        NORMAL_ALLOW,
        PROTECTED
    }

    enum AdmissionResult {
        ACCEPTED,
        REPLACED,
        REJECTED
    }

    public AsyncEvidenceLogger(File directory, int queueCapacity, int retentionDays) {
        this(directory, queueCapacity, retentionDays, true);
    }

    /** Package-private delayed start makes bounded-queue recovery deterministic in tests. */
    AsyncEvidenceLogger(File directory, int queueCapacity, int retentionDays,
                        boolean startImmediately) {
        if (directory == null) throw new IllegalArgumentException("directory");
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        if (directory.exists() && !directory.isDirectory()) {
            throw new IllegalArgumentException("Evidence path is not a directory: "
                    + directory);
        }
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Could not create evidence directory: "
                    + directory);
        }
        this.directory = directory;
        this.retentionDays = retentionDays;
        this.queue = new PriorityEvidenceQueue(queueCapacity);
        this.worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runWriter();
            }
        }, "reachguard-evidence-writer");
        this.worker.setDaemon(true);
        if (startImmediately) startWorker();
    }

    /**
     * Returns false without waiting for queue capacity when the logger is closed
     * or the full queue contains only records more important than this record.
     */
    public boolean log(EvidenceRecord record) {
        synchronized (lifecycleLock) {
            if (record == null || !accepting.get()) {
                droppedCount.incrementAndGet();
                return false;
            }
            AdmissionResult admission = offerPrioritized(queue, record);
            if (admission != AdmissionResult.ACCEPTED) {
                droppedCount.incrementAndGet();
                incrementPendingDropCount();
            }
            return admission != AdmissionResult.REJECTED;
        }
    }

    static AdmissionResult offerPrioritized(PriorityEvidenceQueue target,
                                             EvidenceRecord incoming) {
        if (target == null) throw new IllegalArgumentException("target");
        return target.offer(incoming);
    }

    static RetentionPriority retentionPriority(EvidenceRecord record) {
        if (record == null) return RetentionPriority.DEBUG;
        ReachDecision decision = record.getDecision();
        String reason = normalize(record.getReason());
        if ((decision != null && decision.isViolation())
                || "LOG_QUEUE_DROPPED".equals(reason)
                || "NO_RECENT_TARGET_STATE".equals(reason)
                || hasReasonTag(reason, "SYNC")
                || hasReasonTag(reason, "PACKET_ERROR")
                || hasSyncAnomaly(record)) {
            return RetentionPriority.PROTECTED;
        }
        if (hasReasonTag(reason, "DEBUG")) return RetentionPriority.DEBUG;
        return RetentionPriority.NORMAL_ALLOW;
    }

    private static boolean hasSyncAnomaly(EvidenceRecord record) {
        if (record.getUnverifiedExceptions() == null) return false;
        for (String exception : record.getUnverifiedExceptions()) {
            String normalized = normalize(exception);
            if (hasReasonTag(normalized, "SYNC")
                    || hasReasonTag(normalized, "CONFIRMED")
                    || hasReasonTag(normalized, "CONFIRMATION")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasReasonTag(String reason, String tag) {
        if (reason.equals(tag) || reason.startsWith(tag + "_")
                || reason.endsWith("_" + tag)) {
            return true;
        }
        return reason.contains("_" + tag + "_");
    }

    public long getDroppedCount() { return droppedCount.get(); }
    public long getErrorCount() { return errorCount.get(); }
    public long getProcessedCount() { return processedCount.get(); }
    public int getQueuedCount() { return queue.size(); }
    public boolean isAccepting() { return accepting.get(); }
    public File getCurrentFile() { return currentFile; }

    /** Stops accepting records and waits until every queued record and drop notice flushes. */
    public void shutdown() {
        synchronized (lifecycleLock) {
            accepting.set(false);
            startWorker();
        }
        queue.wakeWriter();
        worker.interrupt();
        boolean interrupted = false;
        while (worker.isAlive()) {
            try {
                worker.join();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    @Override
    public void close() {
        shutdown();
    }

    private void startWorker() {
        if (workerStarted.compareAndSet(false, true)) worker.start();
    }

    private void incrementPendingDropCount() {
        while (true) {
            long previous = pendingDroppedEvidenceCount.get();
            if (previous == Long.MAX_VALUE) return;
            if (pendingDroppedEvidenceCount.compareAndSet(previous, previous + 1L)) {
                return;
            }
        }
    }

    private void restorePendingDropCount(long value) {
        if (value <= 0L) return;
        while (true) {
            long previous = pendingDroppedEvidenceCount.get();
            long restored = Long.MAX_VALUE - previous < value
                    ? Long.MAX_VALUE : previous + value;
            if (pendingDroppedEvidenceCount.compareAndSet(previous, restored)) return;
        }
    }

    private void runWriter() {
        try {
            while (accepting.get() || !queue.isEmpty()
                    || pendingDroppedEvidenceCount.get() > 0L) {
                EvidenceRecord record;
                try {
                    record = queue.poll(WRITER_WAIT_MILLIS);
                } catch (InterruptedException exception) {
                    continue;
                }
                OffsetDateTime noticeTime = record == null
                        ? OffsetDateTime.now() : record.getTimestamp();
                writeDroppedEvidenceNotice(noticeTime);
                if (record != null) write(record);
                flushIfNeeded(record == null || queue.isEmpty());
            }
            writeDroppedEvidenceNotice(OffsetDateTime.now());
            flushIfNeeded(true);
        } finally {
            closeWriter();
        }
    }

    /** Writes outside the bounded queue so a full queue cannot drop its own loss notice. */
    private void writeDroppedEvidenceNotice(OffsetDateTime timestamp) {
        long dropped = pendingDroppedEvidenceCount.getAndSet(0L);
        if (dropped <= 0L) return;
        EvidenceRecord notice = EvidenceRecord.builder(timestamp)
                .decision(ReachDecision.ALLOW_UNVERIFIED)
                .reason("LOG_QUEUE_DROPPED")
                .reliability(Reliability.LOW)
                .droppedEvidenceCount(dropped)
                .build();
        if (!write(notice) && (accepting.get() || !queue.isEmpty())) {
            restorePendingDropCount(dropped);
        }
    }

    private boolean write(EvidenceRecord record) {
        try {
            LocalDate recordDate = record.getTimestamp().toLocalDate();
            ensureWriter(recordDate);
            writer.write(encoder.encode(record));
            writer.newLine();
            unflushedLineCount++;
            processedCount.incrementAndGet();
            return true;
        } catch (IOException | RuntimeException exception) {
            errorCount.incrementAndGet();
            closeWriter();
            return false;
        }
    }

    private void flushIfNeeded(boolean force) {
        if (writer == null || unflushedLineCount == 0) return;
        long now = System.nanoTime();
        if (!force && unflushedLineCount < MAX_FLUSH_BATCH_SIZE
                && now - lastFlushNanoTime < MAX_FLUSH_INTERVAL_NANOS) {
            return;
        }
        try {
            writer.flush();
            unflushedLineCount = 0;
            lastFlushNanoTime = now;
        } catch (IOException exception) {
            errorCount.incrementAndGet();
            closeWriter();
        }
    }

    private void ensureWriter(LocalDate date) throws IOException {
        if (writer != null && date.equals(writerDate)) return;
        closeWriter();
        cleanupRetention(date);
        File file = new File(directory, FILE_PREFIX + FILE_DATE.format(date) + FILE_SUFFIX);
        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8));
        writerDate = date;
        currentFile = file;
        lastFlushNanoTime = System.nanoTime();
    }

    private void cleanupRetention(LocalDate referenceDate) {
        File[] files = directory.listFiles();
        if (files == null) {
            errorCount.incrementAndGet();
            return;
        }
        LocalDate oldestRetained = referenceDate.minusDays(retentionDays - 1L);
        for (File file : files) {
            LocalDate fileDate = evidenceDate(file.getName());
            if (fileDate != null && fileDate.isBefore(oldestRetained)
                    && file.isFile() && !file.delete()) {
                errorCount.incrementAndGet();
            }
        }
    }

    private static LocalDate evidenceDate(String name) {
        if (name == null || !name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) {
            return null;
        }
        String date = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
        try {
            return LocalDate.parse(date, FILE_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private void closeWriter() {
        BufferedWriter closing = writer;
        writer = null;
        writerDate = null;
        unflushedLineCount = 0;
        lastFlushNanoTime = System.nanoTime();
        if (closing == null) return;
        try {
            closing.flush();
        } catch (IOException exception) {
            errorCount.incrementAndGet();
        }
        try {
            closing.close();
        } catch (IOException exception) {
            errorCount.incrementAndGet();
        }
    }

    /**
     * Fixed-capacity, three-lane queue. Admission checks at most DEBUG, NORMAL,
     * and PROTECTED lane heads, so full-queue eviction is capacity-independent.
     * Every lane is FIFO. The writer uses an 8:3:1 protected/normal/debug cycle;
     * empty slots are skipped, which keeps protected evidence non-starving while
     * still allowing sustained lower-priority traffic to make progress.
     */
    static final class PriorityEvidenceQueue {
        private static final RetentionPriority[] WRITE_ORDER = {
                RetentionPriority.PROTECTED, RetentionPriority.PROTECTED,
                RetentionPriority.PROTECTED, RetentionPriority.PROTECTED,
                RetentionPriority.PROTECTED, RetentionPriority.PROTECTED,
                RetentionPriority.PROTECTED, RetentionPriority.PROTECTED,
                RetentionPriority.NORMAL_ALLOW, RetentionPriority.NORMAL_ALLOW,
                RetentionPriority.NORMAL_ALLOW, RetentionPriority.DEBUG
        };

        private final int capacity;
        private final ArrayDeque<EvidenceRecord> debug;
        private final ArrayDeque<EvidenceRecord> normal;
        private final ArrayDeque<EvidenceRecord> protectedRecords;
        private int size;
        private int writeIndex;
        private int lastAdmissionLaneChecks;

        PriorityEvidenceQueue(int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("capacity");
            this.capacity = capacity;
            // Each lane can hold the full shared capacity in the worst case.
            // Preallocation prevents a cross-lane replacement from triggering
            // an ArrayDeque resize on the packet producer path.
            this.debug = new ArrayDeque<EvidenceRecord>(capacity);
            this.normal = new ArrayDeque<EvidenceRecord>(capacity);
            this.protectedRecords = new ArrayDeque<EvidenceRecord>(capacity);
        }

        synchronized AdmissionResult offer(EvidenceRecord record) {
            if (record == null) throw new IllegalArgumentException("record");
            RetentionPriority priority = retentionPriority(record);
            lastAdmissionLaneChecks = 0;
            if (size < capacity) {
                lane(priority).addLast(record);
                size++;
                notifyAll();
                return AdmissionResult.ACCEPTED;
            }

            ArrayDeque<EvidenceRecord> evictionLane = evictionLane(priority);
            if (evictionLane == null) return AdmissionResult.REJECTED;
            evictionLane.removeFirst();
            lane(priority).addLast(record);
            notifyAll();
            return AdmissionResult.REPLACED;
        }

        synchronized EvidenceRecord poll(long timeoutMillis)
                throws InterruptedException {
            if (size == 0 && timeoutMillis > 0L) wait(timeoutMillis);
            return pollAvailable();
        }

        synchronized EvidenceRecord pollNow() {
            return pollAvailable();
        }

        synchronized int size() { return size; }
        synchronized boolean isEmpty() { return size == 0; }

        synchronized boolean contains(EvidenceRecord record) {
            return debug.contains(record) || normal.contains(record)
                    || protectedRecords.contains(record);
        }

        synchronized int getLastAdmissionLaneChecks() {
            return lastAdmissionLaneChecks;
        }

        synchronized void wakeWriter() {
            notifyAll();
        }

        private ArrayDeque<EvidenceRecord> evictionLane(RetentionPriority incoming) {
            lastAdmissionLaneChecks++;
            if (!debug.isEmpty()) return debug;
            if (incoming == RetentionPriority.DEBUG) return null;

            lastAdmissionLaneChecks++;
            if (!normal.isEmpty()) return normal;
            if (incoming == RetentionPriority.NORMAL_ALLOW) return null;

            lastAdmissionLaneChecks++;
            return protectedRecords.isEmpty() ? null : protectedRecords;
        }

        private EvidenceRecord pollAvailable() {
            if (size == 0) return null;
            for (int checked = 0; checked < WRITE_ORDER.length; checked++) {
                RetentionPriority priority = WRITE_ORDER[writeIndex];
                writeIndex = (writeIndex + 1) % WRITE_ORDER.length;
                ArrayDeque<EvidenceRecord> selected = lane(priority);
                if (!selected.isEmpty()) {
                    size--;
                    return selected.removeFirst();
                }
            }
            throw new IllegalStateException("Priority queue size is inconsistent");
        }

        private ArrayDeque<EvidenceRecord> lane(RetentionPriority priority) {
            if (priority == RetentionPriority.DEBUG) return debug;
            if (priority == RetentionPriority.NORMAL_ALLOW) return normal;
            return protectedRecords;
        }
    }
}
