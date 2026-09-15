package com.poppy.practice.reach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CandidateStateResolver {
    public TargetResolution resolve(List<TargetFrame> history, long now,
                                    long maximumRewindMs) {
        if (history == null || history.isEmpty()) {
            return TargetResolution.empty();
        }
        TargetFrame confirmed = null;
        TargetFrame unconfirmed = null;
        TargetFrame newest = null;
        for (TargetFrame frame : history) {
            if (!frame.isValid()) continue;
            newest = frame;
            if (frame.getStatus() == TargetFrameStatus.CONFIRMED) confirmed = frame;
            if (frame.getStatus() == TargetFrameStatus.SENT_UNCONFIRMED) unconfirmed = frame;
        }
        boolean currentConfirmed = confirmed != null && newest != null
                && confirmed.getStateSequence() == newest.getStateSequence();
        List<TargetFrame> candidates = new ArrayList<TargetFrame>(5);
        addCandidate(candidates, confirmed, currentConfirmed, now, maximumRewindMs);
        if (unconfirmed != confirmed) addRecent(candidates, unconfirmed, now, maximumRewindMs);
        boolean recentConfirmed = currentConfirmed
                || isRecent(confirmed, now, maximumRewindMs);
        boolean recentUnconfirmed = isRecent(unconfirmed, now, maximumRewindMs);
        if (recentConfirmed && recentUnconfirmed && confirmed != unconfirmed
                && !crossesTeleport(history, confirmed, unconfirmed)) {
            candidates.add(confirmed.interpolate(unconfirmed, 0.25D, -1L));
            candidates.add(confirmed.interpolate(unconfirmed, 0.50D, -2L));
            candidates.add(confirmed.interpolate(unconfirmed, 0.75D, -3L));
        }
        if (candidates.size() > 5) {
            candidates = new ArrayList<TargetFrame>(candidates.subList(0, 5));
        }
        // Keep the last acknowledged position as a stale anchor even while
        // newer, unacknowledged frames remain available as normal candidates.
        // This is what lets delayed Transaction responses be distinguished
        // from ordinary high latency/backtrack uncertainty.
        TargetFrame stale = confirmed != null && !recentConfirmed
                && !currentConfirmed ? confirmed : null;
        return new TargetResolution(candidates, recentConfirmed,
                recentConfirmed && recentUnconfirmed && confirmed != unconfirmed,
                newest, stale, currentConfirmed);
    }

    private static void addRecent(List<TargetFrame> frames, TargetFrame frame,
                                  long now, long maximumRewindMs) {
        if (isRecent(frame, now, maximumRewindMs) && !frames.contains(frame)) {
            frames.add(frame);
        }
    }

    private static void addCandidate(List<TargetFrame> frames, TargetFrame frame,
                                     boolean current, long now,
                                     long maximumRewindMs) {
        if (frame != null && (current || isRecent(frame, now, maximumRewindMs))
                && !frames.contains(frame)) {
            frames.add(frame);
        }
    }

    private static boolean isRecent(TargetFrame frame, long now,
                                    long maximumRewindMs) {
        return frame != null && frame.ageMillis(now) <= maximumRewindMs;
    }

    private static boolean crossesTeleport(List<TargetFrame> history,
                                           TargetFrame first,
                                           TargetFrame second) {
        if (first.getTeleportEpoch() != second.getTeleportEpoch()) return true;
        long lower = Math.min(first.getStateSequence(), second.getStateSequence());
        long upper = Math.max(first.getStateSequence(), second.getStateSequence());
        for (TargetFrame frame : history) {
            long sequence = frame.getStateSequence();
            if (frame.isValid() && frame.isTeleport()
                    && sequence > lower && sequence <= upper) return true;
        }
        return false;
    }

    public static final class TargetResolution {
        private final List<TargetFrame> candidates;
        private final boolean hasConfirmed;
        private final boolean mixedConfirmation;
        private final TargetFrame newest;
        private final TargetFrame stale;
        private final boolean currentConfirmed;

        private TargetResolution(List<TargetFrame> candidates, boolean hasConfirmed,
                                 boolean mixedConfirmation, TargetFrame newest,
                                 TargetFrame stale, boolean currentConfirmed) {
            this.candidates = Collections.unmodifiableList(
                    new ArrayList<TargetFrame>(candidates));
            this.hasConfirmed = hasConfirmed;
            this.mixedConfirmation = mixedConfirmation;
            this.newest = newest;
            this.stale = stale;
            this.currentConfirmed = currentConfirmed;
        }

        static TargetResolution empty() {
            return new TargetResolution(Collections.<TargetFrame>emptyList(),
                    false, false, null, null, false);
        }

        public List<TargetFrame> getCandidates() { return candidates; }
        public boolean hasConfirmed() { return hasConfirmed; }
        public boolean hasMixedConfirmation() { return mixedConfirmation; }
        public TargetFrame getNewest() { return newest; }
        public TargetFrame getStale() { return stale; }
        public boolean isCurrentConfirmed() { return currentConfirmed; }
    }
}
