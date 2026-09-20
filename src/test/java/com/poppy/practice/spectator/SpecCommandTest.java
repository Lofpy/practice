package com.poppy.practice.spectator;

import com.poppy.practice.command.SpecCommand;
import com.poppy.practice.cosmetic.PreferencesService;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.spectator.SpectatorFixture.PlayerStub;
import org.bukkit.command.CommandSender;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class SpecCommandTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LanguageService language() {
        return new LanguageService(new PreferencesService(temporary.getRoot(), Logger.getAnonymousLogger()));
    }

    @Test public void noArgumentAndExplicitLeaveBothExitWithoutPermission() {
        for (String[] args : new String[][]{new String[0], new String[]{"leave"}, new String[]{"LEAVE"}}) {
            SpectatorFixture f = new SpectatorFixture();
            PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
            f.match(first, second);
            assertTrue(f.service.spectate(viewer.player, first.player));
            viewer.permission = false;
            SpecCommand command = new SpecCommand(f.service, language());
            assertTrue(command.onCommand(viewer.player, null, "spec", args));
            assertFalse(f.service.isSpectating(viewer.id));
            assertTrue(f.calls.contains("lobby:Viewer"));
        }
    }

    @Test public void permissionAndArgumentValidationHappenBeforePlayerLookup() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer");
        SpecCommand command = new SpecCommand(f.service, language());
        viewer.permission = false;
        // No Bukkit server is installed in this test: any player lookup would fail.
        assertTrue(command.onCommand(viewer.player, null, "spec", new String[]{"Other"}));
        assertTrue(command.onCommand(viewer.player, null, "spec", new String[]{"Other", "extra"}));
        assertTrue(command.onCommand(viewer.player, null, "spec", new String[0]));
        assertEquals(0, viewer.teleports);
    }

    @Test public void consoleIsRejectedWithoutAnyPlayerServices() {
        AtomicInteger messages = new AtomicInteger();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) messages.incrementAndGet();
                    return SpectatorFixture.defaultValue(method.getReturnType());
                });
        assertTrue(new SpecCommand(null, null).onCommand(sender, null, "spec", new String[]{"Other"}));
        assertEquals(1, messages.get());
    }
}
