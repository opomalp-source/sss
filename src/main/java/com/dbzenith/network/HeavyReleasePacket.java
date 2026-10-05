package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the heavy key let go, with the movement held (it picks a directional heavy). */
public record HeavyReleasePacket(float forward, float strafe) {
    public static void encode(HeavyReleasePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.strafe);
    }

    public static HeavyReleasePacket decode(FriendlyByteBuf buf) {
        return new HeavyReleasePacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(HeavyReleasePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && Float.isFinite(msg.forward) && Float.isFinite(msg.strafe)) com.dbzenith.data.ModCapabilities.get(player).ifPresent(d -> com.dbzenith.combat.CombatMoves.heavyReleased(player, d, msg.forward, msg.strafe));
    }
}
