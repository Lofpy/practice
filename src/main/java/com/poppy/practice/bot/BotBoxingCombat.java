package com.poppy.practice.bot;

import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.result.MatchCombatStatistics;
import com.poppy.practice.result.MatchParticipantStats;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Applies the same zero-damage, confirmed-hit rules in both bot-match directions. */
public final class BotBoxingCombat {
    private final MatchAccess access;
    private final Map<EntityDamageEvent, PendingHit> pendingHits =
            new IdentityHashMap<EntityDamageEvent, PendingHit>();

    public BotBoxingCombat(final BotService botService, final ProfileManager profileManager) {
        this(new MatchAccess() {
            @Override
            public boolean isActive(BotMatch match) {
                if (botService == null
                        || botService.getByPlayer(match.getPlayerId()) != match
                        || botService.getByBot(match.getBotEntityId()) != match) {
                    return false;
                }
                PlayerProfile profile = profileManager == null
                        ? null : profileManager.get(match.getPlayerId());
                return profileManager == null
                        || (profile != null && profile.getState() == PlayerState.FIGHTING);
            }

            @Override
            public void onScoredHit(BotMatch match, Player attacker, Player victim) {
                MatchParticipantStats victimStats = match.getStats(victim.getUniqueId());
                if (victimStats.getGuards() >= MatchParticipantStats.MAX_GUARDS_PER_MATCH) {
                    if (victim.getUniqueId().equals(match.getBotEntityId())) {
                        botService.stopBotBlocking(match.getBotEntityId());
                    } else {
                        net.minecraft.server.v1_8_R3.EntityPlayer handle =
                                ((CraftPlayer) victim).getHandle();
                        if (handle.isBlocking()) {
                            handle.bV();
                        }
                    }
                }
                if (attacker.getUniqueId().equals(match.getBotEntityId())) {
                    botService.handleBotMeleeHitLanded(match.getBotEntityId());
                } else {
                    botService.handleBotMeleeHit(match.getBotEntityId());
                }
            }

            @Override
            public void onHitLimit(BotMatch match, UUID winnerId) {
                botService.handleBoxingHitLimit(match, winnerId);
            }
        });
    }

    BotBoxingCombat(MatchAccess access) {
        this.access = access;
    }

    /** HIGHEST: protect HP without cancelling the native knockback / hurt-time path. */
    public void prepare(EntityDamageEvent event, BotMatch match) {
        if (match == null || !match.isBoxing()) {
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        EntityDamageByEntityEvent melee = (EntityDamageByEntityEvent) event;
        if (!(melee.getDamager() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = (Player) melee.getDamager();
        Player victim = (Player) event.getEntity();
        if (!match.canScoreBoxingHit(attacker.getUniqueId(), victim.getUniqueId())
                || !access.isActive(match)) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled() || event.getFinalDamage() <= 0.0D) {
            return;
        }
        pendingHits.put(event, new PendingHit(match, attacker, victim));
        event.setDamage(0.0D);
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0D);
            }
        }
    }

    /** MONITOR: cancellations and matches that ended during dispatch never score. */
    public void resolve(EntityDamageEvent event) {
        PendingHit hit = pendingHits.remove(event);
        if (hit == null || event.isCancelled()
                || !hit.match.canScoreBoxingHit(hit.attacker.getUniqueId(), hit.victim.getUniqueId())
                || !access.isActive(hit.match)) {
            return;
        }
        MatchParticipantStats attackerStats = hit.match.getStats(hit.attacker.getUniqueId());
        MatchCombatStatistics.recordMeleeHit(attackerStats,
                hit.match.getStats(hit.victim.getUniqueId()), hit.attacker, hit.victim);
        access.onScoredHit(hit.match, hit.attacker, hit.victim);
        if (attackerStats.getHits() >= BoxingRules.HITS_TO_WIN) {
            access.onHitLimit(hit.match, hit.attacker.getUniqueId());
        }
    }

    interface MatchAccess {
        boolean isActive(BotMatch match);

        void onScoredHit(BotMatch match, Player attacker, Player victim);

        void onHitLimit(BotMatch match, UUID winnerId);
    }

    private static final class PendingHit {
        private final BotMatch match;
        private final Player attacker;
        private final Player victim;

        private PendingHit(BotMatch match, Player attacker, Player victim) {
            this.match = match;
            this.attacker = attacker;
            this.victim = victim;
        }
    }
}
