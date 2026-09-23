package com.ascendingmc.survival;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

/** Prevents old saved positions from placing players inside regenerated terrain. */
public final class RegeneratedWorldSafety implements Listener {
    private static final String UNAVAILABLE = "ワールド再生成後の安全な移動先を確認できません。管理者にお問い合わせください。";
    private static final Set<String> HAZARDS = Set.of("MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE",
            "CACTUS", "FIRE", "SOUL_FIRE", "LAVA", "WATER", "POWDER_SNOW", "SWEET_BERRY_BUSH",
            "WITHER_ROSE", "END_PORTAL", "NETHER_PORTAL", "END_GATEWAY", "POINTED_DRIPSTONE", "COBWEB");
    private final JavaPlugin plugin;
    private final World primary;
    private final NamespacedKey epochKey;
    private final Map<UUID, Location> prepared = new HashMap<>();
    private final Set<UUID> redirected = new java.util.HashSet<>();
    private final Set<UUID> blockedBeforeSpawn = ConcurrentHashMap.newKeySet();
    private final RegenerationCompletion completion;
    private final boolean invalidMarker;
    private Location cachedSafe;

    /** dataRoot is the plugin data folder, not the server's world container. */
    public RegeneratedWorldSafety(JavaPlugin plugin, World primary, Path dataRoot) {
        this.plugin = plugin;
        this.primary = primary;
        this.epochKey = new NamespacedKey(plugin, "regeneration-safe-epoch");
        RegenerationCompletion loaded = null;
        boolean invalid = false;
        try {
            loaded = RegenerationCompletion.load(dataRoot).orElse(null);
        } catch (Exception failure) {
            invalid = true;
            plugin.getLogger().log(Level.SEVERE, "Regeneration safety marker is invalid; Survival logins are blocked.", failure);
        }
        this.completion = loaded;
        this.invalidMarker = invalid;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        if (invalidMarker) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
            return;
        }
        Player player = event.getPlayer();
        try {
            if (!unseenBeforeSpawn(player)) return;
            // Legacy login callbacks can run before the saved dimension is final. Preflight conservatively;
            // the spawn callback below redirects only players whose actual saved dimension was affected.
            Location safe = findSafe();
            if (safe == null) event.disallow(PlayerLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
            else prepared.put(player.getUniqueId(), safe);
        } catch (RuntimeException failure) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, UNAVAILABLE);
            plugin.getLogger().log(Level.SEVERE, "Could not read the player's regeneration safety epoch.", failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoginDecision(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            prepared.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        try {
            if (!unseenBeforeSpawn(player) || !affected(event.getSpawnLocation().getWorld())) return;
            Location safe = prepared.get(player.getUniqueId());
            if (safe == null || !isSafe(safe)) safe = findSafe();
            if (safe == null) {
                blockedBeforeSpawn.add(player.getUniqueId());
                prepared.remove(player.getUniqueId());
                return;
            }
            event.setSpawnLocation(safe.clone());
            prepared.put(player.getUniqueId(), safe);
            redirected.add(player.getUniqueId());
        } catch (RuntimeException failure) {
            blockedBeforeSpawn.add(player.getUniqueId());
            prepared.remove(player.getUniqueId());
            plugin.getLogger().log(Level.SEVERE, "Could not prepare a safe regenerated-world spawn.", failure);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConfigurationSpawn(AsyncPlayerSpawnLocationEvent event) {
        // Paper's legacy spawn event has a temporary Player with no game connection. Use the actual
        // configuration connection to reject the rare case where safety changed after login preflight.
        UUID id = event.getConnection().getProfile().getId();
        if (invalidMarker || blockedBeforeSpawn.remove(id)) {
            event.getConnection().disconnect(Component.text(UNAVAILABLE));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            if (invalidMarker) {
                player.kickPlayer(UNAVAILABLE);
                return;
            }
            if (!unseen(player)) return;
            boolean relocate = redirected.contains(player.getUniqueId()) || affected(player.getWorld());
            if (relocate) {
                Location expected = prepared.get(player.getUniqueId());
                Location actual = player.getLocation();
                if (expected == null || actual.getWorld() != primary || actual.distanceSquared(expected) > 1.0
                        || !isSafe(actual)) {
                    expected = findSafe();
                    if (expected == null || !player.teleport(expected) || !isSafe(player.getLocation())) {
                        player.kickPlayer(UNAVAILABLE);
                        return;
                    }
                }
                player.setVelocity(new Vector());
                player.setFallDistance(0);
            }
            // Also clear a removed bed/forced spawn when the player's current dimension was not affected.
            Location respawn = player.getPotentialRespawnLocation();
            if (respawn != null && affected(respawn.getWorld())) player.setRespawnLocation(null, false);
            String old = player.getPersistentDataContainer().get(epochKey, PersistentDataType.STRING);
            player.getPersistentDataContainer().set(epochKey, PersistentDataType.STRING, completion.id());
            try {
                // Save the new position and epoch together; inventory, experience and ender chest are untouched.
                player.saveData();
            } catch (RuntimeException failure) {
                if (old == null) player.getPersistentDataContainer().remove(epochKey);
                else player.getPersistentDataContainer().set(epochKey, PersistentDataType.STRING, old);
                throw failure;
            }
            if (relocate) player.sendMessage("§cSurvival §8» §fワールド更新後の安全な場所へ移動しました。持ち物は保持されています。");
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Could not safely place player after world regeneration: "
                    + player.getUniqueId(), failure);
            player.kickPlayer(UNAVAILABLE);
        } finally {
            prepared.remove(player.getUniqueId());
            redirected.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prepared.remove(event.getPlayer().getUniqueId());
        redirected.remove(event.getPlayer().getUniqueId());
    }

    private boolean unseen(Player player) {
        return completion != null && completion.unseen(
                player.getPersistentDataContainer().get(epochKey, PersistentDataType.STRING));
    }

    private boolean unseenBeforeSpawn(Player player) {
        // In Paper 26.3 legacy login/spawn callbacks precede loading the live player's NBT/PDC.
        // Read the persisted offline view or every reconnect would appear to be an unseen epoch.
        return completion != null && completion.unseen(plugin.getServer().getOfflinePlayer(player.getUniqueId())
                .getPersistentDataContainer().get(epochKey, PersistentDataType.STRING));
    }

    private boolean affected(World world) {
        return world != null && completion != null && completion.affects(world.getKey().asString());
    }

    private Location findSafe() {
        try {
            if (cachedSafe != null && isSafe(cachedSafe)) return cachedSafe.clone();
            WorldBorder border = primary.getWorldBorder();
            double half = border.getSize() / 2.0 - 1.5;
            if (!Double.isFinite(half) || half < 0) return null;
            Location center = border.getCenter();
            Location spawn = primary.getSpawnLocation();
            double x = Math.max(center.getX() - half, Math.min(center.getX() + half, spawn.getX()));
            double z = Math.max(center.getZ() - half, Math.min(center.getZ() + half, spawn.getZ()));
            SafeSpawnSearch.Position position = SafeSpawnSearch.find((int) Math.floor(x), (int) Math.floor(z), 32,
                    (bx, bz) -> primary.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES),
                    candidate -> isSafe(new Location(primary, candidate.x() + 0.5, candidate.feetY(), candidate.z() + 0.5)))
                    .orElse(null);
            if (position == null) return null;
            cachedSafe = new Location(primary, position.x() + 0.5, position.feetY(), position.z() + 0.5);
            return cachedSafe.clone();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Safe spawn search failed after world regeneration.", failure);
            return null;
        }
    }

    private boolean isSafe(Location location) {
        if (location.getWorld() != primary || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())) return false;
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        if (y <= primary.getMinHeight() || y + 1 >= primary.getMaxHeight()) return false;
        WorldBorder border = primary.getWorldBorder();
        if (!border.isInside(location.clone().add(-0.31, 0, -0.31))
                || !border.isInside(location.clone().add(0.31, 0, 0.31))) return false;
        Block floor = primary.getBlockAt(x, y - 1, z);
        if (!floor.isSolid() || floor.isLiquid() || hazard(floor) || floor.getType().name().endsWith("_LEAVES")) return false;
        BoundingBox box = floor.getBoundingBox();
        if (Math.abs(box.getMaxY() - y) > 0.001 || box.getMinX() > x + 0.19 || box.getMaxX() < x + 0.81
                || box.getMinZ() > z + 0.19 || box.getMaxZ() < z + 0.81) return false;
        for (int dy = 0; dy < 2; dy++) {
            Block body = primary.getBlockAt(x, y + dy, z);
            if (!body.isPassable() || body.isLiquid() || hazard(body)) return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy < 2; dy++) {
                    if (hazard(primary.getBlockAt(x + dx, y + dy, z + dz))) return false;
                }
            }
        }
        return true;
    }

    private static boolean hazard(Block block) {
        return HAZARDS.contains(block.getType().name());
    }
}
