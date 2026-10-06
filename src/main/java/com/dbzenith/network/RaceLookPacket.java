package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server (CX-16b): the race's own look, chosen at creation or later from the Life screen: the style of the
 * race's signature part and the skin, marking and part colours (-1 = the race's own).
 */
public record RaceLookPacket(int style, int skin, int mark, int part) {
    public static void encode(RaceLookPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.style);
        buf.writeInt(msg.skin);
        buf.writeInt(msg.mark);
        buf.writeInt(msg.part);
    }

    public static RaceLookPacket decode(FriendlyByteBuf buf) {
        return new RaceLookPacket(buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(RaceLookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ModCapabilities.get(player).ifPresent(d -> d.setRaceCustom(msg.style, msg.skin, msg.mark, msg.part));
    }
}
