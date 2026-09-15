package com.poppy.practice.chatter;

final class AttackSample {
    final long arrivalNs;
    final int protocol;
    final long packetSequence;
    final long clientFrame;
    final int targetEntityId;
    final long gapFromPreviousNs;
    final int frameDelta;
    final int attackIndexInFrame;
    final boolean predictedSprintBefore;
    final boolean slowdownEligible;
    final AttackClassification classification;
    final BurstType burstType;
    final Long kbWindowId;

    AttackSample(long arrivalNs, int protocol, long packetSequence, long clientFrame,
                 int targetEntityId, long gapFromPreviousNs, int frameDelta,
                 int attackIndexInFrame, boolean predictedSprintBefore,
                 boolean slowdownEligible, AttackClassification classification,
                 BurstType burstType, Long kbWindowId) {
        this.arrivalNs = arrivalNs;
        this.protocol = protocol;
        this.packetSequence = packetSequence;
        this.clientFrame = clientFrame;
        this.targetEntityId = targetEntityId;
        this.gapFromPreviousNs = gapFromPreviousNs;
        this.frameDelta = frameDelta;
        this.attackIndexInFrame = attackIndexInFrame;
        this.predictedSprintBefore = predictedSprintBefore;
        this.slowdownEligible = slowdownEligible;
        this.classification = classification;
        this.burstType = burstType;
        this.kbWindowId = kbWindowId;
    }
}
