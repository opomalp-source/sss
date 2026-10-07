package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to clients tracking a fighter (CX-19e): its combat state changed (stunned, launched, knocked down, guarding, or
 * back to idle), for its stance animation. Players and NPCs alike.
 */
public record FighterStatePacket(int entityId, int state) {
    public static void encode(FighterStatePacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeByte(m.state);
    }

    public static FighterStatePacket decode(FriendlyByteBuf buf) {
        return new FighterStatePacket(buf.readVarInt(), buf.readUnsignedByte());
    }

    public static void handle(FighterStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.anim.FighterStates.set(m.entityId, m.state);   // client-only class, loaded only here
    }
}
