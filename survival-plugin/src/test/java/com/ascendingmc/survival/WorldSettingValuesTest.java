package com.ascendingmc.survival;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class WorldSettingValuesTest {
    @Test public void strictBooleansRejectTypos() {
        assertTrue(WorldSettingValues.bool("TRUE"));
        assertFalse(WorldSettingValues.bool("false"));
        assertThrows(IllegalArgumentException.class, () -> WorldSettingValues.bool("yes"));
    }

    @Test public void timeAllowsNamedAndBoundedValues() {
        assertEquals(1000, WorldSettingValues.time("day"));
        assertEquals(13000, WorldSettingValues.time("NIGHT"));
        assertEquals(23999, WorldSettingValues.time("23999"));
        assertThrows(IllegalArgumentException.class, () -> WorldSettingValues.time("24000"));
        assertThrows(IllegalArgumentException.class, () -> WorldSettingValues.time("-1"));
    }

    @Test public void weatherIsValidatedBeforeMutation() {
        assertEquals("rain", WorldSettingValues.weather("RAIN"));
        assertThrows(IllegalArgumentException.class, () -> WorldSettingValues.weather("snow"));
    }

    @Test public void borderRejectsNonFiniteAndOutOfBoundsValues() {
        assertEquals(16, WorldSettingValues.border("16"), 0);
        assertEquals(59999968, WorldSettingValues.border("59999968"), 0);
        for (String value : List.of("NaN", "Infinity", "-Infinity", "0", "15", "59999969", "not-a-number")) {
            assertThrows(value, IllegalArgumentException.class, () -> WorldSettingValues.border(value));
        }
    }

    @Test public void seedsAcceptFullSignedRange() {
        assertEquals(Long.MIN_VALUE, WorldSettingValues.integer(Long.toString(Long.MIN_VALUE), Long.MIN_VALUE, Long.MAX_VALUE, "Seed"));
        assertEquals(Long.MAX_VALUE, WorldSettingValues.integer(Long.toString(Long.MAX_VALUE), Long.MIN_VALUE, Long.MAX_VALUE, "Seed"));
        assertThrows(IllegalArgumentException.class, () -> WorldSettingValues.integer("9223372036854775808", Long.MIN_VALUE, Long.MAX_VALUE, "Seed"));
    }
}
