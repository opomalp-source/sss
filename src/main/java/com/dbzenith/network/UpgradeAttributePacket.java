package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.AttributeTraining;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: spend TP to raise an attribute. */
public record UpgradeAttributePacket(Attribute attribute, int times) {
    public static void encode(UpgradeAttributePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.attribute);
        buf.writeVarInt(msg.times);
    }

    public static UpgradeAttributePacket decode(FriendlyByteBuf buf) {
        return new UpgradeAttributePacket(buf.readEnum(Attribute.class), buf.readVarInt());
    }

    public static void handle(UpgradeAttributePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) ModCapabilities.get(player).ifPresent(d -> AttributeTraining.upgrade(d, msg.attribute, msg.times));
    }
}
