package com.poppy.practice.spectator;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.Match;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class SpectatorFixture implements SpectatorService.Environment {
    final ProfileManager profiles = new ProfileManager();
    final Map<UUID, PlayerStub> players = new HashMap<UUID, PlayerStub>();
    final Map<UUID, SpectatorTarget> targets = new HashMap<UUID, SpectatorTarget>();
    final List<String> calls = new ArrayList<String>();
    final World world = world();
    final SpectatorService service = new SpectatorService(null, profiles, this);
    Runnable duringReset;
    boolean failReset;
    boolean failBotHide;

    PlayerStub player(String name) {
        PlayerStub result = new PlayerStub(name, new Location(world, 0, 65, 0));
        players.put(result.id, result);
        profiles.create(result.id);
        return result;
    }

    Match match(PlayerStub first, PlayerStub second) {
        Match match = new Match(first.id, second.id, "nodebuff", "arena");
        targets.put(first.id, new SpectatorTarget(first.id, match));
        targets.put(second.id, new SpectatorTarget(second.id, match));
        profiles.get(first.id).setState(PlayerState.FIGHTING);
        profiles.get(second.id).setState(PlayerState.FIGHTING);
        match.markFighting();
        return match;
    }

    BotMatch botMatch(PlayerStub participant) {
        BotMatch match = new BotMatch(participant.id, UUID.randomUUID(), "nodebuff", "arena");
        targets.put(participant.id, new SpectatorTarget(participant.id, match));
        profiles.get(participant.id).setState(PlayerState.FIGHTING);
        match.markFighting();
        return match;
    }

    @Override public Player player(UUID id) {
        PlayerStub player = players.get(id);
        return player == null ? null : player.player;
    }
    @Override public Collection<? extends Player> onlinePlayers() {
        List<Player> result = new ArrayList<Player>();
        for (PlayerStub player : players.values()) if (player.online) result.add(player.player);
        return result;
    }
    @Override public SpectatorTarget target(UUID id) { return targets.get(id); }
    @Override public void reset(Player player) {
        calls.add("reset:" + player.getName());
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
        if (duringReset != null) duringReset.run();
        if (failReset) throw new IllegalStateException("reset failed");
    }
    @Override public void lobby(Player player) {
        calls.add("lobby:" + player.getName());
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
        profiles.getOrCreate(player.getUniqueId()).setState(PlayerState.LOBBY);
    }
    @Override public void showBot(SpectatorTarget target, Player viewer) {
        if (target.getBotMatch() != null) calls.add("showbot:" + viewer.getName());
    }
    @Override public void hideBot(SpectatorTarget target, Player viewer) {
        if (target.getBotMatch() != null) {
            calls.add("hidebot:" + viewer.getName());
            if (failBotHide) throw new IllegalStateException("hide failed");
        }
    }
    @Override public void message(Player player, String ja, String en) { calls.add("message:" + en); }
    @Override public void failure(String name, RuntimeException error) { calls.add("error:" + name); }

    static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMaxHeight")) return 256;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("getName")) return "arena";
                    if (method.getName().equals("toString")) return "arena-world";
                    return defaultValue(method.getReturnType());
                });
    }

    static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0D;
        if (type == float.class) return 0.0F;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }

    static final class PlayerStub implements InvocationHandler {
        final UUID id = UUID.randomUUID();
        final String name;
        final Player player;
        final Set<UUID> hidden = new HashSet<UUID>();
        final Player.Spigot spigot = new Player.Spigot() {
            @Override public boolean getCollidesWithEntities() { return collides; }
            @Override public void setCollidesWithEntities(boolean value) { collides = value; }
        };
        boolean online = true;
        boolean permission = true;
        boolean allowFlight;
        boolean flying;
        boolean collides = true;
        boolean canPickup = true;
        boolean sleepingIgnored;
        boolean rejectTeleport;
        boolean failShow;
        int teleports;
        int shows;
        GameMode gameMode;
        Location location;
        Runnable teleportCallback;

        PlayerStub(String name, Location location) {
            this.name = name;
            this.location = location;
            // WindSpigot retains both int/double health ABI methods; JDK Proxy rejects them.
            player = Mockito.mock(Player.class, (Answer<Object>) invocation ->
                    invoke(invocation.getMock(), invocation.getMethod(), invocation.getArguments()));
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getUniqueId": return id;
                case "getName": return name;
                case "isOnline": return online;
                case "hasPermission": return permission;
                case "getLocation": return location.clone();
                case "getWorld": return location.getWorld();
                case "getGameMode": return gameMode;
                case "setGameMode": gameMode = (GameMode) args[0]; return null;
                case "getAllowFlight": return allowFlight;
                case "setAllowFlight": allowFlight = (Boolean) args[0]; return null;
                case "isFlying": return flying;
                case "setFlying": flying = (Boolean) args[0]; return null;
                case "getCanPickupItems": return canPickup;
                case "setCanPickupItems": canPickup = (Boolean) args[0]; return null;
                case "isSleepingIgnored": return sleepingIgnored;
                case "setSleepingIgnored": sleepingIgnored = (Boolean) args[0]; return null;
                case "spigot": return spigot;
                case "teleport":
                    teleports++;
                    if (teleportCallback != null) teleportCallback.run();
                    if (rejectTeleport) return false;
                    location = ((Location) args[0]).clone();
                    return true;
                case "canSee": return !hidden.contains(((Player) args[0]).getUniqueId());
                case "hidePlayer": hidden.add(((Player) args[0]).getUniqueId()); return null;
                case "showPlayer":
                    shows++;
                    if (failShow) throw new IllegalStateException("visibility failure");
                    hidden.remove(((Player) args[0]).getUniqueId()); return null;
                case "hashCode": return id.hashCode();
                case "equals": return proxy == args[0];
                case "toString": return name;
                default: return defaultValue(method.getReturnType());
            }
        }
    }
}
