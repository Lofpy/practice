package com.poppy.practice.rating;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Per-kit certification/rating ledger. Save a complete candidate atomically before publishing it. */
public final class RatingService {
    private final Path file;
    private final Logger logger;
    private final AtomicWriter writer;
    private Map<String, Record> records = new LinkedHashMap<String, Record>();
    private Map<UUID, String> completedMatches = new LinkedHashMap<UUID, String>();
    /** Reset results no longer reference live records, but must still reject duplicate match callbacks. */
    private Set<UUID> retiredMatches = new LinkedHashSet<UUID>();
    private long revision;
    private String persistedHash;
    private boolean resetRecoveryRequired;

    public RatingService(File dataDirectory, Logger logger) {
        this(dataDirectory, logger, RatingService::writeAtomically);
    }

    RatingService(File dataDirectory, Logger logger, AtomicWriter writer) {
        if (dataDirectory == null || logger == null || writer == null) {
            throw new IllegalArgumentException("Rating dependencies must not be null");
        }
        this.file = dataDirectory.toPath().toAbsolutePath().normalize().resolve("ratings.yml");
        this.logger = logger;
        this.writer = writer;
        load();
    }

    public synchronized boolean isQualified(UUID playerId, String kitId) {
        Record record = records.get(key(playerId, kitId));
        return record != null && record.scores.size() == EloCalculator.REQUIRED_PLACEMENTS;
    }

    public synchronized int getPlacementCount(UUID playerId, String kitId) {
        Record record = records.get(key(playerId, kitId));
        return record == null ? 0 : record.scores.size();
    }

    public synchronized double getPlacementAverage(UUID playerId, String kitId) {
        Record record = records.get(key(playerId, kitId));
        return record == null ? 0.0D : average(record.scores);
    }

    /** Zero means unqualified, not a rating of zero. */
    public synchronized long getRatingMilli(UUID playerId, String kitId) {
        Record record = records.get(key(playerId, kitId));
        return record == null ? 0L : record.ratingMilli;
    }

    public synchronized boolean recordPlacement(UUID playerId, String kitId, UUID matchId, double score) {
        String key = key(playerId, kitId);
        requireMatch(matchId);
        EloCalculator.requireScore(score);
        Record old = records.get(key);
        if (completedMatches.containsKey(matchId) || retiredMatches.contains(matchId)
                || (old != null && old.scores.size() >= EloCalculator.REQUIRED_PLACEMENTS)) {
            return false;
        }
        List<Double> scores = old == null ? new ArrayList<Double>() : new ArrayList<Double>(old.scores);
        scores.add(score);
        long rating = scores.size() == EloCalculator.REQUIRED_PLACEMENTS
                ? EloCalculator.initialRating(average(scores)) : 0L;
        Map<String, Record> candidate = new LinkedHashMap<String, Record>(records);
        candidate.put(key, new Record(scores, rating));
        Map<UUID, String> ledger = new LinkedHashMap<UUID, String>(completedMatches);
        ledger.put(matchId, "placement:" + playerId + ":" + kitId);
        commit(candidate, ledger);
        return true;
    }

    /** Null means the match was already committed. Both players must be certified in this kit. */
    public synchronized RatingChange recordRankedWin(UUID matchId, String kitId, UUID winner, UUID loser) {
        requireMatch(matchId);
        String winnerKey = key(winner, kitId);
        String loserKey = key(loser, kitId);
        if (winner.equals(loser)) {
            throw new IllegalArgumentException("A player cannot play a ranked match against themselves");
        }
        if (completedMatches.containsKey(matchId) || retiredMatches.contains(matchId)) {
            return null;
        }
        if (!isQualified(winner, kitId) || !isQualified(loser, kitId)) {
            throw new IllegalStateException("Both ranked participants must complete three placements in this kit");
        }
        Record winnerBefore = records.get(winnerKey);
        Record loserBefore = records.get(loserKey);
        RatingChange change = EloCalculator.win(winnerBefore.ratingMilli, loserBefore.ratingMilli);
        Map<String, Record> candidate = new LinkedHashMap<String, Record>(records);
        candidate.put(winnerKey, new Record(winnerBefore.scores, change.getWinnerAfterMilli()));
        candidate.put(loserKey, new Record(loserBefore.scores, change.getLoserAfterMilli()));
        Map<UUID, String> ledger = new LinkedHashMap<UUID, String>(completedMatches);
        ledger.put(matchId, "ranked:" + winner + ":" + loser + ":" + kitId);
        commit(candidate, ledger);
        return change;
    }

    public static String format(long milli) { return EloCalculator.format(milli); }

    /** Null selects all existing players/kits. The preview never changes files or ratings. */
    public synchronized CertificationResetPlan previewCertificationReset(UUID playerOrNullAll, String kitOrNullAll) {
        if (kitOrNullAll != null) { key(new UUID(0L, 0L), kitOrNullAll); }
        requireCurrentLedger();
        Map<UUID, Set<String>> selected = new LinkedHashMap<UUID, Set<String>>();
        int placements = 0;
        int qualified = 0;
        for (Map.Entry<String, Record> entry : records.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            UUID player = parseUuid(parts[0]);
            if ((playerOrNullAll != null && !playerOrNullAll.equals(player))
                    || (kitOrNullAll != null && !kitOrNullAll.equals(parts[1]))) { continue; }
            if (!selected.containsKey(player)) { selected.put(player, new LinkedHashSet<String>()); }
            selected.get(player).add(parts[1]);
            placements += entry.getValue().scores.size();
            if (entry.getValue().scores.size() == EloCalculator.REQUIRED_PLACEMENTS) { qualified++; }
        }
        return new CertificationResetPlan(this, revision, persistedHash, selected, placements, qualified);
    }

    /** Administrative destructive operation: callers must obtain explicit confirmation and reject active players. */
    public synchronized CertificationResetResult resetCertifications(CertificationResetPlan plan, String actor) {
        if (plan == null || plan.owner != this || plan.revision != revision
                || !Objects.equals(plan.ledgerHash, persistedHash)) {
            throw new IllegalStateException("Certification reset preview is stale; request a new preview");
        }
        if (actor == null || actor.trim().isEmpty()) { throw new IllegalArgumentException("A reset actor is required"); }
        if (plan.getRecordCount() == 0) { throw new IllegalArgumentException("No certification results selected"); }
        requireCurrentLedger();
        Map<String, Record> candidate = new LinkedHashMap<String, Record>(records);
        for (String selected : plan.recordKeys) { candidate.remove(selected); }
        Map<UUID, String> ledger = new LinkedHashMap<UUID, String>();
        Set<UUID> retired = new LinkedHashSet<UUID>(retiredMatches);
        for (Map.Entry<UUID, String> entry : completedMatches.entrySet()) {
            String[] parts = entry.getValue().split(":", -1);
            boolean affected = "placement".equals(parts[0])
                    ? plan.recordKeys.contains(parts[1] + ":" + parts[2])
                    : plan.recordKeys.contains(parts[1] + ":" + parts[3])
                        || plan.recordKeys.contains(parts[2] + ":" + parts[3]);
            if (affected) { retired.add(entry.getKey()); } else { ledger.put(entry.getKey(), entry.getValue()); }
        }
        CertificationResetBackup backup;
        try {
            backup = CertificationResetBackup.prepare(file.getParent(), plan, actor.trim());
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not back up certification results; no results were reset", failure);
        }
        try {
            backup.stage();
            commit(candidate, ledger, retired);
        } catch (IOException | RuntimeException failure) {
            boolean originalLedgerIntact = false;
            try {
                originalLedgerIntact = Objects.equals(plan.ledgerHash,
                        CertificationResetBackup.hash(file.getParent(), file));
            } catch (IOException | RuntimeException inspectionFailure) {
                failure.addSuppressed(inspectionFailure);
            }
            if (!originalLedgerIntact) {
                // A writer may have replaced ratings.yml before reporting an error. Do not remove the
                // recovery marker or restore old evidence against an uncertain/new ledger.
                resetRecoveryRequired = true;
                logger.log(Level.SEVERE, "Certification reset ledger state is uncertain; manual recovery required from "
                        + backup.getDirectory(), failure);
            } else {
                try {
                    backup.rollback();
                } catch (IOException | RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    resetRecoveryRequired = true;
                    logger.log(Level.SEVERE, "Certification reset needs manual recovery from " + backup.getDirectory(), failure);
                }
            }
            throw new IllegalStateException("Certification reset did not complete; backup: " + backup.getDirectory(), failure);
        }
        try {
            backup.finish();
        } catch (IOException | RuntimeException failure) {
            resetRecoveryRequired = true;
            logger.log(Level.SEVERE, "Certification data reset, but transaction marker could not be cleared. Backup: "
                    + backup.getDirectory(), failure);
            throw new IllegalStateException("Results were reset, but recovery marker requires administrator attention: "
                    + backup.getDirectory(), failure);
        }
        logger.info("Certification reset by " + actor.trim() + ": " + plan.getRecordCount() + " player/kit records, "
                + plan.getPlacementCount() + " placements; backup: " + backup.getDirectory());
        return new CertificationResetResult(backup.getDirectory(), plan);
    }

    private void requireCurrentLedger() {
        if (resetRecoveryRequired) {
            throw new IllegalStateException("A previous certification reset requires administrator recovery");
        }
        try {
            if (!Objects.equals(persistedHash, CertificationResetBackup.hash(file.getParent(), file))) {
                throw new IllegalStateException("ratings.yml changed outside this server; reload safely before continuing");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify the current ratings ledger", failure);
        }
    }

    private void load() {
        if (Files.exists(file.getParent().resolve(CertificationResetBackup.PENDING_FILE), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Interrupted certification reset: inspect certification-reset.pending and its backup before loading ratings");
        }
        if (!Files.exists(file)) {
            return;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            CertificationResetBackup.requireSafePath(file.getParent(), file);
            byte[] loadedBytes = Files.readAllBytes(file);
            yaml.loadFromString(new String(loadedBytes, StandardCharsets.UTF_8));
            if (integral(yaml.get("version"), "version") != 1L) {
                throw new IllegalArgumentException("Unsupported ratings ledger version");
            }
            ConfigurationSection players = requireSection(yaml, "players");
            ConfigurationSection matches = requireSection(yaml, "matches");
            Map<String, Record> loaded = new LinkedHashMap<String, Record>();
            for (String rawPlayer : players.getKeys(false)) {
                UUID player = parseUuid(rawPlayer);
                ConfigurationSection kits = requireSection(requireSection(players, rawPlayer), "kits");
                for (String kit : kits.getKeys(false)) {
                    String key = key(player, kit);
                    ConfigurationSection row = requireSection(kits, kit);
                    Object rawScores = row.get("placements");
                    if (!(rawScores instanceof List)) {
                        throw new IllegalArgumentException("Missing placement scores for " + key);
                    }
                    List<Double> scores = new ArrayList<Double>();
                    for (Object rawScore : (List<?>) rawScores) {
                        if (!(rawScore instanceof Number)) {
                            throw new IllegalArgumentException("Invalid placement score for " + key);
                        }
                        double score = ((Number) rawScore).doubleValue();
                        EloCalculator.requireScore(score);
                        scores.add(score);
                    }
                    if (scores.isEmpty() || scores.size() > EloCalculator.REQUIRED_PLACEMENTS) {
                        throw new IllegalArgumentException("Invalid placement count for " + key);
                    }
                    long rating = integral(row.get("rating-milli"), key + ".rating-milli");
                    if ((scores.size() < EloCalculator.REQUIRED_PLACEMENTS && rating != 0L)
                            || (scores.size() == EloCalculator.REQUIRED_PLACEMENTS
                            && rating < EloCalculator.FLOOR_MILLI)) {
                        throw new IllegalArgumentException("Invalid qualified rating for " + key);
                    }
                    loaded.put(key, new Record(scores, rating));
                }
            }
            Map<UUID, String> ledger = new LinkedHashMap<UUID, String>();
            Map<String, Integer> placementCounts = new LinkedHashMap<String, Integer>();
            for (String rawMatch : matches.getKeys(false)) {
                UUID matchId = parseUuid(rawMatch);
                Object value = matches.get(rawMatch);
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("Invalid processed match " + rawMatch);
                }
                String identity = (String) value;
                validateMatchIdentity(identity, loaded, placementCounts);
                ledger.put(matchId, identity);
            }
            for (Map.Entry<String, Record> row : loaded.entrySet()) {
                if (!Integer.valueOf(row.getValue().scores.size()).equals(placementCounts.get(row.getKey()))) {
                    throw new IllegalArgumentException("Placement ledger does not match scores for " + row.getKey());
                }
            }
            Set<UUID> retired = new LinkedHashSet<UUID>();
            if (yaml.contains("retired-matches")) {
                Object rawRetired = yaml.get("retired-matches");
                if (!(rawRetired instanceof List)) { throw new IllegalArgumentException("Invalid retired match IDs"); }
                for (Object rawId : (List<?>) rawRetired) {
                    if (!(rawId instanceof String)) { throw new IllegalArgumentException("Invalid retired match ID"); }
                    UUID id = parseUuid((String) rawId);
                    if (!retired.add(id) || ledger.containsKey(id)) {
                        throw new IllegalArgumentException("Duplicate or active retired match ID");
                    }
                }
            }
            records = loaded;
            completedMatches = ledger;
            retiredMatches = retired;
            persistedHash = CertificationResetBackup.hashBytes(loadedBytes);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            logger.log(Level.SEVERE, "Cannot load ratings.yml. Ranked/certification data was NOT reset or overwritten.", failure);
            throw new IllegalStateException("Could not safely load ratings.yml; restore or repair the ledger", failure);
        }
    }

    private static void validateMatchIdentity(String identity, Map<String, Record> loaded,
                                              Map<String, Integer> placementCounts) {
        String[] parts = identity.split(":", -1);
        if (parts.length == 3 && "placement".equals(parts[0])) {
            String key = key(parseUuid(parts[1]), parts[2]);
            if (!loaded.containsKey(key)) {
                throw new IllegalArgumentException("Placement references missing player/kit");
            }
            placementCounts.put(key, placementCounts.getOrDefault(key, 0) + 1);
        } else if (parts.length == 4 && "ranked".equals(parts[0])) {
            UUID winner = parseUuid(parts[1]);
            UUID loser = parseUuid(parts[2]);
            Record first = loaded.get(key(winner, parts[3]));
            Record second = loaded.get(key(loser, parts[3]));
            if (winner.equals(loser) || first == null || second == null
                    || first.scores.size() != 3 || second.scores.size() != 3) {
                throw new IllegalArgumentException("Ranked match references uncertified players");
            }
        } else {
            throw new IllegalArgumentException("Invalid match identity");
        }
    }

    private void commit(Map<String, Record> candidate, Map<UUID, String> ledger) {
        commit(candidate, ledger, retiredMatches);
    }

    private void commit(Map<String, Record> candidate, Map<UUID, String> ledger, Set<UUID> retired) {
        requireCurrentLedger();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.createSection("players");
        yaml.createSection("matches");
        for (Map.Entry<String, Record> row : candidate.entrySet()) {
            String[] keyParts = row.getKey().split(":", 2);
            String path = "players." + keyParts[0] + ".kits." + keyParts[1];
            yaml.set(path + ".placements", row.getValue().scores);
            yaml.set(path + ".rating-milli", row.getValue().ratingMilli);
        }
        for (Map.Entry<UUID, String> row : ledger.entrySet()) {
            yaml.set("matches." + row.getKey(), row.getValue());
        }
        if (!retired.isEmpty()) {
            List<String> ids = new ArrayList<String>();
            for (UUID id : retired) { ids.add(id.toString()); }
            yaml.set("retired-matches", ids);
        }
        String serialized = yaml.saveToString();
        try {
            writer.write(file, serialized);
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.SEVERE, "Could not save ratings.yml; no rating/certification changes committed.", failure);
            throw new IllegalStateException("Could not persist the match result", failure);
        }
        records = candidate;
        completedMatches = ledger;
        retiredMatches = retired;
        persistedHash = CertificationResetBackup.hashBytes(serialized.getBytes(StandardCharsets.UTF_8));
        revision++;
    }

    private static void writeAtomically(Path destination, String yaml) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "ratings-", ".tmp");
        try {
            Files.write(temporary, yaml.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Missing ratings section: " + path);
        }
        return section;
    }

    private static long integral(Object value, String path) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Invalid integer: " + path);
        }
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid integer: " + path, failure);
        }
    }

    private static String key(UUID player, String kit) {
        if (player == null || kit == null || !kit.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("A player UUID and a valid lowercase kit ID are required");
        }
        return player + ":" + kit;
    }

    private static UUID parseUuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) {
            throw new IllegalArgumentException("UUID must use its canonical lowercase representation");
        }
        return id;
    }

    private static void requireMatch(UUID matchId) {
        if (matchId == null) {
            throw new IllegalArgumentException("A match UUID is required for idempotency");
        }
    }

    private static double average(List<Double> scores) {
        double sum = 0.0D;
        for (double score : scores) { sum += score; }
        return scores.isEmpty() ? 0.0D : sum / scores.size();
    }

    private static final class Record {
        private final List<Double> scores;
        private final long ratingMilli;

        private Record(List<Double> scores, long ratingMilli) {
            this.scores = Collections.unmodifiableList(new ArrayList<Double>(scores));
            this.ratingMilli = ratingMilli;
        }
    }

    interface AtomicWriter {
        void write(Path destination, String yaml) throws IOException;
    }
}
