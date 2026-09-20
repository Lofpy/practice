package com.poppy.practice.listener;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public final class FoodListener implements Listener {
    private final ProfileManager profileManager;
    private final MatchManager matchManager;
    private final BotService botService;

    public FoodListener(ProfileManager profileManager, MatchManager matchManager) {
        this(profileManager, matchManager, null);
    }

    public FoodListener(ProfileManager profileManager, MatchManager matchManager,
                        BotService botService) {
        this.profileManager = profileManager;
        this.matchManager = matchManager;
        this.botService = botService;
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        Match match = matchManager.getByPlayer(player.getUniqueId());
        BotMatch botMatch = botService == null ? null : botService.getByPlayer(player.getUniqueId());
        if (botMatch == null && botService != null) {
            botMatch = botService.getByBot(player.getUniqueId());
        }
        if (shouldKeepFullHunger(profile, match, botMatch)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setExhaustion(0.0F);
        }
    }

    static boolean shouldKeepFullHunger(PlayerProfile profile, Match match, BotMatch botMatch) {
        if (botMatch != null && !botMatch.isBoxing() && botMatch.getState() == MatchState.FIGHTING) {
            // NPCs have no PlayerProfile; NoDebuff and Combo both use native hunger.
            return false;
        }
        return profile == null || profile.getState() != PlayerState.FIGHTING
                || (match != null && BoxingRules.isBoxing(match.getKitId()))
                || (botMatch != null && botMatch.isBoxing());
    }
}
