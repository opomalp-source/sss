package com.dbzenith.network;

import com.dbzenith.world.TitleEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: choose a title (validated: must be earned). */
public record SelectTitlePacket(String titleId) {
    public static void encode(SelectTitlePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.titleId, 32);
    }

    public static SelectTitlePacket decode(FriendlyByteBuf buf) {
        return new SelectTitlePacket(buf.readUtf(32));
    }

    public static void handle(SelectTitlePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) TitleEvents.select(player, msg.titleId);
    }
}
