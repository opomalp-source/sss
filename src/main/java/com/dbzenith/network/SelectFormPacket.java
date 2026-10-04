package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.transform.Forms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: choose the form the transform key aims for ("" = next unlocked). */
public record SelectFormPacket(String formId) {
    public static void encode(SelectFormPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.formId, 64);
    }

    public static SelectFormPacket decode(FriendlyByteBuf buf) {
        return new SelectFormPacket(buf.readUtf(64));
    }

    public static void handle(SelectFormPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        String id = msg.formId.isEmpty() || Forms.exists(msg.formId) ? msg.formId : "";
        ModCapabilities.get(player).ifPresent(d -> d.setTargetForm(id));
    }
}
