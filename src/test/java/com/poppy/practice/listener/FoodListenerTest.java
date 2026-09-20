package com.poppy.practice.listener;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.Test;
import org.mockito.Mockito;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public final class FoodListenerTest {
    @Test
    public void comboNpcKeepsFullHungerButItsOpponentUsesNormalHunger() {
        UUID playerId = UUID.randomUUID();
        BotMatch match = new BotMatch(playerId, UUID.randomUUID(), "combo", "arena");
        match.markFighting();
        PlayerProfile humanProfile = new ProfileManager().create(playerId);
        humanProfile.setState(PlayerState.FIGHTING);

        assertTrue(FoodListener.shouldKeepFullHunger(match.getBotEntityId(), null, null, match));
        assertFalse(FoodListener.shouldKeepFullHunger(playerId, humanProfile, null, match));
    }

    @Test
    public void comboNpcStillKeepsFullHungerBeforeTheFightAndAfterItEnds() {
        BotMatch match = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "combo", "arena");
        assertTrue(FoodListener.shouldKeepFullHunger(match.getBotEntityId(), null, null, match));
        match.markFighting();
        match.beginEnding();
        assertTrue(FoodListener.shouldKeepFullHunger(match.getBotEntityId(), null, null, match));
    }

    @Test
    public void noDebuffNpcWithoutPlayerProfileKeepsFullHunger() {
        BotMatch match = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "arena");
        match.markFighting();
        assertTrue(FoodListener.shouldKeepFullHunger(match.getBotEntityId(), null, null, match));
    }

    @Test
    public void boxingBotParticipantsKeepFullHungerDuringTheFight() {
        UUID playerId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        BotMatch match = new BotMatch(playerId, botId, "boxing", "arena");
        match.markFighting();
        ProfileManager profiles = new ProfileManager();
        PlayerProfile humanProfile = profiles.create(playerId);
        humanProfile.setState(PlayerState.FIGHTING);
        PlayerProfile botProfile = profiles.create(botId);
        botProfile.setState(PlayerState.FIGHTING);

        assertTrue(FoodListener.shouldKeepFullHunger(playerId, humanProfile, null, match));
        assertTrue(FoodListener.shouldKeepFullHunger(botId, botProfile, null, match));
        assertTrue(FoodListener.shouldKeepFullHunger(botId, null, null, match));
    }

    @Test
    public void nodebuffBotPlayerStillConsumesFoodDuringTheFight() {
        UUID playerId = UUID.randomUUID();
        PlayerProfile profile = new ProfileManager().create(playerId);
        profile.setState(PlayerState.FIGHTING);
        BotMatch match = new BotMatch(playerId, UUID.randomUUID(), "nodebuff", "arena");
        match.markFighting();

        assertFalse(FoodListener.shouldKeepFullHunger(playerId, profile, null, match));
    }

    @Test
    public void consumableBotMatchesCancelOnlyNpcHungerEventsAndKeepSprintAvailable() throws Exception {
        for (String kitId : new String[] { "nodebuff", "combo" }) {
            UUID playerId = UUID.randomUUID();
            UUID botId = UUID.randomUUID();
            BotMatch match = new BotMatch(playerId, botId, kitId, "arena");
            match.markFighting();
            ProfileManager profiles = new ProfileManager();
            profiles.create(playerId).setState(PlayerState.FIGHTING);
            // NPC identity, not the presence/absence of a profile, controls this.
            profiles.create(botId).setState(PlayerState.FIGHTING);
            BotService bots = new ObjenesisStd().newInstance(BotService.class);
            Field byPlayer = BotService.class.getDeclaredField("matchesByPlayer");
            byPlayer.setAccessible(true);
            byPlayer.set(bots, Collections.singletonMap(playerId, match));
            Field byBot = BotService.class.getDeclaredField("matchesByBot");
            byBot.setAccessible(true);
            byBot.set(bots, Collections.singletonMap(botId, match));
            FoodListener listener = new FoodListener(profiles, new MatchManager(), bots);
            Player npc = mock(Player.class);
            Player human = mock(Player.class);
            when(npc.getUniqueId()).thenReturn(botId);
            when(human.getUniqueId()).thenReturn(playerId);

            FoodLevelChangeEvent npcEvent = new FoodLevelChangeEvent(npc, 6);
            listener.onFoodLevelChange(npcEvent);
            assertTrue(npcEvent.isCancelled());
            verify(npc).setFoodLevel(20);
            verify(npc).setSaturation(20.0F);
            verify(npc).setExhaustion(0.0F);

            FoodLevelChangeEvent humanEvent = new FoodLevelChangeEvent(human, 6);
            listener.onFoodLevelChange(humanEvent);
            assertFalse(humanEvent.isCancelled());
            verify(human, never()).setFoodLevel(anyInt());
            verify(human, never()).setSaturation(anyFloat());
            verify(human, never()).setExhaustion(anyFloat());
        }
    }

    @Test
    public void boxingKeepsFullHungerAndClearsExhaustion() {
        Fixture fixture = new Fixture("boxing", PlayerState.FIGHTING);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(fixture.player, 19);
        fixture.listener.onFoodLevelChange(event);
        assertTrue(event.isCancelled());
        verify(fixture.player).setFoodLevel(20);
        verify(fixture.player).setSaturation(20.0F);
        verify(fixture.player).setExhaustion(0.0F);
    }

    @Test
    public void nodebuffMatchesContinueUsingNormalFoodConsumption() {
        Fixture fixture = new Fixture("nodebuff", PlayerState.FIGHTING);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(fixture.player, 19);
        fixture.listener.onFoodLevelChange(event);
        assertFalse(event.isCancelled());
        verify(fixture.player, never()).setFoodLevel(Mockito.anyInt());
    }

    @Test
    public void lobbyPlayersStillHaveFullHunger() {
        Fixture fixture = new Fixture(null, PlayerState.LOBBY);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(fixture.player, 19);
        fixture.listener.onFoodLevelChange(event);
        assertTrue(event.isCancelled());
        verify(fixture.player).setFoodLevel(20);
    }

    private static final class Fixture {
        private final Player player = Mockito.mock(Player.class);
        private final ProfileManager profiles = new ProfileManager();
        private final MatchManager matches = new MatchManager();
        private final FoodListener listener = new FoodListener(profiles, matches);

        private Fixture(String kitId, PlayerState state) {
            UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            profiles.create(playerId).setState(state);
            if (kitId != null) {
                Match match = new Match(playerId, UUID.randomUUID(), kitId, "one");
                matches.register(match);
                match.markFighting();
            }
        }
    }
}
