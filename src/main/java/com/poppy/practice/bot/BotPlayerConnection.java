package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PlayerConnection;

/**
 * Identifies a fake player's connection to WindSpigot's hit detection.
 *
 * WindSpigot only rewinds targets backed by the exact vanilla
 * {@link PlayerConnection} class. Bot movement is generated server-side and
 * therefore has no stream of client movement packets to populate that history.
 * Keeping a distinct connection type makes reach checks use the bot's current
 * server position instead of a stale lag-compensation entry.
 */
final class BotPlayerConnection extends PlayerConnection {
    BotPlayerConnection(MinecraftServer server, BotNetworkManager networkManager,
                        EntityPlayer player) {
        super(server, networkManager, player);
    }
}
