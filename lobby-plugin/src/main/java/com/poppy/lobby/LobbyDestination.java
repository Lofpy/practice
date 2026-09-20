package com.poppy.lobby;

/** Shared selector/command routing; backend admission is always enforced by the proxy. */
enum LobbyDestination {
    PRACTICE(11, "Practice"),
    SURVIVAL(15, "Survival");

    private final int slot;
    private final String displayName;

    LobbyDestination(int slot, String displayName) {
        this.slot = slot;
        this.displayName = displayName;
    }

    int slot() { return slot; }
    String displayName() { return displayName; }

    static LobbyDestination atSlot(int slot) {
        for (LobbyDestination destination : values()) {
            if (destination.slot == slot) {
                return destination;
            }
        }
        return null;
    }
}
