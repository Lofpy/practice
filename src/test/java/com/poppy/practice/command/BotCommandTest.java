package com.poppy.practice.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotCommandTest {
    // A null service ensures retired commands cannot reach any match operations.
    private final BotCommand command = new BotCommand(null);

    @Test
    public void retiredDebugCommandCannotStartMatchForAnyPlayer() {
        for (boolean operator : new boolean[] {false, true}) {
            SenderStub sender = new SenderStub(operator);

            assertTrue(command.onCommand(sender.player(), null, "bot",
                    new String[] {"debug", "straight"}));

            assertEquals(1, sender.messages.size());
            assertEquals("使い方: /bot [start|settings|leave]",
                    ChatColor.stripColor(sender.messages.get(0)));
        }
    }

    @Test
    public void noPlayerSeesRetiredDebugHelpOrCompletions() {
        for (boolean operator : new boolean[] {false, true}) {
            SenderStub sender = new SenderStub(operator);
            Player player = sender.player();

            command.onCommand(player, null, "bot", new String[] {"help"});

            assertEquals(1, sender.messages.size());
            assertFalse(sender.messages.get(0).contains("debug"));
            assertEquals(Arrays.asList("start", "settings", "leave"),
                    command.onTabComplete(player, null, "bot", new String[] {""}));
            assertEquals(Collections.emptyList(),
                    command.onTabComplete(player, null, "bot", new String[] {"D"}));
            assertEquals(Collections.emptyList(),
                    command.onTabComplete(player, null, "bot", new String[] {"debug", ""}));
            assertEquals(Collections.emptyList(),
                    command.onTabComplete(player, null, "bot", new String[] {"DEBUG", "S"}));
        }
    }

    @Test
    public void ordinaryCompletionsRemainCaseInsensitive() {
        Player player = new SenderStub(false).player();

        assertEquals(Arrays.asList("start", "settings"),
                command.onTabComplete(player, null, "bot", new String[] {"S"}));
        assertEquals(Collections.singletonList("leave"),
                command.onTabComplete(player, null, "bot", new String[] {"L"}));
    }

    @Test
    public void allRetiredDebugFormsOnlyReturnOrdinaryUsage() {
        SenderStub sender = new SenderStub(true);
        Player player = sender.player();

        command.onCommand(player, null, "bot", new String[] {"debug"});
        command.onCommand(player, null, "bot", new String[] {"debug", "unknown"});
        command.onCommand(player, null, "bot", new String[] {"debug", "straight", "extra"});
        command.onCommand(player, null, "bot", new String[] {"DEBUG", "STRAIGHT"});

        assertEquals(4, sender.messages.size());
        for (String message : sender.messages) {
            assertEquals("使い方: /bot [start|settings|leave]", ChatColor.stripColor(message));
        }
    }

    @Test
    public void consoleCannotStartOrCompletePlayerOnlyDebugMode() {
        SenderStub sender = new SenderStub(true);
        CommandSender console = sender.console();

        command.onCommand(console, null, "bot", new String[] {"debug", "straight"});

        assertEquals(1, sender.messages.size());
        assertTrue(sender.messages.get(0).contains("only be used by a player"));
        assertEquals(Collections.emptyList(),
                command.onTabComplete(console, null, "bot", new String[] {"debug", ""}));
    }

    @Test
    public void descriptorDoesNotDeclareOrInheritRetiredBotDebugPermission() throws Exception {
        YamlConfiguration descriptor = descriptor();

        assertFalse(descriptor.contains("permissions.practice.bot.debug"));
        assertFalse(descriptor.contains("permissions.practice.admin.children.practice.bot.debug"));
        assertEquals("op", descriptor.getString("permissions.practice.admin.default"));
        assertFalse(descriptor.getString("commands.bot.usage").contains("debug"));
    }

    @Test
    public void descriptorPreservesUnrelatedDebugTools() throws Exception {
        YamlConfiguration descriptor = descriptor();

        assertEquals("practice.admin", descriptor.getString("commands.hitdebug.permission"));
        assertTrue(descriptor.getString("commands.practice.usage").contains("debug"));
        assertTrue(descriptor.getString("commands.chatterkb.usage").contains("debug"));
        assertEquals("op", descriptor.getString("permissions.chatterkb.admin.debug.default"));
        assertTrue(descriptor.contains("commands.reachguard"));
        assertEquals("op", descriptor.getString("permissions.reachguard.admin.default"));
    }

    private YamlConfiguration descriptor() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                BotCommandTest.class.getResourceAsStream("/plugin.yml"), "UTF-8")) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static final class SenderStub {
        private final boolean operator;
        private final List<String> messages = new ArrayList<String>();

        private SenderStub(boolean operator) {
            this.operator = operator;
        }

        private Player player() {
            // WindSpigot bundles Mockito; its class-based mock generation supports
            // the legacy int/double getHealth bridges that JDK proxies reject.
            return mockSender(Player.class);
        }

        private CommandSender console() {
            return mockSender(CommandSender.class);
        }

        private <T> T mockSender(Class<T> type) {
            return Mockito.mock(type, (Answer<Object>) invocation -> {
                String method = invocation.getMethod().getName();
                Object[] args = invocation.getArguments();
                if (method.equals("hasPermission") || method.equals("isOp")) {
                    return operator;
                }
                if (method.equals("sendMessage")) {
                    if (args[0] instanceof String[]) {
                        messages.addAll(Arrays.asList((String[]) args[0]));
                    } else {
                        messages.add((String) args[0]);
                    }
                    return null;
                }
                if (method.equals("toString")) {
                    return "BotCommandTest sender";
                }
                throw new AssertionError("Unexpected sender method: " + method);
            });
        }
    }
}
