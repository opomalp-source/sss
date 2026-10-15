package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to clients (the knocked-out rework): {@code carrierId} picked up {@code carriedId} ({@code on}), or let them go. */
public record CarryPacket(int carrierId, int carriedId, boolean on) {
    public static void encode(CarryPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.carrierId);
        buf.writeVarInt(m.carriedId);
        buf.writeBoolean(m.on);
    }

    public static CarryPacket decode(FriendlyByteBuf buf) {
        return new CarryPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(CarryPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientCarry.set(m);                                 // client-only class
    }
}
