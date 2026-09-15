package com.poppy.practice.reach;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class MovementFrameBuffer {
    private final Deque<MovementFrame> frames = new ArrayDeque<MovementFrame>();
    private int capacity;

    MovementFrameBuffer(int capacity) {
        this.capacity = Math.max(2, capacity);
    }

    void setCapacity(int capacity) {
        this.capacity = Math.max(2, capacity);
        trim();
    }

    void add(MovementFrame frame) {
        frames.addLast(frame);
        trim();
    }

    MovementFrame latest() {
        return frames.peekLast();
    }

    List<MovementFrame> newestValidCandidates(long now, long maximumAgeMs) {
        List<MovementFrame> result = new ArrayList<MovementFrame>(1);
        MovementFrame newest = frames.peekLast();
        if (!isUsable(newest, now, maximumAgeMs)) return result;
        result.add(newest);
        return result;
    }

    private static boolean isUsable(MovementFrame frame, long now,
                                    long maximumAgeMs) {
        if (frame == null || !frame.isValid() || !frame.hasFiniteCoordinates()) {
            return false;
        }
        long age = Math.max(0L,
                (now - frame.getReceiveNanoTime()) / 1_000_000L);
        return age <= maximumAgeMs;
    }

    void clear() {
        frames.clear();
    }

    private void trim() {
        while (frames.size() > capacity) frames.removeFirst();
    }
}
