package com.poppy.practice.tier;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class TierTestServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Logger logger = Logger.getLogger("TierTestServiceTest");

    @Test
    public void threeValidPlacementsUnlockOnlyTheirOwnKitAndSurviveReload() throws Exception {
        File folder = temporary.newFolder();
        RatingService ratings = new RatingService(folder, logger);
        TierTestService service = new TierTestService(folder, ratings, logger);
        UUID playerId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            BotMatch match = completed(playerId, "boxing", true);
            assertTrue(service.complete(match, result(match), null));
            assertEquals(i + 1, ratings.getPlacementCount(playerId, "boxing"));
            assertEquals(i == 2, ratings.isQualified(playerId, "boxing"));
        }
        assertFalse(ratings.isQualified(playerId, "nodebuff"));
        assertFalse(ratings.isQualified(playerId, "combo"));
        assertEquals(0, ratings.getPlacementCount(playerId, "nodebuff"));
        RatingService reloaded = new RatingService(folder, logger);
        assertEquals(1800000L, reloaded.getRatingMilli(playerId, "boxing"));
        assertTrue(reloaded.isQualified(playerId, "boxing"));
    }

    @Test
    public void metricsAndRawStatisticsAreDurableBeforeRatingCount() throws Exception {
        File folder = temporary.newFolder();
        RatingService ratings = new RatingService(folder, logger);
        TierTestService service = new TierTestService(folder, ratings, logger);
        BotMatch match = completed(UUID.randomUUID(), "nodebuff", true);
        match.getStats(match.getPlayerId()).recordHealingPotionThrown();
        match.getStats(match.getPlayerId()).recordHealingPotionResult(4, 4, 2);
        assertTrue(service.complete(match, result(match), null));
        File audit = new File(folder, "tier-assessments/" + match.getPlayerId()
                + "/nodebuff/" + match.getId() + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(audit);
        assertEquals(TierAssessment.MODEL_VERSION, yaml.getString("model"));
        assertEquals(50, yaml.getDouble("metrics.potion-accuracy.percent"), 0);
        assertEquals(20, yaml.getDouble("metrics.potion-accuracy.weight"), 0);
        assertEquals(2, yaml.getDouble("player.opponent-healed-hp"), 0);
        assertEquals(1, yaml.getInt("player.potions-missed"));
        assertEquals(1, ratings.getPlacementCount(match.getPlayerId(), "nodebuff"));
    }

    @Test
    public void ordinaryBotMatchesNeverCountEvenAfterNormalFinish() throws Exception {
        File folder = temporary.newFolder();
        RatingService ratings = new RatingService(folder, logger);
        TierTestService service = new TierTestService(folder, ratings, logger);
        BotMatch ordinary = completed(UUID.randomUUID(), "boxing", false);
        assertFalse(service.complete(ordinary, result(ordinary), null));
        assertEquals(0, ratings.getPlacementCount(ordinary.getPlayerId(), "boxing"));
        assertFalse(new File(folder, "tier-assessments").exists());
    }

    @Test
    public void countdownAbortAndStillFightingDoNotCount() throws Exception {
        File folder = temporary.newFolder();
        RatingService ratings = new RatingService(folder, logger);
        TierTestService service = new TierTestService(folder, ratings, logger);
        BotMatch countdown = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "boxing", "arena", true);
        countdown.beginEnding();
        assertFalse(service.complete(countdown, result(countdown), null));
        BotMatch fighting = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "boxing", "arena", true);
        fighting.markFighting();
        assertFalse(service.complete(fighting, result(fighting), null));
    }

    @Test
    public void duplicateCompletionAndFourthMatchCannotAlterCertification() throws Exception {
        File folder = temporary.newFolder();
        RatingService ratings = new RatingService(folder, logger);
        TierTestService service = new TierTestService(folder, ratings, logger);
        UUID id = UUID.randomUUID();
        BotMatch first = completed(id, "boxing", true);
        assertTrue(service.complete(first, result(first), null));
        assertFalse(service.complete(first, result(first), null));
        assertEquals(1, ratings.getPlacementCount(id, "boxing"));
        for (int i = 0; i < 2; i++) {
            BotMatch next = completed(id, "boxing", true);
            assertTrue(service.complete(next, result(next), null));
        }
        BotMatch fourth = completed(id, "boxing", true);
        assertFalse(service.complete(fourth, result(fourth), null));
        assertEquals(3, ratings.getPlacementCount(id, "boxing"));
    }

    @Test
    public void auditFailureDoesNotUnlockOrIncrementRating() throws Exception {
        File folder = temporary.newFolder();
        RatingService ratings = new RatingService(folder, logger);
        assertTrue(new File(folder, "tier-assessments").createNewFile());
        TierTestService service = new TierTestService(folder, ratings, logger);
        BotMatch match = completed(UUID.randomUUID(), "boxing", true);
        assertFalse(service.complete(match, result(match), null));
        assertEquals(0, ratings.getPlacementCount(match.getPlayerId(), "boxing"));
    }

    @Test
    public void mismatchedResultsNeverCount() throws Exception {
        File folder = temporary.newFolder();
        RatingService ratings = new RatingService(folder, logger);
        TierTestService service = new TierTestService(folder, ratings, logger);
        BotMatch match = completed(UUID.randomUUID(), "boxing", true);
        BotMatch other = completed(match.getPlayerId(), "boxing", true);
        assertFalse(service.complete(match, result(other), null));
        assertEquals(0, ratings.getPlacementCount(match.getPlayerId(), "boxing"));
    }

    private static BotMatch completed(UUID id, String kit, boolean placement) {
        BotMatch match = new BotMatch(id, UUID.randomUUID(), kit, "arena", placement);
        match.markFighting();
        match.getStats(id).recordMeleeHit(false);
        match.beginEnding();
        return match;
    }

    private static MatchResult result(BotMatch match) {
        return new MatchResult(match.getPlayerId(),
                MatchParticipantSnapshot.capture(match.getPlayerId(), "Player", null,
                        match.getStats(match.getPlayerId())),
                MatchParticipantSnapshot.capture(match.getBotEntityId(), "Bot", null,
                        match.getStats(match.getBotEntityId())), 60);
    }
}
