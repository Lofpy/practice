package com.poppy.practice.rating;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class CertificationResetTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void previewSelectsExistingRecordsWithoutMutatingAnything() throws Exception {
        RatingService service = service();
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        qualify(service, player, "nodebuff");
        service.recordPlacement(player, "boxing", UUID.randomUUID(), 40);
        qualify(service, other, "nodebuff");
        byte[] before = Files.readAllBytes(ledger());
        CertificationResetPlan plan = service.previewCertificationReset(player, null);
        assertEquals(2, plan.getRecordCount());
        assertEquals(4, plan.getPlacementCount());
        assertEquals(1, plan.getQualifiedCount());
        assertEquals(1, plan.getPlayerIds().size());
        assertTrue(plan.getKitIds(player).containsAll(Arrays.asList("nodebuff", "boxing")));
        assertTrue(plan.getKitIds(other).isEmpty());
        try { plan.getPlayerIds().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { plan.getKitIds(player).clear(); fail(); } catch (UnsupportedOperationException expected) { }
        assertFalse(Files.exists(root().resolve("backups")));
        assertArrayEquals(before, Files.readAllBytes(ledger()));
        assertEquals(3, service.previewCertificationReset(null, null).getRecordCount());
        assertEquals(0, service.previewCertificationReset(UUID.randomUUID(), null).getRecordCount());
    }

    @Test public void selectedKitResetBacksUpLedgerAndAssessmentsAndKeepsOtherRecords() throws Exception {
        RatingService service = service();
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        qualify(service, player, "nodebuff");
        qualify(service, player, "boxing");
        qualify(service, other, "nodebuff");
        Path selected = assessment(player, "nodebuff", "selected.yml", "selected evidence");
        Path otherKit = assessment(player, "boxing", "other-kit.yml", "keep kit");
        Path otherPlayer = assessment(other, "nodebuff", "other-player.yml", "keep player");
        byte[] before = Files.readAllBytes(ledger());
        CertificationResetResult result = service.resetCertifications(service.previewCertificationReset(player, "nodebuff"), "Admin");
        assertEquals(1, result.getRecordCount());
        assertEquals(3, result.getPlacementCount());
        assertTrue(result.getBackupDirectory().startsWith(root().resolve("backups")));
        assertArrayEquals(before, Files.readAllBytes(result.getBackupDirectory().resolve("ratings.yml")));
        assertEquals("selected evidence", read(result.getBackupDirectory().resolve(root().relativize(selected))));
        assertFalse(Files.exists(selected.getParent()));
        assertEquals("keep kit", read(otherKit));
        assertEquals("keep player", read(otherPlayer));
        assertFalse(Files.exists(root().resolve(CertificationResetBackup.PENDING_FILE)));
        assertEquals(0, service.getPlacementCount(player, "nodebuff"));
        assertEquals(0L, service.getRatingMilli(player, "nodebuff"));
        assertEquals(1650000L, service.getRatingMilli(player, "boxing"));
        assertEquals(1650000L, service.getRatingMilli(other, "nodebuff"));
        RatingService reloaded = service();
        assertFalse(reloaded.isQualified(player, "nodebuff"));
        assertTrue(reloaded.isQualified(player, "boxing"));
        assertTrue(reloaded.isQualified(other, "nodebuff"));
        YamlConfiguration manifest = YamlConfiguration.loadConfiguration(result.getBackupDirectory().resolve("manifest.yml").toFile());
        assertEquals("Admin", manifest.getString("actor"));
        assertEquals(2, manifest.getStringList("sha256").size());
    }

    @Test public void resetRetiresRankedIdsWithoutRevertingOpponentRating() {
        RatingService service = service();
        UUID player = UUID.randomUUID(), other = UUID.randomUUID(), placement = UUID.randomUUID(), ranked = UUID.randomUUID();
        service.recordPlacement(player, "nodebuff", placement, 50);
        for (int index = 0; index < 2; index++) { service.recordPlacement(player, "nodebuff", UUID.randomUUID(), 50); }
        qualify(service, other, "nodebuff");
        service.recordRankedWin(ranked, "nodebuff", player, other);
        long opponentRating = service.getRatingMilli(other, "nodebuff");
        service.resetCertifications(service.previewCertificationReset(player, "nodebuff"), "Console");
        RatingService reloaded = service();
        assertEquals(opponentRating, reloaded.getRatingMilli(other, "nodebuff"));
        assertFalse(reloaded.recordPlacement(player, "nodebuff", placement, 100));
        assertFalse(reloaded.recordPlacement(player, "boxing", ranked, 100));
        assertNull(reloaded.recordRankedWin(ranked, "nodebuff", player, other));
        assertTrue(reloaded.recordPlacement(player, "nodebuff", UUID.randomUUID(), 20));
        assertEquals(1, reloaded.getPlacementCount(player, "nodebuff"));
        assertEquals(opponentRating, reloaded.getRatingMilli(other, "nodebuff"));
    }

    @Test public void allPlayersOneKitAndAllKitsSelectionsRemainIndependent() {
        RatingService service = service();
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        for (UUID selected : Arrays.asList(player, other)) {
            for (String kit : Arrays.asList("nodebuff", "boxing", "combo")) { qualify(service, selected, kit); }
        }
        CertificationResetResult first = service.resetCertifications(service.previewCertificationReset(null, "boxing"), "Admin");
        assertEquals(2, first.getRecordCount());
        assertEquals(6, first.getPlacementCount());
        assertEquals(0, service.getPlacementCount(player, "boxing"));
        assertEquals(0, service.getPlacementCount(other, "boxing"));
        assertTrue(service.isQualified(player, "combo"));
        CertificationResetResult second = service.resetCertifications(service.previewCertificationReset(null, null), "Admin");
        assertEquals(4, second.getRecordCount());
        assertEquals(12, second.getPlacementCount());
        assertNotEquals(first.getBackupDirectory(), second.getBackupDirectory());
        RatingService reloaded = service();
        assertEquals(0, reloaded.previewCertificationReset(null, null).getRecordCount());
        qualify(reloaded, player, "combo");
        assertTrue(reloaded.isQualified(player, "combo"));
    }

    @Test public void staleForeignAndReusedPlansCannotResetData() throws Exception {
        RatingService service = service();
        UUID player = UUID.randomUUID();
        service.recordPlacement(player, "nodebuff", UUID.randomUUID(), 40);
        CertificationResetPlan stale = service.previewCertificationReset(player, null);
        service.recordPlacement(player, "boxing", UUID.randomUUID(), 40);
        try { service.resetCertifications(stale, "Admin"); fail(); } catch (IllegalStateException expected) { }
        RatingService foreign = new RatingService(temporary.newFolder(), logger());
        try { foreign.resetCertifications(service.previewCertificationReset(player, null), "Admin"); fail(); }
        catch (IllegalStateException expected) { }
        CertificationResetPlan valid = service.previewCertificationReset(player, "nodebuff");
        service.resetCertifications(valid, "Admin");
        try { service.resetCertifications(valid, "Admin"); fail(); } catch (IllegalStateException expected) { }
        assertEquals(1, service.getPlacementCount(player, "boxing"));
    }

    @Test public void externalLedgerChangeRefusesResetWithoutOverwritingIt() throws Exception {
        RatingService service = service();
        UUID player = UUID.randomUUID();
        qualify(service, player, "nodebuff");
        CertificationResetPlan plan = service.previewCertificationReset(player, null);
        byte[] externallyEdited = (read(ledger()) + "\n# external edit\n").getBytes(StandardCharsets.UTF_8);
        Files.write(ledger(), externallyEdited);
        try { service.resetCertifications(plan, "Admin"); fail(); } catch (IllegalStateException expected) { }
        assertArrayEquals(externallyEdited, Files.readAllBytes(ledger()));
        assertTrue(service.isQualified(player, "nodebuff"));
        assertFalse(Files.exists(root().resolve("backups")));
    }

    @Test public void emptyOrMalformedSelectionsCannotCreateBackups() {
        RatingService service = service();
        try { service.previewCertificationReset(null, "../escape"); fail(); } catch (IllegalArgumentException expected) { }
        try { service.resetCertifications(service.previewCertificationReset(null, null), "Admin"); fail(); }
        catch (IllegalArgumentException expected) { }
        assertFalse(Files.exists(root().resolve("backups")));
    }

    @Test public void failedCommitRestoresStagedAssessmentsAndCanRetrySamePlan() throws Exception {
        RatingService initial = service();
        UUID player = UUID.randomUUID();
        qualify(initial, player, "nodebuff");
        Path evidence = assessment(player, "nodebuff", "evidence.yml", "evidence");
        byte[] before = Files.readAllBytes(ledger());
        AtomicBoolean failing = new AtomicBoolean(true);
        RatingService service = new RatingService(temporary.getRoot(), logger(), (path, yaml) -> {
            if (failing.get()) { throw new IOException("simulated disk full"); }
            Files.write(path, yaml.getBytes(StandardCharsets.UTF_8));
        });
        CertificationResetPlan plan = service.previewCertificationReset(player, null);
        try { service.resetCertifications(plan, "Admin"); fail(); } catch (IllegalStateException expected) { }
        assertArrayEquals(before, Files.readAllBytes(ledger()));
        assertEquals("evidence", read(evidence));
        assertTrue(service.isQualified(player, "nodebuff"));
        assertFalse(Files.exists(root().resolve(CertificationResetBackup.PENDING_FILE)));
        assertTrue(service().isQualified(player, "nodebuff"));
        failing.set(false);
        service.resetCertifications(plan, "Admin");
        assertFalse(service.isQualified(player, "nodebuff"));
        assertFalse(Files.exists(evidence));
    }

    @Test public void backupPathOrAssessmentPathCollisionFailsWithoutChangingRatings() throws Exception {
        for (String collision : Arrays.asList("backups", "tier-assessments")) {
            File directory = temporary.newFolder();
            RatingService service = new RatingService(directory, logger());
            UUID player = UUID.randomUUID();
            qualify(service, player, "combo");
            Files.write(directory.toPath().resolve(collision), new byte[] { 1, 2, 3 });
            byte[] before = Files.readAllBytes(directory.toPath().resolve("ratings.yml"));
            try { service.resetCertifications(service.previewCertificationReset(player, null), "Admin"); fail(); }
            catch (IllegalStateException expected) { }
            assertTrue(service.isQualified(player, "combo"));
            assertArrayEquals(before, Files.readAllBytes(directory.toPath().resolve("ratings.yml")));
        }
    }

    @Test public void writerThatReplacesLedgerThenThrowsKeepsRecoveryMarkerAndBlocksFurtherWrites() throws Exception {
        RatingService initial = service();
        UUID player = UUID.randomUUID();
        qualify(initial, player, "nodebuff");
        Path evidence = assessment(player, "nodebuff", "evidence.yml", "evidence");
        RatingService service = new RatingService(temporary.getRoot(), logger(), (path, yaml) -> {
            Files.write(path, yaml.getBytes(StandardCharsets.UTF_8));
            throw new IOException("write completed but acknowledgement failed");
        });
        try { service.resetCertifications(service.previewCertificationReset(player, null), "Admin"); fail(); }
        catch (IllegalStateException expected) { }
        assertTrue(service.isQualified(player, "nodebuff"));
        assertTrue(Files.exists(root().resolve(CertificationResetBackup.PENDING_FILE)));
        assertFalse(Files.exists(evidence));
        Path backup = java.nio.file.Paths.get(read(root().resolve(CertificationResetBackup.PENDING_FILE)));
        assertEquals("evidence", read(backup.resolve(root().relativize(evidence))));
        assertEquals("evidence", read(backup.resolve("staged-assessments").resolve(player.toString())
                .resolve("nodebuff").resolve("evidence.yml")));
        try { service.previewCertificationReset(player, null); fail(); } catch (IllegalStateException expected) { }
        try { service.recordPlacement(UUID.randomUUID(), "boxing", UUID.randomUUID(), 50); fail(); }
        catch (IllegalStateException expected) { }
        try { service(); fail(); } catch (IllegalStateException expected) { }
    }

    @Test public void pendingCrashMarkerPreventsLoadingUntilAnAdministratorRecovers() throws Exception {
        RatingService service = service();
        qualify(service, UUID.randomUUID(), "combo");
        Files.write(root().resolve(CertificationResetBackup.PENDING_FILE), "backup directory".getBytes(StandardCharsets.UTF_8));
        try { service(); fail(); } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Interrupted certification reset"));
        }
    }

    @Test public void linkedBackupAndAssessmentDirectoriesCannotEscapeTheDataDirectory() throws Exception {
        for (boolean linkedBackup : Arrays.asList(true, false)) {
            Path data = temporary.newFolder().toPath();
            Path outside = temporary.newFolder().toPath();
            Path outsideEvidence = outside.resolve("outside.yml");
            Files.write(outsideEvidence, "must remain untouched".getBytes(StandardCharsets.UTF_8));
            RatingService service = new RatingService(data.toFile(), logger());
            UUID player = UUID.randomUUID();
            qualify(service, player, "nodebuff");
            Path link = linkedBackup ? data.resolve("backups")
                    : data.resolve("tier-assessments").resolve(player.toString()).resolve("nodebuff");
            Files.createDirectories(link.getParent());
            try {
                Files.createSymbolicLink(link, outside);
            } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
                org.junit.Assume.assumeNoException(unavailable);
                return;
            }
            byte[] before = Files.readAllBytes(data.resolve("ratings.yml"));
            try { service.resetCertifications(service.previewCertificationReset(player, null), "Admin"); fail(); }
            catch (IllegalStateException expected) { }
            assertArrayEquals(before, Files.readAllBytes(data.resolve("ratings.yml")));
            assertEquals("must remain untouched", read(outsideEvidence));
            assertTrue(service.isQualified(player, "nodebuff"));
        }
    }

    @Test public void invalidRetiredIdsFailClosedButLegacyLedgersRemainSupported() throws Exception {
        RatingService service = service();
        UUID player = UUID.randomUUID(), active = UUID.randomUUID();
        service.recordPlacement(player, "nodebuff", active, 50);
        assertEquals(1, service().getPlacementCount(player, "nodebuff"));
        byte[] valid = Files.readAllBytes(ledger());
        UUID retired = UUID.randomUUID();
        for (Object invalid : Arrays.<Object>asList("not-a-list", Arrays.asList("invalid"),
                Arrays.asList(retired.toString(), retired.toString()), Arrays.asList(active.toString()), Arrays.asList(42))) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(valid, StandardCharsets.UTF_8));
            yaml.set("retired-matches", invalid);
            yaml.save(ledger().toFile());
            byte[] corrupt = Files.readAllBytes(ledger());
            try { service(); fail(); } catch (IllegalStateException expected) { }
            assertArrayEquals(corrupt, Files.readAllBytes(ledger()));
        }
    }

    private Path root() { return temporary.getRoot().toPath().toAbsolutePath().normalize(); }
    private Path ledger() { return root().resolve("ratings.yml"); }
    private RatingService service() { return new RatingService(temporary.getRoot(), logger()); }
    private Path assessment(UUID player, String kit, String name, String contents) throws IOException {
        Path path = root().resolve("tier-assessments").resolve(player.toString()).resolve(kit).resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        return path;
    }
    private static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static Logger logger() { Logger logger = Logger.getAnonymousLogger(); logger.setLevel(Level.OFF); return logger; }
    private static void qualify(RatingService service, UUID player, String kit) {
        for (int index = 0; index < 3; index++) { assertTrue(service.recordPlacement(player, kit, UUID.randomUUID(), 50)); }
    }
}
