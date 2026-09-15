package com.poppy.practice.reach;

public final class ReachResult {
    private final ReachDecision decision;
    private final double measuredReach;
    private final double allowedReach;
    private final double excess;
    private final Reliability reliability;
    private final String reason;
    private final long selectedAttackerSequence;
    private final long selectedTargetSequence;
    private final int attackerCandidateCount;
    private final int targetCandidateCount;
    private final long snapshotAgeMs;

    public ReachResult(ReachDecision decision, double measuredReach,
                       double allowedReach, Reliability reliability, String reason,
                       long selectedAttackerSequence, long selectedTargetSequence,
                       int attackerCandidateCount, int targetCandidateCount,
                       long snapshotAgeMs) {
        this.decision = decision;
        this.measuredReach = measuredReach;
        this.allowedReach = allowedReach;
        this.excess = Math.max(0.0D, measuredReach - allowedReach);
        this.reliability = reliability;
        this.reason = reason;
        this.selectedAttackerSequence = selectedAttackerSequence;
        this.selectedTargetSequence = selectedTargetSequence;
        this.attackerCandidateCount = attackerCandidateCount;
        this.targetCandidateCount = targetCandidateCount;
        this.snapshotAgeMs = snapshotAgeMs;
    }

    public ReachDecision getDecision() { return decision; }
    public double getMeasuredReach() { return measuredReach; }
    public double getAllowedReach() { return allowedReach; }
    public double getExcess() { return excess; }
    public Reliability getReliability() { return reliability; }
    public String getReason() { return reason; }
    public long getSelectedAttackerSequence() { return selectedAttackerSequence; }
    public long getSelectedTargetSequence() { return selectedTargetSequence; }
    public int getAttackerCandidateCount() { return attackerCandidateCount; }
    public int getTargetCandidateCount() { return targetCandidateCount; }
    public long getSnapshotAgeMs() { return snapshotAgeMs; }
}
