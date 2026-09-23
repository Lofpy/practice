package com.ascendingmc.survival;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.UUID;

/**
 * Pure-JDK pre-start maintenance. The Minecraft session lock must be acquired before changing
 * any world. Every displaced directory is retained; this class never recursively deletes data.
 */
public final class OfflineWorldRegenerator {
    public static final String REQUEST_PATH = "plugins/AscendingSurvival/regeneration-request.properties";
    public static final String COMPLETED_PATH = "plugins/AscendingSurvival/regeneration-completed.properties";
    public static final String ARCHIVE_DIRECTORY = ".ascending-regenerated-worlds";
    private static final String MARKER = ".ascending-regeneration.properties";
    private static final String SEED = "data/minecraft/world_gen_settings.dat";
    private static final String IDENTITY = "data/paper/metadata.dat";
    // Paper 26.3 stores the seed and UUID in dimension-local files, not in the shared level.dat.
    private static final List<String> METADATA = List.of(
            SEED, IDENTITY, "paper-world.yml", "uid.dat",
            "data/minecraft/game_rules.dat", "data/minecraft/world_border.dat",
            "data/minecraft/world_clocks.dat", "data/minecraft/weather.dat",
            "data/paper/level_overrides.dat", "data/paper/persistent_data_container.dat");

    private OfflineWorldRegenerator() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Usage: OfflineWorldRegenerator <server-data-root>");
        if (runPending(Path.of(arguments[0]))) {
            System.out.println("AscendingSurvival offline world operation completed; all displaced worlds remain archived.");
        }
    }

    /** Queues only; safe to call from the running server. A pending operation is never replaced. */
    public static void request(Path dataRoot, RegenerationPlan plan) throws IOException {
        Path root = root(dataRoot);
        try (MaintenanceLock ignored = controlLock(root)) {
            validateLevel(root, plan);
            validateSources(root, plan);
            Path request = checked(root, REQUEST_PATH);
            createDirectories(root, request.getParent());
            writeNew(request, plan.toProperties());
        }
    }

    public static RegenerationPlan readPending(Path dataRoot) throws IOException {
        Path request = checked(root(dataRoot), REQUEST_PATH);
        return exists(request) ? RegenerationPlan.fromProperties(read(request)) : null;
    }

    /** Cancellation archives the request, and is refused once any transaction directory exists. */
    public static boolean cancelPending(Path dataRoot) throws IOException {
        Path root = root(dataRoot);
        try (MaintenanceLock ignored = controlLock(root)) {
            Path pending = checked(root, REQUEST_PATH);
            if (!exists(pending)) return false;
            RegenerationPlan plan = RegenerationPlan.fromProperties(read(pending));
            if (exists(checked(root, ARCHIVE_DIRECTORY + "/" + plan.id()))) {
                throw new IOException("The operation has started and must be recovered, not cancelled.");
            }
            Path cancelled = checked(root, pending.resolveSibling("regeneration-cancelled-" + plan.id() + ".properties"));
            Files.move(pending, cancelled);
            syncDirectory(pending.getParent());
            return true;
        }
    }

    /** Restores an entire completed regeneration, while retaining both its backup and current data. */
    public static RegenerationPlan restorePlan(Path dataRoot, String archiveId) throws IOException {
        try { RegenerationPlan.validateId(archiveId); }
        catch (IllegalArgumentException invalid) { throw new IOException(invalid.getMessage(), invalid); }
        Path root = root(dataRoot);
        Properties manifest = read(checked(root, ARCHIVE_DIRECTORY + "/" + archiveId + "/manifest.properties"));
        RegenerationPlan source = RegenerationPlan.fromProperties(manifest);
        if (!source.id().equals(archiveId) || source.operation() != RegenerationPlan.Operation.REGENERATE
                || !"COMPLETE".equals(manifest.getProperty("state"))
                || manifest.containsKey("restored-by")) {
            throw new IOException("Only a completed, not-yet-restored regeneration can be restored.");
        }
        RegenerationPlan plan = RegenerationPlan.restore(source);
        validateLevel(root, plan);
        validateSources(root, plan);
        return plan;
    }

    /** @return false when no request is present; failures intentionally prevent Minecraft startup. */
    public static boolean runPending(Path dataRoot) throws IOException {
        return runPending(dataRoot, (point, dimension) -> { });
    }

    // Failure injection is package-private and used only by recovery tests.
    static boolean runPending(Path dataRoot, Checkpoint checkpoint) throws IOException {
        Path root = root(dataRoot);
        Path pending = checked(root, REQUEST_PATH);
        if (!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) return false;
        try (MaintenanceLock ignored = controlLock(root)) {
            if (!exists(pending)) return false;
            runLocked(root, pending, checkpoint);
        }
        return true;
    }

    private static void runLocked(Path root, Path pending, Checkpoint checkpoint) throws IOException {
        RegenerationPlan plan = RegenerationPlan.fromProperties(read(pending));
        validateLevel(root, plan);
        Path lockPath = checked(root, plan.levelName() + "/session.lock");
        requireFile(lockPath);
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE)) {
            FileLock acquired;
            try { acquired = channel.tryLock(); }
            catch (OverlappingFileLockException running) { throw new IOException("Survival is still running; world maintenance refused.", running); }
            if (acquired == null) throw new IOException("Survival is still running; world maintenance refused.");
            try (FileLock ignored = acquired) {
                RegenerationPlan current = RegenerationPlan.fromProperties(read(pending));
                if (!current.toProperties().equals(plan.toProperties())) throw new IOException("Pending request changed while acquiring lock.");
                execute(root, pending, plan, checkpoint);
            }
        }
    }

    private static void execute(Path root, Path pending, RegenerationPlan plan, Checkpoint checkpoint) throws IOException {
        completionDimensions(root, plan);
        Path archive = checked(root, ARCHIVE_DIRECTORY + "/" + plan.id());
        Path manifestPath = checked(root, ARCHIVE_DIRECTORY + "/" + plan.id() + "/manifest.properties");
        Properties manifest;
        if (Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            manifest = read(manifestPath);
            if (!RegenerationPlan.fromProperties(manifest).toProperties().equals(plan.toProperties())) {
                throw new IOException("Archive does not match the pending operation.");
            }
        } else {
            if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Archive exists without a manifest; manual inspection required.");
            }
            validateSources(root, plan);
            createDirectories(root, archive);
            manifest = plan.toProperties();
            manifest.setProperty("state", "PREPARING");
            for (String dimension : plan.dimensions()) manifest.setProperty(key(dimension), "QUEUED");
            writeAtomic(root, manifestPath, manifest);
        }
        String state = manifest.getProperty("state");
        if (!List.of("PREPARING", "APPLYING", "COMPLETE").contains(state)) throw new IOException("Unknown operation state.");
        if ("PREPARING".equals(state)) {
            // All replacement dimensions are prepared before the first live directory is moved.
            for (String dimension : plan.dimensions()) {
                String dimensionState = manifest.getProperty(key(dimension));
                if ("QUEUED".equals(dimensionState)) {
                    prepare(root, archive, plan, dimension);
                    checkpoint.reached("STAGED", dimension);
                    manifest.setProperty(key(dimension), "PREPARED");
                    writeAtomic(root, manifestPath, manifest);
                } else if (!"PREPARED".equals(dimensionState)) {
                    throw new IOException("Invalid preparation state for " + dimension);
                }
                verifyMarker(checked(root, stage(archive, dimension)), plan, dimension);
            }
            manifest.setProperty("state", "APPLYING");
            writeAtomic(root, manifestPath, manifest);
        }
        for (String dimension : plan.dimensions()) {
            apply(root, archive, manifestPath, manifest, plan, dimension, checkpoint);
        }
        manifest.setProperty("state", "COMPLETE");
        manifest.setProperty("completed-at", Instant.now().toString());
        writeAtomic(root, manifestPath, manifest);
        if (plan.operation() == RegenerationPlan.Operation.RESTORE) {
            Path sourceManifest = checked(root, ARCHIVE_DIRECTORY + "/" + plan.sourceArchiveId() + "/manifest.properties");
            Properties source = read(sourceManifest);
            String restoredBy = source.getProperty("restored-by");
            if (restoredBy != null && !restoredBy.equals(plan.id())) throw new IOException("Source archive was restored by another operation.");
            source.setProperty("restored-by", plan.id());
            writeAtomic(root, sourceManifest, source);
        }
        writeCompletion(root, plan);
        checkpoint.reached("COMPLETE", "");
        Path completedRequest = checked(root, archive.resolve("completed-request.properties"));
        if (Files.exists(completedRequest, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Completed request already exists while pending request remains; inspect manually.");
        }
        Files.move(pending, completedRequest);
        syncDirectory(pending.getParent());
        syncDirectory(archive);
    }

    private static void writeCompletion(Path root, RegenerationPlan plan) throws IOException {
        Path completed = checked(root, COMPLETED_PATH);
        LinkedHashSet<String> affected = completionDimensions(root, plan);
        affected.addAll(plan.dimensions());
        Properties epoch = plan.toProperties();
        epoch.setProperty("dimensions", String.join(",", affected));
        epoch.setProperty("operation-dimensions", String.join(",", plan.dimensions()));
        epoch.setProperty("completed-at", Instant.now().toString());
        writeAtomic(root, completed, epoch);
    }

    private static LinkedHashSet<String> completionDimensions(Path root, RegenerationPlan plan) throws IOException {
        Path completed = checked(root, COMPLETED_PATH);
        LinkedHashSet<String> dimensions = new LinkedHashSet<>();
        if (!exists(completed)) return dimensions;
        Properties previous = read(completed);
        try {
            if (!"1".equals(previous.getProperty("format")) || !plan.levelName().equals(previous.getProperty("level-name"))) {
                throw new IllegalArgumentException("Previous relocation epoch has an invalid format or belongs to another level.");
            }
            RegenerationPlan.validateId(previous.getProperty("id"));
            for (String dimension : previous.getProperty("dimensions", "").split(",", -1)) {
                RegenerationPlan.validateDimension(dimension);
                if (!dimensions.add(dimension)) throw new IllegalArgumentException("Duplicate dimension in relocation epoch.");
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid previous relocation epoch: " + invalid.getMessage(), invalid);
        }
        return dimensions;
    }

    private static void prepare(Path root, Path archive, RegenerationPlan plan, String dimension) throws IOException {
        Path live = live(root, plan, dimension);
        Path original = checked(root, archive.resolve("original").resolve(dimension));
        if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unprepared dimension already has an original archive.");
        requireDirectory(live);
        Path source = plan.operation() == RegenerationPlan.Operation.REGENERATE ? live
                : checked(root, ARCHIVE_DIRECTORY + "/" + plan.sourceArchiveId() + "/original/" + dimension);
        scanSafeTree(source);
        requireMetadata(root, source);
        Path staged = checked(root, stage(archive, dimension));
        if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
            // A power loss during copying is recoverable without deleting the partial copy.
            scanSafeTree(staged);
            Path abandoned = checked(root, archive.resolve("abandoned-staged").resolve(dimension + "-" + UUID.randomUUID()));
            createDirectories(root, abandoned.getParent());
            Files.move(staged, abandoned);
        }
        createDirectories(root, staged);
        if (plan.operation() == RegenerationPlan.Operation.RESTORE) copyTree(root, source, staged);
        else {
            for (String relative : METADATA) {
                Path from = checked(root, source.resolve(relative));
                if (Files.exists(from, LinkOption.NOFOLLOW_LINKS)) copyFile(root, from, staged.resolve(relative));
            }
        }
        Properties marker = new Properties();
        marker.setProperty("operation-id", plan.id());
        marker.setProperty("dimension", dimension);
        // Restored snapshots may contain an earlier receipt. The full original remains archived.
        writeAtomic(root, staged.resolve(MARKER), marker);
        requireMetadata(root, staged);
    }

    private static void apply(Path root, Path archive, Path manifestPath, Properties manifest,
                              RegenerationPlan plan, String dimension, Checkpoint checkpoint) throws IOException {
        Path live = live(root, plan, dimension);
        Path original = checked(root, archive.resolve("original").resolve(dimension));
        Path staged = checked(root, stage(archive, dimension));
        String state = manifest.getProperty(key(dimension));
        if ("PREPARED".equals(state)) {
            verifyMarker(staged, plan, dimension);
            boolean hasLive = exists(live), hasOriginal = exists(original);
            if (hasLive && !hasOriginal) {
                scanSafeTree(live);
                createDirectories(root, original.getParent());
                Files.move(live, original);
                syncDirectory(live.getParent());
                syncDirectory(original.getParent());
                checkpoint.reached("ORIGINAL_MOVED", dimension);
            } else if (hasLive || !hasOriginal) throw new IOException("Ambiguous original move state for " + dimension);
            manifest.setProperty(key(dimension), "ARCHIVED");
            writeAtomic(root, manifestPath, manifest);
            state = "ARCHIVED";
        }
        if ("ARCHIVED".equals(state)) {
            requireDirectory(original);
            boolean hasLive = exists(live), hasStage = exists(staged);
            if (!hasLive && hasStage) {
                verifyMarker(staged, plan, dimension);
                Files.move(staged, live);
                syncDirectory(staged.getParent());
                syncDirectory(live.getParent());
                checkpoint.reached("REPLACEMENT_MOVED", dimension);
            } else if (!hasLive || hasStage) throw new IOException("Ambiguous replacement move state for " + dimension);
            verifyMarker(live, plan, dimension);
            manifest.setProperty(key(dimension), "PUBLISHED");
            writeAtomic(root, manifestPath, manifest);
            state = "PUBLISHED";
        }
        if (!"PUBLISHED".equals(state)) throw new IOException("Unknown dimension state for " + dimension);
        requireDirectory(original);
        if (exists(staged)) throw new IOException("Published dimension still has a staging directory.");
        verifyMarker(live, plan, dimension);
    }

    private static void validateLevel(Path root, RegenerationPlan plan) throws IOException {
        Properties server = read(checked(root, "server.properties"));
        if (!plan.levelName().equals(server.getProperty("level-name", "world").trim())) {
            throw new IOException("Regeneration level-name does not match server.properties.");
        }
        requireDirectory(checked(root, plan.levelName()));
        requireDirectory(checked(root, plan.levelName() + "/dimensions/minecraft"));
        // Never operate on an incomplete, never-started server tree.
        requireFile(checked(root, plan.levelName() + "/level.dat"));
    }

    private static void validateSources(Path root, RegenerationPlan plan) throws IOException {
        if (plan.operation() == RegenerationPlan.Operation.RESTORE) {
            Properties manifest = read(checked(root, ARCHIVE_DIRECTORY + "/" + plan.sourceArchiveId() + "/manifest.properties"));
            RegenerationPlan source = RegenerationPlan.fromProperties(manifest);
            if (source.operation() != RegenerationPlan.Operation.REGENERATE || !source.id().equals(plan.sourceArchiveId())
                    || !source.levelName().equals(plan.levelName()) || !source.dimensions().equals(plan.dimensions())
                    || !"COMPLETE".equals(manifest.getProperty("state")) || manifest.containsKey("restored-by")) {
                throw new IOException("Restore source is not an available matching regeneration archive.");
            }
        }
        for (String dimension : plan.dimensions()) {
            Path live = live(root, plan, dimension);
            requireDirectory(live);
            requireMetadata(root, live);
            if (plan.operation() == RegenerationPlan.Operation.RESTORE) {
                Path source = checked(root, ARCHIVE_DIRECTORY + "/" + plan.sourceArchiveId() + "/original/" + dimension);
                requireDirectory(source);
                requireMetadata(root, source);
            }
        }
    }

    private static void requireMetadata(Path root, Path source) throws IOException {
        requireFile(checked(root, source.resolve(SEED)));
        requireFile(checked(root, source.resolve(IDENTITY)));
        if (Files.size(source.resolve(SEED)) == 0 || Files.size(source.resolve(IDENTITY)) == 0) {
            throw new IOException("Seed/identity metadata is empty; refusing a potentially different world.");
        }
    }

    private static Path live(Path root, RegenerationPlan plan, String dimension) throws IOException {
        return checked(root, plan.levelName() + "/dimensions/minecraft/" + dimension);
    }

    private static Path stage(Path archive, String dimension) { return archive.resolve("staged").resolve(dimension); }
    private static String key(String dimension) { return "dimension." + dimension + ".state"; }
    private static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }

    private static void verifyMarker(Path dimension, RegenerationPlan plan, String name) throws IOException {
        requireDirectory(dimension);
        Properties marker = read(dimension.resolve(MARKER));
        if (!plan.id().equals(marker.getProperty("operation-id")) || !name.equals(marker.getProperty("dimension"))) {
            throw new IOException("Replacement identity does not match the operation: " + name);
        }
    }

    private static Path root(Path root) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        requireDirectory(absolute);
        if (!absolute.toRealPath().equals(absolute)) throw new IOException("Server root must not contain symbolic links.");
        return absolute;
    }

    private static Path checked(Path root, String relative) throws IOException { return checked(root, root.resolve(relative)); }
    private static Path checked(Path root, Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) throw new IOException("Path escapes server data root.");
        Path cursor = root;
        for (Path component : root.relativize(normalized)) {
            cursor = cursor.resolve(component);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Symbolic link in world operation path: " + cursor);
            if (exists(cursor) && !cursor.toRealPath().equals(cursor)) throw new IOException("Indirect world operation path: " + cursor);
        }
        return normalized;
    }

    private static void createDirectories(Path root, Path directory) throws IOException {
        checked(root, directory);
        List<Path> created = new ArrayList<>();
        Path cursor = directory;
        while (!exists(cursor) && !cursor.equals(root)) {
            created.add(cursor);
            cursor = cursor.getParent();
        }
        Files.createDirectories(directory);
        checked(root, directory);
        for (Path added : created) {
            syncDirectory(added);
            syncDirectory(added.getParent());
        }
    }

    private static void requireDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing or unsafe directory: " + path);
        if (!path.toRealPath().equals(path.toAbsolutePath().normalize())) throw new IOException("Indirect directory in world data: " + path);
    }

    private static void requireFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing or unsafe file: " + path);
    }

    private static Properties read(Path path) throws IOException {
        requireFile(path);
        if (Files.size(path) > 1024 * 1024) throw new IOException("Oversized maintenance properties file.");
        Properties result = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { result.load(stream); }
        return result;
    }

    private static void writeNew(Path target, Properties properties) throws IOException {
        byte[] bytes = bytes(properties);
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        syncDirectory(target.getParent());
    }

    private static void writeAtomic(Path root, Path target, Properties properties) throws IOException {
        checked(root, target);
        createDirectories(root, target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".pending-" + UUID.randomUUID());
        writeNew(temporary, properties);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("World maintenance requires atomic journal replacement on this filesystem.", unsupported);
        }
        syncDirectory(target.getParent());
    }

    private static byte[] bytes(Properties properties) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        properties.store(output, "AscendingSurvival offline maintenance; do not edit while a request is pending");
        return output.toByteArray();
    }

    private static void syncDirectory(Path directory) throws IOException {
        // Windows cannot open directories as FileChannels. Unix directory fsync makes renames durable.
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return;
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }

    private static void scanSafeTree(Path root) throws IOException {
        requireDirectory(root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                requireDirectory(directory);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                requireFile(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyTree(Path root, Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                requireDirectory(directory);
                createDirectories(root, destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                requireFile(file);
                copyFile(root, file, destination.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyFile(Path root, Path source, Path target) throws IOException {
        checked(root, source);
        checked(root, target);
        requireFile(source);
        createDirectories(root, target.getParent());
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) { channel.force(true); }
        syncDirectory(target.getParent());
    }

    private static MaintenanceLock controlLock(Path root) throws IOException {
        Path path = checked(root, "plugins/AscendingSurvival/regeneration-control.lock");
        createDirectories(root, path.getParent());
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException busy) { throw new IOException("Another world maintenance operation is in progress.", busy); }
            if (lock == null) throw new IOException("Another world maintenance operation is in progress.");
            return new MaintenanceLock(channel, lock);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    private record MaintenanceLock(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException {
            try { lock.close(); } finally { channel.close(); }
        }
    }

    @FunctionalInterface interface Checkpoint { void reached(String point, String dimension) throws IOException; }
}
