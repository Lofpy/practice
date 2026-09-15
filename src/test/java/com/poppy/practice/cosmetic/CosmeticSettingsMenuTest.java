package com.poppy.practice.cosmetic;

import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CosmeticSettingsMenuTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void defaultAndAvailableEffectsHaveUniqueTopInventorySlots() {
        assertEquals(KillEffect.LIGHTNING, KillEffect.atSlot(11));
        assertEquals(KillEffect.EXPLOSION, KillEffect.atSlot(13));
        assertEquals(KillEffect.REDSTONE, KillEffect.atSlot(15));
        assertNull(KillEffect.atSlot(-999));
        assertNull(KillEffect.atSlot(27));
        assertNull(KillEffect.atSlot(22));
    }

    @Test
    public void leftClickChangesOnlyTheViewersSavedEffect() {
        Fixture fixture = new Fixture();
        fixture.menu.open(fixture.player);
        assertEquals(KillEffect.LIGHTNING, fixture.rendered);
        InventoryClickEvent click = fixture.click(13, ClickType.LEFT);
        fixture.menu.onClick(click);
        assertTrue(click.isCancelled());
        assertEquals(KillEffect.EXPLOSION, fixture.preferences.getKillEffect(fixture.id));
        assertEquals(KillEffect.EXPLOSION, fixture.rendered);
        assertEquals(KillEffect.LIGHTNING, fixture.preferences.getKillEffect(UUID.randomUUID()));
    }

    @Test
    public void shiftNumberDropDoubleAndOutsideClicksNeverChangePreferences() {
        Fixture fixture = new Fixture();
        fixture.menu.open(fixture.player);
        for (ClickType type : new ClickType[] { ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT,
                ClickType.RIGHT, ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP,
                ClickType.CONTROL_DROP, ClickType.MIDDLE }) {
            InventoryClickEvent click = fixture.click(15, type);
            fixture.menu.onClick(click);
            assertTrue(click.isCancelled());
            assertEquals(KillEffect.LIGHTNING, fixture.preferences.getKillEffect(fixture.id));
        }
        for (int slot : new int[] { -999, 27, 40, 53, 4, 22 }) {
            InventoryClickEvent click = fixture.click(slot, ClickType.LEFT);
            fixture.menu.onClick(click);
            assertTrue(click.isCancelled());
            assertEquals(KillEffect.LIGHTNING, fixture.preferences.getKillEffect(fixture.id));
        }
    }

    @Test
    public void allDragsAreCancelledIncludingBottomInventoryOnly() {
        Fixture fixture = new Fixture();
        fixture.menu.open(fixture.player);
        for (int slot : new int[] { 13, 35 }) {
            Map<Integer, ItemStack> additions = new HashMap<Integer, ItemStack>();
            additions.put(slot, new ItemStack(Material.STONE));
            InventoryDragEvent event = new InventoryDragEvent(fixture.view, null,
                    new ItemStack(Material.STONE), false, additions);
            fixture.menu.onDrag(event);
            assertTrue(event.isCancelled());
        }
    }

    @Test
    public void queueStartingAndCombatCannotOpenOrChangeSettings() {
        Fixture fixture = new Fixture();
        fixture.menu.open(fixture.player);
        Inventory original = fixture.top;
        for (PlayerState state : PlayerState.values()) {
            if (state == PlayerState.LOBBY) continue;
            fixture.profiles.get(fixture.id).setState(state);
            fixture.menu.open(fixture.player);
            assertSame(original, fixture.top);
            assertFalse(fixture.menu.handleClick(fixture.player, original, 15));
            InventoryClickEvent click = fixture.click(15, ClickType.LEFT);
            fixture.menu.onClick(click);
            assertTrue(click.isCancelled());
        }
        assertEquals(KillEffect.LIGHTNING, fixture.preferences.getKillEffect(fixture.id));
    }

    @Test
    public void staleInventoriesAndForgedTitlesCannotSelectAnEffect() {
        Fixture fixture = new Fixture();
        fixture.menu.open(fixture.player);
        Inventory first = fixture.top;
        fixture.menu.open(fixture.player);
        assertNotSame(first, fixture.top);
        assertFalse(fixture.menu.handleClick(fixture.player, first, 15));
        assertFalse(fixture.menu.handleClick(fixture.player, inventory(null), 15));
        assertEquals(KillEffect.LIGHTNING, fixture.preferences.getKillEffect(fixture.id));
    }

    @Test
    public void craftInventoryWrappersForTheSameHolderRemainTheSameMenuSession() {
        Fixture fixture = new Fixture();
        fixture.menu.open(fixture.player);
        Inventory originallyCreated = fixture.top;
        InventoryHolder holder = originallyCreated.getHolder();
        fixture.top = inventory(holder);
        Inventory eventWrapper = inventory(holder);
        assertNotSame(originallyCreated, fixture.top);
        assertNotSame(fixture.top, eventWrapper);

        assertTrue(fixture.menu.handleClick(fixture.player, eventWrapper, KillEffect.REDSTONE.getSlot()));
        assertEquals(KillEffect.REDSTONE, fixture.preferences.getKillEffect(fixture.id));
        assertEquals(KillEffect.REDSTONE, fixture.rendered);

        fixture.menu.open(fixture.player);
        assertFalse(fixture.menu.handleClick(fixture.player, eventWrapper, KillEffect.EXPLOSION.getSlot()));
        assertEquals(KillEffect.REDSTONE, fixture.preferences.getKillEffect(fixture.id));
    }

    @Test
    public void anotherPlayersMenuCannotBeUsedToChangePreferences() {
        Fixture fixture = new Fixture();
        fixture.menu.open(fixture.player);
        UUID otherId = UUID.randomUUID();
        fixture.profiles.create(otherId);
        Player other = fixture.player(otherId);
        assertFalse(fixture.menu.handleClick(other, fixture.top, 15));
        assertEquals(KillEffect.LIGHTNING, fixture.preferences.getKillEffect(fixture.id));
        assertEquals(KillEffect.LIGHTNING, fixture.preferences.getKillEffect(otherId));
    }

    @Test
    public void unrelatedInventoriesAreNotCancelledByThisListener() {
        Fixture fixture = new Fixture();
        fixture.top = inventory(null);
        InventoryClickEvent event = fixture.click(13, ClickType.LEFT);
        fixture.menu.onClick(event);
        assertFalse(event.isCancelled());
    }

    private final class Fixture {
        private final UUID id = UUID.randomUUID();
        private final ProfileManager profiles = new ProfileManager();
        private final PreferencesService preferences = new PreferencesService(temporary.getRoot(), Logger.getAnonymousLogger());
        private Inventory top;
        private KillEffect rendered;
        private final Player player = player(id);
        private final InventoryView view = new InventoryView() {
            @Override public Inventory getTopInventory() { return top; }
            @Override public Inventory getBottomInventory() { return inventory(null); }
            @Override public HumanEntity getPlayer() { return player; }
            @Override public InventoryType getType() { return InventoryType.CHEST; }
        };
        private final CosmeticSettingsMenu menu = new CosmeticSettingsMenu(preferences, profiles,
                new CosmeticSettingsMenu.MenuRenderer() {
                    @Override public Inventory create(InventoryHolder holder) { return inventory(holder); }
                    @Override public void render(Inventory inventory, KillEffect selected) { rendered = selected; }
                });

        Fixture() { profiles.create(id); }

        Player player(final UUID playerId) {
            Player mocked = mock(Player.class);
            when(mocked.getUniqueId()).thenReturn(playerId);
            when(mocked.openInventory(any(Inventory.class))).thenAnswer(invocation -> {
                top = (Inventory) invocation.getArguments()[0];
                return view;
            });
            when(mocked.getOpenInventory()).thenAnswer(invocation -> view);
            return mocked;
        }

        InventoryClickEvent click(int slot, ClickType type) {
            return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.NOTHING);
        }
    }

    private static Inventory inventory(final InventoryHolder holder) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[] { Inventory.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getHolder")) return holder;
                    if (method.getName().equals("getSize")) return 27;
                    if (method.getName().equals("getTitle")) return "Settings | Kill Effects";
                    return null;
                });
    }
}
