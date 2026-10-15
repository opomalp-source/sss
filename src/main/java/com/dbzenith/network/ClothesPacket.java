package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to clients (the knocked-out rework): how torn a fighter's own clothes are, 0 whole .. 3 in tatters. */
public record ClothesPacket(int entityId, int level) {
    public static void encode(ClothesPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeByte(m.level);
    }

    public static ClothesPacket decode(FriendlyByteBuf buf) {
        return new ClothesPacket(buf.readVarInt(), buf.readByte());
    }

    public static void handle(ClothesPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.render.BattleDamageLayer.clothes(m.entityId(), m.level());   // client-only class
    }
}
