package com.ascendingmc.survival;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public final class OfflineWorldRegeneratorTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void serializesOnlyValidatedIdentifiers() throws Exception {
        RegenerationPlan plan = RegenerationPlan.regenerate("survival", List.of("overworld", "the_nether", "the_end", "resource"));
        assertEquals(plan.toProperties(), RegenerationPlan.fromProperties(plan.toProperties()).toProperties());
        assertThrows(IllegalArgumentException.class, () -> RegenerationPlan.regenerate("../survival", List.of("overworld")));
        assertThrows(IllegalArgumentException.class, () -> RegenerationPlan.regenerate("survival", List.of("../world")));
        assertThrows(IllegalArgumentException.class, () -> RegenerationPlan.regenerate("survival", List.of("NUL")));
        assertThrows(IllegalArgumentException.class, () -> RegenerationPlan.regenerate("survival", List.of("a", "a")));
        assertThrows(IllegalArgumentException.class, () -> RegenerationPlan.regenerate("survival", List.of()));
    }

    @Test public void missingRequestDoesNotTouchServerTree() throws Exception {
        Path root = temporary.newFolder("empty").toPath();
        assertFalse(OfflineWorldRegenerator.runPending(root));
        try (var files = Files.list(root)) { assertEquals(0L, files.count()); }
    }

    @Test public void requestQueuesWithoutRegeneratingAndCannotOverwrite() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan plan = plan("overworld");
        OfflineWorldRegenerator.request(root, plan);
        assertEquals(plan.id(), OfflineWorldRegenerator.readPending(root).id());
        assertTrue(Files.exists(world(root, "overworld").resolve("region/r.0.0.mca")));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.request(root, plan("overworld")));
        assertEquals(plan.id(), OfflineWorldRegenerator.readPending(root).id());
    }

    @Test public void runningServerLockPreventsAllWorldChanges() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan plan = plan("overworld");
        OfflineWorldRegenerator.request(root, plan);
        try (FileChannel channel = FileChannel.open(root.resolve("survival/session.lock"), StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            IOException failure = assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root));
            assertTrue(failure.getMessage().contains("still running"));
            assertFalse(Files.exists(archive(root, plan)));
            assertEquals("terrain-overworld", Files.readString(world(root, "overworld").resolve("region/r.0.0.mca")));
        }
    }

    @Test public void regeneratesPrimaryDimensionsAndPreservesSeedIdentitySettingsAndPlayerData() throws Exception {
        Path root = fixture("overworld", "the_nether", "the_end", "untouched");
        RegenerationPlan plan = plan("overworld", "the_nether", "the_end");
        OfflineWorldRegenerator.request(root, plan);
        assertTrue(OfflineWorldRegenerator.runPending(root));
        assertNull(OfflineWorldRegenerator.readPending(root));
        assertFalse(OfflineWorldRegenerator.runPending(root));
        assertEquals("inventory-and-location", Files.readString(root.resolve("survival/players/alex.dat")));
        assertEquals("shared-level-data", Files.readString(root.resolve("survival/level.dat")));
        assertEquals("server-owner-configuration", Files.readString(root.resolve("plugins/AscendingSurvival/worlds.yml")));
        for (String dimension : plan.dimensions()) {
            Path live = world(root, dimension);
            Path original = archive(root, plan).resolve("original/" + dimension);
            assertFalse(Files.exists(live.resolve("region")));
            assertFalse(Files.exists(live.resolve("entities")));
            assertFalse(Files.exists(live.resolve("poi")));
            assertFalse(Files.exists(live.resolve("data/minecraft/raids.dat")));
            assertFalse(Files.exists(live.resolve("data/minecraft/ender_dragon_fight.dat")));
            for (String metadata : List.of("data/minecraft/world_gen_settings.dat", "data/paper/metadata.dat",
                    "data/minecraft/game_rules.dat", "data/minecraft/world_border.dat", "data/minecraft/world_clocks.dat",
                    "data/minecraft/weather.dat", "data/paper/level_overrides.dat", "data/paper/persistent_data_container.dat", "paper-world.yml")) {
                assertArrayEquals(metadata, Files.readAllBytes(original.resolve(metadata)), Files.readAllBytes(live.resolve(metadata)));
            }
            assertEquals("terrain-" + dimension, Files.readString(original.resolve("region/r.0.0.mca")));
            assertEquals("entities", Files.readString(original.resolve("entities/r.0.0.mca")));
            assertEquals("poi", Files.readString(original.resolve("poi/r.0.0.mca")));
        }
        assertTrue(Files.exists(world(root, "untouched").resolve("region/r.0.0.mca")));
        Properties epoch = read(root.resolve(OfflineWorldRegenerator.COMPLETED_PATH));
        assertEquals(plan.id(), epoch.getProperty("id"));
        assertEquals("overworld,the_nether,the_end", epoch.getProperty("dimensions"));
        assertEquals("COMPLETE", read(archive(root, plan).resolve("manifest.properties")).getProperty("state"));
        assertTrue(Files.exists(archive(root, plan).resolve("completed-request.properties")));
    }

    @Test public void restoresEntireSnapshotWhileKeepingOriginalAndRegeneratedBackups() throws Exception {
        Path root = fixture("overworld", "the_nether");
        RegenerationPlan regeneration = plan("overworld", "the_nether");
        OfflineWorldRegenerator.request(root, regeneration);
        OfflineWorldRegenerator.runPending(root);
        write(world(root, "overworld").resolve("region/new-region.mca"), "newly-generated-terrain");
        RegenerationPlan restoration = OfflineWorldRegenerator.restorePlan(root, regeneration.id());
        assertNotEquals(regeneration.id(), restoration.id());
        assertEquals(RegenerationPlan.Operation.RESTORE, restoration.operation());
        OfflineWorldRegenerator.request(root, restoration);
        OfflineWorldRegenerator.runPending(root);
        assertEquals("terrain-overworld", Files.readString(world(root, "overworld").resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(world(root, "overworld").resolve("region/new-region.mca")));
        assertEquals("newly-generated-terrain", Files.readString(archive(root, restoration).resolve("original/overworld/region/new-region.mca")));
        assertEquals("terrain-overworld", Files.readString(archive(root, regeneration).resolve("original/overworld/region/r.0.0.mca")));
        assertEquals(restoration.id(), read(root.resolve(OfflineWorldRegenerator.COMPLETED_PATH)).getProperty("id"));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.restorePlan(root, regeneration.id()));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.restorePlan(root, restoration.id()));
    }

    @Test public void repeatedRegenerationCanRestoreSnapshotContainingEarlierReceipt() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan first = plan("overworld");
        OfflineWorldRegenerator.request(root, first);
        OfflineWorldRegenerator.runPending(root);
        write(world(root, "overworld").resolve("region/second-world.mca"), "second-world");
        RegenerationPlan second = plan("overworld");
        OfflineWorldRegenerator.request(root, second);
        OfflineWorldRegenerator.runPending(root);
        RegenerationPlan restoration = OfflineWorldRegenerator.restorePlan(root, second.id());
        OfflineWorldRegenerator.request(root, restoration);
        OfflineWorldRegenerator.runPending(root);
        assertEquals("second-world", Files.readString(world(root, "overworld").resolve("region/second-world.mca")));
        assertEquals(restoration.id(), read(world(root, "overworld").resolve(".ascending-regeneration.properties")).getProperty("operation-id"));
        assertEquals(first.id(), read(archive(root, second).resolve("original/overworld/.ascending-regeneration.properties")).getProperty("operation-id"));
    }

    @Test public void cumulativeEpochSupportsMoreThanOneRequestDimensionLimit() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan previousPlan = plan("old_dimension");
        Properties previous = previousPlan.toProperties();
        java.util.ArrayList<String> priorDimensions = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) priorDimensions.add("retired_" + index);
        previous.setProperty("dimensions", String.join(",", priorDimensions));
        store(root.resolve(OfflineWorldRegenerator.COMPLETED_PATH), previous);
        RegenerationPlan current = plan("overworld");
        OfflineWorldRegenerator.request(root, current);
        OfflineWorldRegenerator.runPending(root);
        String[] dimensions = read(root.resolve(OfflineWorldRegenerator.COMPLETED_PATH)).getProperty("dimensions").split(",");
        assertEquals(101, dimensions.length);
        assertEquals("overworld", dimensions[100]);
    }

    @Test public void malformedPreviousEpochIsRejectedBeforeAnyDimensionMoves() throws Exception {
        Path root = fixture("overworld");
        Properties previous = plan("old_dimension").toProperties();
        previous.setProperty("dimensions", "../invalid");
        store(root.resolve(OfflineWorldRegenerator.COMPLETED_PATH), previous);
        RegenerationPlan current = plan("overworld");
        OfflineWorldRegenerator.request(root, current);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root));
        assertTrue(Files.exists(world(root, "overworld").resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(archive(root, current)));
    }

    @Test public void recoversAfterInterruptedStagingWithoutDeletingPartialCopy() throws Exception {
        recoverAt("STAGED");
    }

    @Test public void recoversAfterOriginalMoveBeforeJournalUpdate() throws Exception {
        recoverAt("ORIGINAL_MOVED");
    }

    @Test public void recoversAfterReplacementMoveBeforeJournalUpdate() throws Exception {
        recoverAt("REPLACEMENT_MOVED");
    }

    @Test public void recoversAfterCompletionBeforePendingRequestRemoval() throws Exception {
        recoverAt("COMPLETE");
    }

    @Test public void restoreAlsoRecoversAfterInterruptedPublish() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan regeneration = plan("overworld");
        OfflineWorldRegenerator.request(root, regeneration);
        OfflineWorldRegenerator.runPending(root);
        RegenerationPlan restoration = OfflineWorldRegenerator.restorePlan(root, regeneration.id());
        OfflineWorldRegenerator.request(root, restoration);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root, failAt("REPLACEMENT_MOVED")));
        assertTrue(OfflineWorldRegenerator.runPending(root));
        assertEquals("terrain-overworld", Files.readString(world(root, "overworld").resolve("region/r.0.0.mca")));
    }

    @Test public void secondDimensionIsPreparedBeforeAnyLiveDirectoryMoves() throws Exception {
        Path root = fixture("overworld", "the_nether");
        RegenerationPlan plan = plan("overworld", "the_nether");
        OfflineWorldRegenerator.request(root, plan);
        // Simulate a metadata problem discovered during the offline preflight, after queuing.
        Files.move(world(root, "the_nether").resolve("data/paper/metadata.dat"), root.resolve("saved-metadata.dat"));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root));
        assertTrue(Files.exists(world(root, "overworld").resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(archive(root, plan)));
    }

    @Test public void wrongLevelAndMissingSeedAreRejectedBeforeQueuing() throws Exception {
        Path root = fixture("overworld");
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.request(root, RegenerationPlan.regenerate("other", List.of("overworld"))));
        Path seed = world(root, "overworld").resolve("data/minecraft/world_gen_settings.dat");
        Files.move(seed, root.resolve("saved-seed.dat"));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.request(root, plan("overworld")));
        assertNull(OfflineWorldRegenerator.readPending(root));
    }

    @Test public void canCancelUnstartedRequestWithoutDeletingIt() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan plan = plan("overworld");
        OfflineWorldRegenerator.request(root, plan);
        assertTrue(OfflineWorldRegenerator.cancelPending(root));
        assertNull(OfflineWorldRegenerator.readPending(root));
        assertTrue(Files.exists(root.resolve("plugins/AscendingSurvival/regeneration-cancelled-" + plan.id() + ".properties")));
        assertFalse(OfflineWorldRegenerator.cancelPending(root));
        assertTrue(Files.exists(world(root, "overworld").resolve("region/r.0.0.mca")));
    }

    @Test public void cannotCancelTransactionAfterItStarts() throws Exception {
        Path root = fixture("overworld");
        OfflineWorldRegenerator.request(root, plan("overworld"));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root, failAt("STAGED")));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.cancelPending(root));
        assertNotNull(OfflineWorldRegenerator.readPending(root));
    }

    @Test public void failsClosedOnMissingOriginalRatherThanAcceptingGeneratedReplacement() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan plan = plan("overworld");
        OfflineWorldRegenerator.request(root, plan);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root, failAt("REPLACEMENT_MOVED")));
        Files.move(archive(root, plan).resolve("original/overworld"), root.resolve("unexpectedly-moved-original"));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root));
        assertNotNull(OfflineWorldRegenerator.readPending(root));
        assertFalse(Files.exists(root.resolve(OfflineWorldRegenerator.COMPLETED_PATH)));
    }

    @Test public void failsClosedOnAmbiguousBothPresentDuringOriginalMove() throws Exception {
        Path root = fixture("overworld");
        RegenerationPlan plan = plan("overworld");
        OfflineWorldRegenerator.request(root, plan);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root, failAt("ORIGINAL_MOVED")));
        Files.createDirectories(world(root, "overworld"));
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root));
        assertTrue(Files.exists(archive(root, plan).resolve("original/overworld/region/r.0.0.mca")));
    }

    @Test public void cumulativeEpochProtectsOfflinePlayersAcrossDifferentDimensions() throws Exception {
        Path root = fixture("overworld", "the_nether");
        RegenerationPlan first = plan("overworld");
        OfflineWorldRegenerator.request(root, first);
        OfflineWorldRegenerator.runPending(root);
        RegenerationPlan second = plan("the_nether");
        OfflineWorldRegenerator.request(root, second);
        OfflineWorldRegenerator.runPending(root);
        Properties epoch = read(root.resolve(OfflineWorldRegenerator.COMPLETED_PATH));
        assertEquals(second.id(), epoch.getProperty("id"));
        assertEquals("overworld,the_nether", epoch.getProperty("dimensions"));
        assertEquals("the_nether", epoch.getProperty("operation-dimensions"));
    }

    @Test public void symlinkedDimensionIsRejected() throws Exception {
        Path root = fixture("overworld");
        Path target = world(root, "overworld");
        Path outside = temporary.newFolder("outside-world").toPath();
        Path link = world(root, "linked");
        createLinkOrSkip(link, outside);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.request(root, plan("linked")));
        assertTrue(Files.exists(target.resolve("region/r.0.0.mca")));
    }

    @Test public void nestedSymlinkPreventsMovingOriginalDimension() throws Exception {
        Path root = fixture("overworld");
        Path outside = temporary.newFolder("outside-data").toPath();
        createLinkOrSkip(world(root, "overworld").resolve("unsafe"), outside);
        RegenerationPlan plan = plan("overworld");
        OfflineWorldRegenerator.request(root, plan);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root));
        assertTrue(Files.exists(world(root, "overworld").resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(archive(root, plan).resolve("original/overworld")));
    }

    @Test public void requestWithTraversalOrUnknownOperationFailsClosed() throws Exception {
        Path root = fixture("overworld");
        Properties request = plan("overworld").toProperties();
        request.setProperty("dimensions", "../../outside");
        store(root.resolve(OfflineWorldRegenerator.REQUEST_PATH), request);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root));
        assertFalse(Files.exists(root.resolve(OfflineWorldRegenerator.ARCHIVE_DIRECTORY)));
    }

    private void recoverAt(String point) throws Exception {
        Path root = fixture("overworld", "the_nether");
        RegenerationPlan plan = plan("overworld", "the_nether");
        OfflineWorldRegenerator.request(root, plan);
        assertThrows(IOException.class, () -> OfflineWorldRegenerator.runPending(root, failAt(point)));
        assertNotNull(OfflineWorldRegenerator.readPending(root));
        assertTrue(OfflineWorldRegenerator.runPending(root));
        assertNull(OfflineWorldRegenerator.readPending(root));
        for (String dimension : plan.dimensions()) {
            assertFalse(Files.exists(world(root, dimension).resolve("region")));
            assertEquals("terrain-" + dimension, Files.readString(archive(root, plan).resolve("original/" + dimension + "/region/r.0.0.mca")));
        }
        if (point.equals("STAGED")) {
            try (var abandoned = Files.list(archive(root, plan).resolve("abandoned-staged"))) { assertEquals(1L, abandoned.count()); }
        }
    }

    private static OfflineWorldRegenerator.Checkpoint failAt(String requested) {
        AtomicBoolean failed = new AtomicBoolean();
        return (point, dimension) -> {
            if (point.equals(requested) && failed.compareAndSet(false, true)) throw new IOException("Simulated process loss at " + point);
        };
    }

    private Path fixture(String... dimensions) throws Exception {
        Path root = temporary.newFolder().toPath();
        write(root.resolve("server.properties"), "level-name=survival\n");
        write(root.resolve("survival/level.dat"), "shared-level-data");
        write(root.resolve("survival/session.lock"), "lock");
        write(root.resolve("survival/players/alex.dat"), "inventory-and-location");
        write(root.resolve("plugins/AscendingSurvival/worlds.yml"), "server-owner-configuration");
        for (String dimension : dimensions) {
            Path world = world(root, dimension);
            write(world.resolve("region/r.0.0.mca"), "terrain-" + dimension);
            write(world.resolve("entities/r.0.0.mca"), "entities");
            write(world.resolve("poi/r.0.0.mca"), "poi");
            write(world.resolve("data/minecraft/raids.dat"), "raids");
            write(world.resolve("data/minecraft/ender_dragon_fight.dat"), "dragon");
            for (String metadata : List.of("data/minecraft/world_gen_settings.dat", "data/paper/metadata.dat",
                    "data/minecraft/game_rules.dat", "data/minecraft/world_border.dat", "data/minecraft/world_clocks.dat",
                    "data/minecraft/weather.dat", "data/paper/level_overrides.dat", "data/paper/persistent_data_container.dat", "paper-world.yml")) {
                write(world.resolve(metadata), dimension + "-" + metadata);
            }
        }
        return root.toAbsolutePath().normalize();
    }

    private static RegenerationPlan plan(String... dimensions) { return RegenerationPlan.regenerate("survival", List.of(dimensions)); }
    private static Path world(Path root, String dimension) { return root.resolve("survival/dimensions/minecraft/" + dimension); }
    private static Path archive(Path root, RegenerationPlan plan) { return root.resolve(OfflineWorldRegenerator.ARCHIVE_DIRECTORY + "/" + plan.id()); }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { properties.load(stream); }
        return properties;
    }

    private static void store(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream stream = Files.newOutputStream(path)) { properties.store(stream, "fixture"); }
    }

    private static void createLinkOrSkip(Path link, Path target) throws IOException {
        try { Files.createSymbolicLink(link, target); }
        catch (IOException | UnsupportedOperationException noPrivileges) { Assume.assumeNoException(noPrivileges); }
    }
}
