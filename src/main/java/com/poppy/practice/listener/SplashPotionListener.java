package com.poppy.practice.listener;

import com.poppy.practice.config.SplashPotionConfig;
import com.poppy.practice.config.SplashPotionConfig.Tuning;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class SplashPotionListener implements Listener {
    private static final int VANILLA_SELF_COLLISION_DELAY_TICKS = 4;

    private final JavaPlugin plugin;
    private SplashPotionConfig config;
    private Method getHandleMethod;
    private Method setSizeMethod;
    private Field ticksInAirField;
    private boolean hitboxErrorLogged;
    private boolean selfCollisionErrorLogged;

    public SplashPotionListener(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfiguration();
    }

    public void reloadConfiguration() {
        config = SplashPotionConfig.load(plugin);
    }

    public SplashPotionConfig getConfiguration() {
        return config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion)) {
            return;
        }

        ThrownPotion potion = (ThrownPotion) event.getEntity();
        Player shooter = potion.getShooter() instanceof Player ? (Player) potion.getShooter() : null;
        Tuning tuning = config.getTuning(shooter != null && hasSpeedTwo(shooter));
        Vector velocity = potion.getVelocity();
        applySpawnOffset(potion, velocity, tuning);
        velocity.setX(velocity.getX() * tuning.getXSpeed());
        velocity.setY(velocity.getY() * tuning.getYSpeed());
        velocity.setZ(velocity.getZ() * tuning.getXSpeed());
        potion.setVelocity(velocity);
        applyHitboxSize(potion, tuning);
        applySelfCollisionDelay(potion, tuning);
    }

    private void applySpawnOffset(ThrownPotion potion, Vector launchVelocity, Tuning tuning) {
        double forwardOffset = tuning.getSpawnForwardOffset();
        double heightOffset = tuning.getSpawnHeightOffset();
        if (forwardOffset == 0.0D && heightOffset == 0.0D) {
            return;
        }
        final ThrownPotion launchedPotion = potion;
        final Vector offset = calculateSpawnOffset(launchVelocity,
                forwardOffset, heightOffset);
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!launchedPotion.isValid() || launchedPotion.isDead()) {
                    return;
                }
                Location location = launchedPotion.getLocation().add(offset);
                launchedPotion.teleport(location);
            }
        });
    }

    static Vector calculateSpawnOffset(Vector launchVelocity, double forwardOffset,
                                       double heightOffset) {
        Vector offset = new Vector(0.0D, heightOffset, 0.0D);
        if (launchVelocity != null && forwardOffset != 0.0D
                && launchVelocity.lengthSquared() > 0.0D) {
            offset.add(launchVelocity.clone().normalize().multiply(forwardOffset));
        }
        return offset;
    }

    private void applyHitboxSize(ThrownPotion potion, Tuning tuning) {
        try {
            if (getHandleMethod == null || setSizeMethod == null) {
                getHandleMethod = potion.getClass().getMethod("getHandle");
                Object handle = getHandleMethod.invoke(potion);
                setSizeMethod = handle.getClass().getMethod("setSize", float.class, float.class);
            }

            Object handle = getHandleMethod.invoke(potion);
            float size = (float) tuning.getHitboxSize();
            setSizeMethod.invoke(handle, size, size);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            if (!hitboxErrorLogged) {
                hitboxErrorLogged = true;
                plugin.getLogger().warning("Could not change the splash potion hitbox on this server version: "
                        + exception.getClass().getSimpleName());
            }
        }
    }

    private void applySelfCollisionDelay(ThrownPotion potion, Tuning tuning) {
        if (!(potion.getShooter() instanceof Player)) {
            return;
        }
        try {
            if (getHandleMethod == null) {
                getHandleMethod = potion.getClass().getMethod("getHandle");
            }
            Object handle = getHandleMethod.invoke(potion);
            if (ticksInAirField == null) {
                ticksInAirField = findField(handle.getClass(), "ar");
                ticksInAirField.setAccessible(true);
            }
            int initialTicksInAir = VANILLA_SELF_COLLISION_DELAY_TICKS
                    - tuning.getSelfCollisionDelayTicks();
            ticksInAirField.setInt(handle, initialTicksInAir);
        } catch (NoSuchMethodException | NoSuchFieldException
                 | IllegalAccessException | InvocationTargetException exception) {
            if (!selfCollisionErrorLogged) {
                selfCollisionErrorLogged = true;
                plugin.getLogger().warning("Could not change the splash potion self-collision delay on this server version: "
                        + exception.getClass().getSimpleName());
            }
        }
    }

    private boolean hasSpeedTwo(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED) && effect.getAmplifier() >= 1) {
                return true;
            }
        }
        return false;
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
