package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client (CX-19 phase 8): a big callout across the middle of the screen (VANISH!, COUNTER!, CLASH!...). */
public record CalloutPacket(String key, int color) {
    public static void send(ServerPlayer p, String key, int color) {
        ModNetwork.sendTo(p, new CalloutPacket(key, color));
    }

    public static void encode(CalloutPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.key, 96);
        buf.writeInt(m.color);
    }

    public static CalloutPacket decode(FriendlyByteBuf buf) {
        return new CalloutPacket(buf.readUtf(96), buf.readInt());
    }

    public static void handle(CalloutPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ui.CalloutOverlay.show(m.key, m.color);   // client-only class, loaded only here
    }
}
