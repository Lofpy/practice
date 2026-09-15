package com.poppy.practice.service;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.junit.Assert.*;

public class CleanupTasksTest {
    @Test
    public void failedCleanupIsReportedAndDoesNotPreventLaterResourceRelease() {
        final List<LogRecord> reports = new ArrayList<LogRecord>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { reports.add(record); }
            public void flush() { }
            public void close() { }
        });
        CleanupTasks cleanup = new CleanupTasks(logger, "Match shutdown");
        final List<String> released = new ArrayList<String>();
        RuntimeException failure = new IllegalStateException("snapshot failed");
        cleanup.run("capture result", () -> { throw failure; });
        cleanup.run("release arena", () -> released.add("arena"));
        cleanup.run("reset player", () -> released.add("player"));
        assertEquals(2, released.size());
        assertEquals(1, reports.size());
        assertSame(failure, reports.get(0).getThrown());
        assertTrue(reports.get(0).getMessage().contains("capture result"));
    }
}
