package com.ascendingmc.survival;

import java.util.List;

public final class SidebarText {
    private SidebarText() { }

    public static List<String> lines(int online, int ping, double x, double y, double z) {
        return List.of("§8§m────────────────────", "§fOnline: §c" + Math.max(0, online),
                "§fPing: §c" + Math.max(0, ping) + " ms", "§0 ",
                "§fX: §c" + block(x), "§fY: §c" + block(y), "§fZ: §c" + block(z),
                "§7§m────────────────────");
    }

    private static int block(double coordinate) {
        return (int) Math.floor(coordinate);
    }
}
