package com.poppy.practice.listener;

import com.poppy.practice.bot.BotSetting;
import com.poppy.practice.bot.BotSettingsMenu;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.service.MatchResultService;
import com.poppy.practice.ui.KitSelectionMenu;
import com.poppy.practice.ui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises the real menu holders and click listener with a queued main-thread scheduler. */
public final class BotMenuFlowTest {
    private Field serverField;
    private Object previousServer;
    private final ProfileManager profiles = new ProfileManager();
    private final KitManager kits = new KitManager();
    private final YamlConfiguration config = new YamlConfiguration();
    private final List<Runnable> tasks = new ArrayList<Runnable>();
    private Player player;
    private PlayerProfile profile;
    private InventoryView currentView;
    private InventoryListener.BotMenuActions botActions;
    private InventoryListener listener;
    private int certificationOpens;
    private Inventory certificationInventory;

    @Before
    public void installMockServer() throws Exception {
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask((Plugin) isNull(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    tasks.add((Runnable) invocation.getArguments()[1]);
                    return mock(BukkitTask.class);
                });
        when(server.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    Inventory inventory = mock(Inventory.class);
                    Map<Integer, ItemStack> items = new HashMap<Integer, ItemStack>();
                    when(inventory.getHolder()).thenReturn((InventoryHolder) invocation.getArguments()[0]);
                    when(inventory.getSize()).thenReturn((Integer) invocation.getArguments()[1]);
                    when(inventory.getTitle()).thenReturn((String) invocation.getArguments()[2]);
                    doAnswer(update -> {
                        items.put((Integer) update.getArguments()[0], (ItemStack) update.getArguments()[1]);
                        return null;
                    }).when(inventory).setItem(anyInt(), any(ItemStack.class));
                    when(inventory.getItem(anyInt())).thenAnswer(read -> items.get(read.getArguments()[0]));
                    return inventory;
                });
        ItemFactory itemFactory = mock(ItemFactory.class);
        when(server.getItemFactory()).thenReturn(itemFactory);
        when(itemFactory.getItemMeta(any(Material.class))).thenAnswer(invocation -> {
            ItemMeta meta = mock(ItemMeta.class);
            when(meta.clone()).thenReturn(meta);
            return meta;
        });
        when(itemFactory.isApplicable(any(ItemMeta.class), any(Material.class))).thenReturn(true);
        when(itemFactory.asMetaFor(any(ItemMeta.class), any(Material.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);

        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenAnswer(invocation -> currentView);
        when(player.openInventory(any(Inventory.class))).thenAnswer(invocation -> {
            display((Inventory) invocation.getArguments()[0]);
            return currentView;
        });
        doAnswer(invocation -> {
            display(mock(Inventory.class));
            return null;
        }).when(player).closeInventory();
        profile = profiles.create(player.getUniqueId());
        profile.setSelectedKitId("nodebuff");

        botActions = mock(InventoryListener.BotMenuActions.class);
        doAnswer(invocation -> {
            openSelector();
            return null;
        }).when(botActions).openSettings(player);
        doAnswer(invocation -> {
            openSettings((String) invocation.getArguments()[1]);
            return null;
        }).when(botActions).openSettings(eq(player), anyString());
        // Queue and kit-editor collaborators intentionally remain null: browsing
        // a BOT menu must never call either workflow or mutate queue membership.
        listener = new InventoryListener(null, profiles, kits, null, null,
                botActions, null, new MatchResultService(profiles));
        listener.setCertificationMenuOpener(viewer -> {
            assertSame(player, viewer);
            certificationOpens++;
            certificationInventory = new MenuHolder(18, "Certification Queue | Kits") { }.getInventory();
            player.openInventory(certificationInventory);
        });
    }

    @After
    public void restoreServer() throws Exception {
        if (serverField != null) serverField.set(null, previousServer);
    }

    @Test
    public void selectingEachKitOpensItsSettingsNextTickWithoutStartingAMatch() {
        for (int slot = 0; slot < 3; slot++) {
            KitSelectionMenu selector = openSelector();
            String selectedKit = selector.kitIdAt(slot);

            assertTrue(click(slot, ClickType.LEFT).isCancelled());
            assertSame(selector, currentView.getTopInventory().getHolder());
            verify(botActions, never()).openSettings(player, selectedKit);
            tick();

            assertEquals(selectedKit, settings().getKitId());
            verify(botActions).openSettings(player, selectedKit);
            verify(botActions, never()).startSelected(any(Player.class), anyString());
            assertEquals(PlayerState.LOBBY, profile.getState());
            assertEquals("nodebuff", profile.getSelectedKitId());
            assertNull(profile.getQueuedKitId());
        }
    }

    @Test
    public void startUsesExactlyTheKitStoredInTheSettingsHolder() {
        for (String kitId : new String[] { "nodebuff", "boxing", "combo" }) {
            openSettings(kitId);
            click(49, ClickType.LEFT);
            verify(botActions, never()).startSelected(player, kitId);

            tick();

            verify(botActions).startSelected(player, kitId);
            assertFalse(currentView.getTopInventory().getHolder() instanceof BotSettingsMenu);
        }
    }

    @Test
    public void queuedPlayerCanBrowseBackAndCloseWithoutChangingQueueSelection() {
        profile.setState(PlayerState.QUEUE);
        profile.setQueuedKitId("nodebuff");
        openSelector();
        click(1, ClickType.LEFT);
        tick();
        assertEquals("boxing", settings().getKitId());
        assertQueuedUnchanged();

        click(45, ClickType.LEFT);
        assertTrue(currentView.getTopInventory().getHolder() instanceof BotSettingsMenu);
        tick();
        assertTrue(currentView.getTopInventory().getHolder() instanceof KitSelectionMenu);
        assertQueuedUnchanged();

        int closeSlot = ((KitSelectionMenu) currentView.getTopInventory().getHolder()).getCloseSlot();
        click(closeSlot, ClickType.LEFT);
        tick();
        assertQueuedUnchanged();
        verify(botActions, never()).startSelected(any(Player.class), anyString());
    }

    @Test
    public void queuedSelectionIsPreservedUntilTheExplicitStartActionRuns() {
        profile.setState(PlayerState.QUEUE);
        profile.setQueuedKitId("nodebuff");
        openSettings("boxing");

        click(49, ClickType.LEFT);
        assertQueuedUnchanged();
        verify(botActions, never()).startSelected(any(Player.class), anyString());
        tick();

        verify(botActions).startSelected(player, "boxing");
    }

    @Test
    public void twoKitClicksBeforeTheNextTickOnlyOpenTheFirstSelection() {
        openSelector();
        click(1, ClickType.LEFT);
        click(0, ClickType.LEFT);

        tick();

        assertEquals("boxing", settings().getKitId());
        verify(botActions).openSettings(player, "boxing");
        verify(botActions, never()).openSettings(player, "nodebuff");
        verify(botActions, never()).startSelected(any(Player.class), anyString());
    }

    @Test
    public void doubleStartClickOnlyStartsOnceAfterClosingTheOldMenu() {
        openSettings("boxing");
        click(49, ClickType.LEFT);
        click(49, ClickType.LEFT);

        tick();

        verify(botActions, times(1)).startSelected(player, "boxing");
        verify(player, times(1)).closeInventory();
    }

    @Test
    public void replacementMenuWithTheSameTitleDoesNotAuthorizeAStaleStart() {
        openSettings("boxing");
        Inventory oldInventory = currentView.getTopInventory();
        click(49, ClickType.LEFT);
        openSettings("boxing");
        assertEquals(oldInventory.getTitle(), currentView.getTopInventory().getTitle());
        assertNotSame(oldInventory.getHolder(), currentView.getTopInventory().getHolder());

        tick();

        verify(botActions, never()).startSelected(any(Player.class), anyString());
        verify(player, never()).closeInventory();
    }

    @Test
    public void matchStartingBetweenClickAndTickPreventsSettingsAndStartTransitions() {
        openSelector();
        click(1, ClickType.LEFT);
        profile.setState(PlayerState.STARTING);
        tick();
        verify(botActions, never()).openSettings(eq(player), anyString());

        profile.setState(PlayerState.LOBBY);
        openSettings("boxing");
        click(49, ClickType.LEFT);
        profile.setState(PlayerState.FIGHTING);
        tick();
        verify(botActions, never()).startSelected(any(Player.class), anyString());
        verify(player, never()).closeInventory();
    }

    @Test
    public void disconnectBetweenClickAndTickPreventsEveryTransition() {
        openSettings("boxing");
        click(49, ClickType.LEFT);
        when(player.isOnline()).thenReturn(false);

        tick();

        verifyZeroInteractions(botActions);
        verify(player, never()).closeInventory();
    }

    @Test
    public void boxingCannotAdjustItsDisabledHealthOrHealingSettings() {
        openSettings("boxing");
        for (BotSetting setting : BotSetting.values()) {
            if (!BotSettingsMenu.isSettingAvailable(setting, "boxing")) {
                for (ClickType click : new ClickType[] {
                        ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
                        ClickType.SHIFT_RIGHT, ClickType.MIDDLE }) {
                    assertTrue(click(setting.getSlot(), click).isCancelled());
                }
            }
        }

        tick();

        verifyZeroInteractions(botActions);
    }

    @Test
    public void bothKitsCanAdjustMovementAndOnlyNoDebuffCanAdjustHealing() {
        when(botActions.adjustSetting(any(BotSetting.class), any(ClickType.class))).thenReturn(true);
        openSettings("boxing");
        Inventory boxing = currentView.getTopInventory();
        click(BotSetting.STRAFE_ENABLED.getSlot(), ClickType.LEFT);
        verify(botActions).adjustSetting(BotSetting.STRAFE_ENABLED, ClickType.LEFT);
        verify(botActions).refreshSettings(boxing);
        assertEquals("boxing", settings().getKitId());

        openSettings("nodebuff");
        Inventory nodebuff = currentView.getTopInventory();
        click(BotSetting.HEALING_ENABLED.getSlot(), ClickType.RIGHT);
        verify(botActions).adjustSetting(BotSetting.HEALING_ENABLED, ClickType.RIGHT);
        verify(botActions).refreshSettings(nodebuff);
        assertEquals("nodebuff", settings().getKitId());
        verify(botActions, never()).startSelected(any(Player.class), anyString());
    }

    @Test
    public void resetRequiresShiftLeftAndKeepsTheSelectedKit() {
        openSettings("boxing");
        Inventory menu = currentView.getTopInventory();
        for (ClickType click : new ClickType[] { ClickType.LEFT, ClickType.RIGHT,
                ClickType.SHIFT_RIGHT, ClickType.MIDDLE, ClickType.DOUBLE_CLICK }) {
            click(51, click);
        }
        verify(botActions, never()).resetSettings();

        click(51, ClickType.SHIFT_LEFT);

        verify(botActions).resetSettings();
        verify(botActions).refreshSettings(menu);
        assertEquals("boxing", settings().getKitId());
        assertEquals(0, tasks.size());
    }

    @Test
    public void bottomOutsideAndNonLeftActionClicksCannotNavigateOrStart() {
        openSelector();
        for (int rawSlot : new int[] { -999, 18, 54, 89 }) {
            click(rawSlot, ClickType.LEFT);
        }
        for (ClickType click : new ClickType[] { ClickType.RIGHT, ClickType.SHIFT_LEFT,
                ClickType.SHIFT_RIGHT, ClickType.MIDDLE, ClickType.DOUBLE_CLICK }) {
            click(1, click);
        }
        openSettings("boxing");
        for (int rawSlot : new int[] { -999, 54, 89 }) {
            click(rawSlot, ClickType.LEFT);
        }
        for (int actionSlot : new int[] { 45, 49, 53 }) {
            for (ClickType click : new ClickType[] { ClickType.RIGHT,
                    ClickType.SHIFT_LEFT, ClickType.MIDDLE, ClickType.DOUBLE_CLICK }) {
                click(actionSlot, click);
            }
        }

        tick();

        verifyZeroInteractions(botActions);
        verify(player, never()).closeInventory();
    }

    @Test
    public void certificationButtonExistsOnlyInTheQueueFooterWithoutReplacingKitsOrClose() {
        for (KitSelectionMenu.Purpose purpose : KitSelectionMenu.Purpose.values()) {
            KitSelectionMenu selector = new KitSelectionMenu(purpose, kits.all());
            int footerEnd = selector.getInventory().getSize() - 1;
            assertEquals(3, kits.all().size());
            for (int slot = 0; slot < 3; slot++) {
                assertNotNull(selector.kitIdAt(slot));
            }
            assertNull(selector.kitIdAt(footerEnd));
            assertEquals(Material.BEDROCK,
                    selector.getInventory().getItem(selector.getCloseSlot()).getType());
            if (purpose == KitSelectionMenu.Purpose.QUEUE) {
                assertEquals(footerEnd, selector.getCertificationSlot());
                assertNotEquals(selector.getCloseSlot(), selector.getCertificationSlot());
                assertEquals(Material.EXP_BOTTLE,
                        selector.getInventory().getItem(selector.getCertificationSlot()).getType());
            } else {
                assertEquals(-1, selector.getCertificationSlot());
                assertEquals(Material.STAINED_GLASS_PANE,
                        selector.getInventory().getItem(footerEnd).getType());
            }
        }
    }

    @Test
    public void certificationQueueOpensNextTickWithoutJoiningRankedOrStartingPracticeBots() {
        KitSelectionMenu selector = openQueueSelector();
        click(selector.getCertificationSlot(), ClickType.LEFT);
        assertEquals(0, certificationOpens);
        assertSame(selector, currentView.getTopInventory().getHolder());

        tick();

        assertEquals(1, certificationOpens);
        assertSame(certificationInventory, currentView.getTopInventory());
        assertEquals(PlayerState.LOBBY, profile.getState());
        assertEquals("nodebuff", profile.getSelectedKitId());
        assertNull(profile.getQueuedKitId());
        verifyZeroInteractions(botActions);
    }

    @Test
    public void doubleCertificationClickOnlyOpensTheFirstNewMenu() {
        KitSelectionMenu selector = openQueueSelector();
        click(selector.getCertificationSlot(), ClickType.LEFT);
        click(selector.getCertificationSlot(), ClickType.LEFT);

        tick();

        assertEquals(1, certificationOpens);
        assertSame(certificationInventory, currentView.getTopInventory());
        verifyZeroInteractions(botActions);
    }

    @Test
    public void replacedQueueMenuDoesNotAuthorizeAStaleCertificationClick() {
        KitSelectionMenu previous = openQueueSelector();
        click(previous.getCertificationSlot(), ClickType.LEFT);
        KitSelectionMenu replacement = openQueueSelector();
        assertEquals(previous.getInventory().getTitle(), replacement.getInventory().getTitle());

        tick();

        assertEquals(0, certificationOpens);
        assertSame(replacement, currentView.getTopInventory().getHolder());
        verifyZeroInteractions(botActions);
    }

    @Test
    public void queuedPlayerCannotUseCertificationShortcutOrLoseQueueMembership() {
        profile.setState(PlayerState.QUEUE);
        profile.setQueuedKitId("nodebuff");
        KitSelectionMenu selector = openQueueSelector();

        click(selector.getCertificationSlot(), ClickType.LEFT);
        tick();

        assertEquals(0, certificationOpens);
        assertQueuedUnchanged();
        verifyZeroInteractions(botActions);
    }

    @Test
    public void leavingLobbyBetweenCertificationClickAndTickPreventsNavigation() {
        for (PlayerState state : PlayerState.values()) {
            if (state == PlayerState.LOBBY) continue;
            profile.setState(PlayerState.LOBBY);
            KitSelectionMenu selector = openQueueSelector();
            click(selector.getCertificationSlot(), ClickType.LEFT);
            profile.setState(state);

            tick();

            assertEquals("Certification must remain lobby-only for " + state, 0, certificationOpens);
            assertSame(selector, currentView.getTopInventory().getHolder());
            assertEquals(state, profile.getState());
        }
        verifyZeroInteractions(botActions);
    }

    @Test
    public void disconnectBetweenCertificationClickAndTickPreventsNavigation() {
        KitSelectionMenu selector = openQueueSelector();
        click(selector.getCertificationSlot(), ClickType.LEFT);
        when(player.isOnline()).thenReturn(false);

        tick();

        assertEquals(0, certificationOpens);
        assertSame(selector, currentView.getTopInventory().getHolder());
        verifyZeroInteractions(botActions);
    }

    @Test
    public void unsupportedCertificationClicksAndOtherMenusCannotOpenCertification() {
        KitSelectionMenu queue = openQueueSelector();
        for (ClickType type : new ClickType[] { ClickType.RIGHT, ClickType.SHIFT_LEFT,
                ClickType.SHIFT_RIGHT, ClickType.MIDDLE, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP }) {
            click(queue.getCertificationSlot(), type);
        }
        for (int slot : new int[] { -999, queue.getInventory().getSize(), 54, 89 }) {
            click(slot, ClickType.LEFT);
        }
        tick();
        assertEquals(0, certificationOpens);

        for (KitSelectionMenu.Purpose purpose : new KitSelectionMenu.Purpose[] {
                KitSelectionMenu.Purpose.BOT, KitSelectionMenu.Purpose.EDIT }) {
            KitSelectionMenu other = new KitSelectionMenu(purpose, kits.all());
            display(other.getInventory());
            click(other.getInventory().getSize() - 1, ClickType.LEFT);
            tick();
            assertEquals(0, certificationOpens);
            assertSame(other, currentView.getTopInventory().getHolder());
        }
        verifyZeroInteractions(botActions);
    }

    private KitSelectionMenu openQueueSelector() {
        KitSelectionMenu selector = new KitSelectionMenu(KitSelectionMenu.Purpose.QUEUE, kits.all());
        display(selector.getInventory());
        return selector;
    }

    private KitSelectionMenu openSelector() {
        KitSelectionMenu selector = new KitSelectionMenu(KitSelectionMenu.Purpose.BOT, kits.all());
        display(selector.getInventory());
        return selector;
    }

    private void openSettings(String kitId) {
        BotSettingsMenu.open(player, config, kits.get(kitId));
    }

    private BotSettingsMenu settings() {
        return (BotSettingsMenu) currentView.getTopInventory().getHolder();
    }

    private void display(Inventory inventory) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        Inventory bottom = mock(Inventory.class);
        when(bottom.getSize()).thenReturn(36);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(view.getPlayer()).thenReturn(player);
        when(view.getType()).thenReturn(InventoryType.CHEST);
        currentView = view;
    }

    private InventoryClickEvent click(int rawSlot, ClickType click) {
        InventoryClickEvent event = new InventoryClickEvent(currentView,
                InventoryType.SlotType.CONTAINER, rawSlot, click, InventoryAction.PICKUP_ALL);
        listener.onClick(event);
        assertTrue("Menu interaction must never move items", event.isCancelled());
        return event;
    }

    private void tick() {
        List<Runnable> pending = new ArrayList<Runnable>(tasks);
        tasks.clear();
        for (Runnable task : pending) task.run();
    }

    private void assertQueuedUnchanged() {
        assertEquals(PlayerState.QUEUE, profile.getState());
        assertEquals("nodebuff", profile.getQueuedKitId());
        assertEquals("nodebuff", profile.getSelectedKitId());
    }
}
