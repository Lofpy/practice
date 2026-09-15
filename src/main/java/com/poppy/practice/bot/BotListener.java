package com.poppy.practice.bot;

import com.poppy.practice.match.MatchState;
import com.poppy.practice.result.MatchCombatStatistics;
import com.poppy.practice.result.MatchParticipantStats;
import com.poppy.practice.service.DamageDebugService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public final class BotListener implements Listener {
    private final BotService botService;
    private final DamageDebugService damageDebugService;
    private final BotBoxingCombat botBoxingCombat;

    public BotListener(BotService botService, DamageDebugService damageDebugService) {
        this(botService, damageDebugService, new BotBoxingCombat(botService, null));
    }

    public BotListener(BotService botService, DamageDebugService damageDebugService,
                       BotBoxingCombat botBoxingCombat) {
        this.botService = botService;
        this.damageDebugService = damageDebugService;
        this.botBoxingCombat = botBoxingCombat;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotDamage(EntityDamageEvent event) {
        BotMatch match = botService.getByBot(event.getEntity().getUniqueId());
        if (match == null) {
            return;
        }
        if (match.getState() != MatchState.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (match.isBoxing()) {
            botBoxingCombat.prepare(event, match);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player bot = botService.getBot(match);
            reportDamage(match, bot, event);
            if (!event.isCancelled() && bot != null && event.getFinalDamage() >= bot.getHealth()) {
                event.setCancelled(true);
                botService.handleBotDefeat(match.getBotEntityId());
            }
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = resolvePlayer(((EntityDamageByEntityEvent) event).getDamager());
        if (attacker == null || !attacker.getUniqueId().equals(match.getPlayerId())) {
            event.setCancelled(true);
            return;
        }
        Player bot = botService.getBot(match);
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D
                && ((EntityDamageByEntityEvent) event).getDamager() instanceof Player
                && bot != null) {
            MatchParticipantStats botStats = match.getStats(match.getBotEntityId());
            MatchCombatStatistics.recordMeleeHit(match.getStats(match.getPlayerId()),
                    botStats, attacker, bot);
            if (botStats != null
                    && botStats.getGuards() >= MatchParticipantStats.MAX_GUARDS_PER_MATCH) {
                    botService.stopBotBlocking(match.getBotEntityId());
            }
        }
        reportDamage(match, bot, event);
        if (!event.isCancelled() && bot != null && event.getFinalDamage() >= bot.getHealth()) {
            event.setCancelled(true);
            botService.handleBotDefeat(match.getBotEntityId());
            return;
        }
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D
                && ((EntityDamageByEntityEvent) event).getDamager() instanceof Player) {
            botService.handleBotMeleeHit(match.getBotEntityId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoxingDamageResolved(EntityDamageEvent event) {
        botBoxingCombat.resolve(event);
    }

    private void reportDamage(BotMatch match, Player victim, EntityDamageEvent event) {
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D) {
            damageDebugService.report(match, victim, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotDeath(PlayerDeathEvent event) {
        BotMatch match = botService.getByBot(event.getEntity().getUniqueId());
        if (match == null) {
            return;
        }
        event.setDeathMessage(null);
        event.setKeepInventory(true);
        event.getDrops().clear();
        botService.handleBotDefeat(match.getBotEntityId());
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (botService.isBotEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBotHealingPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!isHealingTwo(potion.getEffects())) {
            return;
        }
        ProjectileSource source = potion.getShooter();
        if (!(source instanceof Player)) {
            return;
        }
        BotMatch match = botService.getByBot(((Player) source).getUniqueId());
        if (match == null || match.getState() != MatchState.FIGHTING) {
            return;
        }
        if (botService.canBotHealOpponent(match)) {
            return;
        }
        Player opponent = ((Player) source).getServer().getPlayer(match.getPlayerId());
        if (opponent != null && event.getIntensity(opponent) > 0.0D) {
            event.setIntensity(opponent, 0.0D);
        }
    }

    static boolean isHealingTwo(Iterable<PotionEffect> effects) {
        if (effects == null) {
            return false;
        }
        for (PotionEffect effect : effects) {
            if (PotionEffectType.HEAL.equals(effect.getType())
                    && effect.getAmplifier() >= 1) {
                return true;
            }
        }
        return false;
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }
}
