package com.poppy.practice.chatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FixedRingBuffer<T> {
    private final Object[] values;
    private int next;
    private int size;

    FixedRingBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        values = new Object[capacity];
    }

    void add(T value) {
        values[next] = value;
        next = (next + 1) % values.length;
        if (size < values.length) {
            size++;
        }
    }

    @SuppressWarnings("unchecked")
    T latest() {
        if (size == 0) {
            return null;
        }
        int index = (next - 1 + values.length) % values.length;
        return (T) values[index];
    }

    @SuppressWarnings("unchecked")
    List<T> snapshot() {
        if (size == 0) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<T>(size);
        int start = (next - size + values.length) % values.length;
        for (int offset = 0; offset < size; offset++) {
            result.add((T) values[(start + offset) % values.length]);
        }
        return result;
    }

    void clear() {
        java.util.Arrays.fill(values, null);
        next = 0;
        size = 0;
    }

    int size() {
        return size;
    }
}
