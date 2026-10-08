package com.dbzenith.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Real, logged-in ServerPlayers for GameTests.
 * Vanilla's {@code makeMockServerPlayerInLevel} NPEs on Forge 1.20.1 because Forge's NetworkFilters inspects
 * the connection's netty channel, which the vanilla mock never creates. An EmbeddedChannel gives the connection
 * a live channel whose outbound packets are simply buffered and discarded.
 */
public final class TestPlayers {
    private TestPlayers() {}

    public static ServerPlayer create(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(id, "dbz-" + id.toString().substring(0, 8)));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        connection.setProtocol(ConnectionProtocol.PLAY);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        channel.releaseOutbound();
        com.dbzenith.combat.PvpRules.TEST_BYPASS.add(player.getUUID());   // older tests fight freely (CX-19)
        com.dbzenith.data.ModCapabilities.get(player).ifPresent(d -> d.setPvp(true));   // and in PvP mode, where combat works (CX-20)
        try {                                                                    // fake players never tick their spawn protection away
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(player, 0);
        } catch (ReflectiveOperationException ignored) {
        }
        return player;
    }

    public static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
