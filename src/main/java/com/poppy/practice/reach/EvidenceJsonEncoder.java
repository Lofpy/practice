package com.poppy.practice.reach;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Dependency-free JSON encoder used from the evidence writer thread. */
public final class EvidenceJsonEncoder {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public String encode(EvidenceRecord record) {
        if (record == null) throw new IllegalArgumentException("record");
        StringBuilder json = new StringBuilder(768);
        json.append('{');
        appendStringField(json, "timestamp",
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(record.getTimestamp()));
        appendStringField(json, "attackerUuid", uuid(record.getAttackerUuid()));
        appendStringField(json, "attackerName", record.getAttackerName());
        appendStringField(json, "targetUuid", uuid(record.getTargetUuid()));
        appendStringField(json, "targetName", record.getTargetName());
        appendLongField(json, "protocol", record.getProtocol());
        appendStringField(json, "mode", name(record.getMode()));
        appendStringField(json, "configuredMode", name(record.getConfiguredMode()));
        appendStringField(json, "decision", name(record.getDecision()));
        appendBooleanField(json, "cancelled", record.isCancelled());
        appendStringField(json, "reason", record.getReason());
        appendStringField(json, "reliability", name(record.getReliability()));
        appendDoubleField(json, "measuredReach", record.getMeasuredReach());
        appendDoubleField(json, "allowedReach", record.getAllowedReach());
        appendDoubleField(json, "excess", record.getExcess());
        appendDoubleField(json, "vl", record.getViolationLevel());
        appendDoubleField(json, "staleVl", record.getStaleViolationLevel());
        appendDoubleField(json, "invalidEntityVl",
                record.getInvalidEntityViolationLevel());
        appendLongField(json, "pingMs", record.getPingMs());
        appendLongField(json, "jitterMs", record.getJitterMs());
        appendDoubleField(json, "tps", record.getTps());
        appendDoubleField(json, "tickDurationMs", record.getTickDurationMs());
        appendLongField(json, "serverTick", record.getServerTick());
        appendLongField(json, "attackerCandidateCount",
                record.getAttackerCandidateCount());
        appendLongField(json, "targetCandidateCount", record.getTargetCandidateCount());
        appendLongField(json, "attackSequence", record.getAttackSequence());
        appendLongField(json, "attackerFrameSequence",
                record.getAttackerFrameSequence());
        appendLongField(json, "targetFrameSequence", record.getTargetFrameSequence());
        appendLongField(json, "targetEntityId", record.getTargetEntityId());
        appendDoubleField(json, "attackerX", record.getAttackerX());
        appendDoubleField(json, "attackerY", record.getAttackerY());
        appendDoubleField(json, "attackerZ", record.getAttackerZ());
        appendDoubleField(json, "attackerEyeX", record.getAttackerEyeX());
        appendDoubleField(json, "attackerEyeY", record.getAttackerEyeY());
        appendDoubleField(json, "attackerEyeZ", record.getAttackerEyeZ());
        appendBooleanField(json, "attackerSneaking", record.isAttackerSneaking());
        appendDoubleField(json, "targetMinX", record.getTargetMinX());
        appendDoubleField(json, "targetMinY", record.getTargetMinY());
        appendDoubleField(json, "targetMinZ", record.getTargetMinZ());
        appendDoubleField(json, "targetMaxX", record.getTargetMaxX());
        appendDoubleField(json, "targetMaxY", record.getTargetMaxY());
        appendDoubleField(json, "targetMaxZ", record.getTargetMaxZ());
        appendDoubleField(json, "selectedExpandedMinX", record.getSelectedExpandedMinX());
        appendDoubleField(json, "selectedExpandedMinY", record.getSelectedExpandedMinY());
        appendDoubleField(json, "selectedExpandedMinZ", record.getSelectedExpandedMinZ());
        appendDoubleField(json, "selectedExpandedMaxX", record.getSelectedExpandedMaxX());
        appendDoubleField(json, "selectedExpandedMaxY", record.getSelectedExpandedMaxY());
        appendDoubleField(json, "selectedExpandedMaxZ", record.getSelectedExpandedMaxZ());
        appendDoubleField(json, "baseReach", record.getBaseReach());
        appendDoubleField(json, "hitboxExpansion", record.getHitboxExpansion());
        appendDoubleField(json, "geometryEpsilon", record.getGeometryEpsilon());
        appendDoubleField(json, "totalExpansion", record.getTotalExpansion());
        appendDoubleField(json, "vlExcess", record.getVlExcess());
        appendDoubleField(json, "vlAdded", record.getVlAdded());
        appendDoubleField(json, "violationBase", record.getViolationBase());
        appendDoubleField(json, "excessMultiplier", record.getExcessMultiplier());
        appendDoubleField(json, "maxExcessAddition", record.getMaxExcessAddition());
        appendBooleanField(json, "teleportGrace", record.isGrace());
        appendBooleanField(json, "targetTeleportGrace",
                record.isTargetTeleportGrace());
        appendBooleanField(json, "unsupportedProtocol", record.isUnsupportedProtocol());
        appendBooleanField(json, "heartbeatStalled", record.isHeartbeatStalled());
        appendBooleanField(json, "recentKnockback", record.hasRecentKnockback());
        appendLongField(json, "transactionRttMs", record.getTransactionRttMs());
        appendLongField(json, "pendingSyncDelayMs", record.getPendingSyncDelayMs());
        appendLongField(json, "droppedEvidenceCount",
                record.getDroppedEvidenceCount());
        appendAttackerCandidateArray(json, "attackerCandidates",
                record.getAttackerCandidates());
        appendCandidateArray(json, "targetCandidates", record.getTargetCandidates());
        appendCandidateField(json, "newestTargetState",
                record.getNewestTargetState());
        appendCandidateField(json, "staleAnchor", record.getStaleAnchor());
        appendCandidateField(json, "currentRuntimeCandidate",
                record.getCurrentRuntimeCandidate());
        appendStringArray(json, "unverifiedExceptions",
                record.getUnverifiedExceptions());
        appendLongField(json, "snapshotAgeMs", record.getSnapshotAgeMs());
        json.append('}');
        return json.toString();
    }

    private static String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static void appendAttackerCandidateArray(StringBuilder output, String key,
            List<EvidenceRecord.AttackerCandidateEvidence> values) {
        appendSeparatorAndName(output, key);
        output.append('[');
        if (values != null) {
            boolean first = true;
            for (EvidenceRecord.AttackerCandidateEvidence value : values) {
                if (!first) output.append(',');
                appendAttackerCandidateValue(output, value);
                first = false;
            }
        }
        output.append(']');
    }

    private static void appendAttackerCandidateValue(StringBuilder output,
            EvidenceRecord.AttackerCandidateEvidence value) {
        if (value == null) {
            output.append("null");
            return;
        }
        StringBuilder candidate = new StringBuilder(320);
        candidate.append('{');
        appendLongField(candidate, "sequence", value.getSequence());
        appendLongField(candidate, "ageMs", value.getAgeMs());
        appendLongField(candidate, "serverTick", value.getServerTick());
        appendDoubleField(candidate, "x", value.getX());
        appendDoubleField(candidate, "y", value.getY());
        appendDoubleField(candidate, "z", value.getZ());
        appendDoubleField(candidate, "eyeX", value.getEyeX());
        appendDoubleField(candidate, "eyeY", value.getEyeY());
        appendDoubleField(candidate, "eyeZ", value.getEyeZ());
        appendDoubleField(candidate, "yaw", value.getYaw());
        appendDoubleField(candidate, "pitch", value.getPitch());
        appendBooleanField(candidate, "hasPosition", value.hasPosition());
        appendBooleanField(candidate, "hasRotation", value.hasRotation());
        appendBooleanField(candidate, "onGround", value.isOnGround());
        appendBooleanField(candidate, "sneaking", value.isSneaking());
        appendBooleanField(candidate, "valid", value.isValid());
        candidate.append('}');
        output.append(candidate);
    }

    private static void appendCandidateArray(StringBuilder output, String key,
                                             List<EvidenceRecord.TargetCandidateEvidence> values) {
        appendSeparatorAndName(output, key);
        output.append('[');
        if (values != null) {
            boolean first = true;
            for (EvidenceRecord.TargetCandidateEvidence value : values) {
                if (!first) output.append(',');
                appendCandidateValue(output, value);
                first = false;
            }
        }
        output.append(']');
    }

    private static void appendCandidateField(StringBuilder output, String key,
                                             EvidenceRecord.TargetCandidateEvidence value) {
        appendSeparatorAndName(output, key);
        appendCandidateValue(output, value);
    }

    private static void appendCandidateValue(StringBuilder output,
                                             EvidenceRecord.TargetCandidateEvidence value) {
        if (value == null) {
            output.append("null");
            return;
        }
        StringBuilder candidate = new StringBuilder(256);
        candidate.append('{');
        appendLongField(candidate, "sequence", value.getSequence());
        appendStringField(candidate, "status", value.getStatus());
        appendLongField(candidate, "ageMs", value.getAgeMs());
        appendLongField(candidate, "serverTick", value.getServerTick());
        appendLongField(candidate, "entityId", value.getEntityId());
        appendStringField(candidate, "entityUuid", uuid(value.getEntityUuid()));
        appendDoubleField(candidate, "minX", value.getMinX());
        appendDoubleField(candidate, "minY", value.getMinY());
        appendDoubleField(candidate, "minZ", value.getMinZ());
        appendDoubleField(candidate, "maxX", value.getMaxX());
        appendDoubleField(candidate, "maxY", value.getMaxY());
        appendDoubleField(candidate, "maxZ", value.getMaxZ());
        appendBooleanField(candidate, "teleport", value.isTeleport());
        appendBooleanField(candidate, "valid", value.isValid());
        appendLongField(candidate, "teleportEpoch", value.getTeleportEpoch());
        candidate.append('}');
        output.append(candidate);
    }

    private static void appendStringArray(StringBuilder output, String key,
                                          List<String> values) {
        appendSeparatorAndName(output, key);
        output.append('[');
        if (values != null) {
            boolean first = true;
            for (String value : values) {
                if (!first) output.append(',');
                if (value == null) output.append("null");
                else appendQuoted(output, value);
                first = false;
            }
        }
        output.append(']');
    }

    private static void appendStringField(StringBuilder output, String key, String value) {
        appendSeparatorAndName(output, key);
        if (value == null) {
            output.append("null");
        } else {
            appendQuoted(output, value);
        }
    }

    private static void appendLongField(StringBuilder output, String key, long value) {
        appendSeparatorAndName(output, key);
        output.append(value);
    }

    private static void appendDoubleField(StringBuilder output, String key, double value) {
        appendSeparatorAndName(output, key);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            output.append("null");
        } else {
            output.append(Double.toString(value));
        }
    }

    private static void appendBooleanField(StringBuilder output, String key, boolean value) {
        appendSeparatorAndName(output, key);
        output.append(value);
    }

    private static void appendSeparatorAndName(StringBuilder output, String key) {
        if (output.length() > 1) output.append(',');
        appendQuoted(output, key);
        output.append(':');
    }

    private static void appendQuoted(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': output.append("\\\""); break;
                case '\\': output.append("\\\\"); break;
                case '\b': output.append("\\b"); break;
                case '\f': output.append("\\f"); break;
                case '\n': output.append("\\n"); break;
                case '\r': output.append("\\r"); break;
                case '\t': output.append("\\t"); break;
                default:
                    if (character < 0x20 || character == 0x2028 || character == 0x2029
                            || Character.isSurrogate(character)) {
                        appendUnicodeEscape(output, character);
                    } else {
                        output.append(character);
                    }
                    break;
            }
        }
        output.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder output, char value) {
        output.append("\\u");
        output.append(HEX[(value >>> 12) & 0x0F]);
        output.append(HEX[(value >>> 8) & 0x0F]);
        output.append(HEX[(value >>> 4) & 0x0F]);
        output.append(HEX[value & 0x0F]);
    }
}
