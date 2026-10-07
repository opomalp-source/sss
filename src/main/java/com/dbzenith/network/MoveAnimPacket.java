package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: a fighter starts a move (CX-19); play its animation clip (named in the move's data). */
public record MoveAnimPacket(int entityId, String clip) {
    public static void encode(MoveAnimPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeUtf(m.clip, 48);
    }

    public static MoveAnimPacket decode(FriendlyByteBuf buf) {
        return new MoveAnimPacket(buf.readVarInt(), buf.readUtf(48));
    }

    public static void handle(MoveAnimPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.anim.AnimController.playClip(m.entityId, m.clip);   // client-only class, loaded only here
    }
}
