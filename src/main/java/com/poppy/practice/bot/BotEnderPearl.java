package com.poppy.practice.bot;

import com.poppy.practice.combat.EnderPearlDamage;
import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.EntityEnderPearl;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.MovingObjectPosition;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * An ender pearl whose owner is a server-side fake player.
 *
 * Vanilla's player pearl path teleports through PlayerConnection and leaves the
 * connection waiting for a movement acknowledgement. A bot has no client that
 * can send that acknowledgement, so subsequent position and hit processing can
 * become desynchronised. This class performs the same cancellable Bukkit
 * teleport without entering PlayerConnection's client-confirmation state.
 */
final class BotEnderPearl extends EntityEnderPearl {
    private static final double TARGET_TOLERANCE = 0.6D;

    private final EntityPlayer bot;
    private final double targetX;
    private final double targetY;
    private final double targetZ;

    BotEnderPearl(World world, EntityPlayer bot,
                   double targetX, double targetY, double targetZ) {
        super(world, bot);
        this.bot = bot;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
    }

    @Override
    public void t_() {
        super.t_();
        if (!dead && !world.isClientSide && hasReachedTarget()) {
            finishAtTarget();
        }
    }

    @Override
    protected void a(MovingObjectPosition hit) {
        if (hit.entity == bot) {
            return;
        }
        if (hit.entity != null) {
            hit.entity.damageEntity(DamageSource.projectile(this, bot), 0.0F);
        }
        if (hasReachedTarget()) {
            setPosition(targetX, targetY, targetZ);
        }
        if (!world.isClientSide && bot.isAlive() && bot.world == world) {
            teleportBot();
        }
        die();
    }

    private boolean hasReachedTarget() {
        return BotPearlTargeting.reachedOrPassed(
                locX, locZ, motX, motZ, targetX, targetZ, TARGET_TOLERANCE);
    }

    private void finishAtTarget() {
        setPosition(targetX, targetY, targetZ);
        if (bot.isAlive() && bot.world == world) {
            teleportBot();
        }
        die();
    }

    private void teleportBot() {
        CraftPlayer player = bot.getBukkitEntity();
        Location from = player.getLocation();
        Location to = new Location(player.getWorld(), locX, locY, locZ,
                from.getYaw(), from.getPitch());
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to,
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        Bukkit.getPluginManager().callEvent(event);
        Location destination = event.getTo();
        if (event.isCancelled() || destination == null
                || destination.getWorld() == null
                || !destination.getWorld().equals(player.getWorld())) {
            return;
        }

        if (bot.au()) {
            bot.mount(null);
        }
        bot.setLocation(destination.getX(), destination.getY(), destination.getZ(),
                destination.getYaw(), destination.getPitch());
        bot.invulnerableTicks = 0;
        EnderPearlDamage.apply(player);
    }
}
