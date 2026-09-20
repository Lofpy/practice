package com.poppy.practice.result;

import com.poppy.practice.ui.MenuHolder;
import com.poppy.practice.util.ItemBuilder;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.language.PlayerLanguage;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Renders immutable match snapshots without owning result storage or player state. */
public final class MatchResultView {
    public static final String OVERVIEW_TITLE = "試合結果";
    public static final String INVENTORY_TITLE_PREFIX = "Inventory: ";
    private LanguageService languages;

    public void setLanguageService(LanguageService languages) { this.languages = languages; }
    private PlayerLanguage language(UUID playerId) {
        return languages == null ? PlayerLanguage.JAPANESE : languages.language(playerId);
    }

    public void sendChat(Player viewer, MatchResult result) {
        PlayerLanguage language = language(viewer.getUniqueId());
        MatchParticipantSnapshot winner = result.getParticipant(result.getWinnerId());
        MatchParticipantSnapshot loser = result.getOpponent(result.getWinnerId());
        if (winner == null || loser == null) {
            return;
        }
        viewer.sendMessage(ChatColor.DARK_GRAY + "-----------------------------");
        sendParticipantLine(viewer, ChatColor.GREEN + language.choose("勝者: ", "Winner: "), winner, language);
        sendParticipantLine(viewer, ChatColor.RED + language.choose("敗者: ", "Loser: "), loser, language);
        viewer.sendMessage(ChatColor.GRAY + language.choose("試合時間: ", "Duration: ") + ChatColor.WHITE
                + duration(result.getDurationSeconds()));
        viewer.sendMessage(ChatColor.RED + language.choose("名前をクリックするとインベントリと戦績を確認できます。",
                "Click a name to view the inventory and statistics."));
        viewer.sendMessage(ChatColor.DARK_GRAY + "-----------------------------");
    }

    private static void sendParticipantLine(Player viewer, String label,
                                            MatchParticipantSnapshot participant, PlayerLanguage language) {
        TextComponent line = new TextComponent(label);
        TextComponent name = new TextComponent(ChatColor.WHITE.toString()
                + ChatColor.UNDERLINE + participant.getPlayerName());
        name.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/matchresult " + participant.getPlayerId()));
        name.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW
                        + language.choose("クリックしてインベントリと戦績を表示", "Click to view inventory and statistics")).create()));
        line.addExtra(name);
        viewer.spigot().sendMessage(line);
    }

    public Inventory overview(UUID viewerId, MatchResult result) {
        PlayerLanguage language = language(viewerId);
        Holder holder = new Holder(27, language.choose(OVERVIEW_TITLE, "Match Results"),
                new MatchResultSelection(viewerId, result, null));
        Inventory inventory = holder.getInventory();
        fillRow(inventory, 0);
        fillRow(inventory, 18);
        inventory.setItem(11, participantItem(result.getFirst(), result, true, language));
        inventory.setItem(13, new ItemBuilder(Material.WATCH)
                .name(language.choose("&c試合情報", "&cMatch Information"))
                .lore(language.choose("&7試合時間: &f", "&7Duration: &f") + duration(result.getDurationSeconds()), "",
                        language.choose("&7プレイヤーの頭をクリックすると", "&7Click a player's head to view"),
                        language.choose("&7終了時の持ち物と戦績を確認できます。", "&7their final inventory and statistics."))
                .build());
        inventory.setItem(15, participantItem(result.getSecond(), result, true, language));
        return inventory;
    }

    public Inventory participant(UUID viewerId, MatchResult result,
                                 MatchParticipantSnapshot participant) {
        PlayerLanguage language = language(viewerId);
        Holder holder = new Holder(54,
                language.choose("インベントリ: ", INVENTORY_TITLE_PREFIX) + shortName(participant.getPlayerName()),
                new MatchResultSelection(viewerId, result, participant.getPlayerId()));
        Inventory inventory = holder.getInventory();
        ItemStack[] contents = participant.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            inventory.setItem(inventorySlot(slot), contents[slot]);
        }
        ItemStack[] armor = participant.getArmor();
        for (int slot = 0; slot < armor.length; slot++) {
            inventory.setItem(36 + slot, armor[armor.length - 1 - slot]);
        }
        inventory.setItem(40, statusStatisticsItem(participant, language));
        inventory.setItem(41, filler());
        inventory.setItem(42, combatStatisticsItem(participant, language));
        inventory.setItem(43, filler());
        inventory.setItem(44, potionStatisticsItem(participant, language));
        fillRow(inventory, 45);
        inventory.setItem(49, participantItem(participant, result, false, language));
        MatchParticipantSnapshot other = result.getOpponent(participant.getPlayerId());
        if (other != null) {
            inventory.setItem(45, navigationItem("&c← " + other.getPlayerName(), language));
            inventory.setItem(53, navigationItem("&c" + other.getPlayerName() + " →", language));
        }
        return inventory;
    }

    public boolean isView(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Holder
                && ((Holder) inventory.getHolder()).owns(inventory);
    }

    public UUID target(Inventory inventory, UUID viewerId, MatchResult latest, int rawSlot) {
        if (!isView(inventory)) {
            return null;
        }
        return ((Holder) inventory.getHolder()).selection.target(viewerId, latest, rawSlot);
    }

    static int inventorySlot(int playerSlot) {
        if (playerSlot < 0 || playerSlot >= 36) {
            throw new IllegalArgumentException("Player inventory slot must be between 0 and 35");
        }
        return playerSlot < 9 ? playerSlot + 27 : playerSlot - 9;
    }

    public static String duration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static ItemStack participantItem(MatchParticipantSnapshot participant,
                                             MatchResult result, boolean clickable, PlayerLanguage language) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        boolean winner = participant.getPlayerId().equals(result.getWinnerId());
        meta.setDisplayName((winner ? ChatColor.GREEN : ChatColor.RED)
                + participant.getPlayerName());
        if (participant.getPlayerName() != null && participant.getPlayerName().length() <= 16) {
            meta.setOwner(participant.getPlayerName());
        }
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + language.choose("結果: ", "Result: ") + (winner ? ChatColor.GREEN + language.choose("勝利", "Winner")
                : ChatColor.RED + language.choose("敗北", "Loser")));
        lore.add(ChatColor.GRAY + language.choose("試合時間: ", "Duration: ") + ChatColor.WHITE
                + duration(result.getDurationSeconds()));
        if (clickable) {
            lore.add("");
            lore.add(ChatColor.RED + language.choose("クリックでインベントリと戦績を表示", "Click to view inventory and statistics"));
        }
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack statusStatisticsItem(MatchParticipantSnapshot participant, PlayerLanguage language) {
        return new ItemBuilder(Material.COOKED_BEEF)
                .name(language.choose("&c状態", "&cStatus"))
                .lore(language.choose("&7残り体力: &f", "&7Remaining Health: &f") + format(participant.getHealth()) + " HP",
                        language.choose("&7満腹度: &f", "&7Hunger: &f") + participant.getFoodLevel() + "/20")
                .build();
    }

    private static ItemStack combatStatisticsItem(MatchParticipantSnapshot participant, PlayerLanguage language) {
        return new ItemBuilder(Material.DIAMOND_SWORD)
                .name(language.choose("&c戦闘統計", "&cCombat Statistics"))
                .lore(language.choose("&7ヒット数: &f", "&7Hits: &f") + participant.getHits(),
                        language.choose("&7クリティカル: &f", "&7Criticals: &f") + participant.getCriticals(),
                        language.choose("&7ガード: &f", "&7Guards: &f") + participant.getGuards())
                .build();
    }

    private static ItemStack potionStatisticsItem(MatchParticipantSnapshot participant, PlayerLanguage language) {
        int remaining = participant.getRemainingHealingPotions();
        ItemStack item = remaining > 0
                ? new ItemStack(Material.POTION, Math.min(64, remaining), (short) 16421)
                : new ItemStack(Material.GLASS_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + language.choose("ポーション統計", "Potion Statistics"));
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + language.choose("残りポーション: ", "Remaining Potions: ") + ChatColor.WHITE + remaining);
        lore.add(ChatColor.GRAY + language.choose("回復した体力: ", "Health Healed: ") + ChatColor.WHITE
                + format(participant.getHealedHealth()) + " HP");
        lore.add(ChatColor.GRAY + language.choose("ポーション精度: ", "Potion Accuracy: ") + ChatColor.WHITE
                + format(participant.getPotionAccuracyPercent()) + "%");
        lore.add(ChatColor.GRAY + language.choose("外したポーション: ", "Missed Potions: ") + ChatColor.WHITE
                + participant.getHealingPotionsMissed());
        lore.add(ChatColor.GRAY + language.choose("過剰回復: ", "Overheal: ") + ChatColor.WHITE
                + format(participant.getOverhealedHealth()) + " HP");
        lore.add(ChatColor.GRAY + language.choose("相手を回復した体力: ", "Opponent Health Healed: ") + ChatColor.WHITE
                + format(participant.getOpponentHealedHealth()) + " HP");
        if (remaining == 0) {
            lore.add(ChatColor.GRAY + language.choose("回復ポーションは残っていません。", "No healing potions remaining."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navigationItem(String name, PlayerLanguage language) {
        return new ItemBuilder(Material.ARROW).name(name)
                .lore(language.choose("&7クリックして相手のインベントリを表示します。", "&7Click to view the opponent's inventory."))
                .build();
    }

    private static void fillRow(Inventory inventory, int firstSlot) {
        ItemStack filler = filler();
        for (int slot = firstSlot; slot < firstSlot + 9; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private static String shortName(String name) {
        if (name == null) {
            return "Unknown";
        }
        return name.length() > 20 ? name.substring(0, 20) : name;
    }

    private static final class Holder extends MenuHolder {
        private final MatchResultSelection selection;

        private Holder(int size, String title, MatchResultSelection selection) {
            super(size, title);
            this.selection = selection;
        }
    }
}
