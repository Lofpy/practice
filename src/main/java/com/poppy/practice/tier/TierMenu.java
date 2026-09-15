package com.poppy.practice.tier;

import com.poppy.practice.bot.BotService;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.ui.MenuHolder;
import com.poppy.practice.ui.MenuNavigation;
import com.poppy.practice.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Per-kit certification status and entry, independent of configurable practice bots. */
public final class TierMenu implements Listener, CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final RatingService ratings;
    private final ProfileManager profiles;
    private final KitManager kits;
    private final BotService bots;

    public TierMenu(Plugin plugin, RatingService ratings, ProfileManager profiles, KitManager kits, BotService bots) {
        this.plugin = plugin; this.ratings = ratings; this.profiles = profiles; this.kits = kits; this.bots = bots;
    }

    public void open(Player player) {
        if (!inLobby(player)) { player.sendMessage(ChatColor.RED + "Return to the lobby first."); return; }
        Screen screen = new Screen(player.getUniqueId());
        int slot = 0;
        for (Kit kit : kits.all()) {
            boolean qualified = ratings.isQualified(player.getUniqueId(), kit.getId());
            screen.kits.put(slot, kit.getId());
            screen.getInventory().setItem(slot++, new ItemBuilder(kit.getIcon()).durability(kit.getIconDurability())
                    .name("&b" + kit.getDisplayName()).lore(
                        "&7Certification: &f" + ratings.getPlacementCount(player.getUniqueId(), kit.getId()) + "/3",
                        qualified ? "&7ELO: &a" + RatingService.format(ratings.getRatingMilli(player.getUniqueId(), kit.getId()))
                                  : "&7Initial ELO: &f1500.000 - 1800.000",
                        "&7Fixed bot. Assesses combat and kit-specific skills.",
                        "&7Win or lose: finish all 3 matches.",
                        "&7Quitting does not count. Practice bots do not count.",
                        qualified ? "&aCertified: use Ranked Queue." : "&eLeft Click: Start certification").build());
        }
        screen.getInventory().setItem(13, new ItemBuilder(Material.BEDROCK).name("&cClose").build());
        player.openInventory(screen.getInventory());
    }

    private boolean inLobby(Player player) {
        return profiles.get(player.getUniqueId()) != null
                && profiles.get(player.getUniqueId()).getState() == PlayerState.LOBBY;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Screen)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Screen screen = (Screen) event.getView().getTopInventory().getHolder();
        if (!screen.owner.equals(player.getUniqueId()) || !inLobby(player) || event.getClick() != ClickType.LEFT) return;
        String kit = screen.kits.get(event.getRawSlot());
        if (kit == null && event.getRawSlot() != 13) return;
        MenuNavigation.nextTick(plugin, player, screen.getInventory(), () -> {
            if (!inLobby(player)) return;
            player.closeInventory();
            if (kit != null) bots.startTierTest(player, kit);
        });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Screen) event.setCancelled(true);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("elo")) {
            if (args.length > 1 || args.length == 1 && kits.get(args[0]) == null) {
                player.sendMessage("/elo [nodebuff|boxing|combo]"); return true;
            }
            for (Kit kit : kits.all()) {
                if (args.length == 1 && !kit.getId().equalsIgnoreCase(args[0])) continue;
                player.sendMessage(ChatColor.GOLD + kit.getDisplayName() + ": "
                        + (ratings.isQualified(player.getUniqueId(), kit.getId())
                           ? RatingService.format(ratings.getRatingMilli(player.getUniqueId(), kit.getId()))
                           : "Certification " + ratings.getPlacementCount(player.getUniqueId(), kit.getId()) + "/3"));
            }
        } else if (args.length == 0) open(player);
        else if (args.length == 1 && kits.get(args[0]) != null) {
            if (inLobby(player)) bots.startTierTest(player, kits.get(args[0]).getId());
            else player.sendMessage(ChatColor.RED + "Return to the lobby first.");
        } else player.sendMessage("/tier [nodebuff|boxing|combo]");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> matches = new ArrayList<String>();
        if (args.length == 1) for (Kit kit : kits.all())
            if (kit.getId().startsWith(args[0].toLowerCase(Locale.ROOT))) matches.add(kit.getId());
        return matches;
    }

    private static final class Screen extends MenuHolder {
        final UUID owner;
        final Map<Integer,String> kits = new HashMap<Integer,String>();
        Screen(UUID owner) { super(18, "Certification | Select a Kit"); this.owner = owner; }
    }
}
