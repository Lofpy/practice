package com.poppy.practice.bot;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.poppy.practice.listener.CombatListener;
import com.poppy.practice.match.BoxingRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
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

public final class BotBoxingCombatTest {
    @Test
    public void bothDirectionsScoreOnceWithoutCancellingNativeKnockback() {
        Fixture fixture = new Fixture();
        for (Player attacker : new Player[]{fixture.player, fixture.bot}) {
            EntityDamageByEntityEvent hit = fixture.hit(attacker, 7.0D);
            fixture.combat.prepare(hit, fixture.match);
            assertFalse(hit.isCancelled());
            assertEquals(0.0D, hit.getFinalDamage(), 0.0D);
            assertEquals(0, fixture.hits(attacker));
            fixture.combat.resolve(hit);
            fixture.combat.resolve(hit);
            assertEquals(1, fixture.hits(attacker));
        }
        assertEquals(1, fixture.botReceivedHits);
        assertEquals(1, fixture.botLandedHits);
        for (Player participant : new Player[]{fixture.player, fixture.bot}) {
            Mockito.verify(participant, Mockito.never()).setHealth(Mockito.anyDouble());
            Mockito.verify(participant, Mockito.never()).setNoDamageTicks(Mockito.anyInt());
            Mockito.verify(participant, Mockito.never()).setVelocity(Mockito.any(org.bukkit.util.Vector.class));
        }
    }

    @Test
    public void bothListenerMonitorsSharingTheHelperCannotDoubleCount() {
        Fixture fixture = new Fixture();
        CombatListener humanListener = new CombatListener(null, null, null, null,
                null, null, fixture.combat);
        BotListener botListener = new BotListener(null, null, fixture.combat);
        EntityDamageByEntityEvent hit = fixture.hit(fixture.player, 7.0D);
        fixture.combat.prepare(hit, fixture.match);
        humanListener.onBoxingDamageResolved(hit);
        botListener.onBoxingDamageResolved(hit);
        assertEquals(1, fixture.hits(fixture.player));
        assertEquals(1, fixture.botReceivedHits);
    }

    @Test
    public void armorAndGuardModifiersCannotLeaveResidualHealthDamage() {
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
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(fixture.player, fixture.bot,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers, functions);
        fixture.resolve(hit);
        assertFalse(hit.isCancelled());
        assertEquals(0.0D, hit.getFinalDamage(), 0.0D);
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            assertEquals(0.0D, hit.getDamage(modifier), 0.0D);
        }
        assertEquals(1, fixture.hits(fixture.player));
    }

    @Test
    public void reachAndChatterCancellationIsPreservedInBothDirections() {
        Fixture fixture = new Fixture();
        for (Player attacker : new Player[]{fixture.player, fixture.bot}) {
            EntityDamageByEntityEvent hit = fixture.hit(attacker, 7.0D);
            hit.setCancelled(true);
            fixture.resolve(hit);
            assertTrue(hit.isCancelled());
            assertEquals(0, fixture.hits(attacker));
        }
        assertEquals(0, fixture.botLandedHits + fixture.botReceivedHits);
    }

    @Test
    public void cancellationAfterPreparationIsAlsoRespected() {
        Fixture fixture = new Fixture();
        for (Player attacker : new Player[]{fixture.player, fixture.bot}) {
            EntityDamageByEntityEvent hit = fixture.hit(attacker, 7.0D);
            fixture.combat.prepare(hit, fixture.match);
            hit.setCancelled(true);
            fixture.combat.resolve(hit);
            assertEquals(0, fixture.hits(attacker));
        }
    }

    @Test
    public void zeroDamageHitsNeverCountAsAcceptedMelee() {
        Fixture fixture = new Fixture();
        fixture.resolve(fixture.hit(fixture.player, 0.0D));
        fixture.resolve(fixture.hit(fixture.bot, 0.0D));
        assertEquals(0, fixture.hits(fixture.player));
        assertEquals(0, fixture.hits(fixture.bot));
        assertEquals(0, fixture.botLandedHits + fixture.botReceivedHits);
    }

    @Test
    public void projectilesEnvironmentalDamageAndOtherAttackersAreRejected() {
        Fixture fixture = new Fixture();
        for (Player victim : new Player[]{fixture.player, fixture.bot}) {
            EntityDamageEvent[] invalid = {
                    new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.FALL, 100.0D),
                    new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.VOID, 100.0D),
                    new EntityDamageByEntityEvent(Mockito.mock(Projectile.class), victim,
                            EntityDamageEvent.DamageCause.PROJECTILE, 7.0D),
                    new EntityDamageByEntityEvent(Fixture.player(UUID.randomUUID()), victim,
                            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 7.0D),
                    new EntityDamageByEntityEvent(fixture.opponent(victim), victim,
                            EntityDamageEvent.DamageCause.THORNS, 7.0D),
                    new EntityDamageByEntityEvent(victim, victim,
                            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 7.0D)
            };
            for (EntityDamageEvent event : invalid) {
                fixture.resolve(event);
                assertTrue(event.isCancelled());
            }
        }
        assertEquals(0, fixture.hits(fixture.player));
        assertEquals(0, fixture.hits(fixture.bot));
    }

    @Test
    public void countdownAndEndedMatchesDoNotAcceptHits() {
        Fixture fixture = new Fixture(false);
        EntityDamageEvent countdown = fixture.hit(fixture.player, 7.0D);
        fixture.resolve(countdown);
        assertTrue(countdown.isCancelled());
        fixture.match.markFighting();
        fixture.match.beginEnding();
        EntityDamageEvent ending = fixture.hit(fixture.bot, 7.0D);
        fixture.resolve(ending);
        assertTrue(ending.isCancelled());
        fixture.match.markFinished();
        EntityDamageEvent finished = fixture.hit(fixture.player, 7.0D);
        fixture.resolve(finished);
        assertTrue(finished.isCancelled());
        assertEquals(0, fixture.hits(fixture.player));
        assertEquals(0, fixture.hits(fixture.bot));
    }

    @Test
    public void changedMembershipBeforeDispatchOrBeforeMonitorPreventsScoring() {
        Fixture fixture = new Fixture();
        EntityDamageEvent pending = fixture.hit(fixture.player, 7.0D);
        fixture.combat.prepare(pending, fixture.match);
        fixture.active = false;
        fixture.combat.resolve(pending);
        EntityDamageEvent removed = fixture.hit(fixture.bot, 7.0D);
        fixture.resolve(removed);
        assertTrue(removed.isCancelled());
        assertEquals(0, fixture.hits(fixture.player));
        assertEquals(0, fixture.hits(fixture.bot));
    }

    @Test
    public void endingBetweenPreparationAndMonitorPreventsScoring() {
        Fixture fixture = new Fixture();
        EntityDamageEvent hit = fixture.hit(fixture.player, 7.0D);
        fixture.combat.prepare(hit, fixture.match);
        fixture.match.beginEnding();
        fixture.combat.resolve(hit);
        assertEquals(0, fixture.hits(fixture.player));
    }

    @Test
    public void humanWinningHitLocksBothScoresAtOneHundred() {
        verifyWinningHit(false);
    }

    @Test
    public void botWinningHitLocksBothScoresAtOneHundred() {
        verifyWinningHit(true);
    }

    private static void verifyWinningHit(boolean botWins) {
        Fixture fixture = new Fixture();
        Player winner = botWins ? fixture.bot : fixture.player;
        Player loser = fixture.opponent(winner);
        for (int hit = 0; hit < BoxingRules.HITS_TO_WIN - 1; hit++) {
            fixture.resolve(fixture.hit(winner, 7.0D));
        }
        assertNull(fixture.match.getBoxingWinnerId());
        assertEquals(0, fixture.finishCalls);
        EntityDamageEvent winningHit = fixture.hit(winner, 7.0D);
        EntityDamageEvent preparedCounter = fixture.hit(loser, 7.0D);
        fixture.combat.prepare(winningHit, fixture.match);
        fixture.combat.prepare(preparedCounter, fixture.match);
        fixture.combat.resolve(winningHit);
        fixture.combat.resolve(preparedCounter);
        assertEquals(winner.getUniqueId(), fixture.match.getBoxingWinnerId());
        assertEquals(1, fixture.finishCalls);
        assertEquals(BoxingRules.HITS_TO_WIN, fixture.hits(winner));
        assertEquals(0, fixture.hits(loser));
        for (Player attacker : new Player[]{winner, loser}) {
            EntityDamageEvent afterWin = fixture.hit(attacker, 7.0D);
            fixture.resolve(afterWin);
            assertTrue(afterWin.isCancelled());
        }
        fixture.combat.resolve(winningHit);
        assertEquals(1, fixture.finishCalls);
        assertEquals(BoxingRules.HITS_TO_WIN, fixture.hits(winner));
    }

    @Test
    public void boxingHitsRetainCriticalAndGuardStatistics() {
        Fixture fixture = new Fixture();
        World world = Mockito.mock(World.class);
        Block air = Mockito.mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(air);
        when(world.getBlockAt(Mockito.any(Location.class))).thenReturn(air);
        when(fixture.bot.getFallDistance()).thenReturn(0.5F);
        when(fixture.bot.getLocation()).thenReturn(new Location(world, 0.0D, 64.0D, 0.0D));
        when(fixture.bot.getEyeLocation()).thenReturn(new Location(world, 0.0D, 65.6D, 0.0D));
        when(fixture.player.isBlocking()).thenReturn(true);
        fixture.resolve(fixture.hit(fixture.bot, 7.0D));
        assertEquals(1, fixture.match.getStats(fixture.bot.getUniqueId()).getCriticals());
        assertEquals(1, fixture.match.getStats(fixture.player.getUniqueId()).getGuards());
    }

    @Test
    public void noDebuffDamageIsLeftUntouched() {
        Fixture fixture = new Fixture();
        BotMatch noDebuff = new BotMatch(fixture.player.getUniqueId(), fixture.bot.getUniqueId(),
                "nodebuff", "one");
        noDebuff.markFighting();
        EntityDamageEvent hit = fixture.hit(fixture.player, 7.0D);
        fixture.combat.prepare(hit, noDebuff);
        fixture.combat.resolve(hit);
        assertFalse(hit.isCancelled());
        assertEquals(7.0D, hit.getFinalDamage(), 0.0D);
        assertEquals(0, noDebuff.getStats(fixture.player.getUniqueId()).getHits());
    }

    private static final class Fixture implements BotBoxingCombat.MatchAccess {
        private final BotMatch match = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "boxing", "one");
        private final Player player = player(match.getPlayerId());
        private final Player bot = player(match.getBotEntityId());
        private final BotBoxingCombat combat = new BotBoxingCombat(this);
        private boolean active = true;
        private int botReceivedHits;
        private int botLandedHits;
        private int finishCalls;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean fighting) {
            if (fighting) {
                match.markFighting();
            }
        }

        private EntityDamageByEntityEvent hit(Player attacker, double damage) {
            return new EntityDamageByEntityEvent(attacker, opponent(attacker),
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage);
        }

        private Player opponent(Player participant) {
            return participant == player ? bot : player;
        }

        private int hits(Player participant) {
            return match.getStats(participant.getUniqueId()).getHits();
        }

        private void resolve(EntityDamageEvent event) {
            combat.prepare(event, match);
            combat.resolve(event);
        }

        @Override
        public boolean isActive(BotMatch candidate) {
            return active && candidate == match;
        }

        @Override
        public void onScoredHit(BotMatch candidate, Player attacker, Player victim) {
            if (attacker.getUniqueId().equals(match.getBotEntityId())) {
                botLandedHits++;
            } else {
                botReceivedHits++;
            }
        }

        @Override
        public void onHitLimit(BotMatch candidate, UUID winnerId) {
            assertTrue(candidate.claimBoxingWinner(winnerId));
            finishCalls++;
        }

        private static Player player(UUID id) {
            Player participant = Mockito.mock(Player.class);
            when(participant.getUniqueId()).thenReturn(id);
            when(participant.getHealth()).thenReturn(20.0D);
            return participant;
        }
    }
}
