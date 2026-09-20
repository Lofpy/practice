package com.poppy.practice.spectator;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.PlayerResetService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Main-thread-only, protocol-1.7-compatible spectator lifecycle. */
public final class SpectatorService {
    public static final String PERMISSION = "poppypractice.spec";
    private static final double MAX_DISTANCE_SQUARED = 224.0D * 224.0D;
    private final ProfileManager profiles;
    private final Environment environment;
    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private BukkitTask monitorTask;
    private boolean shuttingDown;

    public SpectatorService(Plugin plugin, ProfileManager profiles, final MatchManager matches,
                            final BotService bots, final LobbyService lobby,
                            final PlayerResetService reset, final LanguageService language) {
        this(plugin, profiles, new Environment() {
            @Override public Player player(UUID id) { return Bukkit.getPlayer(id); }
            @Override public Collection<? extends Player> onlinePlayers() { return Bukkit.getOnlinePlayers(); }
            @Override public SpectatorTarget target(UUID participant) {
                Match match = matches.getByPlayer(participant);
                if (match != null) return new SpectatorTarget(participant, match);
                BotMatch botMatch = bots.getByPlayer(participant);
                return botMatch == null ? null : new SpectatorTarget(participant, botMatch);
            }
            @Override public void reset(Player player) { reset.reset(player, GameMode.ADVENTURE); }
            @Override public void lobby(Player player) { lobby.sendToLobby(player); }
            @Override public void showBot(SpectatorTarget target, Player viewer) {
                if (target.getBotMatch() != null) bots.addSpectator(target.getBotMatch(), viewer);
            }
            @Override public void hideBot(SpectatorTarget target, Player viewer) {
                if (target.getBotMatch() != null) bots.removeSpectator(target.getBotMatch(), viewer);
            }
            @Override public void message(Player player, String japanese, String english) {
                language.send(player, japanese, english);
            }
            @Override public void failure(String operation, RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Spectator " + operation + " failed", error);
            }
        });
    }

    SpectatorService(Plugin plugin, ProfileManager profiles, Environment environment) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.environment = environment;
    }

    interface Environment {
        Player player(UUID id);
        Collection<? extends Player> onlinePlayers();
        SpectatorTarget target(UUID participant);
        void reset(Player player);
        void lobby(Player player);
        void showBot(SpectatorTarget target, Player viewer);
        void hideBot(SpectatorTarget target, Player viewer);
        void message(Player player, String japanese, String english);
        void failure(String operation, RuntimeException error);
    }

    public void start() {
        if (monitorTask == null && !shuttingDown) {
            monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        }
    }

    public boolean spectate(Player viewer, Player participant) {
        if (viewer == null || !viewer.isOnline()) return false;
        if (!viewer.hasPermission(PERMISSION)) {
            message(viewer, "&c観戦する権限がありません。", "&cYou do not have permission to spectate.");
            return false;
        }
        PlayerProfile profile = profiles.getOrCreate(viewer.getUniqueId());
        if (shuttingDown || profile.getState() != PlayerState.LOBBY
                || sessions.containsKey(viewer.getUniqueId()) || environment.target(viewer.getUniqueId()) != null) {
            message(viewer, "&c観戦はロビーから開始してください。キューや現在の観戦から先に退出してください。",
                    "&cStart spectating from the lobby. Leave your queue or current spectating session first.");
            return false;
        }
        if (participant == null || !participant.isOnline() || !viewer.canSee(participant)
                || viewer.getUniqueId().equals(participant.getUniqueId())) {
            message(viewer, "&c観戦できるプレイヤーが見つかりません。", "&cNo spectatable player was found.");
            return false;
        }
        SpectatorTarget target = environment.target(participant.getUniqueId());
        if (target == null || !target.canJoin()) {
            message(viewer, "&cそのプレイヤーは現在試合中ではありません。", "&cThat player is not in an active match.");
            return false;
        }
        Location location = participant.getLocation().clone().add(0.0D, 3.0D, 0.0D);
        if (location.getWorld() == null || !finite(location)) return false;
        location.setY(Math.max(1.0D, Math.min(location.getY(), location.getWorld().getMaxHeight() - 2.0D)));
        Session session = new Session(viewer, target, location);
        // Record the session before callbacks, reset or teleport can re-enter the lifecycle.
        sessions.put(viewer.getUniqueId(), session);
        profile.setQueuedKitId(null);
        profile.resetEnderPearlCooldown();
        profile.setState(PlayerState.SPECTATING);
        try {
            environment.reset(viewer);
            if (!current(session)) return false;
            viewer.spigot().setCollidesWithEntities(false);
            viewer.setCanPickupItems(false);
            viewer.setSleepingIgnored(true);
            viewer.setAllowFlight(true);
            viewer.setFlying(true);
            for (Player other : environment.onlinePlayers()) hideFrom(session, other);
            if (!current(session)) return false;
            environment.showBot(target, viewer);
            if (!current(session)) return false;
            session.teleporting = true;
            boolean moved;
            try { moved = viewer.teleport(location); }
            finally { session.teleporting = false; }
            if (!current(session)) return false;
            if (!moved || !targetStillValid(session)) throw new IllegalStateException("Spectator destination unavailable");
            // A teleport can change abilities; reassert flight after the world transition.
            viewer.setAllowFlight(true);
            viewer.setFlying(true);
            message(viewer, "&a" + participant.getName() + " の試合を観戦中です。&7 /spec または /spawn で退出できます。",
                    "&aSpectating " + participant.getName() + ". &7Use /spec or /spawn to leave.");
            return true;
        } catch (RuntimeException error) {
            environment.failure("join", error);
            if (current(session)) leave(viewer, false);
            message(viewer, "&c観戦を開始できませんでした。", "&cCould not start spectating.");
            return false;
        }
    }

    public boolean isSpectating(UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        return sessions.containsKey(playerId) || profile != null && profile.getState() == PlayerState.SPECTATING;
    }

    public boolean leave(Player viewer, boolean notify) {
        if (viewer == null || !isSpectating(viewer.getUniqueId())) return false;
        Session session = sessions.remove(viewer.getUniqueId());
        release(viewer, session, true);
        if (notify && viewer.isOnline()) message(viewer, "&a観戦を終了しました。", "&aYou stopped spectating.");
        return true;
    }

    public void handleMatchEnd(UUID matchId) {
        if (matchId == null) return;
        for (Session session : snapshot()) {
            if (session.target.getMatchId().equals(matchId)) end(session, true);
        }
    }

    public void handleQuit(Player player) {
        Session own = sessions.remove(player.getUniqueId());
        if (own != null) release(player, own, false);
        for (Session session : snapshot()) {
            session.hiddenFrom.remove(player.getUniqueId());
            if (session.target.contains(player.getUniqueId())) end(session, true);
        }
    }

    public void handleJoin(Player player) {
        for (Session session : snapshot()) cleanup("join visibility", () -> hideFrom(session, player));
    }

    public void shutdown() {
        shuttingDown = true;
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
        for (Session session : snapshot()) end(session, false);
    }

    public boolean permitsTeleport(UUID viewerId) {
        Session session = sessions.get(viewerId);
        return session != null && session.teleporting;
    }

    public boolean isWithinBounds(UUID viewerId, Location location) {
        Session session = sessions.get(viewerId);
        if (session == null || location == null || !finite(location)
                || location.getWorld() == null || !location.getWorld().equals(session.origin.getWorld())) return false;
        double dx = location.getX() - session.origin.getX();
        double dz = location.getZ() - session.origin.getZ();
        return dx * dx + dz * dz <= MAX_DISTANCE_SQUARED && location.getY() >= 1.0D
                && location.getY() < location.getWorld().getMaxHeight() - 1.0D;
    }

    public Location returnLocation(UUID viewerId) {
        Session session = sessions.get(viewerId);
        return session == null ? null : session.origin.clone();
    }

    public void blockedCommand(Player viewer) {
        message(viewer, "&c観戦中は /spec または /spawn で退出してください。",
                "&cUse /spec or /spawn to leave spectating before using other commands.");
    }

    void tick() {
        for (Session session : snapshot()) {
            try {
                Player viewer = environment.player(session.viewerId);
                if (viewer == null || !viewer.isOnline()) {
                    sessions.remove(session.viewerId);
                    release(viewer == null ? session.viewer : viewer, session, false);
                    continue;
                }
                if (!targetStillValid(session) || !isWithinBounds(session.viewerId, viewer.getLocation())) {
                    end(session, true);
                    continue;
                }
                viewer.setAllowFlight(true);
                viewer.setFlying(true);
                for (Player other : environment.onlinePlayers()) hideFrom(session, other);
            } catch (RuntimeException error) {
                environment.failure("monitor", error);
                end(session, false);
            }
        }
    }

    private boolean targetStillValid(Session session) {
        PlayerProfile profile = profiles.get(session.viewerId);
        if (!current(session) || profile == null || profile.getState() != PlayerState.SPECTATING) return false;
        SpectatorTarget current = environment.target(session.target.getParticipantId());
        Player participant = environment.player(session.target.getParticipantId());
        return current != null && current.getMatchId().equals(session.target.getMatchId())
                && current.canContinue() && participant != null && participant.isOnline()
                && session.origin.getWorld().equals(participant.getWorld());
    }

    private void end(Session session, boolean notify) {
        if (!current(session)) return;
        sessions.remove(session.viewerId);
        Player viewer = environment.player(session.viewerId);
        if (viewer != null) {
            release(viewer, session, viewer.isOnline());
            if (notify && viewer.isOnline()) message(viewer, "&e試合の観戦が終了しました。", "&eThe spectated match has ended.");
        } else {
            release(session.viewer, session, false);
        }
    }

    private void release(Player viewer, Session session, boolean returnToLobby) {
        PlayerProfile profile = profiles.get(viewer.getUniqueId());
        // An old session must never teleport a player out of a newer combat/debug session.
        boolean lobbyEligible = profile != null && (profile.getState() == PlayerState.SPECTATING
                || profile.getState() == PlayerState.LOBBY);
        clearProfile(viewer.getUniqueId());
        if (session != null) {
            cleanup("bot visibility", () -> environment.hideBot(session.target, viewer));
            cleanup("collision", () -> viewer.spigot().setCollidesWithEntities(session.collides));
            cleanup("pickup", () -> viewer.setCanPickupItems(session.canPickup));
            cleanup("sleep", () -> viewer.setSleepingIgnored(session.sleepingIgnored));
            // Restore only visibility this service actually changed, preserving existing vanish state.
            for (UUID otherId : session.hiddenFrom) {
                Player other = environment.player(otherId);
                if (other != null && other.isOnline()) cleanup("visibility", () -> other.showPlayer(viewer));
            }
            session.hiddenFrom.clear();
        }
        cleanup("flight", () -> viewer.setFlying(false));
        cleanup("flight permission", () -> viewer.setAllowFlight(false));
        if (returnToLobby && lobbyEligible && viewer.isOnline()) cleanup("lobby return", () -> environment.lobby(viewer));
    }

    private void clearProfile(UUID viewerId) {
        PlayerProfile profile = profiles.get(viewerId);
        if (profile != null && profile.getState() == PlayerState.SPECTATING) {
            profile.setState(PlayerState.LOBBY);
            profile.setQueuedKitId(null);
            profile.resetEnderPearlCooldown();
        }
    }

    private void hideFrom(Session session, Player other) {
        if (session.viewerId.equals(other.getUniqueId()) || !other.isOnline()) return;
        Player viewer = environment.player(session.viewerId);
        if (viewer != null && other.canSee(viewer)) {
            // Track before invoking a callback that may throw after changing visibility.
            session.hiddenFrom.add(other.getUniqueId());
            other.hidePlayer(viewer);
        }
    }

    private boolean current(Session session) { return sessions.get(session.viewerId) == session; }
    private Collection<Session> snapshot() { return new ArrayList<Session>(sessions.values()); }
    private void cleanup(String name, Runnable step) {
        try { step.run(); } catch (RuntimeException error) { environment.failure(name, error); }
    }
    private void message(Player viewer, String japanese, String english) {
        cleanup("message", () -> environment.message(viewer, japanese, english));
    }
    private static boolean finite(Location location) {
        return Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ());
    }

    private static final class Session {
        private final Player viewer;
        private final UUID viewerId;
        private final SpectatorTarget target;
        private final Location origin;
        private final boolean collides;
        private final boolean canPickup;
        private final boolean sleepingIgnored;
        private final Set<UUID> hiddenFrom = new HashSet<UUID>();
        private boolean teleporting;

        private Session(Player viewer, SpectatorTarget target, Location origin) {
            this.viewer = viewer;
            this.viewerId = viewer.getUniqueId();
            this.target = target;
            this.origin = origin.clone();
            this.collides = viewer.spigot().getCollidesWithEntities();
            this.canPickup = viewer.getCanPickupItems();
            this.sleepingIgnored = viewer.isSleepingIgnored();
        }
    }
}
