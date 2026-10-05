package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the face, hair highlight and aura colour chosen in the Face screen (free, any time). */
public record FacePacket(int face, int highlight, int aura) {
    public static void encode(FacePacket m, FriendlyByteBuf buf) {
        buf.writeInt(m.face);
        buf.writeInt(m.highlight);
        buf.writeInt(m.aura);
    }

    public static FacePacket decode(FriendlyByteBuf buf) {
        return new FacePacket(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(FacePacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ModCapabilities.get(player).ifPresent(d -> {
            d.setFace(m.face);
            d.setHighlightColor(m.highlight);
            d.setAuraColor(m.aura);
        });
    }
}
