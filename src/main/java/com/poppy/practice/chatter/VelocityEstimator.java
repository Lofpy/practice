package com.poppy.practice.chatter;

final class VelocityEstimator {
    Vec3 advance(Vec3 velocity, boolean onGround, ChatterKbConfig config) {
        double horizontalDrag = config.airDrag * (onGround ? config.groundSlipperiness : 1.0D);
        double y = onGround ? velocity.getY()
                : (velocity.getY() - config.gravity) * config.verticalDrag;
        return new Vec3(velocity.getX() * horizontalDrag, y,
                velocity.getZ() * horizontalDrag);
    }

    Vec3 blendObservation(Vec3 model, Vec3 observed, Vec3 initial,
                          ChatterKbConfig config) {
        if (observed == null || observed.horizontalLength() > 1.5D
                || observed.horizontalAngleDegrees(initial) > config.maxDirectionDegrees) {
            return model;
        }
        double alpha = config.observationAlpha;
        return new Vec3(model.getX() * (1.0D - alpha) + observed.getX() * alpha,
                model.getY(),
                model.getZ() * (1.0D - alpha) + observed.getZ() * alpha);
    }

    Vec3 project(Vec3 baseline, boolean onGround, int frames, ChatterKbConfig config) {
        Vec3 result = baseline;
        for (int frame = 0; frame < frames; frame++) {
            result = advance(result, onGround, config);
        }
        return result;
    }

    Vec3 correction(Vec3 expected, Vec3 initial, ChatterKbConfig config) {
        Vec3 scaled = expected.multiply(config.correctionScale);
        double expectedHorizontal = expected.horizontalLength();
        double scaledHorizontal = scaled.horizontalLength();
        if (scaledHorizontal > expectedHorizontal && scaledHorizontal > 0.0D) {
            double clamp = expectedHorizontal / scaledHorizontal;
            scaled = new Vec3(scaled.getX() * clamp, scaled.getY(), scaled.getZ() * clamp);
        }
        if (scaled.horizontalAngleDegrees(initial) > config.maxDirectionDegrees) {
            return null;
        }
        return scaled.isFinite() ? scaled : null;
    }

    int estimatedOneWayFrames(int pingMs, ChatterKbConfig config) {
        if (!config.pingProjection || pingMs < 0) return 0;
        return Math.max(0, Math.min(2, (int) Math.round(pingMs / 100.0D)));
    }
}
