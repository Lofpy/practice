package com.poppy.practice.rating;

import java.nio.file.Path;

/** Returned only after the ledger and active assessment paths have both been reset. */
public final class CertificationResetResult {
    private final Path backupDirectory;
    private final int recordCount;
    private final int placementCount;

    CertificationResetResult(Path backupDirectory, CertificationResetPlan plan) {
        this.backupDirectory = backupDirectory;
        this.recordCount = plan.getRecordCount();
        this.placementCount = plan.getPlacementCount();
    }

    public Path getBackupDirectory() { return backupDirectory; }
    public int getRecordCount() { return recordCount; }
    public int getPlacementCount() { return placementCount; }
}
