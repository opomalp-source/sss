package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to clients (the knocked-out rework): a grip between {@code gripperId} and {@code victimId} with a finisher
 * ({@code Grip.Finisher} ordinal): it started, reached its killing frame, ended, or was broken.
 */
public record GripPacket(int gripperId, int victimId, int finisher, int phase) {
    public static final int START = 0, KILL = 1, DONE = 2, BROKEN = 3;

    public static void encode(GripPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.gripperId);
        buf.writeVarInt(m.victimId);
        buf.writeByte(m.finisher);
        buf.writeByte(m.phase);
    }

    public static GripPacket decode(FriendlyByteBuf buf) {
        return new GripPacket(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte());
    }

    public static void handle(GripPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientGrip.set(m);                                   // client-only class
    }
}
