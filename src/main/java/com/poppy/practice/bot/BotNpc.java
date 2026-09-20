package com.poppy.practice.bot;

import com.mojang.authlib.GameProfile;
import com.poppy.practice.kit.Kit;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.function.DoubleUnaryOperator;

final class BotNpc {
    private static final int PERMANENT_EFFECT_DURATION = Integer.MAX_VALUE;
    private static final int SPEED_TWO_AMPLIFIER = 1;

    private final EntityPlayer handle;
    private final Player player;
    private final WorldServer world;
    private final BotNetworkManager networkManager;
    private int unansweredMeleeHits;
    private long landedMeleeHits;
    private long receivedMeleeHits;
    private boolean spawned;
    private DoubleUnaryOperator verticalVelocityController;

    private BotNpc(EntityPlayer handle, WorldServer world, BotNetworkManager networkManager) {
        this.handle = handle;
        this.player = handle.getBukkitEntity();
        this.world = world;
        this.networkManager = networkManager;
    }

    static BotNpc spawn(Player viewer, Location location, Kit kit, BotSettings settings) {
        return spawn(viewer, location, kit, settings, settings.getName(), false);
    }

    static BotNpc spawn(Player viewer, Location location, Kit kit, BotSettings settings,
                        String name, boolean knockbackImmune) {
        CraftServer craftServer = (CraftServer) viewer.getServer();
        MinecraftServer server = craftServer.getServer();
        WorldServer world = ((CraftWorld) location.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), profileName(name));
        if (settings.isCopyPlayerSkin()) {
            GameProfile viewerProfile = ((CraftPlayer) viewer).getHandle().getProfile();
            profile.getProperties().putAll(viewerProfile.getProperties());
        }

        EntityPlayer entity = knockbackImmune
                ? new KnockbackImmuneEntityPlayer(server, world, profile,
                new PlayerInteractManager(world))
                : new EntityPlayer(server, world, profile, new PlayerInteractManager(world));
        BotNetworkManager networkManager = new BotNetworkManager();
        networkManager.bind(entity);
        new BotPlayerConnection(server, networkManager, entity);
        entity.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        entity.invulnerableTicks = 0;
        entity.joining = false;
        entity.collidesWithEntities = true;
        entity.getAttributeInstance(GenericAttributes.maxHealth).setValue(settings.getMaximumHealth());
        entity.setHealth((float) settings.getMaximumHealth());
        entity.playerInteractManager.setGameMode(net.minecraft.server.v1_8_R3.WorldSettings.EnumGamemode.SURVIVAL);

        BotNpc npc = new BotNpc(entity, world, networkManager);
        npc.player.setGameMode(GameMode.SURVIVAL);
        npc.player.setFoodLevel(20);
        npc.player.setSaturation(20.0F);
        kit.apply(npc.player);
        if ("nodebuff".equalsIgnoreCase(kit.getId())) {
            // NoDebuff must drink the real kit potions, not inherit an infinite buff.
            npc.player.removePotionEffect(PotionEffectType.SPEED);
        } else {
            npc.ensureSpeedTwo();
        }
        entity.inventory.itemInHandIndex = 0;

        npc.sendPlayerInfo(viewer, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER);
        if (!world.addEntity(entity)) {
            npc.sendPlayerInfo(viewer, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER);
            throw new IllegalStateException("WindSpigot rejected the bot EntityPlayer");
        }
        npc.spawned = true;
        return npc;
    }

    EntityPlayer getHandle() {
        return handle;
    }

    Player getPlayer() {
        return player;
    }

    UUID getUniqueId() {
        return handle.getUniqueID();
    }

    boolean isValid() {
        return spawned && handle.valid && !handle.dead;
    }

    void applyPendingVelocity() {
        networkManager.applyPendingVelocity();
    }

    void setVerticalVelocityController(DoubleUnaryOperator controller) {
        verticalVelocityController = controller;
    }

    void applyVerticalVelocityControl() {
        if (verticalVelocityController != null) {
            // This is local client motion. Do not send another velocity packet or
            // touch the separate server/client horizontal knockback baselines.
            double controlled = verticalVelocityController.applyAsDouble(handle.motY);
            if (Double.isFinite(controlled)) {
                handle.motY = controlled;
            }
        }
    }

    void runClientTick(Runnable tick) {
        networkManager.runClientTick(tick);
    }

    void runServerAttack(Runnable attack) {
        networkManager.runServerAttack(attack);
    }

    void applyClientAttackSlowdown() {
        networkManager.applyClientAttackSlowdown();
    }

    void advanceServerMotion(double horizontalDrag) {
        networkManager.advanceServerMotion(horizontalDrag);
    }

    void discardPendingVelocity() {
        networkManager.discardPendingVelocity();
    }

    void recordMeleeHit() {
        unansweredMeleeHits++;
        receivedMeleeHits++;
    }

    void recordMeleeHitLanded() {
        unansweredMeleeHits = 0;
        landedMeleeHits++;
    }

    long getLandedMeleeHits() {
        return landedMeleeHits;
    }

    long getReceivedMeleeHits() {
        return receivedMeleeHits;
    }

    int getUnansweredMeleeHits() {
        return unansweredMeleeHits;
    }

    void stopBlocking() {
        if (handle.isBlocking()) {
            handle.bV();
        }
    }

    void ensureSpeedTwo() {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)
                    && effect.getAmplifier() == SPEED_TWO_AMPLIFIER
                    && effect.getDuration() > 40) {
                return;
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                PERMANENT_EFFECT_DURATION, SPEED_TWO_AMPLIFIER, false, true), true);
    }

    /** Remove only when this viewer stops watching the NPC, never on a hide-tab timer. */
    void removePlayerInfo(Player viewer) {
        if (viewer != null && viewer.isOnline()) {
            sendPlayerInfo(viewer, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER);
        }
    }

    /** Player spawns in 1.8+ require this profile, including after tracker re-entry. */
    void showPlayerInfo(Player viewer) {
        if (viewer != null && viewer.isOnline()) {
            sendPlayerInfo(viewer, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER);
        }
    }

    void remove(Player viewer) {
        verticalVelocityController = null;
        if (!spawned) {
            return;
        }
        spawned = false;
        if (viewer != null && viewer.isOnline()) {
            ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(
                    new PacketPlayOutEntityDestroy(handle.getId()));
            sendPlayerInfo(viewer, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER);
        }
        handle.setSprinting(false);
        world.removeEntity(handle);
    }

    private void sendPlayerInfo(Player viewer, PacketPlayOutPlayerInfo.EnumPlayerInfoAction action) {
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(
                new PacketPlayOutPlayerInfo(action, handle));
    }

    private static String profileName(String configured) {
        String translated = ChatColor.translateAlternateColorCodes('&', configured == null ? "" : configured);
        String plain = ChatColor.stripColor(translated).replaceAll("[^A-Za-z0-9_]", "");
        if (plain.isEmpty()) {
            plain = "PracticeBot";
        }
        return plain.length() > 16 ? plain.substring(0, 16) : plain;
    }

    private static final class KnockbackImmuneEntityPlayer extends EntityPlayer {
        private KnockbackImmuneEntityPlayer(MinecraftServer server, WorldServer world,
                                             GameProfile profile,
                                             PlayerInteractManager interactManager) {
            super(server, world, profile, interactManager);
        }

        @Override
        public void g(double x, double y, double z) {
            // Keep the hit and damage event intact while rejecting only motion.
        }
    }
}
