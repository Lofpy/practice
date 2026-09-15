package com.poppy.practice.rating;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class RatingServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void newPlayerNeedsExactlyThreePerKitAndHasNoInventedRating() {
        RatingService service = service(temporary.getRoot());
        UUID player = UUID.randomUUID();
        assertFalse(service.isQualified(player, "nodebuff"));
        assertEquals(0L, service.getRatingMilli(player, "nodebuff"));
        assertTrue(service.recordPlacement(player, "nodebuff", UUID.randomUUID(), 0));
        assertTrue(service.recordPlacement(player, "nodebuff", UUID.randomUUID(), 50));
        assertFalse(service.isQualified(player, "nodebuff"));
        assertEquals(0L, service.getRatingMilli(player, "nodebuff"));
        assertTrue(service.recordPlacement(player, "nodebuff", UUID.randomUUID(), 100));
        assertTrue(service.isQualified(player, "nodebuff"));
        assertEquals(3, service.getPlacementCount(player, "nodebuff"));
        assertEquals(50.0D, service.getPlacementAverage(player, "nodebuff"), 0D);
        assertEquals(1650000L, service.getRatingMilli(player, "nodebuff"));
        assertFalse(service.isQualified(player, "boxing"));
        assertEquals(0, service.getPlacementCount(player, "boxing"));
    }

    @Test public void allThreeKitsHaveIndependentCertificationsAndRatings() {
        RatingService service = service(temporary.getRoot());
        UUID player = UUID.randomUUID();
        qualify(service, player, "nodebuff", 0);
        qualify(service, player, "boxing", 50);
        qualify(service, player, "combo", 100);
        assertEquals(1500000L, service.getRatingMilli(player, "nodebuff"));
        assertEquals(1650000L, service.getRatingMilli(player, "boxing"));
        assertEquals(1800000L, service.getRatingMilli(player, "combo"));
    }

    @Test public void additionalBotMatchesNeverRerollInitialElo() {
        RatingService service = service(temporary.getRoot());
        UUID player = UUID.randomUUID();
        qualify(service, player, "nodebuff", 0);
        assertFalse(service.recordPlacement(player, "nodebuff", UUID.randomUUID(), 100));
        assertEquals(3, service.getPlacementCount(player, "nodebuff"));
        assertEquals(1500000L, service.getRatingMilli(player, "nodebuff"));
    }

    @Test public void placementsAndRankedResultSurviveRestartWithoutDuplicates() {
        File directory = temporary.getRoot();
        RatingService service = service(directory);
        UUID winner = UUID.randomUUID(), loser = UUID.randomUUID();
        UUID firstPlacement = UUID.randomUUID();
        service.recordPlacement(winner, "nodebuff", firstPlacement, 50);
        assertFalse(service.recordPlacement(winner, "nodebuff", firstPlacement, 100));
        service.recordPlacement(winner, "nodebuff", UUID.randomUUID(), 50);
        service.recordPlacement(winner, "nodebuff", UUID.randomUUID(), 50);
        qualify(service, loser, "nodebuff", 50);
        UUID match = UUID.randomUUID();
        RatingChange change = service.recordRankedWin(match, "nodebuff", winner, loser);
        assertEquals(16000L, change.getWinnerDeltaMilli());
        assertNull(service.recordRankedWin(match, "nodebuff", winner, loser));
        RatingService restored = service(directory);
        assertEquals(1666000L, restored.getRatingMilli(winner, "nodebuff"));
        assertEquals(1634000L, restored.getRatingMilli(loser, "nodebuff"));
        assertFalse(restored.recordPlacement(winner, "boxing", firstPlacement, 100));
        assertEquals(0, restored.getPlacementCount(winner, "boxing"));
        assertNull(restored.recordRankedWin(match, "nodebuff", loser, winner));
    }

    @Test public void rankedOneKitDoesNotChangeAnotherKitOrPlacementScores() {
        RatingService service = service(temporary.getRoot());
        UUID winner = UUID.randomUUID(), loser = UUID.randomUUID();
        qualify(service, winner, "nodebuff", 50);
        qualify(service, loser, "nodebuff", 50);
        qualify(service, winner, "boxing", 100);
        service.recordRankedWin(UUID.randomUUID(), "nodebuff", winner, loser);
        assertEquals(1800000L, service.getRatingMilli(winner, "boxing"));
        assertEquals(50.0D, service.getPlacementAverage(winner, "nodebuff"), 0D);
    }

    @Test public void bothParticipantsMustBeQualifiedInTheSelectedKit() {
        RatingService service = service(temporary.getRoot());
        UUID winner = UUID.randomUUID(), loser = UUID.randomUUID();
        qualify(service, winner, "nodebuff", 50);
        qualify(service, loser, "boxing", 50);
        try { service.recordRankedWin(UUID.randomUUID(), "nodebuff", winner, loser); fail(); }
        catch (IllegalStateException expected) { }
        assertEquals(1650000L, service.getRatingMilli(winner, "nodebuff"));
        assertEquals(0L, service.getRatingMilli(loser, "nodebuff"));
    }

    @Test public void repeatedLosingStopsAtTheFloorWithoutResettingCertification() {
        RatingService service = service(temporary.getRoot());
        UUID winner = UUID.randomUUID(), loser = UUID.randomUUID();
        qualify(service, winner, "nodebuff", 0);
        qualify(service, loser, "nodebuff", 0);
        for (int i = 0; i < 40; i++) {
            service.recordRankedWin(UUID.randomUUID(), "nodebuff", winner, loser);
        }
        assertEquals(1400000L, service.getRatingMilli(loser, "nodebuff"));
        assertTrue(service.isQualified(loser, "nodebuff"));
    }

    @Test public void failedPlacementSaveDoesNotCountAndSameMatchCanRetry() {
        AtomicBoolean failing = new AtomicBoolean(true);
        RatingService service = new RatingService(temporary.getRoot(), quietLogger(), (file, yaml) -> {
            if (failing.get()) { throw new IOException("disk full"); }
            Files.write(file, yaml.getBytes(StandardCharsets.UTF_8));
        });
        UUID player = UUID.randomUUID(), match = UUID.randomUUID();
        try { service.recordPlacement(player, "combo", match, 40); fail(); }
        catch (IllegalStateException expected) { }
        assertEquals(0, service.getPlacementCount(player, "combo"));
        failing.set(false);
        assertTrue(service.recordPlacement(player, "combo", match, 40));
        assertEquals(1, service(temporary.getRoot()).getPlacementCount(player, "combo"));
    }

    @Test public void failedRankedSavePublishesNeitherSideNorIdempotencyToken() throws Exception {
        File directory = temporary.getRoot();
        RatingService initial = service(directory);
        UUID winner = UUID.randomUUID(), loser = UUID.randomUUID(), match = UUID.randomUUID();
        qualify(initial, winner, "nodebuff", 50);
        qualify(initial, loser, "nodebuff", 50);
        byte[] before = Files.readAllBytes(new File(directory, "ratings.yml").toPath());
        AtomicBoolean failing = new AtomicBoolean(true);
        RatingService service = new RatingService(directory, quietLogger(), (file, yaml) -> {
            if (failing.get()) { throw new IOException("disk full"); }
            Files.write(file, yaml.getBytes(StandardCharsets.UTF_8));
        });
        try { service.recordRankedWin(match, "nodebuff", winner, loser); fail(); }
        catch (IllegalStateException expected) { }
        assertEquals(1650000L, service.getRatingMilli(winner, "nodebuff"));
        assertEquals(1650000L, service.getRatingMilli(loser, "nodebuff"));
        assertArrayEquals(before, Files.readAllBytes(new File(directory, "ratings.yml").toPath()));
        failing.set(false);
        assertNotNull(service.recordRankedWin(match, "nodebuff", winner, loser));
        RatingService restored = service(directory);
        assertEquals(1666000L, restored.getRatingMilli(winner, "nodebuff"));
        assertEquals(1634000L, restored.getRatingMilli(loser, "nodebuff"));
    }

    @Test public void corruptOrIncompleteYamlFailsClosedAndDoesNotOverwrite() throws Exception {
        for (String malformed : new String[] { "", "players: {}\n", "version: 2\nplayers: {}\nmatches: {}\n",
                "version: 1\nplayers: [\n", "version: '1'\nplayers: {}\nmatches: {}\n" }) {
            File directory = temporary.newFolder();
            File file = new File(directory, "ratings.yml");
            byte[] original = malformed.getBytes(StandardCharsets.UTF_8);
            Files.write(file.toPath(), original);
            try { service(directory); fail("Accepted corrupt ledger"); }
            catch (IllegalStateException expected) { }
            assertArrayEquals(original, Files.readAllBytes(file.toPath()));
        }
    }

    @Test public void missingProcessedPlacementLedgerIsRejected() throws Exception {
        File directory = temporary.getRoot();
        RatingService service = service(directory);
        service.recordPlacement(UUID.randomUUID(), "nodebuff", UUID.randomUUID(), 50);
        File file = new File(directory, "ratings.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("matches", null);
        yaml.createSection("matches");
        yaml.save(file);
        try { service(directory); fail("Missing placement match ids must not be accepted"); }
        catch (IllegalStateException expected) { }
    }

    @Test public void fractionalStoredRatingsAreRejectedInsteadOfSilentlyTruncated() throws Exception {
        File directory = temporary.getRoot();
        RatingService service = service(directory);
        UUID player = UUID.randomUUID();
        qualify(service, player, "nodebuff", 50);
        File file = new File(directory, "ratings.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("players." + player + ".kits.nodebuff.rating-milli", 1650000.5D);
        yaml.save(file);
        try { service(directory); fail(); }
        catch (IllegalStateException expected) { }
    }

    @Test public void invalidScoresAndKitPathsCannotMutateLedger() {
        RatingService service = service(temporary.getRoot());
        UUID player = UUID.randomUUID();
        try { service.recordPlacement(player, "nodebuff", UUID.randomUUID(), Double.NaN); fail(); }
        catch (IllegalArgumentException expected) { }
        try { service.recordPlacement(player, "nodebuff.evil", UUID.randomUUID(), 50); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(0, service.getPlacementCount(player, "nodebuff"));
        assertFalse(new File(temporary.getRoot(), "ratings.yml").exists());
    }

    @Test public void actualAtomicWriterCreatesNoLeftoverTemporaryFile() {
        RatingService service = service(temporary.getRoot());
        qualify(service, UUID.randomUUID(), "combo", 50);
        assertArrayEquals(new String[] { "ratings.yml" }, temporary.getRoot().list());
    }

    private static RatingService service(File directory) { return new RatingService(directory, quietLogger()); }
    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }
    private static void qualify(RatingService service, UUID player, String kit, double score) {
        for (int i = 0; i < 3; i++) { assertTrue(service.recordPlacement(player, kit, UUID.randomUUID(), score)); }
    }
}
