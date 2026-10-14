package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to clients (CX-51): a fighter is knocked out on the ground ({@code ticksLeft} until they bleed out, of
 * {@code total}; whether they are strong enough to crawl), or is not any more ({@code ticksLeft} below zero).
 */
public record KnockOutPacket(int entityId, int ticksLeft, int total, boolean crawl) {
    public static void encode(KnockOutPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeInt(m.ticksLeft);
        buf.writeVarInt(m.total);
        buf.writeBoolean(m.crawl);
    }

    public static KnockOutPacket decode(FriendlyByteBuf buf) {
        return new KnockOutPacket(buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(KnockOutPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientKnockouts.set(m);                              // client-only class
    }
}
