package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to the clients near a caster: an ultimate was fired (CX-19); play its cinematic. */
public record UltimatePacket(int casterId, String techniqueId, int color) {
    public static void encode(UltimatePacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.casterId);
        buf.writeUtf(m.techniqueId, 64);
        buf.writeInt(m.color);
    }

    public static UltimatePacket decode(FriendlyByteBuf buf) {
        return new UltimatePacket(buf.readVarInt(), buf.readUtf(64), buf.readInt());
    }

    public static void handle(UltimatePacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.fx.Cinematics.ultimate(m.casterId, m.techniqueId, m.color);   // client-only class, loaded only here
    }
}
