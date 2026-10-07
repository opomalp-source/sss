package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client (CX-19 phase 8): the foe you face, for the enemy panel ({@code entityId} -1: none). Ki and guard as
 * fractions and the special meter in points, for player foes; -1 where not known.
 */
public record FoeStatusPacket(int entityId, float ki, float guard, float special) {
    public static void encode(FoeStatusPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId + 1);
        buf.writeFloat(m.ki);
        buf.writeFloat(m.guard);
        buf.writeFloat(m.special);
    }

    public static FoeStatusPacket decode(FriendlyByteBuf buf) {
        return new FoeStatusPacket(buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(FoeStatusPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ui.EnemyPanel.update(m.entityId, m.ki, m.guard, m.special);   // client-only class, loaded only here
    }
}
