package com.poppy.practice.bot;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.command.BotCommand;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.ui.KitSelectionMenu;
import com.poppy.practice.cosmetic.PreferencesService;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.language.PlayerLanguage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real setup entrypoints with headless inventories; no arenas or NPCs are created. */
public class BotSetupFlowTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static Field serverField;
    private static Object previousServer;

    @BeforeClass
    public static void installHeadlessInventories() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        Server server = mock(Server.class);
        when(server.getItemFactory()).thenReturn(CraftItemFactory.instance());
        when(server.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    InventoryHolder holder = (InventoryHolder) invocation.getArguments()[0];
                    int size = (Integer) invocation.getArguments()[1];
                    String title = (String) invocation.getArguments()[2];
                    ItemStack[] items = new ItemStack[size];
                    Inventory inventory = mock(Inventory.class);
                    when(inventory.getHolder()).thenReturn(holder);
                    when(inventory.getSize()).thenReturn(size);
                    when(inventory.getTitle()).thenReturn(title);
                    when(inventory.getItem(anyInt())).thenAnswer(read -> items[(Integer) read.getArguments()[0]]);
                    doAnswer(write -> {
                        items[(Integer) write.getArguments()[0]] = (ItemStack) write.getArguments()[1];
                        return null;
                    }).when(inventory).setItem(anyInt(), any(ItemStack.class));
                    doAnswer(clear -> {
                        Arrays.fill(items, null);
                        return null;
                    }).when(inventory).clear();
                    return inventory;
                });
        serverField.set(null, server);
    }

    @AfterClass
    public static void restoreBukkitServer() throws Exception {
        if (serverField != null) {
            serverField.set(null, previousServer);
        }
    }

    @Test
    public void botCommandAliasesAllOpenKitSelectionWithoutStartingOrChangingProfile() throws Exception {
        for (String[] args : new String[][] {{}, {"start"}, {"settings"}, {"START"}}) {
            Fixture fixture = new Fixture();
            BotCommand command = new BotCommand(fixture.service);

            assertTrue(command.onCommand(fixture.player, null, "bot", args));

            KitSelectionMenu menu = (KitSelectionMenu) fixture.opened.getHolder();
            assertEquals(KitSelectionMenu.Purpose.BOT, menu.getPurpose());
            assertEquals("nodebuff", menu.kitIdAt(0));
            assertEquals("boxing", menu.kitIdAt(1));
            assertEquals("combo", menu.kitIdAt(2));
            assertEquals(Material.POTION, fixture.opened.getItem(0).getType());
            assertEquals(Material.DIAMOND_SWORD, fixture.opened.getItem(1).getType());
            assertEquals(Material.GOLDEN_APPLE, fixture.opened.getItem(2).getType());
            assertEquals(1, fixture.opened.getItem(2).getDurability());
            fixture.assertProfileUnchanged(PlayerState.LOBBY);
        }
    }

    @Test
    public void setupMenuOnlyListsTheThreeSupportedBotKits() throws Exception {
        Fixture fixture = new Fixture();
        Kit custom = mock(Kit.class);
        when(custom.getId()).thenReturn("custom");
        fixture.kits.register(custom);

        fixture.service.openSettings(fixture.player);

        KitSelectionMenu menu = (KitSelectionMenu) fixture.opened.getHolder();
        assertEquals("combo", menu.kitIdAt(2));
        assertNull(menu.kitIdAt(3));
        assertEquals(18, fixture.opened.getSize());
    }

    @Test
    public void selectingAnyKitOnlyOpensSettingsAndPreservesQueueSelection() throws Exception {
        for (String kitId : new String[] {"nodebuff", "boxing", "combo"}) {
            Fixture fixture = new Fixture();
            fixture.profile.setState(PlayerState.QUEUE);

            fixture.service.openSettings(fixture.player, kitId);

            assertTrue(fixture.opened.getHolder() instanceof BotSettingsMenu);
            assertEquals(kitId, ((BotSettingsMenu) fixture.opened.getHolder()).getKitId());
            fixture.assertProfileUnchanged(PlayerState.QUEUE);
        }
    }

    @Test
    public void queuedPlayersCanBrowseTheKitSelectorWithoutLeavingQueue() throws Exception {
        Fixture fixture = new Fixture();
        fixture.profile.setState(PlayerState.QUEUE);

        fixture.service.openSettings(fixture.player);

        assertTrue(fixture.opened.getHolder() instanceof KitSelectionMenu);
        fixture.assertProfileUnchanged(PlayerState.QUEUE);
    }

    @Test
    public void refreshingSharedSettingsDoesNotChangeAnyViewersSelectedKit() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Fixture third = new Fixture();
        first.service.openSettings(first.player, "nodebuff");
        first.service.openSettings(second.player, "boxing");
        first.service.openSettings(third.player, "combo");
        doReturn(Arrays.asList(first.player, second.player, third.player)).when(Bukkit.getServer()).getOnlinePlayers();
        first.config.set("bot.movement.strafe-enabled", false);

        first.service.refreshSettings(first.opened);

        assertEquals("nodebuff", ((BotSettingsMenu) first.opened.getHolder()).getKitId());
        assertEquals("boxing", ((BotSettingsMenu) second.opened.getHolder()).getKitId());
        assertEquals("combo", ((BotSettingsMenu) third.opened.getHolder()).getKitId());
        assertEquals(Material.GOLDEN_APPLE, third.opened.getItem(4).getType());
        assertTrue(third.opened.getItem(49).getItemMeta().getDisplayName().contains("Combo Bot対戦を開始"));
        assertEquals(Material.STAINED_GLASS_PANE,
                third.opened.getItem(BotSetting.HEALING_ENABLED.getSlot()).getType());
        assertFalse(first.config.getBoolean("bot.movement.strafe-enabled"));
        assertEquals(0, first.service.size());
        assertEquals("original-selected-kit", first.profile.getSelectedKitId());
        assertEquals("original-selected-kit", second.profile.getSelectedKitId());
        assertEquals("original-selected-kit", third.profile.getSelectedKitId());
    }

    @Test
    public void comboShowsDisabledPotionControlsWithoutChangingTheirSavedValues() throws Exception {
        Fixture fixture = new Fixture();
        fixture.config.set("bot.healing.enabled", false);
        fixture.config.set("bot.healing.health-threshold", 13.0D);
        String before = fixture.config.saveToString();

        fixture.service.openSettings(fixture.player, "combo");

        BotSettingsMenu menu = (BotSettingsMenu) fixture.opened.getHolder();
        for (BotSetting setting : BotSetting.values()) {
            if (setting.getCategory() == BotSettingCategory.HEALING) {
                ItemStack item = fixture.opened.getItem(setting.getSlot());
                assertEquals(Material.STAINED_GLASS_PANE, item.getType());
                assertEquals(7, item.getDurability());
                assertTrue(item.getItemMeta().getLore().toString().contains("Combo では使用しません"));
                assertTrue(item.getItemMeta().getLore().toString().contains("金リンゴ"));
                assertNull(menu.getEditableSetting(setting.getSlot()));
            } else {
                assertEquals(setting, menu.getEditableSetting(setting.getSlot()));
            }
        }
        assertEquals(before, fixture.config.saveToString());
        fixture.assertProfileUnchanged(PlayerState.LOBBY);
    }

    @Test public void savedEnglishLocaleAppliesToBothBotSetupStepsWhileDefaultRemainsJapanese() throws Exception {
        Fixture fixture = new Fixture();
        PreferencesService preferences = new PreferencesService(temporary.getRoot(), Logger.getAnonymousLogger());
        fixture.service.setLanguageService(new LanguageService(preferences));
        fixture.service.openSettings(fixture.player);
        assertTrue(fixture.opened.getTitle().contains("キット選択"));
        assertTrue(preferences.setLanguage(fixture.player.getUniqueId(), PlayerLanguage.ENGLISH));
        fixture.service.openSettings(fixture.player);
        assertEquals("Bot Fight | Select a Kit", fixture.opened.getTitle());
        fixture.service.openSettings(fixture.player, "combo");
        assertEquals("Bot Settings | Combo", fixture.opened.getTitle());
        assertTrue(fixture.opened.getItem(49).getItemMeta().getDisplayName().contains("Start Combo"));
        assertTrue(fixture.opened.getItem(BotSetting.HEALING_ENABLED.getSlot())
                .getItemMeta().getLore().toString().contains("Not used in Combo"));
    }

    @Test
    public void countdownFightAndDebugRoomCannotOpenEitherSetupStep() throws Exception {
        for (PlayerState state : new PlayerState[] {PlayerState.STARTING, PlayerState.FIGHTING, PlayerState.DEBUG}) {
            Fixture fixture = new Fixture();
            fixture.profile.setState(state);

            fixture.service.openSettings(fixture.player);
            for (String kitId : new String[] {"nodebuff", "boxing", "combo"}) {
                fixture.service.openSettings(fixture.player, kitId);
            }

            assertNull(fixture.opened);
            verify(fixture.player, times(4)).sendMessage(anyString());
            fixture.assertProfileUnchanged(state);
        }
    }

    @Test
    public void invalidSelectionsNeverFallBackOrLeaveQueue() throws Exception {
        for (String kitId : new String[] {null, "", "unknown", "custom", " boxing "}) {
            Fixture fixture = new Fixture();
            fixture.profile.setState(PlayerState.QUEUE);

            fixture.service.openSettings(fixture.player, kitId);
            assertFalse(fixture.service.startSelected(fixture.player, kitId));

            assertNull(fixture.opened);
            verify(fixture.player, times(2)).sendMessage(anyString());
            fixture.assertProfileUnchanged(PlayerState.QUEUE);
        }
    }

    @Test
    public void aMissingSupportedKitCannotOpenSettingsOrStartMatch() throws Exception {
        Fixture fixture = new Fixture();
        Field kitsField = KitManager.class.getDeclaredField("kits");
        kitsField.setAccessible(true);
        ((java.util.Map<?, ?>) kitsField.get(fixture.kits)).clear();

        fixture.service.openSettings(fixture.player, "boxing");
        assertFalse(fixture.service.startSelected(fixture.player, "boxing"));

        assertNull(fixture.opened);
        fixture.assertProfileUnchanged(PlayerState.LOBBY);
    }

    @Test
    public void onlySupportedKitIdsAreAcceptedCaseInsensitively() {
        assertTrue(BotService.isSupportedKit("nodebuff"));
        assertTrue(BotService.isSupportedKit("Boxing"));
        assertTrue(BotService.isSupportedKit("CoMbO"));
        assertFalse(BotService.isSupportedKit(null));
        assertFalse(BotService.isSupportedKit("debug"));
        assertFalse(BotService.isSupportedKit("custom"));
    }

    private static final class Fixture {
        private final YamlConfiguration config = new YamlConfiguration();
        private final ProfileManager profiles = new ProfileManager();
        private final KitManager kits = new KitManager();
        private final Player player = mock(Player.class);
        private final PlayerProfile profile;
        private final BotService service;
        private Inventory opened;

        private Fixture() throws Exception {
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            doAnswer(invocation -> {
                opened = (Inventory) invocation.getArguments()[0];
                return null;
            }).when(player).openInventory(any(Inventory.class));
            InventoryView view = mock(InventoryView.class);
            when(view.getTopInventory()).thenAnswer(invocation -> opened);
            when(player.getOpenInventory()).thenReturn(view);
            profile = profiles.create(id);
            profile.setSelectedKitId("original-selected-kit");
            profile.setQueuedKitId("original-queued-kit");
            BotSettings.ensureManagedDefaults(config);
            // Only the in-memory getConfig path is needed; Bukkit's plugin loader is absent.
            PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
            Field configField = JavaPlugin.class.getDeclaredField("newConfig");
            configField.setAccessible(true);
            configField.set(plugin, config);
            service = new BotService(plugin, profiles, null, kits,
                    null, null, null, null, null, null);
        }

        private void assertProfileUnchanged(PlayerState state) {
            assertEquals(state, profile.getState());
            assertEquals("original-selected-kit", profile.getSelectedKitId());
            assertEquals("original-queued-kit", profile.getQueuedKitId());
            assertEquals(0, service.size());
            assertNull(service.getByPlayer(player.getUniqueId()));
        }
    }
}
