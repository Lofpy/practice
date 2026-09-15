package com.poppy.practice.chatter;

import com.poppy.practice.PracticePlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class TelemetryService {
    private final PracticePlugin plugin;
    private final File directory;
    private final ArrayBlockingQueue<Work> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final Thread writerThread;
    private volatile boolean running = true;
    private volatile ChatterKbConfig config;
    private BufferedWriter writer;
    private File activeFile;

    TelemetryService(PracticePlugin plugin, ChatterKbConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.directory = new File(plugin.getDataFolder(), "chatterkb");
        this.queue = new ArrayBlockingQueue<Work>(config.asyncQueueSize);
        this.writerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writerLoop();
            }
        }, "PoppyPractice-ChatterKB-Telemetry");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    void reload(ChatterKbConfig config) {
        this.config = config;
    }

    void record(PlayerCombatState state, String event, String details, boolean important) {
        String time = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String line = "{\"time\":\"" + escape(time) + "\",\"player\":\""
                + escape(state.playerName == null ? "unknown" : state.playerName)
                + "\",\"uuid\":\"" + state.playerId + "\",\"event\":\""
                + escape(event) + "\",\"details\":\"" + escape(details) + "\"}";
        synchronized (state) {
            state.addDebug(time + ',' + event + ',' + details);
        }
        ChatterKbConfig current = config;
        if (current.loggingEnabled && (important || current.includeAttackSamples)) {
            offer(new LineWork(line));
        }
    }

    void export(final UUID playerId, final String playerName, final List<String> lines,
                final ExportCallback callback) {
        offer(new ExportWork(playerId, playerName, lines, callback));
    }

    long getDroppedCount() {
        return dropped.get();
    }

    void shutdown() {
        running = false;
        writerThread.interrupt();
        try {
            writerThread.join(3000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        closeWriter();
    }

    private void offer(Work work) {
        if (!queue.offer(work)) {
            dropped.incrementAndGet();
        }
    }

    private void writerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                Work work = queue.poll(500L, TimeUnit.MILLISECONDS);
                if (work != null) work.run();
            } catch (InterruptedException exception) {
                if (!running) break;
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("ChatterKB telemetry failed: " + exception.getMessage());
            }
        }
        Work remaining;
        while ((remaining = queue.poll()) != null) {
            try {
                remaining.run();
            } catch (RuntimeException ignored) {
                // Shutdown must not hold the server open for telemetry.
            }
        }
        closeWriter();
    }

    private void writeLine(String line) {
        try {
            ensureWriter();
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private void ensureWriter() throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("could not create " + directory);
        }
        if (writer != null && activeFile != null && activeFile.length() < config.rotateBytes) {
            return;
        }
        closeWriter();
        activeFile = new File(directory, "chatterkb-" + System.currentTimeMillis() + ".jsonl");
        writer = new BufferedWriter(new FileWriter(activeFile, true));
        pruneFiles();
    }

    private void pruneFiles() {
        File[] files = directory.listFiles();
        if (files == null) return;
        List<File> logs = new ArrayList<File>();
        for (File file : files) {
            if (file.getName().startsWith("chatterkb-") && file.getName().endsWith(".jsonl")) {
                logs.add(file);
            }
        }
        File[] sorted = logs.toArray(new File[logs.size()]);
        Arrays.sort(sorted, new Comparator<File>() {
            @Override
            public int compare(File first, File second) {
                return Long.compare(second.lastModified(), first.lastModified());
            }
        });
        for (int index = config.retainFiles; index < sorted.length; index++) {
            if (!sorted[index].delete()) {
                plugin.getLogger().fine("Could not delete old ChatterKB log " + sorted[index]);
            }
        }
    }

    private void exportCsv(UUID playerId, String playerName, List<String> lines,
                           ExportCallback callback) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            callback.complete(null, "Could not create export directory.");
            return;
        }
        String safeName = playerName.replaceAll("[^A-Za-z0-9_-]", "_");
        File output = new File(directory, "export-" + safeName + '-'
                + System.currentTimeMillis() + ".csv");
        try (BufferedWriter csv = new BufferedWriter(new FileWriter(output))) {
            csv.write("time,event,details");
            csv.newLine();
            for (String line : lines) {
                String[] fields = line.split(",", 3);
                csv.write(csv(fields.length > 0 ? fields[0] : ""));
                csv.write(',');
                csv.write(csv(fields.length > 1 ? fields[1] : ""));
                csv.write(',');
                csv.write(csv(fields.length > 2 ? fields[2] : ""));
                csv.newLine();
            }
            callback.complete(output, null);
        } catch (IOException exception) {
            callback.complete(null, exception.getMessage());
        }
    }

    private void closeWriter() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException ignored) {
            // Best effort during rotation/shutdown.
        }
        writer = null;
        activeFile = null;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    interface ExportCallback {
        void complete(File file, String error);
    }

    private interface Work {
        void run();
    }

    private final class LineWork implements Work {
        private final String line;

        private LineWork(String line) {
            this.line = line;
        }

        @Override
        public void run() {
            writeLine(line);
        }
    }

    private final class ExportWork implements Work {
        private final UUID playerId;
        private final String playerName;
        private final List<String> lines;
        private final ExportCallback callback;

        private ExportWork(UUID playerId, String playerName, List<String> lines,
                           ExportCallback callback) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.lines = lines;
            this.callback = callback;
        }

        @Override
        public void run() {
            exportCsv(playerId, playerName, lines, callback);
        }
    }
}
