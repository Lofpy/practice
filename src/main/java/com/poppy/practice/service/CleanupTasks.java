package com.poppy.practice.service;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs independent cleanup steps even when one resource fails to close. */
public final class CleanupTasks {
    private final Logger logger;
    private final String context;

    public CleanupTasks(Logger logger, String context) {
        this.logger = logger;
        this.context = context;
    }

    public void run(String action, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, context + ": could not " + action, exception);
        }
    }
}
