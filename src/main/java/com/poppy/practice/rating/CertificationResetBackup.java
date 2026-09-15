package com.poppy.practice.rating;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Back up first, stage exact assessment directories, and restore them on a failed commit. */
final class CertificationResetBackup {
    static final String PENDING_FILE = "certification-reset.pending";
    private final Path root;
    private final Path directory;
    private final Path pending;
    private final Map<Path, Path> assessmentDirectories = new LinkedHashMap<Path, Path>();
    private final Map<Path, String> originalHashes = new LinkedHashMap<Path, String>();
    private final List<Path> moved = new ArrayList<Path>();
    private boolean markerCreated;

    private CertificationResetBackup(Path root, Path directory) {
        this.root = root;
        this.directory = directory;
        this.pending = root.resolve(PENDING_FILE);
    }

    static CertificationResetBackup prepare(Path root, CertificationResetPlan plan, String actor) throws IOException {
        requireSafePath(root, root);
        Path parent = root.resolve("backups");
        requireSafePath(root, parent);
        Files.createDirectories(parent);
        Path backupPath = parent.resolve("certification-reset-" + System.currentTimeMillis() + "-" + UUID.randomUUID());
        Files.createDirectory(backupPath);
        CertificationResetBackup backup = new CertificationResetBackup(root, backupPath);
        backup.copyVerified(root.resolve("ratings.yml"), backupPath.resolve("ratings.yml"));
        for (UUID player : plan.getPlayerIds()) {
            for (String kit : plan.getKitIds(player)) {
                Path source = root.resolve("tier-assessments").resolve(player.toString()).resolve(kit);
                requireSafePath(root, source);
                if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) { continue; }
                if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Expected an assessment directory: " + source);
                }
                Path copy = backupPath.resolve("tier-assessments").resolve(player.toString()).resolve(kit);
                Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        requireSafePath(root, dir);
                        Files.createDirectories(copy.resolve(source.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        backup.copyVerified(file, copy.resolve(source.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
                backup.assessmentDirectories.put(source, backupPath.resolve("staged-assessments")
                        .resolve(player.toString()).resolve(kit));
            }
        }
        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("version", 1);
        manifest.set("created-at", Instant.now().toString());
        manifest.set("actor", actor);
        manifest.set("records", plan.getRecordCount());
        manifest.set("placements", plan.getPlacementCount());
        List<String> targets = new ArrayList<String>(plan.recordKeys);
        manifest.set("targets", targets);
        List<String> checksums = new ArrayList<String>();
        for (Map.Entry<Path, String> entry : backup.originalHashes.entrySet()) {
            checksums.add(entry.getValue() + "  " + root.relativize(entry.getKey()).toString());
        }
        manifest.set("sha256", checksums);
        Files.write(backupPath.resolve("manifest.yml"), manifest.saveToString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW);
        return backup;
    }

    Path getDirectory() { return directory; }

    void stage() throws IOException {
        for (Map.Entry<Path, String> entry : originalHashes.entrySet()) {
            if (!entry.getValue().equals(hash(root, entry.getKey()))) {
                throw new IOException("Certification files changed while being backed up: " + entry.getKey());
            }
        }
        requireSafePath(root, pending);
        Files.write(pending, directory.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        markerCreated = true;
        for (Map.Entry<Path, Path> entry : assessmentDirectories.entrySet()) {
            requireSafePath(root, entry.getKey());
            requireSafePath(root, entry.getValue());
            Files.createDirectories(entry.getValue().getParent());
            Files.move(entry.getKey(), entry.getValue(), StandardCopyOption.ATOMIC_MOVE);
            moved.add(entry.getKey());
        }
    }

    void finish() throws IOException {
        if (markerCreated) {
            requireSafePath(root, pending);
            Files.delete(pending);
            markerCreated = false;
        }
    }

    void rollback() throws IOException {
        IOException problem = null;
        for (int index = moved.size() - 1; index >= 0; index--) {
            Path original = moved.get(index);
            Path staged = assessmentDirectories.get(original);
            try {
                requireSafePath(root, original);
                requireSafePath(root, staged);
                if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Cannot overwrite an assessment path during rollback: " + original);
                }
                Files.move(staged, original, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException failure) {
                if (problem == null) { problem = failure; } else { problem.addSuppressed(failure); }
            }
        }
        if (problem != null) { throw problem; }
        finish();
    }

    private void copyVerified(Path source, Path destination) throws IOException {
        String before = hash(root, source);
        if (before == null) { throw new IOException("Missing certification source: " + source); }
        requireSafePath(root, destination);
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, LinkOption.NOFOLLOW_LINKS);
        if (!before.equals(hash(root, destination)) || !before.equals(hash(root, source))) {
            throw new IOException("Certification backup checksum mismatch: " + source);
        }
        originalHashes.put(source, before);
    }

    static String hash(Path root, Path file) throws IOException {
        requireSafePath(root, file);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { return null; }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a regular certification file: " + file);
        }
        return hashBytes(Files.readAllBytes(file));
    }

    static String hashBytes(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (byte value : digest) { result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static void requireSafePath(Path root, Path target) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) {
            throw new IOException("Certification path escapes its data directory: " + target);
        }
        // Check every existing ancestor, including the data directory itself; never traverse a link/junction.
        Path current = normalized.getRoot();
        for (Path part : normalized) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) { continue; }
            BasicFileAttributes attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.isSymbolicLink() || attrs.isOther()) {
                throw new IOException("Linked or special certification paths are not allowed: " + current);
            }
            if (!current.equals(normalized) && !attrs.isDirectory()) {
                throw new IOException("Certification path ancestor is not a directory: " + current);
            }
        }
    }
}
