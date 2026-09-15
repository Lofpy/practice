package com.poppy.practice.duel;

import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.service.MatchService;
import com.poppy.practice.ui.MenuHolder;
import com.poppy.practice.ui.MenuNavigation;
import com.poppy.practice.util.ItemBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class DuelService implements Listener, CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final ProfileManager profiles;
    private final KitManager kits;
    private final ArenaManager arenas;
    private final MatchService matches;
    private final DuelRequests requests = new DuelRequests();
    public DuelService(Plugin plugin, ProfileManager profiles, KitManager kits, ArenaManager arenas, MatchService matches) {
        this.plugin = plugin; this.profiles = profiles; this.kits = kits; this.arenas = arenas; this.matches = matches;
    }
    private boolean available(Player player) {
        return player != null && player.isOnline() && !player.isDead()
                && profiles.get(player.getUniqueId()) != null
                && profiles.get(player.getUniqueId()).getState() == PlayerState.LOBBY;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
        Player player = (Player) sender;
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            answer(player, args[1], args[0].equalsIgnoreCase("accept")); return true;
        }
        if (args.length < 1 || args.length > 2) {
            player.sendMessage("/duel <player> [nodebuff|boxing|combo] | /duel <accept|deny> <player>"); return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.getUniqueId().equals(player.getUniqueId()) || !available(player) || !available(target)) {
            player.sendMessage(ChatColor.RED + "Both players must be in the PvP lobby (not queued)."); return true;
        }
        if (args.length == 2) {
            Kit kit = kits.get(args[1]);
            if (kit == null) player.sendMessage(ChatColor.RED + "Unknown kit. Choose nodebuff, boxing or combo.");
            else invite(player, target, kit.getId());
        } else player.openInventory(new Screen(player.getUniqueId(), target.getUniqueId(), kits).getInventory());
        return true;
    }
    private void invite(Player sender, Player target, String kit) {
        if (!available(sender) || !available(target)) { sender.sendMessage(ChatColor.RED + "Player is no longer available."); return; }
        DuelRequests.Request request = requests.send(sender.getUniqueId(), target.getUniqueId(), kit, System.currentTimeMillis());
        if (request == null) { sender.sendMessage(ChatColor.RED + "Wait 3 seconds before sending another request."); return; }
        sender.sendMessage(ChatColor.GREEN + "Duel request sent to " + target.getName() + " (" + kit + ", 60s). No ELO change.");
        target.sendMessage(ChatColor.AQUA + sender.getName() + " invited you to " + kit + ". No ELO change. Expires in 60s.");
        TextComponent accept = new TextComponent(ChatColor.GREEN + "[Accept] ");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept " + sender.getName()));
        TextComponent deny = new TextComponent(ChatColor.RED + "[Deny]");
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel deny " + sender.getName()));
        target.spigot().sendMessage(accept, deny);
    }
    private void answer(Player recipient, String name, boolean accept) {
        Player sender = Bukkit.getPlayerExact(name);
        DuelRequests.Request request = sender == null ? null : requests.find(sender.getUniqueId(), recipient.getUniqueId(), System.currentTimeMillis());
        if (request == null) { recipient.sendMessage(ChatColor.RED + "No active invitation from that player."); return; }
        if (!accept) {
            requests.remove(request); recipient.sendMessage(ChatColor.YELLOW + "Duel declined.");
            sender.sendMessage(ChatColor.YELLOW + recipient.getName() + " declined your duel."); return;
        }
        if (!available(sender) || !available(recipient)) {
            recipient.sendMessage(ChatColor.RED + "Both players must be in the PvP lobby (not queued)."); return;
        }
        Arena arena = arenas.acquireAvailable(request.kit);
        if (arena == null) { recipient.sendMessage(ChatColor.RED + "No arena available. Try accepting again shortly."); return; }
        // MatchService owns the arena reservation and rolls back failed starts.
        if (matches.startDuel(request.sender, request.target, request.kit, arena)) {
            requests.removePlayer(request.sender); requests.removePlayer(request.target);
        } else recipient.sendMessage(ChatColor.RED + "Could not start duel. Please try again.");
    }
    @EventHandler public void quit(PlayerQuitEvent event) { requests.removePlayer(event.getPlayer().getUniqueId()); }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Screen)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getClick() != ClickType.LEFT) return;
        Player player = (Player) event.getWhoClicked();
        Screen screen = (Screen) event.getView().getTopInventory().getHolder();
        String kit = screen.kits.get(event.getRawSlot());
        if (kit == null || !screen.owner.equals(player.getUniqueId()) || !available(player)) return;
        MenuNavigation.nextTick(plugin, player, screen.getInventory(), () -> {
            player.closeInventory(); invite(player, Bukkit.getPlayer(screen.target), kit);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Screen) event.setCancelled(true);
    }
    public void shutdown() { requests.clear(); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<String>();
        if (args.length == 1) { options.add("accept"); options.add("deny"); }
        if (args.length == 1 || args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            for (Player player : Bukkit.getOnlinePlayers()) if (!player.equals(sender)) options.add(player.getName());
        } else if (args.length == 2) for (Kit kit : kits.all()) options.add(kit.getId());
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(prefix)); return options;
    }
    private static final class Screen extends MenuHolder {
        final UUID owner, target;
        final Map<Integer,String> kits = new HashMap<Integer,String>();
        Screen(UUID owner, UUID target, KitManager manager) {
            super(9, "Duel | Select a Kit"); this.owner = owner; this.target = target;
            int slot = 0;
            for (Kit kit : manager.all()) {
                kits.put(slot, kit.getId());
                getInventory().setItem(slot++, new ItemBuilder(kit.getIcon()).durability(kit.getIconDurability())
                        .name("&b" + kit.getDisplayName()).lore("&7No ELO change. Certification not required.", "&eClick: Send invitation").build());
            }
        }
    }
}
