package com.ascendingmc.survival;

import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SidebarTextTest {
    @Test public void displaysBackendOnlinePingAndCoordinatesInEnglish() {
        List<String> lines = SidebarText.lines(7, 42, 12.3, 65.9, 234.5);
        assertTrue(lines.contains("§fOnline: §c7"));
        assertTrue(lines.contains("§fPing: §c42 ms"));
        assertTrue(lines.contains("§fX: §c12"));
        assertTrue(lines.contains("§fY: §c65"));
        assertTrue(lines.contains("§fZ: §c234"));
        assertEquals(lines.size(), new HashSet<>(lines).size());
    }

    @Test public void negativeCoordinatesAreFlooredNotTruncated() {
        List<String> lines = SidebarText.lines(0, -1, -0.1, -64, -100.9);
        assertTrue(lines.contains("§fX: §c-1"));
        assertTrue(lines.contains("§fY: §c-64"));
        assertTrue(lines.contains("§fZ: §c-101"));
        assertTrue(lines.contains("§fPing: §c0 ms"));
    }
}
