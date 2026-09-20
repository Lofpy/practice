package com.ascendingmc.survival;

/** Fail closed when a crash leaves an ambiguous world/archive pair; never regenerate lost worlds. */
public final class WorldRecovery {
    private WorldRecovery() { }

    public static String finishPending(String state, boolean worldExists, boolean archiveExists) {
        if (!state.equals("DELETE_PENDING") && !state.equals("RESTORE_PENDING")) {
            throw new IllegalArgumentException("Not a pending operation.");
        }
        if (worldExists == archiveExists) {
            throw new IllegalStateException("World and archive locations are ambiguous; manual recovery required.");
        }
        return worldExists ? "ACTIVE" : "DELETED";
    }
}
