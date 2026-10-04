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
    private static final String PROTOCOL = "14";

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
        CHANNEL.messageBuilder(PublicStatePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PublicStatePacket::encode)
                .decoder(PublicStatePacket::decode)
                .consumerMainThread(PublicStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(DashPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DashPacket::encode)
                .decoder(DashPacket::decode)
                .consumerMainThread(DashPacket::handle)
                .add();
        CHANNEL.messageBuilder(SelectFormPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectFormPacket::encode)
                .decoder(SelectFormPacket::decode)
                .consumerMainThread(SelectFormPacket::handle)
                .add();
        CHANNEL.messageBuilder(TechniquePackets.Learn.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TechniquePackets.Learn::encode)
                .decoder(TechniquePackets.Learn::decode)
                .consumerMainThread(TechniquePackets.Learn::handle)
                .add();
        CHANNEL.messageBuilder(TechniquePackets.SetDeck.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TechniquePackets.SetDeck::encode)
                .decoder(TechniquePackets.SetDeck::decode)
                .consumerMainThread(TechniquePackets.SetDeck::handle)
                .add();
        CHANNEL.messageBuilder(CreateCharacterPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateCharacterPacket::encode)
                .decoder(CreateCharacterPacket::decode)
                .consumerMainThread(CreateCharacterPacket::handle)
                .add();
        CHANNEL.messageBuilder(RadarPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RadarPacket::encode).decoder(RadarPacket::decode).consumerMainThread(RadarPacket::handle).add();
        CHANNEL.messageBuilder(OpenWishPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenWishPacket::encode).decoder(OpenWishPacket::decode).consumerMainThread(OpenWishPacket::handle).add();
        CHANNEL.messageBuilder(MakeWishPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MakeWishPacket::encode).decoder(MakeWishPacket::decode).consumerMainThread(MakeWishPacket::handle).add();
        CHANNEL.messageBuilder(QuestPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestPackets.Open::encode).decoder(QuestPackets.Open::decode).consumerMainThread(QuestPackets.Open::handle).add();
        CHANNEL.messageBuilder(QuestPackets.Action.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(QuestPackets.Action::encode).decoder(QuestPackets.Action::decode).consumerMainThread(QuestPackets.Action::handle).add();
        CHANNEL.messageBuilder(TravelPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TravelPacket::encode).decoder(TravelPacket::decode).consumerMainThread(TravelPacket::handle).add();
        CHANNEL.messageBuilder(SelectTitlePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectTitlePacket::encode).decoder(SelectTitlePacket::decode).consumerMainThread(SelectTitlePacket::handle).add();
        CHANNEL.messageBuilder(com.dbzenith.world.Cosmetics.Packet.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.dbzenith.world.Cosmetics.Packet::encode).decoder(com.dbzenith.world.Cosmetics.Packet::decode)
                .consumerMainThread(com.dbzenith.world.Cosmetics.Packet::handle).add();
        CHANNEL.messageBuilder(com.dbzenith.stats.Prestige.Packet.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.dbzenith.stats.Prestige.Packet::encode).decoder(com.dbzenith.stats.Prestige.Packet::decode)
                .consumerMainThread(com.dbzenith.stats.Prestige.Packet::handle).add();
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

    /** To everyone tracking {@code player}, and the player. */
    public static void sendToTrackingAndSelf(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
