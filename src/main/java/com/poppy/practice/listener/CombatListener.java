package com.poppy.practice.listener;

import com.poppy.practice.bot.BotBoxingCombat;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.bot.HitDebugRoomService;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.result.MatchCombatStatistics;
import com.poppy.practice.result.MatchParticipantStats;
import com.poppy.practice.service.DamageDebugService;
import com.poppy.practice.service.MatchService;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.IdentityHashMap;
import java.util.Map;

public final class CombatListener implements Listener {
    private final ProfileManager profileManager;
    private final MatchManager matchManager;
    private final MatchService matchService;
    private final BotService botService;
    private final HitDebugRoomService hitDebugRoomService;
    private final DamageDebugService damageDebugService;
    private final BotBoxingCombat botBoxingCombat;
    private final Map<EntityDamageEvent, BoxingHit> pendingBoxingHits =
            new IdentityHashMap<EntityDamageEvent, BoxingHit>();

    public CombatListener(ProfileManager profileManager,
                          MatchManager matchManager, MatchService matchService,
                          BotService botService, HitDebugRoomService hitDebugRoomService,
                          DamageDebugService damageDebugService) {
        this(profileManager, matchManager, matchService, botService, hitDebugRoomService,
                damageDebugService, new BotBoxingCombat(botService, profileManager));
    }

    public CombatListener(ProfileManager profileManager,
                          MatchManager matchManager, MatchService matchService,
                          BotService botService, HitDebugRoomService hitDebugRoomService,
                          DamageDebugService damageDebugService, BotBoxingCombat botBoxingCombat) {
        this.profileManager = profileManager;
        this.matchManager = matchManager;
        this.matchService = matchService;
        this.botService = botService;
        this.hitDebugRoomService = hitDebugRoomService;
        this.damageDebugService = damageDebugService;
        this.botBoxingCombat = botBoxingCombat;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (hitDebugRoomService.handleDamage(event)) {
            return;
        }
        if (botService.isBotEntity(event.getEntity())) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        boolean fallDamage = event.getCause() == EntityDamageEvent.DamageCause.FALL;
        PlayerProfile profile = profileManager.get(victim.getUniqueId());
        if (profile == null || profile.getState() != PlayerState.FIGHTING) {
            event.setCancelled(true);
            return;
        }

        BotMatch botMatch = botService.getByPlayer(victim.getUniqueId());
        if (botMatch != null) {
            handleBotDamage(event, victim, botMatch, fallDamage);
            return;
        }

        Match match = matchManager.getByPlayer(victim.getUniqueId());
        if (match == null || match.getState() != MatchState.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (BoxingRules.isBoxing(match.getKitId())) {
            handleBoxingDamage(event, victim, match);
            return;
        }
        if (fallDamage) {
            reportDamage(match, victim, event);
            if (!event.isCancelled() && event.getFinalDamage() >= victim.getHealth()) {
                matchService.handleDeath(victim);
            }
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
            return;
        }

        EntityDamageByEntityEvent damageByEntity = (EntityDamageByEntityEvent) event;
        Player attacker = resolveAttacker(damageByEntity.getDamager());
        if (attacker == null || !attacker.getUniqueId().equals(match.getOpponent(victim.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }

        if (!event.isCancelled() && event.getFinalDamage() > 0.0D
                && damageByEntity.getDamager() instanceof Player) {
            MatchParticipantStats victimStats = match.getStats(victim.getUniqueId());
            MatchCombatStatistics.recordMeleeHit(match.getStats(attacker.getUniqueId()),
                    victimStats, attacker, victim);
            enforcePlayerGuardLimit(victim, victimStats);
        }
        reportDamage(match, victim, event);
        if (!event.isCancelled() && event.getFinalDamage() >= victim.getHealth()) {
            matchService.handleDeath(victim);
        }
    }

    void handleBoxingDamage(EntityDamageEvent event, Player victim, Match match) {
        if (!(event instanceof EntityDamageByEntityEvent)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.setCancelled(true);
            return;
        }
        EntityDamageByEntityEvent melee = (EntityDamageByEntityEvent) event;
        if (!(melee.getDamager() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = (Player) melee.getDamager();
        PlayerProfile attackerProfile = profileManager.get(attacker.getUniqueId());
        if (!BoxingRules.canScore(match, attacker.getUniqueId(), victim.getUniqueId())
                || matchManager.getByPlayer(attacker.getUniqueId()) != match
                || attackerProfile == null || attackerProfile.getState() != PlayerState.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled() || event.getFinalDamage() <= 0.0D) {
            return;
        }
        pendingBoxingHits.put(event, new BoxingHit(match, attacker, victim));
        // WindSpigot EntityLiving.d returns true at zero final damage. Leaving
        // the event uncancelled preserves native lastDamage, hurt ticks and KB.
        event.setDamage(0.0D);
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0D);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoxingDamageResolved(EntityDamageEvent event) {
        botBoxingCombat.resolve(event);
        BoxingHit hit = pendingBoxingHits.remove(event);
        if (hit == null || event.isCancelled()
                || matchManager.getByPlayer(hit.victim.getUniqueId()) != hit.match
                || matchManager.getByPlayer(hit.attacker.getUniqueId()) != hit.match
                || !BoxingRules.canScore(hit.match, hit.attacker.getUniqueId(), hit.victim.getUniqueId())) {
            return;
        }
        PlayerProfile attackerProfile = profileManager.get(hit.attacker.getUniqueId());
        PlayerProfile victimProfile = profileManager.get(hit.victim.getUniqueId());
        if (attackerProfile == null || victimProfile == null
                || attackerProfile.getState() != PlayerState.FIGHTING
                || victimProfile.getState() != PlayerState.FIGHTING) {
            return;
        }
        MatchParticipantStats attackerStats = hit.match.getStats(hit.attacker.getUniqueId());
        MatchParticipantStats victimStats = hit.match.getStats(hit.victim.getUniqueId());
        MatchCombatStatistics.recordMeleeHit(attackerStats, victimStats, hit.attacker, hit.victim);
        enforcePlayerGuardLimit(hit.victim, victimStats);
        if (attackerStats.getHits() >= BoxingRules.HITS_TO_WIN) {
            matchService.handleBoxingHitLimit(hit.match, hit.attacker.getUniqueId());
        }
    }

    private static final class BoxingHit {
        private final Match match;
        private final Player attacker;
        private final Player victim;

        private BoxingHit(Match match, Player attacker, Player victim) {
            this.match = match;
            this.attacker = attacker;
            this.victim = victim;
        }
    }

    private void handleBotDamage(EntityDamageEvent event, Player victim, BotMatch match,
                                 boolean fallDamage) {
        if (match.getState() != MatchState.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (match.isBoxing()) {
            botBoxingCombat.prepare(event, match);
            return;
        }
        if (fallDamage) {
            reportDamage(match, victim, event);
            if (!event.isCancelled() && event.getFinalDamage() >= victim.getHealth()) {
                botService.handlePlayerDefeat(victim.getUniqueId());
            }
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
            return;
        }
        EntityDamageByEntityEvent damage = (EntityDamageByEntityEvent) event;
        if (!damage.getDamager().getUniqueId().equals(match.getBotEntityId())) {
            event.setCancelled(true);
            return;
        }
        Player bot = botService.getBot(match);
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D && bot != null) {
            MatchParticipantStats victimStats = match.getStats(victim.getUniqueId());
            MatchCombatStatistics.recordMeleeHit(match.getStats(match.getBotEntityId()),
                    victimStats, bot, victim);
            enforcePlayerGuardLimit(victim, victimStats);
        }
        reportDamage(match, victim, event);
        if (!event.isCancelled() && event.getFinalDamage() >= victim.getHealth()) {
            botService.handlePlayerDefeat(victim.getUniqueId());
            return;
        }
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D) {
            botService.handleBotMeleeHitLanded(match.getBotEntityId());
        }
    }

    private void reportDamage(Match match, Player victim, EntityDamageEvent event) {
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D) {
            damageDebugService.report(match, victim, event.getFinalDamage());
        }
    }

    private void reportDamage(BotMatch match, Player victim, EntityDamageEvent event) {
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D) {
            damageDebugService.report(match, victim, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuardAttempt(PlayerInteractEvent event) {
        if (!isSwordGuardAttempt(event.getAction(), event.getItem())) {
            return;
        }
        MatchParticipantStats stats = activeStats(event.getPlayer());
        if (stats == null || stats.getGuards() < MatchParticipantStats.MAX_GUARDS_PER_MATCH) {
            return;
        }
        event.setCancelled(true);
        stopBlocking(event.getPlayer());
    }

    private MatchParticipantStats activeStats(Player player) {
        BotMatch botMatch = botService.getByPlayer(player.getUniqueId());
        if (botMatch != null && botMatch.getState() == MatchState.FIGHTING) {
            return botMatch.getStats(player.getUniqueId());
        }
        Match match = matchManager.getByPlayer(player.getUniqueId());
        if (match != null && match.getState() == MatchState.FIGHTING) {
            return match.getStats(player.getUniqueId());
        }
        return null;
    }

    private static void enforcePlayerGuardLimit(Player player,
                                                MatchParticipantStats stats) {
        if (stats != null
                && stats.getGuards() >= MatchParticipantStats.MAX_GUARDS_PER_MATCH) {
            stopBlocking(player);
        }
    }

    private static void stopBlocking(Player player) {
        if (player == null) {
            return;
        }
        net.minecraft.server.v1_8_R3.EntityPlayer handle =
                ((CraftPlayer) player).getHandle();
        if (handle.isBlocking()) {
            handle.bV();
        }
    }

    static boolean isSwordGuardAttempt(Action action, ItemStack item) {
        boolean rightClick = action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
        Material material = item == null ? Material.AIR : item.getType();
        return rightClick && material.name().endsWith("_SWORD");
    }

    private Player resolveAttacker(Entity damager) {
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
