package com.ascendingmc.survival;

import java.util.Locale;
import java.util.Set;

/** Pure input validation shared by commands and persisted world settings. */
public final class WorldSettingValues {
    private WorldSettingValues() { }

    public static boolean bool(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Use true or false.");
    }

    public static long time(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "day" -> 1000;
            case "night" -> 13000;
            default -> integer(value, 0, 23999, "Time");
        };
    }

    public static String weather(String value) {
        String result = value.toLowerCase(Locale.ROOT);
        if (!Set.of("clear", "rain", "thunder").contains(result)) {
            throw new IllegalArgumentException("Weather must be clear, rain or thunder.");
        }
        return result;
    }

    public static double border(String value) {
        double size;
        try { size = Double.parseDouble(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Border size must be a number."); }
        if (!Double.isFinite(size) || size < 16 || size > 59_999_968) {
            throw new IllegalArgumentException("Border size must be between 16 and 59999968.");
        }
        return size;
    }

    public static long integer(String value, long minimum, long maximum, String label) {
        try {
            long number = Long.parseLong(value);
            if (number >= minimum && number <= maximum) return number;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum + ".");
    }
}
