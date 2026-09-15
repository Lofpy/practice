package com.poppy.practice.protocol;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ClientProtocolResolverTest {
    private static final UUID PLAYER_ID = UUID.fromString("41d8daf1-02e1-45c4-92e1-44e439fd11c8");
    private final Map<String, Plugin> plugins = new HashMap<String, Plugin>();
    private final Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
    private boolean resolvedClass;

    @Before
    public void resetApiState() {
        FakeProtocolSupport.version = new FakeVersion(5);
        FakeVia.api = new FakeViaApi();
        FakeViaApi.protocol = 47;
        FakeViaApi.requestedPlayer = null;
        FakeViaApi.failure = null;
        classes.put("protocolsupport.api.ProtocolSupportAPI", FakeProtocolSupport.class);
        classes.put("com.viaversion.viaversion.api.Via", FakeVia.class);
    }

    @Test
    public void nativeServerUses47WithoutLoadingOptionalClasses() throws Exception {
        assertEquals(47, resolve());
        assertFalse(resolvedClass);
    }

    @Test
    public void disabledTranslatorsDoNotOverrideNativeVersion() throws Exception {
        addPlugin("ProtocolSupport", false);
        addPlugin("ViaVersion", false);
        assertEquals(47, resolve());
        assertFalse(resolvedClass);
    }

    @Test
    public void protocolSupportContinuesToExpose17Clients() throws Exception {
        addPlugin("ProtocolSupport", true);
        assertEquals(5, resolve());
    }

    @Test
    public void protocolSupportPreservesExistingPriorityWhenBothAreInstalled() throws Exception {
        addPlugin("ProtocolSupport", true);
        addPlugin("ViaVersion", true);
        FakeViaApi.protocol = 774;
        assertEquals(5, resolve());
    }

    @Test
    public void viaVersionResolves17ClientAndUsesItsUuid() throws Exception {
        addPlugin("ViaVersion", true);
        FakeViaApi.protocol = 5;
        assertEquals(5, resolve());
        assertEquals(PLAYER_ID, FakeViaApi.requestedPlayer);
    }

    @Test
    public void viaVersionNativeClientRemains47() throws Exception {
        addPlugin("ViaVersion", true);
        assertEquals(47, resolve());
    }

    @Test
    public void modernClientIsNotMislabelledAsNative18() throws Exception {
        addPlugin("ViaVersion", true);
        FakeViaApi.protocol = 774;
        assertEquals(774, resolve());
    }

    @Test
    public void futureProtocolIsPreservedForFailOpenPolicy() throws Exception {
        addPlugin("ViaVersion", true);
        FakeViaApi.protocol = 12345;
        assertEquals(12345, resolve());
    }

    @Test
    public void unknownViaConnectionIsNeverAssumedToBeNative18() throws Exception {
        addPlugin("ViaVersion", true);
        FakeViaApi.protocol = -1;
        assertEquals(-1, resolve());
    }

    @Test
    public void disabledProtocolSupportFallsBackToActiveViaVersion() throws Exception {
        addPlugin("ProtocolSupport", false);
        addPlugin("ViaVersion", true);
        FakeViaApi.protocol = 5;
        assertEquals(5, resolve());
    }

    @Test(expected = ClassNotFoundException.class)
    public void missingViaApiPropagatesInsteadOfReturning47() throws Exception {
        addPlugin("ViaVersion", true);
        classes.clear();
        resolve();
    }

    @Test(expected = NoSuchMethodException.class)
    public void incompatibleViaApiPropagatesInsteadOfReturning47() throws Exception {
        addPlugin("ViaVersion", true);
        FakeVia.api = new Object();
        resolve();
    }

    @Test(expected = InvocationTargetException.class)
    public void translatorFailurePropagatesForCallerToFailOpen() throws Exception {
        addPlugin("ViaVersion", true);
        FakeViaApi.failure = new IllegalStateException("connection is not ready");
        resolve();
    }

    @Test(expected = ClassNotFoundException.class)
    public void brokenActiveProtocolSupportDoesNotSilentlyFallBackToAnotherVersion() throws Exception {
        addPlugin("ProtocolSupport", true);
        addPlugin("ViaVersion", true);
        classes.remove("protocolsupport.api.ProtocolSupportAPI");
        resolve();
    }

    private int resolve() throws ReflectiveOperationException {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        PluginManager manager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPlugin")) return plugins.get(args[0]);
                    throw new AssertionError("Unexpected plugin access: " + method.getName());
                });
        return ClientProtocolResolver.resolve(player, manager, name -> {
            resolvedClass = true;
            Class<?> type = classes.get(name);
            if (type == null) throw new ClassNotFoundException(name);
            return type;
        });
    }

    private void addPlugin(String name, boolean enabled) {
        plugins.put(name, (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isEnabled")) return enabled;
                    throw new AssertionError("Unexpected translator access: " + method.getName());
                }));
    }

    public static final class FakeProtocolSupport {
        static Object version;

        public static Object getProtocolVersion(Player player) {
            return version;
        }
    }

    public static final class FakeVersion {
        private final int id;

        FakeVersion(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    public static final class FakeVia {
        static Object api;

        public static Object getAPI() {
            return api;
        }
    }

    public static class FakeViaApiBase {
        public int getPlayerVersion(UUID player) {
            FakeViaApi.requestedPlayer = player;
            if (FakeViaApi.failure != null) throw FakeViaApi.failure;
            return FakeViaApi.protocol;
        }
    }

    public static final class FakeViaApi extends FakeViaApiBase {
        static int protocol;
        static UUID requestedPlayer;
        static RuntimeException failure;
    }
}
