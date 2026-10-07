package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to a watcher (CX-19 phase 9): frame these two duelists (-1, -1: stop). */
public record DuelCamPacket(int a, int b) {
    public static void encode(DuelCamPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.a + 1);
        buf.writeVarInt(m.b + 1);
    }

    public static DuelCamPacket decode(FriendlyByteBuf buf) {
        return new DuelCamPacket(buf.readVarInt() - 1, buf.readVarInt() - 1);
    }

    public static void handle(DuelCamPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.DuelCam.set(m.a, m.b);   // client-only class, loaded only here
    }
}
