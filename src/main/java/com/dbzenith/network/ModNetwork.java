package com.dbzenith.network;

import com.dbzenith.DBZenith;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's single packet channel. Bump {@link #PROTOCOL} whenever a packet's wire format changes,
 * so mismatched client/server versions are refused at login instead of desyncing.
 */
public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DBZenith.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int nextId;

    private ModNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SyncPlayerDataPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPlayerDataPacket::encode)
                .decoder(SyncPlayerDataPacket::decode)
                .consumerMainThread(SyncPlayerDataPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
