package com.poppy.practice.match;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Separates result presentation failures from the cleanup that every match needs. */
public final class MatchCompletion {
    private final MatchManager matches;
    private final Consumer<UUID> returnToLobby;
    private final Consumer<String> releaseArena;
    private final BiConsumer<String, RuntimeException> reportFailure;

    public MatchCompletion(MatchManager matches, Consumer<UUID> returnToLobby,
                           Consumer<String> releaseArena,
                           BiConsumer<String, RuntimeException> reportFailure) {
        this.matches = matches;
        this.returnToLobby = returnToLobby;
        this.releaseArena = releaseArena;
        this.reportFailure = reportFailure;
    }

    public boolean complete(Match match, Runnable presentResult) {
        if (match == null || matches.getByArena(match.getArenaId()) != match || !match.beginEnding()) {
            return false;
        }
        try {
            attempt("present result for " + match.getId(), presentResult);
        } finally {
            finish(match);
        }
        return true;
    }

    /** Reserves participants and arena until the visual result delay finishes. */
    public boolean begin(Match match, Runnable presentResult) {
        if (match == null || matches.getByArena(match.getArenaId()) != match || !match.beginEnding()) {
            return false;
        }
        attempt("present result for " + match.getId(), presentResult);
        return true;
    }

    public boolean finish(Match match) {
        if (match == null || matches.getByArena(match.getArenaId()) != match
                || match.getState() != MatchState.ENDING) return false;
            matches.remove(match);
            try {
                attempt("return player " + match.getFirstPlayerId(),
                        () -> returnToLobby.accept(match.getFirstPlayerId()));
            } finally {
                try {
                    attempt("return player " + match.getSecondPlayerId(),
                            () -> returnToLobby.accept(match.getSecondPlayerId()));
                } finally {
                    try {
                        attempt("release arena " + match.getArenaId(),
                                () -> releaseArena.accept(match.getArenaId()));
                    } finally {
                        match.markFinished();
                    }
                }
            }
        return true;
    }

    private void attempt(String action, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            try {
                reportFailure.accept("Could not " + action, exception);
            } catch (RuntimeException reportingFailure) {
                // Error reporting is not allowed to strand an ENDING match or skip later cleanup.
                if (reportingFailure != exception) exception.addSuppressed(reportingFailure);
            }
        }
    }
}
