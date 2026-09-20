package com.poppy.lobby;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Separate lobby backend, or a deliberately minimal /hub bridge on the PvP backend. */
public final class PoppyLobbyPlugin extends JavaPlugin implements Listener {
    private static final String CHANNEL = "BungeeCord";
    private static final String BUILD_PERMISSION = "poppylobby.build";
    private static final int PRACTICE_SLOT = 13;
    private final Set<UUID> builders = new HashSet<UUID>();
    private final Map<UUID, Long> lastConnect = new HashMap<UUID, Long>();
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<UUID, Scoreboard>();
    private boolean lobbyMode;
    private String worldName;
    private byte[] practiceMessage;
    private byte[] hubMessage;
    private BukkitTask scoreboardTask;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        lobbyMode = getConfig().getBoolean("lobby-server", true);
        worldName = getConfig().getString("world-name", "lobby");
        try {
            hubMessage = ProxyConnectMessage.encode(getConfig().getString("hub-server", "lobby"));
            if (lobbyMode) {
                practiceMessage = ProxyConnectMessage.encode(getConfig().getString("practice-server", "pvp"));
            }
        } catch (IllegalArgumentException invalid) {
            getLogger().severe(invalid.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        if (!lobbyMode) {
            getLogger().info("PvP bridge mode: only /hub -> proxy lobby. No gameplay listeners or tasks are enabled.");
            return;
        }
        registerLobbyCommand("pvp", Collections.<String>emptyList(), "Connect to Practice");
        registerLobbyCommand("lobby", Arrays.asList("spawn"), "Return to the lobby spawn");
        registerLobbyCommand("lobbybuild", Collections.<String>emptyList(), "Toggle protected lobby editing");
        getServer().getPluginManager().registerEvents(this, this);
        for (World world : getServer().getWorlds()) {
            configureWorld(world);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            preparePlayer(player);
        }
        scoreboardTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (inLobby(player.getWorld())) {
                        updateScoreboard(player);
                    }
                }
            }
        }, 20L, 20L);
        getLogger().info("Standalone lobby enabled. Generator: PoppyLobby; world: " + worldName);
    }

    private void registerLobbyCommand(String name, java.util.List<String> aliases, String description) {
        Command command = new Command(name, description, "/" + name, aliases) {
            @Override public boolean execute(CommandSender sender, String label, String[] args) {
                if (!isEnabled()) {
                    return false;
                }
                return PoppyLobbyPlugin.this.onCommand(sender, this, label, args);
            }
        };
        if ("lobbybuild".equals(name)) {
            command.setPermission(BUILD_PERMISSION);
        }
        ((CraftServer) getServer()).getCommandMap().register("poppylobby", command);
    }

    @Override
    public void onDisable() {
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard owned = scoreboards.get(player.getUniqueId());
            if (owned != null && player.getScoreboard() == owned) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        builders.clear();
        lastConnect.clear();
        scoreboards.clear();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String requestedWorld, String id) {
        return getConfig().getBoolean("lobby-server", true)
                && requestedWorld.equals(getConfig().getString("world-name", "lobby"))
                ? new LobbyGenerator() : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is available to players only.");
            return true;
        }
        Player player = (Player) sender;
        String name = command.getName();
        if ("hub".equalsIgnoreCase(name)) {
            if (lobbyMode) {
                returnToSpawn(player);
            } else {
                connect(player, hubMessage, "ロビー");
            }
            return true;
        }
        if (!lobbyMode) {
            return false;
        }
        if ("lobby".equalsIgnoreCase(name)) {
            returnToSpawn(player);
        } else if ("pvp".equalsIgnoreCase(name)) {
            connect(player, practiceMessage, "Practice");
        } else if ("lobbybuild".equalsIgnoreCase(name)) {
            if (!player.hasPermission(BUILD_PERMISSION)) {
                player.sendMessage(color("&cこのコマンドを使用する権限がありません。"));
            } else if (!inLobby(player.getWorld())) {
                player.sendMessage(color("&cロビーワールド内で使用してください。"));
            } else if (builders.remove(player.getUniqueId())) {
                preparePlayer(player);
                player.sendMessage(color("&7ロビー編集: &cOFF"));
            } else {
                builders.add(player.getUniqueId());
                clearInventory(player);
                player.setGameMode(GameMode.CREATIVE);
                player.sendMessage(color("&7ロビー編集: &aON &7（もう一度 /lobbybuild で終了）"));
            }
        } else {
            return false;
        }
        return true;
    }

    private void connect(Player player, byte[] message, String destination) {
        long now = System.nanoTime();
        // Bridge mode intentionally has no quit listener or scheduler; prune here instead.
        java.util.Iterator<Map.Entry<UUID, Long>> requests = lastConnect.entrySet().iterator();
        while (requests.hasNext()) {
            if (now - requests.next().getValue().longValue() >= 2_000_000_000L) {
                requests.remove();
            }
        }
        Long previous = lastConnect.get(player.getUniqueId());
        if (previous != null && now - previous.longValue() < 2_000_000_000L) {
            return;
        }
        lastConnect.put(player.getUniqueId(), now);
        player.closeInventory();
        player.sendPluginMessage(this, CHANNEL, message);
        player.sendMessage(color("&c" + destination + " &7へ接続しています…"));
    }

    private boolean inLobby(World world) {
        return lobbyMode && world != null && world.getName().equals(worldName);
    }

    private boolean editing(Player player) {
        return inLobby(player.getWorld()) && builders.contains(player.getUniqueId())
                && player.hasPermission(BUILD_PERMISSION);
    }

    private Location spawn() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, 0.5, LobbyLayout.SPAWN_Y, 8.5, 180.0F, 0.0F);
    }

    private void configureWorld(World world) {
        if (!inLobby(world)) {
            return;
        }
        world.setSpawnLocation(0, LobbyLayout.SPAWN_Y, 8);
        world.setPVP(false);
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(6000L);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("keepInventory", "true");
    }

    private void preparePlayer(Player player) {
        Location spawn = spawn();
        if (spawn == null) {
            getLogger().warning("Lobby world is not loaded: " + worldName);
            return;
        }
        builders.remove(player.getUniqueId());
        player.closeInventory();
        clearInventory(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0.0F);
        player.setFireTicks(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setExp(0.0F);
        player.setLevel(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.getInventory().setItem(0, item(Material.COMPASS, "&c&lサーバー選択 / Servers", "&7右クリックでサーバーを選択"));
        player.getInventory().setHeldItemSlot(0);
        player.teleport(spawn);
        updateScoreboard(player);
    }

    private void clearInventory(Player player) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    private void returnToSpawn(Player player) {
        Location spawn = spawn();
        if (spawn == null) {
            player.sendMessage(color("&cロビーワールドの読み込みが完了していません。"));
            return;
        }
        player.setFallDistance(0.0F);
        player.teleport(spawn);
    }

    private void openSelector(Player player) {
        SelectorHolder holder = new SelectorHolder();
        holder.inventory = Bukkit.createInventory(holder, 27, color("&8Server Selector"));
        ItemStack filler = item(Material.STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < holder.inventory.getSize(); slot++) {
            holder.inventory.setItem(slot, filler);
        }
        holder.inventory.setItem(PRACTICE_SLOT, item(Material.DIAMOND_SWORD, "&c&lPractice",
                "&7PvP / Bot / Kit / Queue", "", "&eクリックして参加"));
        player.openInventory(holder.inventory);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        java.util.List<String> lines = new java.util.ArrayList<String>();
        for (String line : lore) {
            lines.add(color(line));
        }
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private void updateScoreboard(Player player) {
        Scoreboard board = scoreboards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective("poppy_lobby", "dummy");
            String title = color(getConfig().getString("scoreboard-title", "&c&lAscendingMC"));
            objective.setDisplayName(title.length() > 32 ? title.substring(0, 32) : title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.getScore(color("&8&m--------------------")).setScore(4);
            objective.getScore(" ").setScore(3);
            Team online = board.registerNewTeam("online");
            online.setPrefix(color("&fOnline: &c"));
            online.addEntry(color("&r"));
            objective.getScore(color("&r")).setScore(2);
            objective.getScore(color("&8&m--------------------&r")).setScore(1);
            scoreboards.put(player.getUniqueId(), board);
        }
        board.getTeam("online").setSuffix(Integer.toString(Bukkit.getOnlinePlayers().size()));
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    @EventHandler public void onWorldLoad(WorldLoadEvent event) { configureWorld(event.getWorld()); }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        final Player player = event.getPlayer();
        preparePlayer(player);
        player.sendMessage(color(getConfig().getString("welcome-message", "&cWelcome to AscendingMC")));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        UUID id = event.getPlayer().getUniqueId();
        builders.remove(id);
        lastConnect.remove(id);
        scoreboards.remove(id);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = spawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (inLobby(event.getPlayer().getWorld()) && !editing(event.getPlayer()) && event.getTo() != null
                && LobbyLayout.needsRescue(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ())) {
            Location spawn = spawn();
            if (spawn != null) {
                event.setTo(spawn);
                event.getPlayer().setFallDistance(0.0F);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!inLobby(event.getPlayer().getWorld())) {
            return;
        }
        if (event.getItem() != null && event.getItem().getType() == Material.COMPASS
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            openSelector(event.getPlayer());
        } else if (!editing(event.getPlayer())) {
            event.setCancelled(true);
        }
        if (event.isCancelled()) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof SelectorHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == PRACTICE_SLOT && event.isLeftClick() && !event.isShiftClick()) {
                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @Override public void run() {
                        if (player.isOnline() && inLobby(player.getWorld())) {
                            connect(player, practiceMessage, "Practice");
                        }
                    }
                });
            }
        } else if (inLobby(player.getWorld()) && !editing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SelectorHolder
                || event.getWhoClicked() instanceof Player && inLobby(event.getWhoClicked().getWorld())
                && !editing((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent event) {
        if (inLobby(event.getPlayer().getWorld()) && !editing(event.getPlayer())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPickup(PlayerPickupItemEvent event) {
        if (inLobby(event.getPlayer().getWorld()) && !editing(event.getPlayer())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBreak(BlockBreakEvent event) {
        if (inLobby(event.getBlock().getWorld()) && !editing(event.getPlayer())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPlace(BlockPlaceEvent event) {
        if (inLobby(event.getBlock().getWorld()) && !editing(event.getPlayer())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (inLobby(event.getPlayer().getWorld()) && !editing(event.getPlayer())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent event) {
        if (inLobby(event.getEntity().getWorld())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onHunger(FoodLevelChangeEvent event) {
        if (inLobby(event.getEntity().getWorld())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (inLobby(event.getLocation().getWorld())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onExplode(EntityExplodeEvent event) {
        if (inLobby(event.getLocation().getWorld())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBurn(BlockBurnEvent event) {
        if (inLobby(event.getBlock().getWorld())) { event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onIgnite(BlockIgniteEvent event) {
        if (inLobby(event.getBlock().getWorld()) && (event.getPlayer() == null || !editing(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onWeather(WeatherChangeEvent event) {
        if (inLobby(event.getWorld()) && event.toWeatherState()) { event.setCancelled(true); }
    }

    private static final class SelectorHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
