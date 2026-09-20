package com.poppy.practice.command;

import com.poppy.practice.bot.HitDebugRoomService;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnCommand implements CommandExecutor {
    private final ProfileManager profileManager;
    private final QueueManager queueManager;
    private final LobbyService lobbyService;
    private final MessageService messageService;
    private final HitDebugRoomService hitDebugRoomService;
    private com.poppy.practice.spectator.SpectatorService spectators;

    public void setSpectatorService(com.poppy.practice.spectator.SpectatorService spectators) {
        this.spectators = spectators;
    }

    public SpawnCommand(ProfileManager profileManager, QueueManager queueManager,
                        LobbyService lobbyService, MessageService messageService,
                        HitDebugRoomService hitDebugRoomService) {
        this.profileManager = profileManager;
        this.queueManager = queueManager;
        this.lobbyService = lobbyService;
        this.messageService = messageService;
        this.hitDebugRoomService = hitDebugRoomService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (spectators != null && spectators.leave(player, false)) return true;
        PlayerProfile profile = profileManager.getOrCreate(player.getUniqueId());
        if (profile.getState() == PlayerState.STARTING || profile.getState() == PlayerState.FIGHTING
                || profile.getState() == PlayerState.ENDING) {
            messageService.send(player, "spawn-not-allowed");
            return true;
        }
        if (profile.getState() == PlayerState.DEBUG) {
            hitDebugRoomService.leave(player);
            return true;
        }
        if (profile.getState() == PlayerState.QUEUE) {
            queueManager.leave(player, false);
        }
        lobbyService.sendToLobby(player);
        return true;
    }
}
