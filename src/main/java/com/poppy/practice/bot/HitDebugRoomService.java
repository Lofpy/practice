package com.poppy.practice.bot;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitLayoutService;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.service.DamageDebugService;
import com.poppy.practice.service.HitDebugRoomLayoutService;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.MatchScoreboardService;
import com.poppy.practice.service.PlayerResetService;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns the isolated room and its four deterministic diagnostic fake players. */
public final class HitDebugRoomService {
    private static final String KIT_ID = "nodebuff";
    private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 10;
    private static final int DEFAULT_PEARL_COOLDOWN_TICKS = 60;
    private static final double DEFAULT_ATTACK_RANGE = 3.0D;
    private static final double PASSIVE_RESET_DISTANCE_SQUARED = 12.0D * 12.0D;

    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final QueueManager queueManager;
    private final PlayerResetService resetService;
    private final LobbyService lobbyService;
    private final KitLayoutService kitLayoutService;
    private final DamageDebugService damageDebugService;
    private final MatchScoreboardService scoreboardService;
    private final Kit kit;
    private final Set<UUID> participants = new HashSet<UUID>();
    private final Map<BotType, DebugBot> bots = new EnumMap<BotType, DebugBot>(BotType.class);
    private final List<BotEnderPearl> activePearls = new ArrayList<BotEnderPearl>();
    private final BukkitTask tickTask;
    private double attackRange;
    private int attackIntervalTicks;
    private int pearlCooldownTicks;
    private boolean shuttingDown;

    public HitDebugRoomService(PracticePlugin plugin, ProfileManager profileManager,
                               QueueManager queueManager, PlayerResetService resetService,
                               LobbyService lobbyService, KitManager kitManager,
                               KitLayoutService kitLayoutService,
                               DamageDebugService damageDebugService,
                               MatchScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.queueManager = queueManager;
        this.resetService = resetService;
        this.lobbyService = lobbyService;
        this.kitLayoutService = kitLayoutService;
        this.damageDebugService = damageDebugService;
        this.scoreboardService = scoreboardService;
        this.kit = kitManager.get(KIT_ID);
        if (kit == null) {
            throw new IllegalStateException("NoDebuff kit is required by the hit debug room");
        }
        reloadConfiguration();
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 1L, 1L);
    }

    public void reloadConfiguration() {
        attackRange = clamp(plugin.getConfig().getDouble(
                "hit-debug-room.attack-bot.range", DEFAULT_ATTACK_RANGE), 1.0D, 6.0D);
        attackIntervalTicks = clamp(plugin.getConfig().getInt(
                "hit-debug-room.attack-bot.interval-ticks",
                DEFAULT_ATTACK_INTERVAL_TICKS), 1, 100);
        pearlCooldownTicks = clamp(plugin.getConfig().getInt(
                "hit-debug-room.pearl-bot.cooldown-ticks",
                DEFAULT_PEARL_COOLDOWN_TICKS), 10, 1200);
    }

    public boolean enter(Player player) {
        if (player == null || !player.isOnline() || shuttingDown) {
            return false;
        }
        PlayerProfile profile = profileManager.getOrCreate(player.getUniqueId());
        if (profile.getState() == PlayerState.QUEUE) {
            queueManager.leave(player, false);
        }
        if (profile.getState() != PlayerState.LOBBY
                && profile.getState() != PlayerState.DEBUG) {
            player.sendMessage(ChatColor.RED
                    + "You cannot enter the hit debug room during a match.");
            return false;
        }
        World world = Bukkit.getWorld(HitDebugRoomLayoutService.WORLD_NAME);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "The hit debug room world is unavailable.");
            return false;
        }

        loadRoomChunks(world);
        ensureBots(player, world);
        showProfiles(player);
        participants.add(player.getUniqueId());
        profile.setQueuedKitId(null);
        profile.setState(PlayerState.DEBUG);
        scoreboardService.clear(player);
        resetService.reset(player, GameMode.SURVIVAL);
        kitLayoutService.applyLayout(player, kit);
        player.teleport(HitDebugRoomLayoutService.playerSpawn(world));
        player.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.GOLD
                + "Hit Debug Room" + ChatColor.DARK_GRAY + " ---");
        player.sendMessage(ChatColor.GREEN + "PassiveDummy" + ChatColor.GRAY
                + ": passive target with normal knockback.");
        player.sendMessage(ChatColor.RED + "NoSprintGuard" + ChatColor.GRAY
                + ": fixed target; always attacks without sprinting.");
        player.sendMessage(ChatColor.YELLOW + "SprintGuard" + ChatColor.GRAY
                + ": fixed target; every hit is a sprint attack.");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "PearlTester" + ChatColor.GRAY
                + ": throws an ender pearl every " + format(pearlCooldownTicks / 20.0D)
                + " seconds.");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/hitdebug reset"
                + ChatColor.GRAY + " to restore bots or " + ChatColor.WHITE
                + "/lobby" + ChatColor.GRAY + " to return. Attack range: "
                + format(attackRange) + " blocks.");
        scheduleTabHide(player);
        return true;
    }

    public void leave(Player player) {
        if (player == null) return;
        participants.remove(player.getUniqueId());
        hideProfiles(player);
        if (player.isOnline()) {
            lobbyService.sendToLobby(player);
            player.sendMessage(ChatColor.YELLOW + "You left the hit debug room.");
        }
        if (participants.isEmpty()) removeBots();
    }

    public boolean resetBots(Player requester) {
        if (requester == null || !isParticipant(requester)) {
            return false;
        }
        World world = Bukkit.getWorld(HitDebugRoomLayoutService.WORLD_NAME);
        if (world == null) return false;
        clearPearls();
        resetBot(BotType.PASSIVE, HitDebugRoomLayoutService.passiveBotSpawn(world));
        resetBot(BotType.NORMAL_ATTACK,
                HitDebugRoomLayoutService.normalAttackBotSpawn(world));
        resetBot(BotType.SPRINT_ATTACK,
                HitDebugRoomLayoutService.sprintAttackBotSpawn(world));
        resetBot(BotType.PEARL, HitDebugRoomLayoutService.pearlBotFirstSpawn(world));
        DebugBot normalAttack = bots.get(BotType.NORMAL_ATTACK);
        if (normalAttack != null) normalAttack.cooldownTicks = 0;
        DebugBot sprintAttack = bots.get(BotType.SPRINT_ATTACK);
        if (sprintAttack != null) sprintAttack.cooldownTicks = 0;
        DebugBot pearl = bots.get(BotType.PEARL);
        if (pearl != null) {
            pearl.cooldownTicks = pearlCooldownTicks;
            pearl.pearlAtSecondStation = false;
            pearl.switchBackTicks = 0;
        }
        requester.sendMessage(ChatColor.GREEN + "All hit debug bots were reset.");
        return true;
    }

    public boolean isParticipant(Player player) {
        return player != null && participants.contains(player.getUniqueId());
    }

    public boolean isDebugBot(Entity entity) {
        return entity != null && findBot(entity.getUniqueId()) != null;
    }

    /** Returns true when normal match damage routing must stop. */
    public boolean handleDamage(EntityDamageEvent event) {
        if (event == null) return false;
        DebugBot victimBot = findBot(event.getEntity().getUniqueId());
        if (victimBot != null) {
            Player attacker = event instanceof EntityDamageByEntityEvent
                    ? resolvePlayer(((EntityDamageByEntityEvent) event).getDamager()) : null;
            if (attacker == null || !isParticipant(attacker)) {
                event.setCancelled(true);
                return true;
            }
            if (event.getFinalDamage() >= victimBot.npc.getPlayer().getHealth()) {
                event.setDamage(0.0D);
            }
            if (!event.isCancelled() && event.getFinalDamage() > 0.0D) {
                damageDebugService.reportDebugRoom(victimBot.npc.getPlayer(),
                        event.getFinalDamage(), attacker);
            }
            return true;
        }

        if (!(event.getEntity() instanceof Player)
                || !isParticipant((Player) event.getEntity())) {
            return false;
        }
        Player victim = (Player) event.getEntity();
        DebugBot attackerBot = event instanceof EntityDamageByEntityEvent
                ? resolveDebugBot(((EntityDamageByEntityEvent) event).getDamager()) : null;
        if (attackerBot == null || !attackerBot.type.isAttackBot()) {
            event.setCancelled(true);
            return true;
        }
        // Do not cancel melee damage: NMS only applies the real attack knockback
        // when damageEntity succeeds. Cap the hit below lethal, then restore health
        // after the attack has completed so the player remains functionally invincible.
        victim.setHealth(victim.getMaxHealth());
        event.setDamage(nonLethalRawDamage(event.getDamage(), victim.getMaxHealth()));
        if (!event.isCancelled() && event.getFinalDamage() > 0.0D) {
            damageDebugService.reportDebugRoom(victim, event.getFinalDamage(), victim);
        }
        restoreParticipantHealth(victim);
        return true;
    }

    public void handleQuit(Player player) {
        if (player == null) return;
        participants.remove(player.getUniqueId());
        if (participants.isEmpty()) removeBots();
    }

    public void shutdown() {
        shuttingDown = true;
        tickTask.cancel();
        clearPearls();
        removeBots();
        participants.clear();
    }

    private void tick() {
        pruneParticipants();
        if (participants.isEmpty()) {
            if (!bots.isEmpty()) removeBots();
            return;
        }
        World world = Bukkit.getWorld(HitDebugRoomLayoutService.WORLD_NAME);
        if (world == null) return;
        Player viewer = firstParticipant();
        if (viewer == null) return;
        if (!hasAllValidBots()) ensureBots(viewer, world);

        tickPassive(world);
        tickAttack(world, BotType.NORMAL_ATTACK,
                HitDebugRoomLayoutService.normalAttackBotSpawn(world), false);
        tickAttack(world, BotType.SPRINT_ATTACK,
                HitDebugRoomLayoutService.sprintAttackBotSpawn(world), true);
        tickPearl(world);
        for (Iterator<BotEnderPearl> iterator = activePearls.iterator(); iterator.hasNext();) {
            if (iterator.next().dead) iterator.remove();
        }
    }

    private void tickPassive(World world) {
        DebugBot passive = bots.get(BotType.PASSIVE);
        if (passive == null || !passive.npc.isValid()) return;
        EntityPlayer handle = passive.npc.getHandle();
        passive.npc.applyPendingVelocity();
        restoreBot(passive);
        Location anchor = HitDebugRoomLayoutService.passiveBotSpawn(world);
        if (distanceSquared(handle, anchor) > PASSIVE_RESET_DISTANCE_SQUARED
                || handle.locY < HitDebugRoomLayoutService.SURFACE_Y - 2.0D) {
            place(handle, anchor);
            clearMotion(handle);
        }
        handle.aZ = 0.0F;
        handle.ba = 0.0F;
        handle.l();
    }

    private void tickAttack(World world, BotType type, Location anchor,
                            boolean sprintAttack) {
        DebugBot attack = bots.get(type);
        if (attack == null || !attack.npc.isValid()) return;
        EntityPlayer handle = attack.npc.getHandle();
        attack.npc.discardPendingVelocity();
        anchor.setYaw(handle.yaw);
        anchor.setPitch(handle.pitch);
        place(handle, anchor);
        clearMotion(handle);
        handle.setSprinting(sprintAttack);
        restoreBot(attack);
        Player target = nearestParticipant(handle.locX, handle.locY, handle.locZ);
        if (target != null) {
            EntityPlayer targetHandle = ((CraftPlayer) target).getHandle();
            face(handle, targetHandle.locX, targetHandle.locY + targetHandle.getHeadHeight(),
                    targetHandle.locZ);
            double distanceSquared = horizontalDistanceSquared(handle, targetHandle);
            if (distanceSquared <= attackRange * attackRange
                    && Math.abs(targetHandle.locY - handle.locY) <= 2.4D
                    && handle.hasLineOfSight(targetHandle)) {
                handle.bw();
                if (attack.cooldownTicks <= 0) {
                    // Reassert immediately before each hit because NMS clears sprint
                    // after a successful sprint attack.
                    handle.setSprinting(sprintAttack);
                    handle.setExtraKnockback(sprintAttack);
                    handle.attack(targetHandle);
                    attack.cooldownTicks = attackIntervalTicks;
                }
            }
        }
        if (attack.cooldownTicks > 0) attack.cooldownTicks--;
        handle.aZ = 0.0F;
        handle.ba = 0.0F;
        handle.l();
        clearMotion(handle);
    }

    private void tickPearl(World world) {
        DebugBot pearl = bots.get(BotType.PEARL);
        if (pearl == null || !pearl.npc.isValid()) return;
        EntityPlayer handle = pearl.npc.getHandle();
        pearl.npc.discardPendingVelocity();
        restoreBot(pearl);
        clearMotion(handle);
        handle.aZ = 0.0F;
        handle.ba = 0.0F;
        if (pearl.switchBackTicks > 0 && --pearl.switchBackTicks == 0) {
            handle.inventory.itemInHandIndex = 0;
        }
        if (pearl.cooldownTicks > 0) {
            pearl.cooldownTicks--;
        } else {
            Location target = pearl.pearlAtSecondStation
                    ? HitDebugRoomLayoutService.pearlBotFirstSpawn(world)
                    : HitDebugRoomLayoutService.pearlBotSecondTarget(world);
            if (launchPearl(pearl, target)) {
                pearl.pearlAtSecondStation = !pearl.pearlAtSecondStation;
                pearl.switchBackTicks = 5;
            }
            pearl.cooldownTicks = pearlCooldownTicks;
        }
        handle.l();
    }

    private boolean launchPearl(DebugBot debugBot, Location target) {
        EntityPlayer bot = debugBot.npc.getHandle();
        double startX = bot.locX;
        double startY = bot.locY + bot.getHeadHeight() - 0.1D;
        double startZ = bot.locZ;
        double deltaX = target.getX() - startX;
        double deltaZ = target.getZ() - startZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontal < 1.0D) return false;
        double deltaY = target.getY() - startY + horizontal * 0.16D;
        bot.inventory.setItem(1, new ItemStack(Items.ENDER_PEARL, 16));
        bot.inventory.itemInHandIndex = 1;
        face(bot, target.getX(), target.getY() + 0.15D, target.getZ());
        bot.bw();
        BotEnderPearl projectile = new BotEnderPearl(bot.world, bot,
                target.getX(), target.getY(), target.getZ());
        projectile.setPosition(startX, startY, startZ);
        projectile.shoot(deltaX, deltaY, deltaZ, 1.5F, 0.0F);
        if (!bot.world.addEntity(projectile)) return false;
        activePearls.add(projectile);
        return true;
    }

    private void ensureBots(Player viewer, World world) {
        if (hasAllValidBots()) return;
        removeBots();
        BotSettings settings = BotSettings.load(plugin);
        bots.put(BotType.PASSIVE, new DebugBot(BotType.PASSIVE,
                BotNpc.spawn(viewer, HitDebugRoomLayoutService.passiveBotSpawn(world),
                        kit, settings, "PassiveDummy", false)));
        bots.put(BotType.NORMAL_ATTACK, new DebugBot(BotType.NORMAL_ATTACK,
                BotNpc.spawn(viewer, HitDebugRoomLayoutService.normalAttackBotSpawn(world),
                        kit, settings, "NoSprintGuard", true)));
        bots.put(BotType.SPRINT_ATTACK, new DebugBot(BotType.SPRINT_ATTACK,
                BotNpc.spawn(viewer, HitDebugRoomLayoutService.sprintAttackBotSpawn(world),
                        kit, settings, "SprintGuard", true)));
        DebugBot pearl = new DebugBot(BotType.PEARL,
                BotNpc.spawn(viewer, HitDebugRoomLayoutService.pearlBotFirstSpawn(world),
                        kit, settings, "PearlTester", true));
        pearl.cooldownTicks = pearlCooldownTicks;
        bots.put(BotType.PEARL, pearl);
    }

    private void resetBot(BotType type, Location location) {
        DebugBot debugBot = bots.get(type);
        if (debugBot == null || !debugBot.npc.isValid()) return;
        EntityPlayer handle = debugBot.npc.getHandle();
        debugBot.npc.discardPendingVelocity();
        place(handle, location);
        clearMotion(handle);
        handle.invulnerableTicks = 0;
        handle.noDamageTicks = 0;
        handle.setHealth(handle.getMaxHealth());
        handle.inventory.itemInHandIndex = 0;
    }

    private void restoreBot(DebugBot bot) {
        EntityPlayer handle = bot.npc.getHandle();
        handle.setHealth(handle.getMaxHealth());
        handle.fireTicks = 0;
        handle.fallDistance = 0.0F;
        repairArmor(bot.npc.getPlayer(), bot.armorTemplate);
    }

    private void restoreParticipantHealth(final Player player) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && isParticipant(player) && !player.isDead()) {
                    player.setHealth(player.getMaxHealth());
                    player.setFireTicks(0);
                }
            }
        });
    }

    private static void repairArmor(Player player, org.bukkit.inventory.ItemStack[] template) {
        if (player == null || template == null) return;
        org.bukkit.inventory.ItemStack[] current = player.getInventory().getArmorContents();
        boolean changed = current.length != template.length;
        if (!changed) {
            for (int slot = 0; slot < template.length; slot++) {
                org.bukkit.inventory.ItemStack expected = template[slot];
                org.bukkit.inventory.ItemStack actual = current[slot];
                if (expected == null ? actual != null
                        : actual == null || actual.getType() != expected.getType()
                        || actual.getDurability() != expected.getDurability()) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return;
        org.bukkit.inventory.ItemStack[] repaired = new org.bukkit.inventory.ItemStack[template.length];
        for (int slot = 0; slot < template.length; slot++) {
            repaired[slot] = template[slot] == null ? null : template[slot].clone();
        }
        player.getInventory().setArmorContents(repaired);
    }

    static double nonLethalRawDamage(double requestedDamage, double maximumHealth) {
        if (Double.isNaN(requestedDamage) || Double.isInfinite(requestedDamage)
                || requestedDamage <= 0.0D || maximumHealth <= 1.0D) {
            return 0.0D;
        }
        return Math.min(requestedDamage, maximumHealth - 1.0D);
    }

    private void pruneParticipants() {
        for (Iterator<UUID> iterator = participants.iterator(); iterator.hasNext();) {
            UUID playerId = iterator.next();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()
                    || !HitDebugRoomLayoutService.contains(player.getLocation())) {
                iterator.remove();
                if (player != null && player.isOnline()) hideProfiles(player);
                PlayerProfile profile = profileManager.get(playerId);
                if (profile != null && profile.getState() == PlayerState.DEBUG) {
                    profile.setState(PlayerState.LOBBY);
                }
            }
        }
    }

    private Player firstParticipant() {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) return player;
        }
        return null;
    }

    private Player nearestParticipant(double x, double y, double z) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()
                    || !HitDebugRoomLayoutService.contains(player.getLocation())) continue;
            Location location = player.getLocation();
            double dx = location.getX() - x;
            double dy = location.getY() - y;
            double dz = location.getZ() - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private DebugBot findBot(UUID id) {
        if (id == null) return null;
        for (DebugBot bot : bots.values()) {
            if (id.equals(bot.npc.getUniqueId())) return bot;
        }
        return null;
    }

    private DebugBot resolveDebugBot(Entity damager) {
        DebugBot direct = damager == null ? null : findBot(damager.getUniqueId());
        if (direct != null) return direct;
        if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof Player) return findBot(((Player) source).getUniqueId());
        }
        return null;
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof Player) return (Player) source;
        }
        return null;
    }

    private boolean hasAllValidBots() {
        if (bots.size() != BotType.values().length) return false;
        for (DebugBot bot : bots.values()) {
            if (!bot.npc.isValid()) return false;
        }
        return true;
    }

    private void showProfiles(Player viewer) {
        for (DebugBot bot : bots.values()) bot.npc.showInTab(viewer);
    }

    private void hideProfiles(Player viewer) {
        for (DebugBot bot : bots.values()) bot.npc.hideFromTab(viewer);
    }

    private void scheduleTabHide(final Player viewer) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (viewer.isOnline() && isParticipant(viewer)) hideProfiles(viewer);
            }
        }, 20L);
    }

    private void removeBots() {
        clearPearls();
        for (DebugBot bot : bots.values()) {
            for (UUID playerId : participants) {
                bot.npc.hideFromTab(Bukkit.getPlayer(playerId));
            }
            bot.npc.remove(null);
        }
        bots.clear();
    }

    private void clearPearls() {
        for (BotEnderPearl pearl : activePearls) {
            if (pearl != null && !pearl.dead) pearl.die();
        }
        activePearls.clear();
    }

    private void loadRoomChunks(World world) {
        int minimumChunkX = HitDebugRoomLayoutService.MINIMUM_X >> 4;
        int maximumChunkX = HitDebugRoomLayoutService.MAXIMUM_X >> 4;
        int minimumChunkZ = HitDebugRoomLayoutService.MINIMUM_Z >> 4;
        int maximumChunkZ = HitDebugRoomLayoutService.MAXIMUM_Z >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                world.loadChunk(chunkX, chunkZ, true);
            }
        }
    }

    private static void face(EntityPlayer entity, double x, double y, double z) {
        double dx = x - entity.locX;
        double dy = y - (entity.locY + entity.getHeadHeight());
        double dz = z - entity.locZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        entity.yaw = BotMovement.yawTo(dx, dz);
        entity.pitch = BotMovement.pitchTo(dy, horizontal);
        entity.aK = entity.yaw;
        entity.aI = entity.yaw;
        entity.aJ = entity.yaw;
    }

    private static void place(EntityPlayer entity, Location location) {
        entity.setLocation(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private static void clearMotion(EntityPlayer entity) {
        entity.motX = 0.0D;
        entity.motY = 0.0D;
        entity.motZ = 0.0D;
        entity.velocityChanged = false;
    }

    private static double distanceSquared(EntityPlayer entity, Location location) {
        double dx = entity.locX - location.getX();
        double dy = entity.locY - location.getY();
        double dz = entity.locZ - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double horizontalDistanceSquared(EntityPlayer first, EntityPlayer second) {
        double dx = first.locX - second.locX;
        double dz = first.locZ - second.locZ;
        return dx * dx + dz * dz;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum BotType {
        PASSIVE,
        NORMAL_ATTACK,
        SPRINT_ATTACK,
        PEARL;

        private boolean isAttackBot() {
            return this == NORMAL_ATTACK || this == SPRINT_ATTACK;
        }
    }

    private static final class DebugBot {
        final BotType type;
        final BotNpc npc;
        final org.bukkit.inventory.ItemStack[] armorTemplate;
        int cooldownTicks;
        int switchBackTicks;
        boolean pearlAtSecondStation;

        private DebugBot(BotType type, BotNpc npc) {
            this.type = type;
            this.npc = npc;
            org.bukkit.inventory.ItemStack[] equipped = npc.getPlayer().getInventory().getArmorContents();
            this.armorTemplate = new org.bukkit.inventory.ItemStack[equipped.length];
            for (int slot = 0; slot < equipped.length; slot++) {
                this.armorTemplate[slot] = equipped[slot] == null
                        ? null : equipped[slot].clone();
            }
        }
    }
}
