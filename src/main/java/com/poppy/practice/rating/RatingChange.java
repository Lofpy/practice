package com.poppy.practice.rating;

/** Immutable committed result. At the 1400 floor, the loser's loss may be smaller. */
public final class RatingChange {
    private final long winnerBeforeMilli;
    private final long winnerAfterMilli;
    private final long loserBeforeMilli;
    private final long loserAfterMilli;

    RatingChange(long winnerBeforeMilli, long winnerAfterMilli, long loserBeforeMilli, long loserAfterMilli) {
        this.winnerBeforeMilli = winnerBeforeMilli;
        this.winnerAfterMilli = winnerAfterMilli;
        this.loserBeforeMilli = loserBeforeMilli;
        this.loserAfterMilli = loserAfterMilli;
    }

    public long getWinnerBeforeMilli() { return winnerBeforeMilli; }
    public long getWinnerAfterMilli() { return winnerAfterMilli; }
    public long getLoserBeforeMilli() { return loserBeforeMilli; }
    public long getLoserAfterMilli() { return loserAfterMilli; }
    public long getWinnerDeltaMilli() { return winnerAfterMilli - winnerBeforeMilli; }
    public long getLoserDeltaMilli() { return loserAfterMilli - loserBeforeMilli; }
}
