package com.poppy.practice.listener;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public final class BoxingCombatListenerTest {
    @Test
    public void validHitsRemainUncancelledAndCountExactlyOnceAtMonitor() {
        Fixture fixture = new Fixture();
        EntityDamageByEntityEvent hit = fixture.hit(7.0D);
        fixture.prepare(hit);

        assertFalse(hit.isCancelled());
        assertEquals(0.0D, hit.getFinalDamage(), 0.0D);
        assertEquals(0, fixture.hits());
        fixture.listener.onBoxingDamageResolved(hit);
        fixture.listener.onBoxingDamageResolved(hit);
        assertEquals(1, fixture.hits());
        assertEquals(0, fixture.match.getStats(fixture.victim.getUniqueId()).getHits());
        Mockito.verify(fixture.victim, Mockito.never()).setHealth(Mockito.anyDouble());
        Mockito.verify(fixture.victim, Mockito.never()).setNoDamageTicks(Mockito.anyInt());
    }

    @Test
    public void damageIsZeroEvenWhenArmorOrBlockingModifiersWerePresent() {
        Fixture fixture = new Fixture();
        Map<EntityDamageEvent.DamageModifier, Double> modifiers =
                new EnumMap<EntityDamageEvent.DamageModifier, Double>(EntityDamageEvent.DamageModifier.class);
        Map<EntityDamageEvent.DamageModifier, Function<? super Double, Double>> functions =
                new EnumMap<EntityDamageEvent.DamageModifier, Function<? super Double, Double>>(
                        EntityDamageEvent.DamageModifier.class);
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            modifiers.put(modifier, modifier == EntityDamageEvent.DamageModifier.BASE ? 7.0D : -0.5D);
            functions.put(modifier, Functions.constant(-0.5D));
        }
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(fixture.attacker, fixture.victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers, functions);

        fixture.resolve(hit);
        assertFalse(hit.isCancelled());
        assertEquals(0.0D, hit.getFinalDamage(), 0.0D);
        assertEquals(1, fixture.hits());
    }

    @Test
    public void reachOrChatterCancelledHitsAreNeverCountedOrUncancelled() {
        Fixture fixture = new Fixture();
        EntityDamageByEntityEvent hit = fixture.hit(7.0D);
        hit.setCancelled(true);
        fixture.resolve(hit);
        assertTrue(hit.isCancelled());
        assertEquals(0, fixture.hits());
    }

    @Test
    public void cancellationByALaterListenerIsRespected() {
        Fixture fixture = new Fixture();
        EntityDamageByEntityEvent hit = fixture.hit(7.0D);
        fixture.prepare(hit);
        hit.setCancelled(true);
        fixture.listener.onBoxingDamageResolved(hit);
        assertEquals(0, fixture.hits());
    }

    @Test
    public void zeroDamageAttacksAndNonMeleeCausesDoNotScore() {
        Fixture fixture = new Fixture();
        fixture.resolve(fixture.hit(0.0D));
        EntityDamageByEntityEvent thorns = new EntityDamageByEntityEvent(fixture.attacker, fixture.victim,
                EntityDamageEvent.DamageCause.THORNS, 7.0D);
        fixture.resolve(thorns);
        EntityDamageEvent fall = new EntityDamageEvent(fixture.victim,
                EntityDamageEvent.DamageCause.FALL, 100.0D);
        fixture.resolve(fall);
        assertTrue(thorns.isCancelled());
        assertTrue(fall.isCancelled());
        assertEquals(0, fixture.hits());
    }

    @Test
    public void outsidersAndProjectilesCannotDealDamageOrScore() {
        Fixture fixture = new Fixture();
        Player outsider = Fixture.player(UUID.randomUUID());
        EntityDamageByEntityEvent outsideHit = new EntityDamageByEntityEvent(outsider, fixture.victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 7.0D);
        EntityDamageByEntityEvent projectile = new EntityDamageByEntityEvent(Mockito.mock(Projectile.class),
                fixture.victim, EntityDamageEvent.DamageCause.PROJECTILE, 7.0D);
        fixture.resolve(outsideHit);
        fixture.resolve(projectile);
        assertTrue(outsideHit.isCancelled());
        assertTrue(projectile.isCancelled());
        assertEquals(0, fixture.hits());
    }

    @Test
    public void leavingTheMatchBeforeMonitorPreventsScoring() {
        Fixture fixture = new Fixture();
        EntityDamageByEntityEvent hit = fixture.hit(7.0D);
        fixture.prepare(hit);
        fixture.matches.remove(fixture.match);
        fixture.listener.onBoxingDamageResolved(hit);
        assertEquals(0, fixture.hits());
    }

    @Test
    public void countdownEndingAndResolvedMatchesRejectAttacks() {
        Fixture fixture = new Fixture();
        fixture.profiles.get(fixture.attacker.getUniqueId()).setState(PlayerState.STARTING);
        EntityDamageByEntityEvent countdown = fixture.hit(7.0D);
        fixture.resolve(countdown);
        assertTrue(countdown.isCancelled());
        fixture.profiles.get(fixture.attacker.getUniqueId()).setState(PlayerState.FIGHTING);
        fixture.match.beginEnding();
        EntityDamageByEntityEvent ending = fixture.hit(7.0D);
        fixture.resolve(ending);
        assertTrue(ending.isCancelled());
        fixture.match.markFinished();
        EntityDamageByEntityEvent finished = fixture.hit(7.0D);
        fixture.resolve(finished);
        assertTrue(finished.isCancelled());
        assertEquals(0, fixture.hits());
    }

    @Test
    public void attacksAfterTheWinningHitCannotRaiseTheScoreOrCounterattack() {
        Fixture fixture = new Fixture();
        for (int hit = 0; hit < BoxingRules.HITS_TO_WIN; hit++) {
            fixture.match.getStats(fixture.attacker.getUniqueId()).recordMeleeHit(false);
        }
        assertTrue(fixture.match.claimBoxingWinner(fixture.attacker.getUniqueId()));
        EntityDamageByEntityEvent extra = fixture.hit(7.0D);
        fixture.resolve(extra);
        EntityDamageByEntityEvent counter = new EntityDamageByEntityEvent(fixture.victim, fixture.attacker,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 7.0D);
        fixture.listener.handleBoxingDamage(counter, fixture.attacker, fixture.match);
        fixture.listener.onBoxingDamageResolved(counter);
        assertTrue(extra.isCancelled());
        assertTrue(counter.isCancelled());
        assertEquals(BoxingRules.HITS_TO_WIN, fixture.hits());
        assertEquals(0, fixture.match.getStats(fixture.victim.getUniqueId()).getHits());
    }

    private static final class Fixture {
        private final ProfileManager profiles = new ProfileManager();
        private final MatchManager matches = new MatchManager();
        private final Match match = new Match(UUID.randomUUID(), UUID.randomUUID(), "boxing", "one");
        private final Player attacker = player(match.getFirstPlayerId());
        private final Player victim = player(match.getSecondPlayerId());
        private final CombatListener listener = new CombatListener(profiles, matches, null, null, null, null);

        private Fixture() {
            matches.register(match);
            match.markFighting();
            profiles.create(attacker.getUniqueId()).setState(PlayerState.FIGHTING);
            profiles.create(victim.getUniqueId()).setState(PlayerState.FIGHTING);
        }

        private EntityDamageByEntityEvent hit(double damage) {
            return new EntityDamageByEntityEvent(attacker, victim,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage);
        }

        private void prepare(EntityDamageEvent event) {
            listener.handleBoxingDamage(event, victim, match);
        }

        private void resolve(EntityDamageEvent event) {
            prepare(event);
            listener.onBoxingDamageResolved(event);
        }

        private int hits() {
            return match.getStats(attacker.getUniqueId()).getHits();
        }

        private static Player player(UUID playerId) {
            Player player = Mockito.mock(Player.class);
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.getHealth()).thenReturn(20.0D);
            return player;
        }
    }
}
