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
    private static final String PROTOCOL = "3";

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
        CHANNEL.messageBuilder(DevScreenshotPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DevScreenshotPacket::encode)
                .decoder(DevScreenshotPacket::decode)
                .consumerMainThread(DevScreenshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(InputPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(InputPacket::encode)
                .decoder(InputPacket::decode)
                .consumerMainThread(InputPacket::handle)
                .add();
        CHANNEL.messageBuilder(UseTechniquePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseTechniquePacket::encode)
                .decoder(UseTechniquePacket::decode)
                .consumerMainThread(UseTechniquePacket::handle)
                .add();
        CHANNEL.messageBuilder(UpgradeAttributePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpgradeAttributePacket::encode)
                .decoder(UpgradeAttributePacket::decode)
                .consumerMainThread(UpgradeAttributePacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
