package com.poppy.practice.network;

import com.poppy.practice.PracticePlugin;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerPingService {
    private final Map<UUID, Integer> currentPings = new HashMap<UUID, Integer>();
    private final BukkitTask updateTask;

    public PlayerPingService(PracticePlugin plugin) {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                updateAll();
            }
        }, 0L, 2L);
    }

    public int getPing(Player player) {
        if (player == null) {
            return 0;
        }
        Integer cached = currentPings.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        int ping = readPing(player);
        currentPings.put(player.getUniqueId(), ping);
        return ping;
    }

    public void shutdown() {
        updateTask.cancel();
        currentPings.clear();
    }

    private void updateAll() {
        Set<UUID> onlinePlayers = new HashSet<UUID>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
            currentPings.put(player.getUniqueId(), readPing(player));
        }
        currentPings.keySet().retainAll(onlinePlayers);
    }

    private static int readPing(Player player) {
        if (!(player instanceof CraftPlayer)) {
            return 0;
        }
        return normalizePing(((CraftPlayer) player).getHandle().ping);
    }

    public static int normalizePing(int ping) {
        return Math.max(0, Math.min(9999, ping));
    }
}
