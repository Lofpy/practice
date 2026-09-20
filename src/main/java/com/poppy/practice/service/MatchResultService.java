package com.poppy.practice.service;

import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchResult;
import com.poppy.practice.result.MatchResultView;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Retains results and authorizes access; the view owns item and chat presentation. */
public final class MatchResultService implements Listener, CommandExecutor {
    private final ProfileManager profileManager;
    private final MatchResultView view = new MatchResultView();
    private final Map<UUID, MatchResult> latestByViewer = new HashMap<UUID, MatchResult>();
    private com.poppy.practice.language.LanguageService languages;

    public void setLanguageService(com.poppy.practice.language.LanguageService languages) {
        this.languages = languages;
        view.setLanguageService(languages);
    }

    private void message(Player player, String japanese, String english) {
        player.sendMessage(ChatColor.RED + (languages == null ? japanese : languages.text(player, japanese, english)));
    }

    public MatchResultService(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public void publish(MatchResult result, UUID... viewerIds) {
        if (result == null || viewerIds == null) {
            return;
        }
        for (UUID viewerId : viewerIds) {
            if (viewerId != null) {
                latestByViewer.put(viewerId, result);
            }
        }
    }

    public void sendResultChat(Player viewer, MatchResult result) {
        if (viewer != null && viewer.isOnline() && result != null) {
            view.sendChat(viewer, result);
        }
    }

    public boolean openParticipant(Player viewer, UUID participantId) {
        if (participantId == null || !canView(viewer)) {
            return false;
        }
        MatchResult result = latestByViewer.get(viewer.getUniqueId());
        MatchParticipantSnapshot participant = result == null
                ? null : result.getParticipant(participantId);
        if (participant == null) {
            message(viewer, "表示できる試合結果がありません。", "No match result is available.");
            return false;
        }
        viewer.openInventory(view.participant(viewer.getUniqueId(), result, participant));
        return true;
    }

    public boolean openLatest(Player viewer) {
        if (!canView(viewer)) {
            return false;
        }
        MatchResult result = latestByViewer.get(viewer.getUniqueId());
        if (result == null) {
            message(viewer, "表示できる直近の試合結果がありません。", "No recent match result is available.");
            return false;
        }
        viewer.openInventory(view.overview(viewer.getUniqueId(), result));
        return true;
    }

    public void handleClick(Player viewer, Inventory inventory, int rawSlot) {
        if (viewer == null) {
            return;
        }
        UUID target = view.target(inventory, viewer.getUniqueId(),
                latestByViewer.get(viewer.getUniqueId()), rawSlot);
        if (target != null) {
            openParticipant(viewer, target);
        }
    }

    public boolean isResultView(Inventory inventory) {
        return view.isView(inventory);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player viewer = (Player) sender;
        if (args.length == 0) {
            openLatest(viewer);
            return true;
        }
        if (args.length != 1) {
            message(viewer, "使い方: /matchresult [プレイヤー]", "Usage: /matchresult [player]");
            return true;
        }
        UUID participantId = participantId(latestByViewer.get(viewer.getUniqueId()), args[0]);
        if (participantId == null) {
            message(viewer, "表示できる試合結果がありません。", "No match result is available.");
        } else {
            openParticipant(viewer, participantId);
        }
        return true;
    }

    static UUID participantId(MatchResult result, String input) {
        if (result == null || input == null) {
            return null;
        }
        for (MatchParticipantSnapshot participant : new MatchParticipantSnapshot[] {
                result.getFirst(), result.getSecond() }) {
            if (input.equalsIgnoreCase(participant.getPlayerName())
                    || input.equalsIgnoreCase(participant.getPlayerId().toString())) {
                return participant.getPlayerId();
            }
        }
        return null;
    }

    private boolean canView(Player player) {
        if (player == null) {
            return false;
        }
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        if (profile != null && (profile.getState() == PlayerState.STARTING
                || profile.getState() == PlayerState.FIGHTING)) {
            message(player, "試合中は過去の試合結果を表示できません。", "You cannot view previous results during a match.");
            return false;
        }
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        latestByViewer.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        latestByViewer.clear();
    }
}
