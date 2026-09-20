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
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.language.PlayerLanguage;
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
    private LanguageService languages;

    public void setLanguageService(LanguageService languages) { this.languages = languages; }
    private PlayerLanguage language(Player player) {
        return languages == null ? PlayerLanguage.JAPANESE : languages.language(player);
    }

    public TierMenu(Plugin plugin, RatingService ratings, ProfileManager profiles, KitManager kits, BotService bots) {
        this.plugin = plugin; this.ratings = ratings; this.profiles = profiles; this.kits = kits; this.bots = bots;
    }

    public void open(Player player) {
        if (!inLobby(player)) { player.sendMessage(ChatColor.RED + language(player).choose("先にロビーへ戻ってください。", "Return to the lobby first.")); return; }
        PlayerLanguage language = language(player);
        Screen screen = new Screen(player.getUniqueId(), language);
        int slot = 0;
        for (Kit kit : kits.all()) {
            boolean qualified = ratings.isQualified(player.getUniqueId(), kit.getId());
            screen.kits.put(slot, kit.getId());
            screen.getInventory().setItem(slot++, new ItemBuilder(kit.getIcon()).durability(kit.getIconDurability())
                    .name("&c" + kit.getDisplayName()).lore(
                        language.choose("&7認定: &f", "&7Certification: &f") + ratings.getPlacementCount(player.getUniqueId(), kit.getId()) + "/3",
                        qualified ? "&7ELO: &c" + RatingService.format(ratings.getRatingMilli(player.getUniqueId(), kit.getId()))
                                  : language.choose("&7初期ELO: &f1500.000 - 1800.000", "&7Initial ELO: &f1500.000 - 1800.000"),
                        language.choose("&7固定Botが戦闘・キット別のスキルを評価。", "&7Fixed bot. Assesses combat and kit-specific skills."),
                        language.choose("&7勝敗にかかわらず3試合を完了してください。", "&7Win or lose: finish all 3 matches."),
                        language.choose("&7途中退出・通常Bot対戦は対象外です。", "&7Quitting does not count. Practice bots do not count."),
                        qualified ? language.choose("&c認定済み: ランク対戦へ。", "&cCertified: use Ranked Queue.")
                                  : language.choose("&f左クリック: 認定試合を開始", "&fLeft Click: Start certification")).build());
        }
        screen.getInventory().setItem(13, new ItemBuilder(Material.BEDROCK).name(language.choose("&c閉じる", "&cClose")).build());
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
                           : language(player).choose("認定 ", "Certification ") + ratings.getPlacementCount(player.getUniqueId(), kit.getId()) + "/3"));
            }
        } else if (args.length == 0) open(player);
        else if (args.length == 1 && kits.get(args[0]) != null) {
            if (inLobby(player)) bots.startTierTest(player, kits.get(args[0]).getId());
            else player.sendMessage(ChatColor.RED + language(player).choose("先にロビーへ戻ってください。", "Return to the lobby first."));
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
        Screen(UUID owner, PlayerLanguage language) { super(18, language.choose("認定 | キット選択", "Certification | Select a Kit")); this.owner = owner; }
    }
}
