package com.poppy.practice.kit;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.language.PlayerLanguage;
import com.poppy.practice.ui.KitSelectionMenu;
import com.poppy.practice.ui.MenuHolder;
import com.poppy.practice.ui.MenuNavigation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KitLayoutService {
    public static final String EDITOR_TITLE_PREFIX = "Edit Kit: ";
    private static final int STORAGE_SIZE = 36;

    private final PracticePlugin plugin;
    private final KitManager kitManager;
    private final File storageFile;
    private final YamlConfiguration storage;
    private final Map<UUID, EditorSession> editors = new HashMap<UUID, EditorSession>();
    private LanguageService languages;

    public void setLanguageService(LanguageService languages) { this.languages = languages; }
    private PlayerLanguage language(Player player) {
        return languages == null ? PlayerLanguage.JAPANESE : languages.language(player);
    }

    public KitLayoutService(PracticePlugin plugin, KitManager kitManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.storageFile = new File(plugin.getDataFolder(), "kit-layouts.yml");
        this.storage = YamlConfiguration.loadConfiguration(storageFile);
    }

    public void openSelector(Player player) {
        player.openInventory(new KitSelectionMenu(KitSelectionMenu.Purpose.EDIT,
                kitManager.all(), null, player.getUniqueId(), language(player)).getInventory());
    }

    public void openEditor(Player player, Kit kit) {
        // Finish any previous editor before replacing its session.
        player.closeInventory();
        ItemStack[] layout = layoutFor(player.getUniqueId(), kit);
        Inventory inventory = new EditorMenu(kit.getDisplayName(), language(player)).getInventory();
        inventory.setContents(toEditorContents(layout));
        editors.put(player.getUniqueId(), new EditorSession(kit.getId(), inventory));
        player.openInventory(inventory);
        player.sendMessage(ChatColor.RED + language(player).choose("キット編集: ", "Kit Editor: ") + ChatColor.GRAY
                + language(player).choose("左クリックでスタックを持つ・入れ替える。最下段はホットバーです。",
                "Left-click to pick up or swap whole stacks. The bottom row is your hotbar."));
        player.sendMessage(ChatColor.GRAY + language(player).choose("閉じると保存されます。アイテムの個数は変更できません。", "Close the inventory to save. Item amounts cannot change."));
    }

    public void swapEditorSlot(Player player, Inventory inventory, int rawSlot) {
        if (!isEditing(player, inventory) || !MenuNavigation.isTopSlot(inventory, rawSlot)) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        ItemStack cursor = swapWholeStack(contents, rawSlot, player.getItemOnCursor());
        inventory.setContents(contents);
        player.setItemOnCursor(cursor);
        player.updateInventory();
    }

    static ItemStack swapWholeStack(ItemStack[] contents, int slot, ItemStack cursor) {
        if (contents == null || slot < 0 || slot >= contents.length) {
            return cursor;
        }
        ItemStack selected = cloneItem(normalize(contents[slot]));
        contents[slot] = cloneItem(normalize(cursor));
        return selected;
    }

    public boolean isEditing(Player player, Inventory inventory) {
        EditorSession session = editors.get(player.getUniqueId());
        return session != null && session.matches(inventory);
    }

    public boolean closeAndSave(Player player, Inventory inventory) {
        EditorSession session = editors.get(player.getUniqueId());
        if (session == null || !session.matches(inventory)) {
            return false;
        }
        editors.remove(player.getUniqueId());
        boolean cursorReturned = returnCursorToEditor(player, inventory);
        Kit kit = kitManager.get(session.kitId);
        if (kit == null) {
            player.sendMessage(ChatColor.RED + language(player).choose("そのキットは現在使用できません。", "That kit is no longer available."));
            return true;
        }
        int[] permutation = createPermutation(kit.createInventoryContents(),
                fromEditorContents(inventory.getContents()));
        if (!cursorReturned || permutation == null) {
            player.sendMessage(ChatColor.RED
                    + language(player).choose("保存できません: アイテムの配置のみ変更できます。", "Layout not saved: only item positions may be changed."));
            return true;
        }
        String storagePath = path(player.getUniqueId(), kit.getId());
        Object previous = storage.get(storagePath);
        storage.set(storagePath, asList(permutation));
        try {
            storage.save(storageFile);
            player.sendMessage(ChatColor.RED + kit.getDisplayName() + language(player).choose(" の配置を保存しました。", " layout saved."));
        } catch (IOException exception) {
            storage.set(storagePath, previous);
            plugin.getLogger().severe("Could not save kit layouts: " + exception.getMessage());
            player.sendMessage(ChatColor.RED + language(player).choose("キット配置を保存できませんでした。", "Could not save your kit layout."));
        }
        return true;
    }

    public void shutdown() {
        for (UUID playerId : new ArrayList<UUID>(editors.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Inventory inventory = player.getOpenInventory().getTopInventory();
                if (isEditing(player, inventory)) {
                    closeAndSave(player, inventory);
                    player.closeInventory();
                }
            }
            editors.remove(playerId);
        }
    }

    private static boolean returnCursorToEditor(Player player, Inventory inventory) {
        ItemStack cursor = normalize(player.getItemOnCursor());
        if (cursor == null) {
            return true;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (normalize(inventory.getItem(slot)) == null) {
                inventory.setItem(slot, cursor);
                player.setItemOnCursor(null);
                return true;
            }
        }
        // Editor stacks are preview copies and must never leak into the lobby.
        player.setItemOnCursor(null);
        return false;
    }

    public void applyLayout(Player player, Kit kit) {
        kit.apply(player);
        player.getInventory().setContents(layoutFor(player.getUniqueId(), kit));
        player.updateInventory();
    }

    ItemStack[] layoutFor(UUID playerId, Kit kit) {
        ItemStack[] defaults = kit.createInventoryContents();
        List<Integer> saved = storage.getIntegerList(path(playerId, kit.getId()));
        if (saved.size() != STORAGE_SIZE) {
            return cloneContents(defaults);
        }
        int[] permutation = new int[STORAGE_SIZE];
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            permutation[slot] = saved.get(slot);
        }
        ItemStack[] reordered = reorder(defaults, permutation);
        return reordered == null ? cloneContents(defaults) : reordered;
    }

    static int[] createPermutation(ItemStack[] defaults, ItemStack[] edited) {
        if (defaults == null || edited == null
                || defaults.length != STORAGE_SIZE || edited.length != STORAGE_SIZE) {
            return null;
        }
        boolean[] used = new boolean[STORAGE_SIZE];
        int[] permutation = new int[STORAGE_SIZE];
        for (int targetSlot = 0; targetSlot < STORAGE_SIZE; targetSlot++) {
            ItemStack editedItem = normalize(edited[targetSlot]);
            if (editedItem == null) {
                permutation[targetSlot] = -1;
                continue;
            }
            int sourceSlot = findMatchingUnused(defaults, editedItem, used);
            if (sourceSlot < 0) {
                return null;
            }
            used[sourceSlot] = true;
            permutation[targetSlot] = sourceSlot;
        }
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            if (normalize(defaults[slot]) != null && !used[slot]) {
                return null;
            }
        }
        return permutation;
    }

    static ItemStack[] reorder(ItemStack[] defaults, int[] permutation) {
        if (defaults == null || permutation == null
                || defaults.length != STORAGE_SIZE || permutation.length != STORAGE_SIZE) {
            return null;
        }
        boolean[] used = new boolean[STORAGE_SIZE];
        ItemStack[] result = new ItemStack[STORAGE_SIZE];
        for (int targetSlot = 0; targetSlot < STORAGE_SIZE; targetSlot++) {
            int sourceSlot = permutation[targetSlot];
            if (sourceSlot == -1) {
                continue;
            }
            if (sourceSlot < 0 || sourceSlot >= STORAGE_SIZE || used[sourceSlot]
                    || normalize(defaults[sourceSlot]) == null) {
                return null;
            }
            used[sourceSlot] = true;
            result[targetSlot] = defaults[sourceSlot].clone();
        }
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            if (normalize(defaults[slot]) != null && !used[slot]) {
                return null;
            }
        }
        return result;
    }

    static ItemStack[] toEditorContents(ItemStack[] storageContents) {
        ItemStack[] editor = new ItemStack[STORAGE_SIZE];
        for (int slot = 9; slot < STORAGE_SIZE; slot++) {
            editor[slot - 9] = cloneItem(storageContents[slot]);
        }
        for (int slot = 0; slot < 9; slot++) {
            editor[27 + slot] = cloneItem(storageContents[slot]);
        }
        return editor;
    }

    static ItemStack[] fromEditorContents(ItemStack[] editorContents) {
        ItemStack[] contents = new ItemStack[STORAGE_SIZE];
        for (int slot = 9; slot < STORAGE_SIZE; slot++) {
            contents[slot] = cloneItem(editorContents[slot - 9]);
        }
        for (int slot = 0; slot < 9; slot++) {
            contents[slot] = cloneItem(editorContents[27 + slot]);
        }
        return contents;
    }

    private static int findMatchingUnused(ItemStack[] defaults, ItemStack wanted, boolean[] used) {
        for (int slot = 0; slot < defaults.length; slot++) {
            ItemStack candidate = normalize(defaults[slot]);
            if (!used[slot] && candidate != null && sameStack(candidate, wanted)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        if (first.getType() != second.getType()
                || first.getDurability() != second.getDurability()
                || first.getAmount() != second.getAmount()) {
            return false;
        }
        // ItemMeta comparison requires Bukkit's ItemFactory, which is unavailable
        // in the isolated unit-test JVM. A running server always takes this branch.
        return Bukkit.getServer() == null || first.isSimilar(second);
    }

    private static ItemStack normalize(ItemStack item) {
        return item == null || item.getType() == org.bukkit.Material.AIR ? null : item;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            cloned[slot] = cloneItem(contents[slot]);
        }
        return cloned;
    }

    private static List<Integer> asList(int[] values) {
        List<Integer> result = new ArrayList<Integer>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private static String path(UUID playerId, String kitId) {
        return "players." + playerId.toString() + "." + kitId.toLowerCase();
    }

    private static String editorTitle(String displayName) {
        String title = EDITOR_TITLE_PREFIX + displayName;
        return title.length() <= 32 ? title : title.substring(0, 32);
    }

    private static final class EditorSession {
        private final String kitId;
        private final MenuHolder holder;

        private EditorSession(String kitId, Inventory inventory) {
            this.kitId = kitId;
            this.holder = (MenuHolder) inventory.getHolder();
        }

        private boolean matches(Inventory inventory) {
            return holder.owns(inventory);
        }
    }

    private static final class EditorMenu extends MenuHolder {
        private EditorMenu(String kitName, PlayerLanguage language) {
            super(STORAGE_SIZE, language.choose("キット編集: " + kitName, editorTitle(kitName)));
        }
    }
}
