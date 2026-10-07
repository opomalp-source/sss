package com.dbzenith.network;

import com.dbzenith.skill.BeamStruggle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: one press of the ki key during a beam struggle. */
public record BeamMashPacket() {
    public static void encode(BeamMashPacket m, FriendlyByteBuf buf) {
    }

    public static BeamMashPacket decode(FriendlyByteBuf buf) {
        return new BeamMashPacket();
    }

    public static void handle(BeamMashPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (InputGuard.allow(player, InputGuard.Kind.BEAM_MASH)) BeamStruggle.mash(player);
    }
}
