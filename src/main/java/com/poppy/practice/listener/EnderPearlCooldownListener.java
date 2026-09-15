package com.poppy.practice.listener;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EnderPearlCooldownListener implements Listener {
    private static final int DEFAULT_COOLDOWN_SECONDS = 16;
    private static final int MAXIMUM_COOLDOWN_SECONDS = 300;
    private static final int DIRECTION_UPDATE_DELAY_TICKS = 2;

    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final MatchManager matchManager;
    private final BotService botService;
    private final Set<UUID> releasingDelayedPearls = new HashSet<UUID>();
    private final Map<UUID, BukkitTask> cooldownDisplayTasks =
            new HashMap<UUID, BukkitTask>();

    public EnderPearlCooldownListener(PracticePlugin plugin, ProfileManager profileManager) {
        this(plugin, profileManager, null);
    }

    public EnderPearlCooldownListener(PracticePlugin plugin, ProfileManager profileManager,
                                     MatchManager matchManager) {
        this(plugin, profileManager, matchManager, null);
    }

    public EnderPearlCooldownListener(PracticePlugin plugin, ProfileManager profileManager,
                                     MatchManager matchManager, BotService botService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.matchManager = matchManager;
        this.botService = botService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnderPearlUse(event.getAction(), event.getItem())) {
            return;
        }
        PlayerProfile profile = fightingProfile(event.getPlayer());
        if (profile == null) {
            return;
        }
        long remaining = remainingMillis(profile.getEnderPearlCooldownUntil(), System.currentTimeMillis());
        if (remaining > 0L) {
            event.setCancelled(true);
            sendCooldown(event.getPlayer(), remaining);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        ProjectileSource shooter = ((EnderPearl) event.getEntity()).getShooter();
        if (!(shooter instanceof Player)) {
            return;
        }
        Player player = (Player) shooter;
        if (releasingDelayedPearls.contains(player.getUniqueId())) {
            return;
        }
        PlayerProfile profile = fightingProfile(player);
        if (profile == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long remaining = remainingMillis(profile.getEnderPearlCooldownUntil(), now);
        if (remaining > 0L) {
            event.setCancelled(true);
            restoreConsumedPearl(player, profile, player.getInventory().getHeldItemSlot());
            sendCooldown(player, remaining);
            return;
        }
        final double speed = event.getEntity().getVelocity().length();
        event.setCancelled(true);
        long cooldownMillis = cooldownMillis(configuredCooldownSeconds(player));
        profile.startEnderPearlCooldown(now, cooldownMillis);
        startCooldownDisplay(player, profile, profile.getEnderPearlCooldownUntil(), cooldownMillis);
        releaseAfterDirectionUpdate(player, profile, speed);
    }

    private void startCooldownDisplay(final Player player, final PlayerProfile profile,
                                      final long expiresAtMillis,
                                      final long durationMillis) {
        stopCooldownDisplay(player.getUniqueId(), false);
        if (durationMillis <= 0L) {
            clearCooldownExperience(player);
            return;
        }

        updateCooldownExperience(player, durationMillis, durationMillis);
        final UUID playerId = player.getUniqueId();
        final long sessionVersion = profile.getCombatSessionVersion();
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(playerId);
                PlayerProfile currentProfile = profileManager.get(playerId);
                if (currentPlayer == null || !currentPlayer.isOnline()
                        || !isCurrentSession(currentProfile, profile, sessionVersion)
                        || currentProfile.getState() != PlayerState.FIGHTING
                        || currentProfile.getEnderPearlCooldownUntil() != expiresAtMillis) {
                    if (currentPlayer == player && currentPlayer.isOnline()
                            && currentProfile == profile) {
                        clearCooldownExperience(currentPlayer);
                    }
                    cooldownDisplayTasks.remove(playerId);
                    cancel();
                    return;
                }

                long remaining = remainingMillis(expiresAtMillis, System.currentTimeMillis());
                if (remaining <= 0L) {
                    currentProfile.resetEnderPearlCooldown();
                    clearCooldownExperience(currentPlayer);
                    cooldownDisplayTasks.remove(playerId);
                    cancel();
                    return;
                }
                updateCooldownExperience(currentPlayer, remaining, durationMillis);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        cooldownDisplayTasks.put(playerId, task);
    }

    private void stopCooldownDisplay(UUID playerId, boolean clearExperience) {
        BukkitTask task = cooldownDisplayTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        if (clearExperience) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                clearCooldownExperience(player);
            }
        }
    }

    private static void updateCooldownExperience(Player player, long remainingMillis,
                                                 long durationMillis) {
        player.setLevel((int) Math.min(Integer.MAX_VALUE, remainingSeconds(remainingMillis)));
        player.setExp(cooldownProgress(remainingMillis, durationMillis));
    }

    private static void clearCooldownExperience(Player player) {
        player.setExp(0.0F);
        player.setLevel(0);
    }

    private void releaseAfterDirectionUpdate(final Player player, final PlayerProfile profile,
                                              final double speed) {
        final long sessionVersion = profile.getCombatSessionVersion();
        final UUID worldId = player.getWorld().getUID();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()
                        || !isCurrentSession(fightingProfile(player), profile, sessionVersion)
                        || !worldId.equals(player.getWorld().getUID())) {
                    return;
                }
                Vector velocity = directionFor(player.getLocation().getYaw(),
                        player.getLocation().getPitch(), speed);
                releasingDelayedPearls.add(player.getUniqueId());
                try {
                    player.launchProjectile(EnderPearl.class, velocity);
                } finally {
                    releasingDelayedPearls.remove(player.getUniqueId());
                }
            }
        }, DIRECTION_UPDATE_DELAY_TICKS);
    }

    private void restoreConsumedPearl(final Player player, final PlayerProfile profile,
                                      final int usedSlot) {
        final long sessionVersion = profile.getCombatSessionVersion();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()
                        || !isCurrentSession(fightingProfile(player), profile, sessionVersion)) {
                    return;
                }
                ItemStack[] contents = player.getInventory().getContents();
                if (restoreEnderPearl(contents, usedSlot)) {
                    player.getInventory().setContents(contents);
                    player.updateInventory();
                }
            }
        });
    }

    private PlayerProfile fightingProfile(Player player) {
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        return profile != null && profile.getState() == PlayerState.FIGHTING ? profile : null;
    }

    static boolean isCurrentSession(PlayerProfile current, PlayerProfile expected,
                                    long sessionVersion) {
        return current != null && current == expected
                && current.isSameCombatSession(sessionVersion);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        stopCooldownDisplay(playerId, false);
        releasingDelayedPearls.remove(playerId);
    }

    public void shutdown() {
        for (BukkitTask task : cooldownDisplayTasks.values()) {
            task.cancel();
        }
        cooldownDisplayTasks.clear();
        releasingDelayedPearls.clear();
    }

    int configuredCooldownSeconds(Player player) {
        Match match = matchManager == null ? null : matchManager.getByPlayer(player.getUniqueId());
        if (match != null) {
            return configuredCooldownSeconds(plugin.getConfig(), match.getKitId());
        }
        BotMatch botMatch = botService == null ? null : botService.getByPlayer(player.getUniqueId());
        return configuredCooldownSeconds(plugin.getConfig(), botMatch == null ? null : botMatch.getKitId());
    }

    static int configuredCooldownSeconds(FileConfiguration config, String activeKitId) {
        if (ComboRules.isCombo(activeKitId)) {
            return ComboRules.ENDER_PEARL_COOLDOWN_SECONDS;
        }
        int configured = config.getInt("match.ender-pearl-cooldown-seconds",
                DEFAULT_COOLDOWN_SECONDS);
        return Math.max(0, Math.min(MAXIMUM_COOLDOWN_SECONDS, configured));
    }

    private void sendCooldown(Player player, long remainingMillis) {
        player.sendMessage(ChatColor.RED + "Ender pearl cooldown: "
                + remainingSeconds(remainingMillis) + "s");
    }

    static boolean isEnderPearlUse(Action action, ItemStack item) {
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        return rightClick && item != null && item.getType() == Material.ENDER_PEARL;
    }

    static long remainingMillis(long expiresAtMillis, long currentTimeMillis) {
        return Math.max(0L, expiresAtMillis - currentTimeMillis);
    }

    static long remainingSeconds(long remainingMillis) {
        return Math.max(0L, (remainingMillis + 999L) / 1000L);
    }

    static long cooldownMillis(int seconds) {
        return Math.max(0L, seconds) * 1000L;
    }

    static float cooldownProgress(long remainingMillis, long durationMillis) {
        if (remainingMillis <= 0L || durationMillis <= 0L) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D,
                (double) remainingMillis / (double) durationMillis));
    }

    static boolean restoreEnderPearl(ItemStack[] contents, int preferredSlot) {
        if (contents == null || preferredSlot < 0 || preferredSlot >= contents.length) {
            return false;
        }
        ItemStack preferred = contents[preferredSlot];
        if (preferred == null || preferred.getType() == Material.AIR) {
            contents[preferredSlot] = new ItemStack(Material.ENDER_PEARL, 1);
            return true;
        }
        if (preferred.getType() == Material.ENDER_PEARL
                && preferred.getAmount() < preferred.getMaxStackSize()) {
            preferred.setAmount(preferred.getAmount() + 1);
            return true;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType() == Material.ENDER_PEARL
                    && item.getAmount() < item.getMaxStackSize()) {
                item.setAmount(item.getAmount() + 1);
                return true;
            }
        }
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] == null || contents[slot].getType() == Material.AIR) {
                contents[slot] = new ItemStack(Material.ENDER_PEARL, 1);
                return true;
            }
        }
        return false;
    }

    static Vector directionFor(float yaw, float pitch, double speed) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRadians);
        return new Vector(-horizontal * Math.sin(yawRadians),
                -Math.sin(pitchRadians), horizontal * Math.cos(yawRadians))
                .multiply(speed);
    }
}
