package com.poppy.practice.rating;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable, service-owned preview. It cannot be reused after the ledger changes. */
public final class CertificationResetPlan {
    final Object owner;
    final long revision;
    final String ledgerHash;
    final Set<String> recordKeys;
    private final Map<UUID, Set<String>> kits;
    private final int placementCount;
    private final int qualifiedCount;

    CertificationResetPlan(Object owner, long revision, String ledgerHash,
                           Map<UUID, Set<String>> selected, int placementCount, int qualifiedCount) {
        this.owner = owner;
        this.revision = revision;
        this.ledgerHash = ledgerHash;
        Map<UUID, Set<String>> copy = new LinkedHashMap<UUID, Set<String>>();
        Set<String> keys = new LinkedHashSet<String>();
        for (Map.Entry<UUID, Set<String>> entry : selected.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<String>(entry.getValue())));
            for (String kit : entry.getValue()) { keys.add(entry.getKey() + ":" + kit); }
        }
        this.kits = Collections.unmodifiableMap(copy);
        this.recordKeys = Collections.unmodifiableSet(keys);
        this.placementCount = placementCount;
        this.qualifiedCount = qualifiedCount;
    }

    public Set<UUID> getPlayerIds() { return kits.keySet(); }
    public Set<String> getKitIds(UUID player) {
        Set<String> result = kits.get(player);
        return result == null ? Collections.<String>emptySet() : result;
    }
    public int getRecordCount() { return recordKeys.size(); }
    public int getPlacementCount() { return placementCount; }
    public int getQualifiedCount() { return qualifiedCount; }
}
