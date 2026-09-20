package com.poppy.practice.tier;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchResult;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.language.PlayerLanguage;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Fixed-opponent, per-kit placement. Ordinary bot practice deliberately never calls this service. */
public final class TierTestService {
    private final Path assessmentDirectory;
    private final RatingService ratings;
    private final Logger logger;
    private LanguageService languages;
    public void setLanguageService(LanguageService languages) { this.languages = languages; }
    private PlayerLanguage language(Player player) {
        return languages == null ? PlayerLanguage.JAPANESE : languages.language(player);
    }

    public TierTestService(File dataDirectory, RatingService ratings, Logger logger) {
        if (dataDirectory == null || ratings == null || logger == null) {
            throw new IllegalArgumentException("Tier test dependencies cannot be null");
        }
        this.assessmentDirectory = dataDirectory.toPath().toAbsolutePath().normalize().resolve("tier-assessments");
        this.ratings = ratings;
        this.logger = logger;
    }

    public boolean canStart(Player player, String kitId) {
        if (ratings.isQualified(player.getUniqueId(), kitId)) {
            player.sendMessage(ChatColor.YELLOW + language(player).choose(kitId + " の認定は完了しています。ランク対戦または通常のBot対戦をご利用ください。",
                    "Your " + kitId + " certification is complete. Use Ranked Queue or normal Bot Practice."));
            return false;
        }
        return true;
    }

    /** Called exactly once by BotService for a natural completed match, before arena cleanup. */
    public boolean complete(BotMatch match, MatchResult result, Player player) {
        if (match == null || !match.isPlacement() || match.getState() != MatchState.ENDING
                || match.getStartedAt() <= 0L || result == null
                || result.getParticipant(match.getPlayerId()) == null
                || result.getParticipant(match.getBotEntityId()) == null
                || (!match.getPlayerId().equals(result.getWinnerId())
                    && !match.getBotEntityId().equals(result.getWinnerId()))) {
            return false;
        }
        if (ratings.isQualified(match.getPlayerId(), match.getKitId())) {
            return false;
        }
        TierAssessment assessment = TierAssessment.assess(match.getKitId(), match.getPlayerId(), result);
        try {
            // Store raw metrics before publishing the rating. A failed rating save leaves a recoverable
            // audit file, never an unlocked queue with missing assessment evidence.
            saveAssessment(match, result, assessment);
            if (!ratings.recordPlacement(match.getPlayerId(), match.getKitId(), match.getId(), assessment.getScore())) {
                return false;
            }
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.SEVERE, "Could not save certification " + match.getId(), failure);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.RED
                        + language(player).choose("認定を保存できませんでした。キューは未解放のままです。管理者に連絡してください。", "Certification could not be saved. Queue remains locked; please contact an administrator."));
            }
            return false;
        }
        if (player != null && player.isOnline()) {
            int count = ratings.getPlacementCount(match.getPlayerId(), match.getKitId());
            player.sendMessage(ChatColor.RED + language(player).choose("認定 [", "Certification [") + match.getKitId() + "] "
                    + count + language(player).choose("/3 | スコア: ", "/3 | Score: ") + number(assessment.getScore()) + "/100");
            player.sendMessage(ChatColor.DARK_GRAY + language(player).choose("採点補正: ", "Calibration: ")
                    + number(assessment.getRawScore()) + " → " + number(assessment.getScore())
                    + " (x" + String.format(Locale.ROOT, "%.2f", TierAssessment.SCORE_MULTIPLIER)
                    + language(player).choose(", 上限100)", ", max100)"));
            for (Map.Entry<String, Double> metric : assessment.getMetrics().entrySet()) {
                player.sendMessage(ChatColor.GRAY + "  " + metricName(language(player), metric.getKey()) + ": "
                        + ChatColor.WHITE + number(metric.getValue()) + "%");
            }
            player.sendMessage(ChatColor.DARK_GRAY + language(player).choose("サーバー内の固定Bot評価です。外部の競技Tierではありません。", "Local fixed-bot assessment; not an external competitive tier."));
            if (ratings.isQualified(match.getPlayerId(), match.getKitId())) {
                player.sendMessage(ChatColor.GREEN + language(player).choose(match.getKitId() + " のランク対戦を解放！初期ELO: ", "Ranked " + match.getKitId() + " unlocked! Initial ELO: ")
                        + RatingService.format(ratings.getRatingMilli(match.getPlayerId(), match.getKitId())));
            }
        }
        return true;
    }

    private void saveAssessment(BotMatch match, MatchResult result, TierAssessment assessment) throws IOException {
        String kit = match.getKitId().toLowerCase(Locale.ROOT);
        if (!kit.equals("nodebuff") && !kit.equals("boxing") && !kit.equals("combo")) {
            throw new IllegalArgumentException("Unsupported certification kit");
        }
        Path directory = assessmentDirectory.resolve(match.getPlayerId().toString()).resolve(kit);
        Files.createDirectories(directory);
        Path destination = directory.resolve(match.getId().toString() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("model", TierAssessment.MODEL_VERSION);
        yaml.set("match-id", match.getId().toString());
        yaml.set("player-id", match.getPlayerId().toString());
        yaml.set("kit", kit);
        yaml.set("finished-at", System.currentTimeMillis());
        yaml.set("duration-seconds", result.getDurationSeconds());
        yaml.set("won", match.getPlayerId().equals(result.getWinnerId()));
        yaml.set("score", assessment.getScore());
        yaml.set("raw-score", assessment.getRawScore());
        yaml.set("calibration-multiplier", TierAssessment.SCORE_MULTIPLIER);
        for (Map.Entry<String, Double> metric : assessment.getMetrics().entrySet()) {
            String path = metric.getKey().toLowerCase(Locale.ROOT).replace(' ', '-');
            yaml.set("metrics." + path + ".percent", metric.getValue());
            yaml.set("metrics." + path + ".weight", assessment.getWeights().get(metric.getKey()));
        }
        recordStats(yaml, "player", result.getParticipant(match.getPlayerId()));
        recordStats(yaml, "bot", result.getParticipant(match.getBotEntityId()));
        Path temporary = Files.createTempFile(directory, "assessment-", ".tmp");
        try {
            Files.write(temporary, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void recordStats(YamlConfiguration yaml, String path, MatchParticipantSnapshot stats) {
        yaml.set(path + ".hits", stats.getHits());
        yaml.set(path + ".criticals", stats.getCriticals());
        yaml.set(path + ".guards", stats.getGuards());
        yaml.set(path + ".health", stats.getHealth());
        yaml.set(path + ".food-level", stats.getFoodLevel());
        yaml.set(path + ".remaining-potions", stats.getRemainingHealingPotions());
        yaml.set(path + ".potions-thrown", stats.getHealingPotionsThrown());
        yaml.set(path + ".potions-missed", stats.getHealingPotionsMissed());
        yaml.set(path + ".healed-hp", stats.getHealedHealth());
        yaml.set(path + ".overhealed-hp", stats.getOverhealedHealth());
        yaml.set(path + ".opponent-healed-hp", stats.getOpponentHealedHealth());
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String metricName(PlayerLanguage language, String name) {
        if (language == PlayerLanguage.ENGLISH) return name;
        if ("Hit share".equals(name)) return "ヒット占有率";
        if ("Result".equals(name)) return "勝敗";
        if ("Remaining health".equals(name)) return "残り体力";
        if ("Potion accuracy".equals(name)) return "ポーション精度";
        return name;
    }
}
