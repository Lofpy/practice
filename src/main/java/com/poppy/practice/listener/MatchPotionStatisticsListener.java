package com.poppy.practice.listener;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.result.MatchParticipantStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatchPotionStatisticsListener implements Listener {
    private final MatchManager matchManager;
    private final BotService botService;
    private final Map<UUID, UUID> trackedPotionShooters = new HashMap<UUID, UUID>();

    public MatchPotionStatisticsListener(MatchManager matchManager, BotService botService) {
        this.matchManager = matchManager;
        this.botService = botService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion)) {
            return;
        }
        ThrownPotion potion = (ThrownPotion) event.getEntity();
        if (!isHealingTwo(potion)) {
            return;
        }
        Player shooter = playerShooter(potion);
        ActiveParticipant participant = shooter == null
                ? null : activeParticipant(shooter.getUniqueId());
        if (participant == null) {
            return;
        }
        participant.stats.recordHealingPotionThrown();
        trackedPotionShooters.put(potion.getUniqueId(), shooter.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!isHealingTwo(potion)) {
            return;
        }
        UUID shooterId = trackedPotionShooters.remove(potion.getUniqueId());
        boolean launchWasTracked = shooterId != null;
        Player shooter = playerShooter(potion);
        if (shooterId == null && shooter != null) {
            shooterId = shooter.getUniqueId();
        }
        ActiveParticipant participant = shooterId == null
                ? null : activeParticipant(shooterId);
        if (participant == null || shooter == null) {
            return;
        }
        if (!launchWasTracked) {
            participant.stats.recordHealingPotionThrown();
        }

        double intensity = event.getIntensity(shooter);
        double nominalHealing = healingAmount(potion, intensity);
        boolean hit = intensity > 0.0D && nominalHealing > 0.0D;
        double missingHealth = Math.max(0.0D, shooter.getMaxHealth() - shooter.getHealth());
        double healed = hit ? Math.min(missingHealth, nominalHealing) : 0.0D;
        double overhealed = hit ? Math.max(0.0D, nominalHealing - healed) : 0.0D;
        double opponentHealed = actualHealing(potion, event, participant.opponent);
        participant.stats.recordHealingPotionResult(healed, overhealed, opponentHealed);
    }

    static double healingAmount(ThrownPotion potion, double intensity) {
        if (potion == null || intensity <= 0.0D) {
            return 0.0D;
        }
        for (PotionEffect effect : potion.getEffects()) {
            if (PotionEffectType.HEAL.equals(effect.getType())) {
                int baseHealing = 4 << Math.max(0, effect.getAmplifier());
                return Math.max(0, (int) (intensity * baseHealing + 0.5D));
            }
        }
        return 0.0D;
    }

    private double actualHealing(ThrownPotion potion, PotionSplashEvent event,
                                 Player player) {
        if (player == null || !player.isValid()) {
            return 0.0D;
        }
        double nominal = healingAmount(potion, event.getIntensity(player));
        double missing = Math.max(0.0D, player.getMaxHealth() - player.getHealth());
        return Math.min(missing, nominal);
    }

    private ActiveParticipant activeParticipant(UUID participantId) {
        Match match = matchManager.getByPlayer(participantId);
        if (match != null && match.getState() == MatchState.FIGHTING) {
            UUID opponentId = match.getOpponent(participantId);
            return new ActiveParticipant(match.getStats(participantId),
                    opponentId == null ? null : Bukkit.getPlayer(opponentId));
        }
        BotMatch botMatch = botService.getByPlayer(participantId);
        if (botMatch == null) {
            botMatch = botService.getByBot(participantId);
        }
        if (botMatch != null && botMatch.getState() == MatchState.FIGHTING) {
            Player opponent = participantId.equals(botMatch.getPlayerId())
                    ? botService.getBot(botMatch) : Bukkit.getPlayer(botMatch.getPlayerId());
            return new ActiveParticipant(botMatch.getStats(participantId), opponent);
        }
        return null;
    }

    private Player playerShooter(ThrownPotion potion) {
        ProjectileSource shooter = potion.getShooter();
        return shooter instanceof Player ? (Player) shooter : null;
    }

    private boolean isHealingTwo(ThrownPotion potion) {
        for (PotionEffect effect : potion.getEffects()) {
            if (PotionEffectType.HEAL.equals(effect.getType()) && effect.getAmplifier() >= 1) {
                return true;
            }
        }
        return false;
    }

    private static final class ActiveParticipant {
        private final MatchParticipantStats stats;
        private final Player opponent;

        private ActiveParticipant(MatchParticipantStats stats, Player opponent) {
            this.stats = stats;
            this.opponent = opponent;
        }
    }
}
