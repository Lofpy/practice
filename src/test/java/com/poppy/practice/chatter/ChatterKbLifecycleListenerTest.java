package com.poppy.practice.chatter;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ChatterKbLifecycleListenerTest {
    @Test
    public void boxingBotZeroDamageCorrelatesInBothAttackDirections() {
        BotFixture fixture = new BotFixture("boxing", true);

        assertTrue(fixture.records(fixture.hit(fixture.human, fixture.bot)));
        assertTrue(fixture.records(fixture.hit(fixture.bot, fixture.human)));
    }

    @Test
    public void boxingBotWinningHitStillCorrelatesWithItsKnockback() {
        BotFixture fixture = new BotFixture("boxing", true);
        for (int hit = 0; hit < BoxingRules.HITS_TO_WIN; hit++) {
            fixture.match.getStats(fixture.bot.getUniqueId()).recordMeleeHit(false);
        }
        assertTrue(fixture.match.claimBoxingWinner(fixture.bot.getUniqueId()));

        assertTrue(fixture.records(fixture.hit(fixture.bot, fixture.human)));
    }

    @Test
    public void boxingBotCancelledSelfAndThirdPartyHitsDoNotCorrelate() {
        BotFixture fixture = new BotFixture("boxing", true);
        EntityDamageByEntityEvent cancelled = fixture.hit(fixture.human, fixture.bot);
        cancelled.setCancelled(true);

        assertFalse(fixture.records(cancelled));
        assertFalse(fixture.records(fixture.hit(fixture.human, fixture.human)));
        assertFalse(fixture.records(fixture.hit(player(), fixture.bot)));
        assertFalse(fixture.records(fixture.hit(fixture.bot, player())));
    }

    @Test
    public void boxingBotRequiresBothParticipantsToRemainRegisteredInTheSameMatch() {
        BotFixture fixture = new BotFixture("boxing", true);
        EntityDamageByEntityEvent event = fixture.hit(fixture.human, fixture.bot);
        BotMatch other = new BotMatch(fixture.human.getUniqueId(), fixture.bot.getUniqueId(),
                "boxing", "other");
        other.markFighting();

        assertFalse(ChatterKbLifecycleListener.shouldRecordBotBoxingDamage(
                event, fixture.match, null));
        assertFalse(ChatterKbLifecycleListener.shouldRecordBotBoxingDamage(
                event, null, fixture.match));
        assertFalse(ChatterKbLifecycleListener.shouldRecordBotBoxingDamage(
                event, fixture.match, other));
    }

    @Test
    public void boxingBotCountdownEndingAndNodebuffZeroDamageDoNotCorrelate() {
        BotFixture countdown = new BotFixture("boxing", false);
        BotFixture ending = new BotFixture("boxing", true);
        ending.match.beginEnding();
        BotFixture nodebuff = new BotFixture("nodebuff", true);

        assertFalse(countdown.records(countdown.hit(countdown.human, countdown.bot)));
        assertFalse(ending.records(ending.hit(ending.human, ending.bot)));
        assertFalse(nodebuff.records(nodebuff.hit(nodebuff.human, nodebuff.bot)));
    }

    @Test
    public void boxingBotProjectileAndNonPlayerHitsDoNotCorrelate() {
        BotFixture fixture = new BotFixture("boxing", true);

        assertFalse(fixture.records(new EntityDamageByEntityEvent(fixture.human, fixture.bot,
                DamageCause.PROJECTILE, 0.0D)));
        assertFalse(fixture.records(new EntityDamageByEntityEvent(mock(Entity.class), fixture.bot,
                DamageCause.ENTITY_ATTACK, 0.0D)));
    }

    @Test
    public void acceptedZeroDamageBoxingHitCorrelatesWithItsKnockback() {
        Fixture fixture = new Fixture("boxing", true);

        assertTrue(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                fixture.hit(0.0D), fixture.manager));
    }

    @Test
    public void winningBoxingHitStillCorrelatesBeforeTheMatchEnds() {
        Fixture fixture = new Fixture("boxing", true);
        for (int i = 0; i < BoxingRules.HITS_TO_WIN; i++) {
            fixture.match.getStats(fixture.attacker.getUniqueId()).recordMeleeHit(false);
        }
        assertTrue(fixture.match.claimBoxingWinner(fixture.attacker.getUniqueId()));

        assertTrue(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                fixture.hit(0.0D), fixture.manager));
    }

    @Test
    public void cancelledBoxingHitDoesNotCorrelate() {
        Fixture fixture = new Fixture("boxing", true);
        EntityDamageByEntityEvent event = fixture.hit(0.0D);
        event.setCancelled(true);

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(event, fixture.manager));
        // Direct invocation also respects cancellation without relying on event dispatch.
        new ChatterKbLifecycleListener(null, null, null, fixture.manager).onMeleeDamage(event);
    }

    @Test
    public void cancelledPositiveDamageHitDoesNotCorrelate() {
        Fixture fixture = new Fixture("nodebuff", true);
        EntityDamageByEntityEvent event = fixture.hit(2.0D);
        event.setCancelled(true);

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(event, fixture.manager));
    }

    @Test
    public void ordinaryZeroDamageHitDoesNotCorrelate() {
        Fixture fixture = new Fixture("nodebuff", true);

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                fixture.hit(0.0D), fixture.manager));
    }

    @Test
    public void boxingCountdownHitDoesNotCorrelate() {
        Fixture fixture = new Fixture("boxing", false);

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                fixture.hit(0.0D), fixture.manager));
    }

    @Test
    public void endingBoxingHitDoesNotCorrelate() {
        Fixture fixture = new Fixture("boxing", true);
        fixture.match.beginEnding();

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                fixture.hit(0.0D), fixture.manager));
    }

    @Test
    public void unregisteredBoxingHitDoesNotCorrelate() {
        Fixture fixture = new Fixture("boxing", true);
        fixture.manager.remove(fixture.match);

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                fixture.hit(0.0D), fixture.manager));
    }

    @Test
    public void thirdPartyAndSelfHitsDoNotCorrelate() {
        Fixture fixture = new Fixture("boxing", true);
        Player outsider = player();

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                new EntityDamageByEntityEvent(outsider, fixture.victim,
                        DamageCause.ENTITY_ATTACK, 0.0D), fixture.manager));
        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                new EntityDamageByEntityEvent(fixture.victim, fixture.victim,
                        DamageCause.ENTITY_ATTACK, 0.0D), fixture.manager));
    }

    @Test
    public void zeroDamageNonMeleeHitDoesNotCorrelate() {
        Fixture fixture = new Fixture("boxing", true);

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                new EntityDamageByEntityEvent(fixture.attacker, fixture.victim,
                        DamageCause.PROJECTILE, 0.0D), fixture.manager));
    }

    @Test
    public void positivePlayerDamageKeepsItsExistingBehaviorWithoutAMatch() {
        Fixture fixture = new Fixture("nodebuff", true);

        assertTrue(ChatterKbLifecycleListener.shouldRecordCombatDamage(fixture.hit(2.0D), null));
        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(fixture.hit(0.0D), null));
    }

    @Test
    public void nonPlayerDamageDoesNotCorrelate() {
        Fixture fixture = new Fixture("boxing", true);

        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                new EntityDamageByEntityEvent(mock(Entity.class), fixture.victim,
                        DamageCause.ENTITY_ATTACK, 2.0D), fixture.manager));
        assertFalse(ChatterKbLifecycleListener.shouldRecordCombatDamage(
                new EntityDamageByEntityEvent(fixture.attacker, mock(Entity.class),
                        DamageCause.ENTITY_ATTACK, 2.0D), fixture.manager));
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static final class Fixture {
        private final Player attacker = player();
        private final Player victim = player();
        private final MatchManager manager = new MatchManager();
        private final Match match;

        private Fixture(String kit, boolean fighting) {
            match = new Match(attacker.getUniqueId(), victim.getUniqueId(), kit, "arena");
            assertTrue(manager.register(match));
            if (fighting) {
                assertTrue(match.markFighting());
            }
        }

        private EntityDamageByEntityEvent hit(double damage) {
            return new EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK, damage);
        }
    }

    private static final class BotFixture {
        private final Player human = player();
        private final Player bot = player();
        private final BotMatch match;

        private BotFixture(String kit, boolean fighting) {
            match = new BotMatch(human.getUniqueId(), bot.getUniqueId(), kit, "arena");
            if (fighting) {
                match.markFighting();
            }
        }

        private EntityDamageByEntityEvent hit(Player attacker, Player victim) {
            return new EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK, 0.0D);
        }

        private boolean records(EntityDamageByEntityEvent event) {
            return ChatterKbLifecycleListener.shouldRecordBotBoxingDamage(event, match, match);
        }
    }
}
