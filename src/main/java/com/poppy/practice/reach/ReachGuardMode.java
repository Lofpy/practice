package com.poppy.practice.reach;

public enum ReachGuardMode {
    OBSERVE,
    PROTECT,
    STRICT;

    public static ReachGuardMode parse(String value, ReachGuardMode fallback) {
        if (value == null) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
