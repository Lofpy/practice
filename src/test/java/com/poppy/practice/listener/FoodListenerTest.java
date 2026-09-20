package com.poppy.practice.listener;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public final class FoodListenerTest {
    @Test
    public void comboNpcWithoutPlayerProfileUsesNormalHungerDuringFight() {
        UUID playerId = UUID.randomUUID();
        BotMatch match = new BotMatch(playerId, UUID.randomUUID(), "combo", "arena");
        match.markFighting();
        PlayerProfile humanProfile = new ProfileManager().create(playerId);
        humanProfile.setState(PlayerState.FIGHTING);

        assertFalse(FoodListener.shouldKeepFullHunger(null, null, match));
        assertFalse(FoodListener.shouldKeepFullHunger(humanProfile, null, match));
    }

    @Test
    public void comboNpcStillKeepsFullHungerBeforeTheFightAndAfterItEnds() {
        BotMatch match = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "combo", "arena");
        assertTrue(FoodListener.shouldKeepFullHunger(null, null, match));
        match.markFighting();
        match.beginEnding();
        assertTrue(FoodListener.shouldKeepFullHunger(null, null, match));
    }

    @Test
    public void noDebuffNpcWithoutPlayerProfileUsesNormalHunger() {
        BotMatch match = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "arena");
        match.markFighting();
        assertFalse(FoodListener.shouldKeepFullHunger(null, null, match));
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

        assertTrue(FoodListener.shouldKeepFullHunger(humanProfile, null, match));
        assertTrue(FoodListener.shouldKeepFullHunger(botProfile, null, match));
        assertTrue(FoodListener.shouldKeepFullHunger(null, null, match));
    }

    @Test
    public void nodebuffBotPlayerStillConsumesFoodDuringTheFight() {
        UUID playerId = UUID.randomUUID();
        PlayerProfile profile = new ProfileManager().create(playerId);
        profile.setState(PlayerState.FIGHTING);
        BotMatch match = new BotMatch(playerId, UUID.randomUUID(), "nodebuff", "arena");
        match.markFighting();

        assertFalse(FoodListener.shouldKeepFullHunger(profile, null, match));
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
