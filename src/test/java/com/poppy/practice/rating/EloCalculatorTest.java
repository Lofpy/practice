package com.poppy.practice.rating;

import org.junit.Test;

import static org.junit.Assert.*;

public class EloCalculatorTest {
    @Test public void equalRatingsMoveExactlySixteenEach() {
        RatingChange change = EloCalculator.win(1650000L, 1650000L);
        assertEquals(1666000L, change.getWinnerAfterMilli());
        assertEquals(1634000L, change.getLoserAfterMilli());
        assertEquals(16000L, change.getWinnerDeltaMilli());
        assertEquals(-16000L, change.getLoserDeltaMilli());
    }

    @Test public void oneHundredPointUnderdogGetsStandardK32UpsetGain() {
        assertEquals(20482L, EloCalculator.winDeltaMilli(1500000L, 1600000L));
        assertEquals(11518L, EloCalculator.winDeltaMilli(1600000L, 1500000L));
    }

    @Test public void floorsOnlyTheLoserAndDoesNotChangeTheWinnersStandardGain() {
        RatingChange change = EloCalculator.win(1400000L, 1400000L);
        assertEquals(1416000L, change.getWinnerAfterMilli());
        assertEquals(1400000L, change.getLoserAfterMilli());
        assertEquals(0L, change.getLoserDeltaMilli());
    }

    @Test public void nearFloorLossIsClampedExactly() {
        RatingChange change = EloCalculator.win(1401000L, 1401000L);
        assertEquals(-1000L, change.getLoserDeltaMilli());
        assertEquals(16000L, change.getWinnerDeltaMilli());
    }

    @Test public void placementRangeAndFractionalScoreMapToThreeDecimals() {
        assertEquals(1500000L, EloCalculator.initialRating(0));
        assertEquals(1650000L, EloCalculator.initialRating(50));
        assertEquals(1800000L, EloCalculator.initialRating(100));
        assertEquals(1500001L, EloCalculator.initialRating(0.000333333333333D));
    }

    @Test public void invalidScoresAreRejected() {
        for (double score : new double[] { -0.01D, 100.01D, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY }) {
            try { EloCalculator.initialRating(score); fail("Invalid score " + score); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void rangeIsInclusiveAndNeverExpanded() {
        assertTrue(EloCalculator.inMatchRange(1500000L, 1600000L));
        assertTrue(EloCalculator.inMatchRange(1600000L, 1500000L));
        assertTrue(EloCalculator.inMatchRange(1500000L, 1599999L));
        assertFalse(EloCalculator.inMatchRange(1500000L, 1600001L));
    }

    @Test public void longRatingsDoNotOverflowDifferenceOrProduceNegativeGain() {
        assertFalse(EloCalculator.inMatchRange(Long.MAX_VALUE, 1400000L));
        assertEquals(32000L, EloCalculator.winDeltaMilli(1400000L, Long.MAX_VALUE));
        assertEquals(0L, EloCalculator.winDeltaMilli(Long.MAX_VALUE, 1400000L));
    }

    @Test public void formattingAlwaysHasThreeFractionalPlacesAndNoLocaleComma() {
        assertEquals("1500.000", EloCalculator.format(1500000L));
        assertEquals("16.001", EloCalculator.format(16001L));
        assertEquals("-16.000", EloCalculator.format(-16000L));
        assertEquals("0.000", EloCalculator.format(0L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unqualifiedSentinelCannotBeUsedAsAnEloRating() {
        EloCalculator.win(0L, 1500000L);
    }
}
