package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: a fighter starts a move (CX-19); play its animation clip (named in the move's data). For your own
 * moves it also confirms or corrects what your client predicted (phase 7), by the move's id.
 */
public record MoveAnimPacket(int entityId, String clip, String moveId) {
    public static void encode(MoveAnimPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeUtf(m.clip, 48);
        buf.writeUtf(m.moveId, 128);
    }

    public static MoveAnimPacket decode(FriendlyByteBuf buf) {
        return new MoveAnimPacket(buf.readVarInt(), buf.readUtf(48), buf.readUtf(128));
    }

    public static void handle(MoveAnimPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.anim.AnimController.onMove(m.entityId, m.clip, m.moveId);   // client-only class, loaded only here
    }
}
