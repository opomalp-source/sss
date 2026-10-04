package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to the summoner: open the wish menu for this dragon. */
public record OpenWishPacket(int dragonId) {
    public static void encode(OpenWishPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.dragonId);
    }

    public static OpenWishPacket decode(FriendlyByteBuf buf) {
        return new OpenWishPacket(buf.readVarInt());
    }

    public static void handle(OpenWishPacket msg, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientHooks.openWishScreen(msg.dragonId); // client-only class, only loaded here
    }
}
