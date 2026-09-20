package com.ascendingmc.survival;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** Does not replace vanilla Survival inventories, hunger, deaths or player persistence. */
public final class AscendingSurvivalPlugin extends JavaPlugin implements Listener {
    private static final String PERMISSION = "ascending.survival.admin";
    private static final String PREFIX = "§cSurvival §8» §f";
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private final Map<UUID, Long> lastHub = new HashMap<>();
    private ManagedWorldPaths paths;
    private YamlConfiguration registry;
    private Path registryFile;
    private DeletionConfirmations confirmations;
    private NamespacedKey editModeKey;
    private String primaryName;
    private byte[] hubMessage;
    private BukkitTask sidebarTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            primaryName = getConfig().getString("primary-world", "survival");
            World primary = Bukkit.getWorld(primaryName);
            if (primary == null) {
                throw new IOException("Configured primary-world is not loaded: " + primaryName);
            }
            if (!primary.getKey().equals(NamespacedKey.minecraft("overworld"))) {
                throw new IOException("primary-world must be the real minecraft:overworld, not another dimension.");
            }
            paths = new ManagedWorldPaths(getServer().getWorldContainer().toPath(), primaryName,
                    primary.getWorldPath().toAbsolutePath().normalize().getParent());
            hubMessage = connectMessage(getConfig().getString("hub-server", "lobby"));
            registryFile = getDataFolder().toPath().resolve("managed-worlds.yml");
            registry = new YamlConfiguration();
            if (Files.exists(registryFile)) registry.load(registryFile.toFile());
            editModeKey = new NamespacedKey(this, "previous-game-mode");
            long lifetime = Math.max(10, Math.min(300,
                    getConfig().getLong("world-management.confirmation-seconds", 30)));
            confirmations = new DeletionConfirmations(System::currentTimeMillis, lifetime * 1000);
            loadManagedWorlds();
            for (World world : Bukkit.getWorlds()) applySettings(world);
            Objects.requireNonNull(getCommand("sworld")).setExecutor(this);
            Objects.requireNonNull(getCommand("sworld")).setTabCompleter(this);
            Objects.requireNonNull(getCommand("hub")).setExecutor(this);
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            getServer().getPluginManager().registerEvents(this, this);
            if (getConfig().getBoolean("scoreboard.enabled", true)) {
                long period = Math.max(2, Math.min(200, getConfig().getLong("scoreboard.update-ticks", 2)));
                sidebarTask = getServer().getScheduler().runTaskTimer(this, this::updateSidebars, 1, period);
            }
            for (Player player : Bukkit.getOnlinePlayers()) restoreEditMode(player);
            getLogger().info("AscendingSurvival 0.1.0 enabled.");
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Survival initialization failed; plugin disabled.", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (sidebarTask != null) sidebarTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (editModeKey != null) restoreEditMode(player);
            Sidebar sidebar = sidebars.get(player.getUniqueId());
            if (sidebar != null && player.getScoreboard() == sidebar.board) {
                player.setScoreboard(sidebar.previous);
            }
        }
        sidebars.clear();
        lastHub.clear();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // A crash during creative editing must not leave that player in creative on reconnect.
        restoreEditMode(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        restoreEditMode(player);
        sidebars.remove(player.getUniqueId());
        lastHub.remove(player.getUniqueId());
        confirmations.forget(player.getUniqueId().toString());
    }

    private void updateSidebars() {
        int online = Bukkit.getOnlinePlayers().size();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Sidebar sidebar = sidebars.computeIfAbsent(player.getUniqueId(), ignored -> new Sidebar(player));
            Location location = player.getLocation();
            List<String> lines = SidebarText.lines(online, player.getPing(), location.getX(), location.getY(), location.getZ());
            if (!lines.equals(sidebar.lines)) {
                for (String old : sidebar.lines) sidebar.board.resetScores(old);
                for (int i = 0; i < lines.size(); i++) sidebar.objective.getScore(lines.get(i)).setScore(lines.size() - i);
                sidebar.lines = lines;
            }
            if (player.getScoreboard() != sidebar.board) player.setScoreboard(sidebar.board);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("hub")) {
            if (!(sender instanceof Player player)) {
                error(sender, "ゲーム内から実行してください。");
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastHub.getOrDefault(player.getUniqueId(), 0L) < 2000) return true;
            lastHub.put(player.getUniqueId(), now);
            restoreEditMode(player);
            player.sendPluginMessage(this, "BungeeCord", hubMessage);
            message(player, "ロビーへ移動します。");
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            error(sender, "このコマンドは管理者専用です。");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "list" -> list(sender);
                case "create" -> create(sender, args);
                case "tp" -> teleport(sender, args);
                case "set" -> setting(sender, args);
                case "edit" -> edit(sender, args);
                case "delete" -> requestDelete(sender, args);
                case "confirm" -> confirmDelete(sender, args);
                case "restore" -> restore(sender, args);
                default -> help(sender);
            }
        } catch (IllegalArgumentException invalid) {
            error(sender, invalid.getMessage());
        } catch (IOException failure) {
            error(sender, "ワールド管理に失敗しました: " + failure.getMessage());
            getLogger().log(Level.WARNING, "World management failed for " + sender.getName(), failure);
        }
        return true;
    }

    private void help(CommandSender sender) {
        message(sender, "/sworld list | create <name> [normal|nether|end] [seed] | tp <name>");
        message(sender, "/sworld set <name> <difficulty|pvp|time|weather|spawn|border|gamerule> [value]");
        message(sender, "/sworld edit [on|off] | delete <name> | confirm <token> | restore <name>");
        message(sender, "削除はアーカイブへ移動します。初期ワールド・Nether・Endは削除できません。");
    }

    private void list(CommandSender sender) {
        message(sender, "ワールド (初期ワールドは protected):");
        for (String name : worldNames()) {
            World world = Bukkit.getWorld(name);
            String state = paths.isProtected(name) ? "protected" : registry.getString("worlds." + name + ".state", "UNKNOWN");
            message(sender, name + " §7[" + state + "]" + (world == null ? "" : " §fOnline: " + world.getPlayers().size()));
        }
    }

    private void create(CommandSender sender, String[] args) throws IOException {
        requireCount(args, 2, 4, "/sworld create <name> [normal|nether|end] [seed]");
        String name = args[1];
        ManagedWorldPaths.validateName(name);
        if (paths.isProtected(name) || worldNames().stream().anyMatch(existing -> existing.equalsIgnoreCase(name))
                || Bukkit.getWorld(name) != null || Files.exists(paths.world(name), LinkOption.NOFOLLOW_LINKS)
                || paths.legacyDirectoryExists(name)) {
            throw new IllegalArgumentException("その名前のワールドまたはディレクトリは既に存在します。");
        }
        World.Environment environment = args.length >= 3 ? environment(args[2]) : World.Environment.NORMAL;
        long seed = args.length == 4 ? WorldSettingValues.integer(args[3], Long.MIN_VALUE, Long.MAX_VALUE, "Seed") : new java.util.Random().nextLong();
        String key = "worlds." + name;
        registry.set(key + ".environment", environment.name());
        registry.set(key + ".seed", seed);
        registry.set(key + ".storage-path", paths.relativeWorldPath(name));
        registry.set(key + ".state", "CREATING");
        // Persist before generation, so an interrupted creation remains a known managed world.
        saveRegistry();
        message(sender, "ワールドを生成しています: " + name);
        World world = Bukkit.createWorld(worldCreator(name).environment(environment).seed(seed));
        if (world == null) throw new IOException("World creation was rejected; its managed record is retained.");
        paths.verifyActualWorldPath(name, world.getWorldPath());
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        world.save();
        registry.set(key + ".state", "ACTIVE");
        saveRegistry();
        message(sender, "作成しました: " + name + "。/sworld tp " + name);
    }

    private void teleport(CommandSender sender, String[] args) {
        requireCount(args, 2, 2, "/sworld tp <name>");
        Player player = requirePlayer(sender);
        World world = requireLoaded(args[1]);
        if (!player.teleport(world.getSpawnLocation())) throw new IllegalArgumentException("テレポートが拒否されました。");
        message(sender, "移動しました: " + world.getName());
    }

    private void setting(CommandSender sender, String[] args) throws IOException {
        requireCount(args, 3, 5, "/sworld set <name> <setting> [value]");
        World world = requireLoaded(args[1]);
        String property = args[2].toLowerCase(Locale.ROOT);
        String key = "settings." + world.getName() + ".";
        if (property.equals("spawn")) {
            requireCount(args, 3, 3, "/sworld set <name> spawn");
            Player player = requirePlayer(sender);
            if (player.getWorld() != world) throw new IllegalArgumentException("対象ワールド内で実行してください。");
            Location location = player.getLocation();
            world.setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getYaw());
        } else {
            if (args.length < 4) throw new IllegalArgumentException("設定値を指定してください。");
            if (!property.equals("gamerule") && args.length != 4) throw new IllegalArgumentException("設定値は1つだけ指定してください。");
            switch (property) {
                case "difficulty" -> {
                    Difficulty difficulty;
                    try { difficulty = Difficulty.valueOf(args[3].toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Difficulty: peaceful, easy, normal, hard"); }
                    world.setDifficulty(difficulty);
                    registry.set(key + "difficulty", difficulty.name());
                }
                case "pvp" -> {
                    boolean enabled = WorldSettingValues.bool(args[3]);
                    world.setPVP(enabled);
                    registry.set(key + "pvp", enabled);
                }
                case "time" -> world.setTime(WorldSettingValues.time(args[3]));
                case "weather" -> {
                    String weather = WorldSettingValues.weather(args[3]);
                    world.setStorm(!weather.equals("clear"));
                    world.setThundering(weather.equals("thunder"));
                    if (weather.equals("clear")) world.setClearWeatherDuration(12000);
                }
                case "border" -> world.getWorldBorder().setSize(WorldSettingValues.border(args[3]));
                case "gamerule" -> {
                    requireCount(args, 5, 5, "/sworld set <name> gamerule <rule> <value>");
                    setGameRule(world, args[3], args[4]);
                }
                default -> throw new IllegalArgumentException("設定: difficulty, pvp, time, weather, spawn, border, gamerule");
            }
        }
        saveRegistry();
        world.save();
        message(sender, world.getName() + " の " + property + " を変更しました。");
    }

    private static <T> void setTypedGameRule(World world, GameRule<T> rule, Object value) {
        if (!world.setGameRule(rule, rule.getType().cast(value))) throw new IllegalArgumentException("ゲームルールの変更が拒否されました。");
    }

    private static void setGameRule(World world, String name, String value) {
        GameRule<?> rule = GameRule.getByName(name);
        if (rule == null) throw new IllegalArgumentException("存在しないゲームルールです: " + name);
        Object parsed;
        if (rule.getType() == Boolean.class) parsed = WorldSettingValues.bool(value);
        else if (rule.getType() == Integer.class) parsed = (int) WorldSettingValues.integer(value, 0, Integer.MAX_VALUE, "Game rule value");
        else throw new IllegalArgumentException("このゲームルールの型は未対応です。");
        setTypedGameRule(world, rule, parsed);
    }

    private void edit(CommandSender sender, String[] args) {
        requireCount(args, 1, 2, "/sworld edit [on|off]");
        Player player = requirePlayer(sender);
        boolean current = player.getPersistentDataContainer().has(editModeKey, PersistentDataType.STRING);
        boolean enabled = args.length == 1 ? !current : switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            default -> throw new IllegalArgumentException("/sworld edit [on|off]");
        };
        if (enabled && !current) {
            player.getPersistentDataContainer().set(editModeKey, PersistentDataType.STRING, player.getGameMode().name());
            player.setGameMode(GameMode.CREATIVE);
            player.saveData();
        } else if (!enabled) restoreEditMode(player);
        message(sender, "ワールド編集モード: " + (enabled ? "ON (Creative)" : "OFF"));
    }

    private void restoreEditMode(Player player) {
        String previous = player.getPersistentDataContainer().get(editModeKey, PersistentDataType.STRING);
        if (previous == null) return;
        GameMode mode;
        try { mode = GameMode.valueOf(previous); }
        catch (IllegalArgumentException invalid) { mode = GameMode.SURVIVAL; }
        player.setGameMode(mode);
        player.getPersistentDataContainer().remove(editModeKey);
        player.saveData();
    }

    private void requestDelete(CommandSender sender, String[] args) throws IOException {
        requireCount(args, 2, 2, "/sworld delete <name>");
        String name = requireManaged(args[1], "ACTIVE");
        if (paths.isProtected(name)) throw new IllegalArgumentException("初期ワールド・Nether・Endは削除できません。");
        paths.world(name);
        String token = confirmations.request(administrator(sender), name);
        message(sender, name + " をアーカイブします。プレイヤーは初期ワールドへ移動します。");
        message(sender, "期限内に確認: §c/sworld confirm " + token + " §7(復元: /sworld restore " + name + ")");
    }

    private void confirmDelete(CommandSender sender, String[] args) throws IOException {
        requireCount(args, 2, 2, "/sworld confirm <token>");
        String name = confirmations.consume(administrator(sender), args[1]);
        if (name == null) throw new IllegalArgumentException("確認コードが違うか、有効期限が切れています。");
        name = requireManaged(name, "ACTIVE");
        if (paths.isProtected(name)) throw new IllegalArgumentException("初期ワールドは削除できません。");
        World fallback = Bukkit.getWorld(primaryName);
        if (fallback == null) throw new IOException("Primary world is unavailable; deletion cancelled.");
        if (!Files.isDirectory(paths.world(name))) throw new IOException("Managed dimension directory is missing; deletion cancelled.");
        World world = Bukkit.getWorld(name);
        if (world != null) {
            paths.verifyActualWorldPath(name, world.getWorldPath());
            for (Player player : List.copyOf(world.getPlayers())) {
                if (!player.teleport(fallback.getSpawnLocation())) {
                    throw new IOException("A player could not leave the world; deletion cancelled.");
                }
                message(player, "ワールド管理により初期ワールドへ移動しました。");
            }
            world.save();
            if (!Bukkit.unloadWorld(world, true)) throw new IOException("World unload was rejected; nothing was deleted.");
        }
        String directory = paths.newArchiveName(name);
        String key = "worlds." + name;
        registry.set(key + ".state", "DELETE_PENDING");
        registry.set(key + ".archive", directory);
        try {
            saveRegistry();
        } catch (IOException failure) {
            registry.set(key + ".state", "ACTIVE");
            registry.set(key + ".archive", null);
            loadManaged(name);
            throw failure;
        }
        try {
            paths.archiveWorld(name, directory);
            registry.set(key + ".state", "DELETED");
            saveRegistry();
        } catch (IOException failure) {
            registry.set(key + ".state", "DELETE_PENDING");
            recoverPending(name);
            if ("ACTIVE".equals(registry.getString(key + ".state"))) loadManaged(name);
            throw failure;
        }
        message(sender, "アーカイブしました: " + name + "。復元: /sworld restore " + name);
        getLogger().info(sender.getName() + " archived managed world " + name + " as " + directory);
    }

    private void restore(CommandSender sender, String[] args) throws IOException {
        requireCount(args, 2, 2, "/sworld restore <name>");
        String name = requireManaged(args[1], "DELETED");
        String key = "worlds." + name;
        String archive = registry.getString(key + ".archive");
        registry.set(key + ".state", "RESTORE_PENDING");
        try {
            saveRegistry();
        } catch (IOException failure) {
            registry.set(key + ".state", "DELETED");
            throw failure;
        }
        try {
            paths.restoreWorld(name, archive);
            registry.set(key + ".state", "ACTIVE");
            registry.set(key + ".archive", null);
            saveRegistry();
        } catch (IOException failure) {
            registry.set(key + ".archive", archive);
            registry.set(key + ".state", "RESTORE_PENDING");
            recoverPending(name);
            throw failure;
        }
        loadManaged(name);
        message(sender, "復元しました: " + name);
        getLogger().info(sender.getName() + " restored managed world " + name);
    }

    private void loadManagedWorlds() throws IOException {
        ConfigurationSection worlds = registry.getConfigurationSection("worlds");
        if (worlds == null) return;
        for (String name : worlds.getKeys(false)) {
            ManagedWorldPaths.validateName(name);
            if (paths.isProtected(name)) throw new IOException("Protected world must not appear in managed registry: " + name);
            verifyStorageBinding(name);
            String state = registry.getString("worlds." + name + ".state");
            if ("DELETE_PENDING".equals(state) || "RESTORE_PENDING".equals(state)) {
                recoverPending(name);
                state = registry.getString("worlds." + name + ".state");
            }
            if ("ACTIVE".equals(state)) loadManaged(name);
            else if ("CREATING".equals(state)) {
                loadManaged(name);
                registry.set("worlds." + name + ".state", "ACTIVE");
                saveRegistry();
            }
            else if ("DELETED".equals(state)) paths.archive(registry.getString("worlds." + name + ".archive"));
            else throw new IOException("Unknown managed world state: " + name);
        }
    }

    private World loadManaged(String name) throws IOException {
        verifyStorageBinding(name);
        Path directory = paths.world(name);
        String key = "worlds." + name;
        if (!Files.isDirectory(directory) && !"CREATING".equals(registry.getString(key + ".state"))) {
            throw new IOException("Managed world directory is missing; refusing to generate a replacement: " + name);
        }
        World.Environment environment = World.Environment.valueOf(registry.getString(key + ".environment", "NORMAL"));
        World world = Bukkit.getWorld(name);
        if (world == null) world = Bukkit.createWorld(worldCreator(name).environment(environment).seed(registry.getLong(key + ".seed")));
        if (world == null) throw new IOException("Cannot load managed world: " + name);
        paths.verifyActualWorldPath(name, world.getWorldPath());
        applySettings(world);
        return world;
    }

    private void verifyStorageBinding(String name) throws IOException {
        String key = "worlds." + name + ".storage-path";
        String expected = paths.relativeWorldPath(name);
        String registered = registry.getString(key);
        if (registered != null && !registered.equals(expected)) {
            throw new IOException("Managed dimension storage path does not match Paper's current layout: " + name);
        }
        if (registered == null) {
            registry.set(key, expected);
            saveRegistry();
        }
    }

    private static WorldCreator worldCreator(String name) {
        return ManagedWorldCreator.create(name);
    }

    private void recoverPending(String name) throws IOException {
        String key = "worlds." + name;
        try {
            String state = WorldRecovery.finishPending(registry.getString(key + ".state", "DELETE_PENDING"),
                    Files.isDirectory(paths.world(name)), Files.isDirectory(paths.archive(registry.getString(key + ".archive"))));
            registry.set(key + ".state", state);
            if (state.equals("ACTIVE")) registry.set(key + ".archive", null);
            saveRegistry();
        } catch (IllegalStateException ambiguous) {
            throw new IOException("Cannot safely recover world " + name + ": " + ambiguous.getMessage(), ambiguous);
        }
    }

    private void applySettings(World world) {
        String key = "settings." + world.getName() + ".";
        if (registry.contains(key + "difficulty")) world.setDifficulty(Difficulty.valueOf(registry.getString(key + "difficulty")));
        if (registry.contains(key + "pvp")) world.setPVP(registry.getBoolean(key + "pvp"));
    }

    private void saveRegistry() throws IOException {
        Files.createDirectories(registryFile.getParent());
        Path temporary = Files.createTempFile(registryFile.getParent(), "managed-worlds-", ".tmp");
        try {
            registry.save(temporary.toFile());
            try { Files.move(temporary, registryFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, registryFile, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String requireManaged(String name, String state) {
        ManagedWorldPaths.validateName(name);
        if (paths.isProtected(name)) throw new IllegalArgumentException("初期ワールド・Nether・Endは削除できません。");
        if (!state.equals(registry.getString("worlds." + name + ".state"))) {
            throw new IllegalArgumentException("対象の管理ワールドが見つからないか、状態が異なります: " + name);
        }
        return name;
    }

    private World requireLoaded(String name) {
        ManagedWorldPaths.validateName(name);
        if (!paths.isProtected(name) && !"ACTIVE".equals(registry.getString("worlds." + name + ".state"))) {
            throw new IllegalArgumentException("管理対象でないワールドです: " + name);
        }
        World world = Bukkit.getWorld(name);
        if (world == null) throw new IllegalArgumentException("ワールドが読み込まれていません: " + name);
        return world;
    }

    private List<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) if (paths.isProtected(world.getName())) names.add(world.getName());
        ConfigurationSection worlds = registry.getConfigurationSection("worlds");
        if (worlds != null) names.addAll(worlds.getKeys(false));
        return names;
    }

    private static World.Environment environment(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "normal" -> World.Environment.NORMAL;
            case "nether" -> World.Environment.NETHER;
            case "end" -> World.Environment.THE_END;
            default -> throw new IllegalArgumentException("Environment: normal, nether, end");
        };
    }

    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        throw new IllegalArgumentException("ゲーム内から実行してください。");
    }

    private static String administrator(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console:" + sender.getName();
    }

    private static void requireCount(String[] args, int minimum, int maximum, String usage) {
        if (args.length < minimum || args.length > maximum) throw new IllegalArgumentException(usage);
    }

    private static void message(CommandSender sender, String text) { sender.sendMessage(PREFIX + text); }
    private static void error(CommandSender sender, String text) { sender.sendMessage(PREFIX + "§c" + text); }

    private static byte[] connectMessage(String server) throws IOException {
        if (server == null || !server.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid hub-server.");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeUTF("Connect");
            output.writeUTF(server);
        }
        return buffer.toByteArray();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION) || !command.getName().equalsIgnoreCase("sworld")) return List.of();
        List<String> options = List.of();
        if (args.length == 1) options = List.of("list", "create", "tp", "set", "edit", "delete", "confirm", "restore");
        else if (args.length == 2 && List.of("tp", "set", "delete", "restore").contains(args[0].toLowerCase(Locale.ROOT))) options = worldNames();
        else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) options = List.of("on", "off");
        else if (args.length == 3 && args[0].equalsIgnoreCase("create")) options = List.of("normal", "nether", "end");
        else if (args.length == 3 && args[0].equalsIgnoreCase("set")) options = List.of("difficulty", "pvp", "time", "weather", "spawn", "border", "gamerule");
        else if (args.length == 4 && args[0].equalsIgnoreCase("set")) options = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "difficulty" -> List.of("peaceful", "easy", "normal", "hard");
            case "pvp" -> List.of("true", "false");
            case "time" -> List.of("day", "night");
            case "weather" -> List.of("clear", "rain", "thunder");
            case "gamerule" -> Arrays.stream(GameRule.values()).map(GameRule::getName).toList();
            default -> List.of();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static final class Sidebar {
        private final Scoreboard previous;
        private final Scoreboard board;
        private final Objective objective;
        private List<String> lines = List.of();

        private Sidebar(Player player) {
            previous = player.getScoreboard();
            board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
            objective = board.registerNewObjective("survival", "dummy", "§c§lSurvival");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(board);
        }
    }
}
