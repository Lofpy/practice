package com.ascendingmc.survival;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RegenerationCommandsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void confirmationQueuesButDoesNotChangeTerrainAndCancelRetainsTerrain() throws Exception {
        Fixture fixture = fixture();
        fixture.command.execute(fixture.sender, new String[]{"regenerate", "survival"});
        assertNull(OfflineWorldRegenerator.readPending(fixture.root));
        assertTrue(fixture.messages.stream().anyMatch(line -> line.contains("同じシード")));
        fixture.command.execute(fixture.sender, new String[]{"regenerate", "confirm", fixture.token()});
        RegenerationPlan plan = OfflineWorldRegenerator.readPending(fixture.root);
        assertEquals(List.of("overworld"), plan.dimensions());
        assertEquals("terrain", Files.readString(fixture.terrain));
        assertThrows(IllegalArgumentException.class, fixture.command::requireNoPending);
        fixture.command.execute(fixture.sender, new String[]{"regenerate", "cancel"});
        assertNull(OfflineWorldRegenerator.readPending(fixture.root));
        assertEquals("terrain", Files.readString(fixture.terrain));
        fixture.command.requireNoPending();
    }

    @Test public void wrongAdministratorCannotUseConfirmationAndAllExplicitlyListsTargets() throws Exception {
        Fixture fixture = fixture();
        fixture.command.execute(fixture.sender, new String[]{"regen", "all"});
        String token = fixture.token();
        assertTrue(fixture.messages.stream().anyMatch(line -> line.contains("overworld, the_nether")));
        CommandSender other = sender("other", new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> fixture.command.execute(other,
                new String[]{"regen", "confirm", token}));
        assertNull(OfflineWorldRegenerator.readPending(fixture.root));
        fixture.command.execute(fixture.sender, new String[]{"regen", "confirm", token});
        assertEquals(List.of("overworld", "the_nether"), OfflineWorldRegenerator.readPending(fixture.root).dimensions());
        assertThrows(IllegalArgumentException.class, () -> fixture.command.execute(fixture.sender,
                new String[]{"regen", "confirm", token}));
    }

    @Test public void confirmationRevalidatesWorldsAndBadTargetsDoNotQueueAnything() throws Exception {
        Fixture fixture = fixture();
        assertThrows(IllegalArgumentException.class, () -> fixture.command.execute(fixture.sender,
                new String[]{"regen", "../players"}));
        fixture.command.execute(fixture.sender, new String[]{"regen", "all"});
        String token = fixture.token();
        fixture.worlds.removeLast();
        assertThrows(IllegalArgumentException.class, () -> fixture.command.execute(fixture.sender,
                new String[]{"regen", "confirm", token}));
        assertNull(OfflineWorldRegenerator.readPending(fixture.root));
    }

    private Fixture fixture() throws Exception {
        Path root = temporary.newFolder().toPath().toRealPath();
        Files.writeString(root.resolve("server.properties"), "level-name=survival\n");
        List<World> worlds = new ArrayList<>();
        Path terrain = null;
        for (String dimension : List.of("overworld", "the_nether")) {
            Path directory = root.resolve("survival/dimensions/minecraft/" + dimension);
            Files.createDirectories(directory.resolve("data/minecraft"));
            Files.createDirectories(directory.resolve("data/paper"));
            Files.createDirectories(directory.resolve("region"));
            Files.writeString(directory.resolve("data/minecraft/world_gen_settings.dat"), "seed");
            Files.writeString(directory.resolve("data/paper/metadata.dat"), "identity");
            Path region = directory.resolve("region/r.0.0.mca");
            Files.writeString(region, "terrain");
            if (terrain == null) terrain = region;
            String name = dimension.equals("overworld") ? "survival" : "survival_nether";
            worlds.add((World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> name;
                        case "getKey" -> NamespacedKey.minecraft(dimension);
                        case "getWorldPath" -> directory;
                        case "save" -> null;
                        case "toString" -> name;
                        default -> throw new AssertionError("Unexpected world method: " + method.getName());
                    }));
        }
        Files.writeString(root.resolve("survival/level.dat"), "level");
        Files.writeString(root.resolve("survival/session.lock"), "lock");
        List<String> messages = new ArrayList<>();
        RegenerationCommands command = new RegenerationCommands(Logger.getAnonymousLogger(), root, "survival", () -> worlds, 30_000);
        return new Fixture(root, terrain, command, worlds, messages, sender("console", messages));
    }

    private static CommandSender sender(String name, List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "sendMessage" -> { messages.add((String) args[0]); yield null; }
                    case "toString" -> name;
                    default -> throw new AssertionError("Unexpected sender method: " + method.getName());
                });
    }

    private record Fixture(Path root, Path terrain, RegenerationCommands command, List<World> worlds,
                           List<String> messages, CommandSender sender) {
        String token() {
            String prefix = "/sworld regenerate confirm ";
            String line = messages.stream().filter(text -> text.contains(prefix)).reduce((first, second) -> second).orElseThrow();
            return line.substring(line.indexOf(prefix) + prefix.length());
        }
    }
}
