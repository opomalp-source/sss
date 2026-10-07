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
    private static final String PROTOCOL = "33";

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
        CHANNEL.messageBuilder(FacePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FacePacket::encode).decoder(FacePacket::decode).consumerMainThread(FacePacket::handle).add();
        CHANNEL.messageBuilder(HeavyReleasePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HeavyReleasePacket::encode).decoder(HeavyReleasePacket::decode).consumerMainThread(HeavyReleasePacket::handle).add();
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
        CHANNEL.messageBuilder(OtherworldPackets.Judgement.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OtherworldPackets.Judgement::encode).decoder(OtherworldPackets.Judgement::decode).consumerMainThread(OtherworldPackets.Judgement::handle).add();
        CHANNEL.messageBuilder(OtherworldPackets.Return.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OtherworldPackets.Return::encode).decoder(OtherworldPackets.Return::decode).consumerMainThread(OtherworldPackets.Return::handle).add();
        CHANNEL.messageBuilder(RaceLookPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RaceLookPacket::encode).decoder(RaceLookPacket::decode).consumerMainThread(RaceLookPacket::handle).add();
        CHANNEL.messageBuilder(FusionPackets.Show.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FusionPackets.Show::encode).decoder(FusionPackets.Show::decode).consumerMainThread(FusionPackets.Show::handle).add();
        CHANNEL.messageBuilder(FusionPackets.End.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FusionPackets.End::encode).decoder(FusionPackets.End::decode).consumerMainThread(FusionPackets.End::handle).add();
        CHANNEL.messageBuilder(FusionPackets.Press.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FusionPackets.Press::encode).decoder(FusionPackets.Press::decode).consumerMainThread(FusionPackets.Press::handle).add();
        CHANNEL.messageBuilder(TournamentPackets.State.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TournamentPackets.State::encode).decoder(TournamentPackets.State::decode).consumerMainThread(TournamentPackets.State::handle).add();
        CHANNEL.messageBuilder(TournamentPackets.Join.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TournamentPackets.Join::encode).decoder(TournamentPackets.Join::decode).consumerMainThread(TournamentPackets.Join::handle).add();
        CHANNEL.messageBuilder(PvpTogglePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PvpTogglePacket::encode).decoder(PvpTogglePacket::decode).consumerMainThread(PvpTogglePacket::handle).add();
        CHANNEL.messageBuilder(KiBlastPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KiBlastPacket::encode).decoder(KiBlastPacket::decode).consumerMainThread(KiBlastPacket::handle).add();
        CHANNEL.messageBuilder(MeleeInputPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MeleeInputPacket::encode).decoder(MeleeInputPacket::decode).consumerMainThread(MeleeInputPacket::handle).add();
        CHANNEL.messageBuilder(UltimatePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UltimatePacket::encode).decoder(UltimatePacket::decode).consumerMainThread(UltimatePacket::handle).add();
        CHANNEL.messageBuilder(TechniqueTiersPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TechniqueTiersPacket::encode).decoder(TechniqueTiersPacket::decode).consumerMainThread(TechniqueTiersPacket::handle).add();
        CHANNEL.messageBuilder(MoveAnimPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MoveAnimPacket::encode).decoder(MoveAnimPacket::decode).consumerMainThread(MoveAnimPacket::handle).add();
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
        CHANNEL.messageBuilder(PathPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PathPackets.Open::encode).decoder(PathPackets.Open::decode).consumerMainThread(PathPackets.Open::handle).add();
        CHANNEL.messageBuilder(RacialPackets.SelectSkill.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RacialPackets.SelectSkill::encode).decoder(RacialPackets.SelectSkill::decode).consumerMainThread(RacialPackets.SelectSkill::handle).add();
        CHANNEL.messageBuilder(RacialPackets.Learn.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RacialPackets.Learn::encode).decoder(RacialPackets.Learn::decode).consumerMainThread(RacialPackets.Learn::handle).add();
        CHANNEL.messageBuilder(RacialPackets.TransmitRequest.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RacialPackets.TransmitRequest::encode).decoder(RacialPackets.TransmitRequest::decode).consumerMainThread(RacialPackets.TransmitRequest::handle).add();
        CHANNEL.messageBuilder(RacialPackets.TransmitTargets.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RacialPackets.TransmitTargets::encode).decoder(RacialPackets.TransmitTargets::decode).consumerMainThread(RacialPackets.TransmitTargets::handle).add();
        CHANNEL.messageBuilder(RacialPackets.Transmit.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RacialPackets.Transmit::encode).decoder(RacialPackets.Transmit::decode).consumerMainThread(RacialPackets.Transmit::handle).add();
        CHANNEL.messageBuilder(RacialPackets.Select.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RacialPackets.Select::encode).decoder(RacialPackets.Select::decode).consumerMainThread(RacialPackets.Select::handle).add();
        CHANNEL.messageBuilder(PathPackets.Choose.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PathPackets.Choose::encode).decoder(PathPackets.Choose::decode).consumerMainThread(PathPackets.Choose::handle).add();
        CHANNEL.messageBuilder(KiCreatorPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KiCreatorPacket::encode).decoder(KiCreatorPacket::decode).consumerMainThread(KiCreatorPacket::handle).add();
        CHANNEL.messageBuilder(StrugglePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StrugglePacket::encode).decoder(StrugglePacket::decode).consumerMainThread(StrugglePacket::handle).add();
        CHANNEL.messageBuilder(BeamMashPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BeamMashPacket::encode).decoder(BeamMashPacket::decode).consumerMainThread(BeamMashPacket::handle).add();
        CHANNEL.messageBuilder(AppearancePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AppearancePacket::encode).decoder(AppearancePacket::decode).consumerMainThread(AppearancePacket::handle).add();
        CHANNEL.messageBuilder(ImpactPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ImpactPacket::encode).decoder(ImpactPacket::decode).consumerMainThread(ImpactPacket::handle).add();
        CHANNEL.messageBuilder(AnimEventPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnimEventPacket::encode).decoder(AnimEventPacket::decode).consumerMainThread(AnimEventPacket::handle).add();
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
