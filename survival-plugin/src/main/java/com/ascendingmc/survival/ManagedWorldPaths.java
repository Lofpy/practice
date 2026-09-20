package com.ascendingmc.survival;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Filesystem operations are deliberately limited to direct children and never recurse. */
public final class ManagedWorldPaths {
    private final Path container;
    private final Path worldRoot;
    private final Path archive;
    private final Set<String> protectedNames;

    public ManagedWorldPaths(Path container, String primaryWorld) throws IOException {
        this(container, primaryWorld, container);
    }

    /** worldRoot comes from Paper's primary World#getWorldPath parent, not a guessed layout. */
    public ManagedWorldPaths(Path container, String primaryWorld, Path worldRoot) throws IOException {
        validateName(primaryWorld);
        this.container = container.toRealPath();
        this.worldRoot = worldRoot.toAbsolutePath().normalize();
        if (!this.worldRoot.startsWith(this.container)) throw new IOException("World storage root is outside the server container.");
        checkWorldRoot();
        this.archive = this.container.resolve(".ascending-deleted-worlds");
        String primary = primaryWorld.toLowerCase(Locale.ROOT);
        this.protectedNames = new java.util.HashSet<>(Set.of("overworld", "the_nether", "the_end"));
        this.protectedNames.addAll(Set.of(primary, primary + "_nether", primary + "_the_end"));
        checkArchive();
    }

    public static void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("World names must contain 1-32 letters, numbers, _ or -.");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")
                || Set.of("plugins", "logs", "config", "cache", "libraries", "versions").contains(normalized)) {
            throw new IllegalArgumentException("Reserved directory name cannot be used for a world.");
        }
    }

    public boolean isProtected(String name) {
        validateName(name);
        return protectedNames.contains(name.toLowerCase(Locale.ROOT));
    }

    public Path world(String name) throws IOException {
        validateName(name);
        checkWorldRoot();
        Path target = worldRoot.resolve(name.toLowerCase(Locale.ROOT)).normalize();
        requireDirectChild(worldRoot, target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                    || !target.toRealPath().equals(target)) {
                throw new IOException("Refusing unsafe world directory: " + name);
            }
        }
        return target;
    }

    public String relativeWorldPath(String name) throws IOException {
        return container.relativize(world(name)).toString().replace('\\', '/');
    }

    public boolean legacyDirectoryExists(String name) {
        validateName(name);
        // Paper can automatically migrate old top-level worlds; never implicitly adopt one.
        return Files.exists(container.resolve(name), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(container.resolve(name.toLowerCase(Locale.ROOT)), LinkOption.NOFOLLOW_LINKS);
    }

    public void verifyActualWorldPath(String name, Path actual) throws IOException {
        Path expected = world(name);
        if (!actual.toAbsolutePath().normalize().equals(expected) || !Files.isDirectory(expected)) {
            throw new IOException("Paper world directory does not match the managed dimension: " + name);
        }
    }

    public Path archive(String directory) throws IOException {
        if (directory == null || !directory.matches("[A-Za-z0-9_-]{1,32}--[a-f0-9-]{36}")) {
            throw new IOException("Invalid archive identifier.");
        }
        checkArchive();
        Path target = archive.resolve(directory).normalize();
        requireDirectChild(archive, target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                || !target.toRealPath().equals(target))) {
            throw new IOException("Refusing unsafe archive directory.");
        }
        return target;
    }

    public String archiveWorld(String name) throws IOException {
        String directory = newArchiveName(name);
        archiveWorld(name, directory);
        return directory;
    }

    public String newArchiveName(String name) {
        validateName(name);
        return name + "--" + UUID.randomUUID();
    }

    public void archiveWorld(String name, String directory) throws IOException {
        if (isProtected(name)) {
            throw new IllegalArgumentException("The primary world, Nether and End cannot be deleted.");
        }
        Path source = world(name);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World directory does not exist.");
        }
        checkArchive();
        Files.createDirectories(archive);
        checkArchive();
        Files.move(source, archive(directory));
    }

    public void restoreWorld(String name, String directory) throws IOException {
        if (isProtected(name)) {
            throw new IllegalArgumentException("Protected worlds cannot be restored through this command.");
        }
        Path source = archive(directory);
        Path target = world(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A directory already exists with this world name.");
        }
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Archive not found.");
        }
        Files.move(source, target);
    }

    private void checkArchive() throws IOException {
        if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(archive) || !Files.isDirectory(archive, LinkOption.NOFOLLOW_LINKS)
                || !archive.toRealPath().equals(archive))) {
            throw new IOException("Refusing unsafe archive root.");
        }
    }

    private void checkWorldRoot() throws IOException {
        Path cursor = container;
        for (Path component : container.relativize(worldRoot)) {
            cursor = cursor.resolve(component);
            if (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)
                    || !cursor.toRealPath().equals(cursor)) {
                throw new IOException("Unsafe dimension storage ancestor: " + cursor.getFileName());
            }
        }
    }

    private static void requireDirectChild(Path parent, Path child) throws IOException {
        if (child.equals(parent) || !parent.equals(child.getParent())) {
            throw new IOException("Path must be a direct child of the managed world container.");
        }
    }
}
