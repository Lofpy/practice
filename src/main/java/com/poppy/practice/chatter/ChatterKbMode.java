package com.poppy.practice.chatter;

public enum ChatterKbMode {
    OFF,
    DETECT_ONLY,
    CHATTER_ONLY,
    STRICT_NORMALIZE;

    public static ChatterKbMode parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("chatter-kb.mode is missing");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("chatter-kb.mode must be OFF, DETECT_ONLY, CHATTER_ONLY, or STRICT_NORMALIZE");
        }
    }
}
