package com.poppy.practice.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.WorldServer;
import net.minecraft.server.v1_8_R3.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.plugin.PluginManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BotNpcPlayerInfoTest {
    private static Field serverField;
    private static Object previousServer;

    @BeforeClass public static void initializePermissions() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.getDefaultPermissions(anyBoolean())).thenReturn(Collections.emptySet());
        serverField.set(null, server);
    }

    @AfterClass public static void restoreServer() throws Exception {
        serverField.set(null, previousServer);
    }

    @Test public void viewerGetsCompleteProfileForPlayerSpawns() throws Exception {
        Fixture fixture = new Fixture();
        fixture.npc.showPlayerInfo(fixture.viewer);
        assertEquals(1, fixture.packets.size());
        PacketPlayOutPlayerInfo info = (PacketPlayOutPlayerInfo) fixture.packets.get(0);
        assertEquals(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, info.getPlayerInfoAction());
        assertEquals(fixture.profile.getId(), info.getPlayersInfo().get(0).a().getId());
        assertEquals("PracticeBot", info.getPlayersInfo().get(0).a().getName());
    }

    @Test public void despawnDestroysEntityBeforeRemovingProfileAndIsIdempotent() throws Exception {
        Fixture fixture = new Fixture();
        fixture.npc.remove(fixture.viewer);
        fixture.npc.remove(fixture.viewer);
        assertEquals(2, fixture.packets.size());
        assertTrue(fixture.packets.get(0) instanceof PacketPlayOutEntityDestroy);
        assertEquals(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER,
                ((PacketPlayOutPlayerInfo) fixture.packets.get(1)).getPlayerInfoAction());
        verify(fixture.world, times(1)).removeEntity(fixture.bot);
        assertFalse(fixture.npc.isValid());
    }

    @Test public void leavingViewerCanExplicitlyReleaseProfile() throws Exception {
        Fixture fixture = new Fixture();
        fixture.npc.showPlayerInfo(fixture.viewer);
        fixture.npc.removePlayerInfo(fixture.viewer);
        assertEquals(2, fixture.packets.size());
        assertEquals(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER,
                ((PacketPlayOutPlayerInfo) fixture.packets.get(1)).getPlayerInfoAction());
        verify(fixture.world, never()).removeEntity(fixture.bot);
    }

    @Test public void offlineViewerDoesNotReceivePacketsButNpcIsRemoved() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.viewer.isOnline()).thenReturn(false);
        fixture.npc.showPlayerInfo(fixture.viewer);
        fixture.npc.removePlayerInfo(fixture.viewer);
        fixture.npc.remove(fixture.viewer);
        assertTrue(fixture.packets.isEmpty());
        verify(fixture.world).removeEntity(fixture.bot);
    }

    @Test public void missingViewerStillAllowsNpcCleanup() throws Exception {
        Fixture fixture = new Fixture();
        fixture.npc.showPlayerInfo(null);
        fixture.npc.removePlayerInfo(null);
        fixture.npc.remove(null);
        assertTrue(fixture.packets.isEmpty());
        verify(fixture.world).removeEntity(fixture.bot);
    }

    private static final class Fixture {
        final EntityPlayer bot = mock(EntityPlayer.class);
        final WorldServer world = mock(WorldServer.class);
        final CraftPlayer viewer = mock(CraftPlayer.class);
        final GameProfile profile = new GameProfile(UUID.randomUUID(), "PracticeBot");
        final List<Packet> packets = new ArrayList<Packet>();
        final BotNpc npc;

        Fixture() throws Exception {
            EntityPlayer viewerHandle = mock(EntityPlayer.class);
            PlayerConnection connection = mock(PlayerConnection.class);
            viewerHandle.playerConnection = connection;
            when(viewer.getHandle()).thenReturn(viewerHandle);
            when(viewer.isOnline()).thenReturn(true);
            doAnswer(invocation -> {
                packets.add((Packet) invocation.getArguments()[0]);
                return null;
            }).when(connection).sendPacket(any(Packet.class));
            when(bot.getProfile()).thenReturn(profile);
            when(bot.getBukkitEntity()).thenReturn(mock(CraftPlayer.class));
            Field interactManager = EntityPlayer.class.getDeclaredField("playerInteractManager");
            interactManager.setAccessible(true);
            interactManager.set(bot, mock(PlayerInteractManager.class));
            when(bot.playerInteractManager.getGameMode()).thenReturn(WorldSettings.EnumGamemode.SURVIVAL);
            bot.valid = true;
            Constructor<BotNpc> constructor = BotNpc.class.getDeclaredConstructor(
                    EntityPlayer.class, WorldServer.class, BotNetworkManager.class);
            constructor.setAccessible(true);
            npc = constructor.newInstance(bot, world, new BotNetworkManager());
            Field spawned = BotNpc.class.getDeclaredField("spawned");
            spawned.setAccessible(true);
            spawned.setBoolean(npc, true);
        }
    }
}
