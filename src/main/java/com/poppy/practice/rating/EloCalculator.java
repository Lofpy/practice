package com.poppy.practice.rating;

import java.math.BigDecimal;

/** Standard Elo (400-point scale, K=32), stored without binary fractional drift. */
public final class EloCalculator {
    public static final int REQUIRED_PLACEMENTS = 3;
    public static final long FLOOR_MILLI = 1400000L;
    public static final long INITIAL_MIN_MILLI = 1500000L;
    public static final long INITIAL_MAX_MILLI = 1800000L;
    public static final long MATCH_RANGE_MILLI = 100000L;
    public static final int K = 32;

    private EloCalculator() { }

    public static long initialRating(double averageScore) {
        requireScore(averageScore);
        return INITIAL_MIN_MILLI + Math.round((INITIAL_MAX_MILLI - INITIAL_MIN_MILLI)
                * averageScore / 100.0D);
    }

    public static long winDeltaMilli(long winner, long loser) {
        requireRating(winner);
        requireRating(loser);
        double expected = 1.0D / (1.0D + Math.pow(10.0D,
                (((double) loser) - ((double) winner)) / 400000.0D));
        return Math.round(K * 1000.0D * (1.0D - expected));
    }

    public static RatingChange win(long winner, long loser) {
        long delta = winDeltaMilli(winner, loser);
        return new RatingChange(winner, Math.addExact(winner, delta), loser,
                Math.max(FLOOR_MILLI, loser - delta));
    }

    public static boolean inMatchRange(long first, long second) {
        requireRating(first);
        requireRating(second);
        return Math.max(first, second) - Math.min(first, second) <= MATCH_RANGE_MILLI;
    }

    public static String format(long ratingMilli) {
        return BigDecimal.valueOf(ratingMilli, 3).toPlainString();
    }

    static void requireScore(double score) {
        if (!Double.isFinite(score) || score < 0.0D || score > 100.0D) {
            throw new IllegalArgumentException("Placement score must be finite and between 0 and 100");
        }
    }

    private static void requireRating(long rating) {
        if (rating < FLOOR_MILLI) {
            throw new IllegalArgumentException("Qualified rating must be at least 1400.000");
        }
    }
}
