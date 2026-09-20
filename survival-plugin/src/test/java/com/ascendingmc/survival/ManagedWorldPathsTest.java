package com.ascendingmc.survival;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public final class ManagedWorldPathsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void validatesNamesAndRejectsTraversal() {
        for (String name : List.of("survival", "event-2026", "world_2")) ManagedWorldPaths.validateName(name);
        for (String name : List.of("", "..", "../world", "a/b", "a\\b", "C:world", ".hidden", "a.b", "a b", "x".repeat(33))) {
            assertThrows(name, IllegalArgumentException.class, () -> ManagedWorldPaths.validateName(name));
        }
        assertThrows(IllegalArgumentException.class, () -> ManagedWorldPaths.validateName(null));
    }

    @Test public void rejectsDeviceAndServiceNamesCaseInsensitively() {
        for (String name : List.of("CON", "nul", "COM1", "lpt9", "Plugins", "config", "cache", "logs", "libraries", "versions")) {
            assertThrows(name, IllegalArgumentException.class, () -> ManagedWorldPaths.validateName(name));
        }
    }

    @Test public void protectsEntirePrimaryFamily() throws Exception {
        ManagedWorldPaths paths = paths();
        assertTrue(paths.isProtected("Survival"));
        assertTrue(paths.isProtected("survival_nether"));
        assertTrue(paths.isProtected("SURVIVAL_THE_END"));
        assertTrue(paths.isProtected("overworld"));
        assertTrue(paths.isProtected("the_nether"));
        assertTrue(paths.isProtected("the_end"));
        assertFalse(paths.isProtected("event"));
        assertThrows(IllegalArgumentException.class, () -> paths.archiveWorld("survival"));
        assertThrows(IllegalArgumentException.class, () -> paths.restoreWorld("survival_nether", paths.newArchiveName("event")));
    }

    @Test public void archiveAndRestorePreserveContents() throws Exception {
        ManagedWorldPaths paths = paths();
        Path source = Files.createDirectory(paths.world("event"));
        Files.writeString(source.resolve("level.dat"), "preserve this exact world");
        Files.createDirectory(source.resolve("region"));
        Files.writeString(source.resolve("region/r.0.0.mca"), "blocks");
        String archive = paths.archiveWorld("event");
        assertFalse(Files.exists(source));
        assertEquals("preserve this exact world", Files.readString(paths.archive(archive).resolve("level.dat")));
        paths.restoreWorld("event", archive);
        assertFalse(Files.exists(paths.archive(archive)));
        assertEquals("blocks", Files.readString(source.resolve("region/r.0.0.mca")));
    }

    @Test public void restoreNeverOverwritesExistingWorld() throws Exception {
        ManagedWorldPaths paths = paths();
        Files.createDirectory(paths.world("event"));
        String archive = paths.archiveWorld("event");
        Files.createDirectory(paths.world("event"));
        assertThrows(IOException.class, () -> paths.restoreWorld("event", archive));
        assertTrue(Files.isDirectory(paths.archive(archive)));
    }

    @Test public void cannotArchiveMissingWorld() throws Exception {
        ManagedWorldPaths paths = paths();
        assertThrows(IOException.class, () -> paths.archiveWorld("missing"));
    }

    @Test public void rejectsFileInsteadOfWorldDirectory() throws Exception {
        ManagedWorldPaths paths = paths();
        Files.createFile(paths.world("file"));
        assertThrows(IOException.class, () -> paths.world("file"));
    }

    @Test public void rejectsUntrustedArchivePaths() throws Exception {
        ManagedWorldPaths paths = paths();
        for (String archive : List.of("../../survival", "a/b", "event", "event--invalid")) {
            assertThrows(IOException.class, () -> paths.archive(archive));
        }
        assertThrows(IOException.class, () -> paths.archive(null));
    }

    @Test public void paper263ArchivesDimensionOnlyAndPreservesSharedLevelAndPlayers() throws Exception {
        Path server = temporary.getRoot().toPath();
        Path level = Files.createDirectory(server.resolve("survival"));
        Path namespace = Files.createDirectories(level.resolve("dimensions/minecraft"));
        Files.writeString(level.resolve("level.dat"), "shared-level-metadata");
        Path players = Files.createDirectory(level.resolve("players"));
        Files.writeString(players.resolve("player.dat"), "player-inventory");
        ManagedWorldPaths paths = new ManagedWorldPaths(server, "survival", namespace);
        Path dimension = Files.createDirectory(namespace.resolve("event"));
        Path metadata = Files.createDirectories(dimension.resolve("data/paper"));
        Files.writeString(metadata.resolve("metadata.dat"), "world-uuid");
        Files.writeString(metadata.resolve("level_overrides.dat"), "world-settings");
        paths.verifyActualWorldPath("event", dimension);
        assertEquals("survival/dimensions/minecraft/event", paths.relativeWorldPath("event"));
        String archive = paths.archiveWorld("event");
        assertFalse(Files.exists(dimension));
        assertEquals("shared-level-metadata", Files.readString(level.resolve("level.dat")));
        assertEquals("player-inventory", Files.readString(players.resolve("player.dat")));
        paths.restoreWorld("event", archive);
        assertEquals("world-uuid", Files.readString(metadata.resolve("metadata.dat")));
        assertEquals("world-settings", Files.readString(metadata.resolve("level_overrides.dat")));
    }

    @Test public void actualPaperPathMustMatchManagedDimensionNotWholeLevelRoot() throws Exception {
        Path server = temporary.getRoot().toPath();
        Path namespace = Files.createDirectories(server.resolve("survival/dimensions/minecraft"));
        ManagedWorldPaths paths = new ManagedWorldPaths(server, "survival", namespace);
        Files.createDirectory(paths.world("event"));
        assertThrows(IOException.class, () -> paths.verifyActualWorldPath("event", server.resolve("survival")));
        assertThrows(IOException.class, () -> paths.verifyActualWorldPath("event", namespace.resolve("overworld")));
        assertThrows(IOException.class, () -> paths.verifyActualWorldPath("event", server));
    }

    @Test public void dimensionNamesUsePaperLowercaseKey() throws Exception {
        ManagedWorldPaths paths = paths();
        assertEquals(paths.world("event"), paths.world("Event"));
    }

    @Test public void rejectsDimensionRootOutsideServerContainer() throws Exception {
        Path server = temporary.newFolder("server").toPath();
        Path outside = temporary.newFolder("outside").toPath();
        assertThrows(IOException.class, () -> new ManagedWorldPaths(server, "survival", outside));
    }

    @Test public void detectsLegacyWorldDirectoryToPreventImplicitPaperMigration() throws Exception {
        Path server = temporary.getRoot().toPath();
        Path namespace = Files.createDirectories(server.resolve("survival/dimensions/minecraft"));
        ManagedWorldPaths paths = new ManagedWorldPaths(server, "survival", namespace);
        assertFalse(paths.legacyDirectoryExists("event"));
        Files.createDirectory(server.resolve("event"));
        assertTrue(paths.legacyDirectoryExists("event"));
        assertFalse(Files.exists(paths.world("event")));
    }

    @Test public void rejectsSymlinkWorldWithoutFollowingIt() throws Exception {
        Path container = temporary.newFolder("worlds").toPath();
        Path outside = temporary.newFolder("outside").toPath();
        Path link = container.resolve("event");
        try { Files.createSymbolicLink(link, outside); }
        catch (IOException | UnsupportedOperationException unavailable) { Assume.assumeNoException(unavailable); }
        try {
            ManagedWorldPaths paths = new ManagedWorldPaths(container, "survival");
            assertThrows(IOException.class, () -> paths.world("event"));
            assertTrue(Files.isDirectory(outside));
        } finally { Files.deleteIfExists(link); }
    }

    @Test public void rejectsSymlinkArchiveRoot() throws Exception {
        Path container = temporary.newFolder("worlds").toPath();
        Path outside = temporary.newFolder("outside").toPath();
        Path link = container.resolve(".ascending-deleted-worlds");
        try { Files.createSymbolicLink(link, outside); }
        catch (IOException | UnsupportedOperationException unavailable) { Assume.assumeNoException(unavailable); }
        try { assertThrows(IOException.class, () -> new ManagedWorldPaths(container, "survival")); }
        finally { Files.deleteIfExists(link); }
    }

    private ManagedWorldPaths paths() throws IOException {
        return new ManagedWorldPaths(temporary.getRoot().toPath(), "survival");
    }
}
