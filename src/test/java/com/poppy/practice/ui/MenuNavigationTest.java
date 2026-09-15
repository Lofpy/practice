package com.poppy.practice.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MenuNavigationTest {
    @Test
    public void playerInventoryAndOutsideClicksCannotResolveToMenuActions() {
        assertTrue(MenuNavigation.isTopSlot(54, 0));
        assertTrue(MenuNavigation.isTopSlot(54, 53));
        assertFalse(MenuNavigation.isTopSlot(54, 54));
        assertFalse(MenuNavigation.isTopSlot(54, 89));
        assertFalse(MenuNavigation.isTopSlot(54, -999));
        assertFalse(MenuNavigation.isTopSlot(0, 0));
    }

    @Test
    public void equalTitlesCannotImpersonateAnotherMenuOrAuthorizeAStaleClick() {
        MenuHolder first = new TestMenu();
        MenuHolder second = new TestMenu();
        Inventory wrapper = inventory(first);
        assertTrue(first.owns(wrapper));
        assertTrue(MenuNavigation.sameMenu(first.getInventory(), wrapper));
        assertFalse(first.owns(second.getInventory()));
        assertFalse(MenuNavigation.sameMenu(first.getInventory(), second.getInventory()));
        assertFalse(MenuNavigation.sameMenu(first.getInventory(), inventory(null)));
        assertFalse(MenuNavigation.sameMenu(null, wrapper));
    }

    private static Inventory inventory(final InventoryHolder holder) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
                new Class<?>[] { Inventory.class }, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) {
                        if (method.getName().equals("getHolder")) {
                            return holder;
                        }
                        if (method.getName().equals("getSize")) {
                            return 9;
                        }
                        if (method.getName().equals("getTitle")) {
                            return "Same title";
                        }
                        return null;
                    }
                });
    }

    private static final class TestMenu extends MenuHolder {
        private TestMenu() {
            super(9, "Same title", new InventoryFactory() {
                @Override
                public Inventory create(InventoryHolder holder, int size, String title) {
                    return inventory(holder);
                }
            });
        }
    }
}
