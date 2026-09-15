package com.poppy.practice.match;

/** Rules selected by the active match, never by a player's lobby kit preference. */
public final class ComboRules {
    public static final String KIT_ID = "combo";
    public static final int ENDER_PEARL_COOLDOWN_SECONDS = 8;

    private ComboRules() {
    }

    public static boolean isCombo(String kitId) {
        return KIT_ID.equalsIgnoreCase(kitId);
    }
}
