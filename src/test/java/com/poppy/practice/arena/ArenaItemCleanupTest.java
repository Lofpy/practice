package com.poppy.practice.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ArenaItemCleanupTest {
    @Test public void removesOnlyDroppedItemsInTheFinishedArena() {
        World world = mock(World.class);
        Arena arena = arena(world);
        Item inside = mock(Item.class), otherArena = mock(Item.class), lobby = mock(Item.class);
        Player player = mock(Player.class);
        when(inside.getLocation()).thenReturn(new Location(world, 999.5, 4, 70));
        when(otherArena.getLocation()).thenReturn(new Location(world, 1999.5, 4, 0));
        when(lobby.getLocation()).thenReturn(new Location(world, 0, 4, 100));
        when(world.getEntities()).thenReturn(Arrays.asList(inside, otherArena, lobby, player));
        assertEquals(1, ArenaItemCleanup.clear(arena));
        verify(inside).remove();
        verify(otherArena, never()).remove();
        verify(lobby, never()).remove();
        verify(player, never()).remove();
    }

    @Test public void rejectsOtherWorldsAndInvalidCoordinates() {
        World world = mock(World.class);
        Arena arena = arena(world);
        assertTrue(ArenaItemCleanup.contains(arena, new Location(world, 950, 53, -75)));
        assertFalse(ArenaItemCleanup.contains(arena, new Location(world, 999.5, 4, 100)));
        assertFalse(ArenaItemCleanup.contains(arena, new Location(mock(World.class), 999.5, 4, 0)));
        assertFalse(ArenaItemCleanup.contains(arena, new Location(world, Double.NaN, 4, 0)));
        assertFalse(ArenaItemCleanup.contains(arena, null));
    }

    @Test public void clearsAnUnloadedCornerWithoutGeneratingChunksOrLoadingOtherArenas() {
        World world = mock(World.class);
        Arena arena = arena(world);
        Item corner = mock(Item.class);
        when(corner.getLocation()).thenReturn(new Location(world, 951, 4, -74));
        java.util.List<org.bukkit.entity.Entity> entities = new java.util.ArrayList<org.bukkit.entity.Entity>();
        when(world.getEntities()).thenReturn(entities);
        when(world.loadChunk(59, -5, false)).thenAnswer(invocation -> {
            entities.add(corner);
            return true;
        });
        assertEquals(1, ArenaItemCleanup.clear(arena));
        verify(corner).remove();
        verify(world).unloadChunkRequest(59, -5);
        verify(world, never()).loadChunk(anyInt(), anyInt(), eq(true));
        verify(world, never()).loadChunk(eq(125), anyInt(), anyBoolean());
        verify(world, never()).loadChunk(eq(0), anyInt(), anyBoolean());
    }

    private static Arena arena(World world) {
        return new Arena("nodebuff_01", "nodebuff", new Location(world, 999.5, 4, -55.5),
                new Location(world, 999.5, 4, 55.5), ArenaState.IN_USE);
    }
}
