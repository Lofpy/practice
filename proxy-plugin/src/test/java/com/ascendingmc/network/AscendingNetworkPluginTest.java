package com.ascendingmc.network;

import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import net.kyori.adventure.text.Component;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import static org.junit.Assert.*;

public class AscendingNetworkPluginTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private Map<String, RegisteredServer> servers;
    private List<Component> messages;
    private List<String> logs;
    private List<Component> disconnects;
    private AscendingNetworkPlugin plugin;

    @Before public void prepare() {
        servers = new HashMap<>();
        messages = new ArrayList<>();
        disconnects = new ArrayList<>();
        logs = new ArrayList<>();
        for (String name : new String[] { "lobby", "pvp", "survival" }) {
            ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25568));
            servers.put(name, mock(RegisteredServer.class, (object, method, args) ->
                    method.getName().equals("getServerInfo") ? info : null));
        }
        ProxyServer proxy = mock(ProxyServer.class, (object, method, args) ->
                method.getName().equals("getServer") ? Optional.ofNullable(servers.get(args[0])) : null);
        Logger logger = mock(Logger.class, (object, method, args) -> {
            if (args != null && args.length > 0 && args[0] instanceof String) logs.add((String) args[0]);
            return method.getReturnType() == boolean.class ? false : null;
        });
        plugin = new AscendingNetworkPlugin(proxy, logger, directory.getRoot().toPath());
    }

    @Test public void generatesDefaultConfigurationAndReportsHealthyGate() {
        initialize();
        assertTrue(Files.isRegularFile(directory.getRoot().toPath().resolve("config.properties")));
        assertTrue(logs.stream().anyMatch(message -> message.startsWith("AscendingNetwork enabled:")));
    }

    @Test public void currentClientCanTransferFromLobbyToSurvival() {
        initialize();
        ServerPreConnectEvent event = connection(777, "survival", "lobby");
        plugin.onConnect(event);
        assertSame(servers.get("survival"), event.getResult().getServer().orElseThrow());
        assertTrue(messages.isEmpty());
    }

    @Test public void oldClientsCannotUseAnyTransferRoute() {
        initialize();
        for (int protocol : new int[] { 5, 47, 774, 775, 776 }) {
            ServerPreConnectEvent event = connection(protocol, "survival", "lobby");
            plugin.onConnect(event);
            assertFalse(event.getResult().isAllowed());
        }
        assertEquals(5, messages.size());
        assertTrue(disconnects.isEmpty());
    }

    @Test public void redirectedConnectionsAreCheckedAgainstFinalDestination() {
        initialize();
        ServerPreConnectEvent event = connection(47, "pvp", "lobby");
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(servers.get("survival")));
        plugin.onConnect(event);
        assertFalse(event.getResult().isAllowed());
    }

    @Test public void initialForcedHostConnectionFallsBackToLobby() {
        initialize();
        ServerPreConnectEvent event = connection(47, "survival", null);
        plugin.onConnect(event);
        assertSame(servers.get("lobby"), event.getResult().getServer().orElseThrow());
        assertEquals(1, messages.size());
        assertTrue(disconnects.isEmpty());
    }

    @Test public void initialDeniedConnectionWithoutFallbackDisconnectsClearly() {
        initialize();
        servers.remove("lobby");
        ServerPreConnectEvent event = connection(47, "survival", null);
        plugin.onConnect(event);
        assertFalse(event.getResult().isAllowed());
        assertEquals(1, disconnects.size());
    }

    @Test public void legacyLobbyAndPracticeConnectionsAreUnchanged() {
        initialize();
        for (String target : new String[] { "lobby", "pvp" }) {
            ServerPreConnectEvent event = connection(5, target, null);
            plugin.onConnect(event);
            assertSame(servers.get(target), event.getResult().getServer().orElseThrow());
        }
        assertTrue(messages.isEmpty());
    }

    @Test public void otherPluginCancellationIsNeverUndone() {
        initialize();
        ServerPreConnectEvent event = connection(777, "survival", null);
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        plugin.onConnect(event);
        assertFalse(event.getResult().isAllowed());
        assertTrue(messages.isEmpty());
    }

    @Test public void startupRaceDoesNotPermitUnvalidatedConnections() {
        ServerPreConnectEvent event = connection(777, "survival", "lobby");
        plugin.onConnect(event);
        assertFalse(event.getResult().isAllowed());
    }

    @Test public void incompatibleReleaseProtocolPairFailsClosed() throws Exception {
        Properties properties = SurvivalAccessPolicyTest.configured();
        properties.setProperty("required-protocol", "776");
        writeConfig(properties);
        initialize();
        ServerPreConnectEvent event = connection(776, "survival", "lobby");
        plugin.onConnect(event);
        assertFalse(event.getResult().isAllowed());
        assertTrue(logs.stream().anyMatch(message -> message.contains("admission is CLOSED")));
        assertFalse(logs.stream().anyMatch(message -> message.startsWith("AscendingNetwork enabled:")));
    }

    @Test public void missingRequiredProtocolDoesNotDefaultToAllowAll() throws Exception {
        Properties properties = SurvivalAccessPolicyTest.configured();
        properties.remove("required-protocol");
        writeConfig(properties);
        initialize();
        ServerPreConnectEvent event = connection(777, "survival", "lobby");
        plugin.onConnect(event);
        assertFalse(event.getResult().isAllowed());
    }

    @Test public void missingConfiguredBackendClosesAdmission() {
        servers.remove("survival");
        initialize();
        assertTrue(logs.stream().anyMatch(message -> message.contains("admission is CLOSED")));
    }

    private void initialize() { plugin.onInitialize(new ProxyInitializeEvent()); }

    private void writeConfig(Properties properties) throws Exception {
        Path path = directory.getRoot().toPath().resolve("config.properties");
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, "test configuration");
        }
    }

    private ServerPreConnectEvent connection(int protocol, String target, String previous) {
        Player player = mock(Player.class, (object, method, args) -> {
            switch (method.getName()) {
                case "getProtocolVersion": return ProtocolVersion.getProtocolVersion(protocol);
                case "sendMessage":
                    for (Object argument : args) if (argument instanceof Component) messages.add((Component) argument);
                    return null;
                case "disconnect": disconnects.add((Component) args[0]); return null;
                default: return null;
            }
        });
        return new ServerPreConnectEvent(player, servers.get(target), previous == null ? null : servers.get(previous));
    }

    private static <T> T mock(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }
}
