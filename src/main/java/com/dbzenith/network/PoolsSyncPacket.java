package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to the owning client (CX-19 phase 7): the pools that change nearly every tick (body, ki, stamina, special and
 * guard meters), 40 bytes, in place of the whole player state (which goes only when something else changed).
 */
public record PoolsSyncPacket(double body, double ki, double stamina, double special, double guardMeter) {
    public static PoolsSyncPacket of(com.dbzenith.data.PlayerData d) {
        return new PoolsSyncPacket(d.getBody(), d.getKi(), d.getStamina(), d.getSpecial(), d.getGuardMeter());
    }

    public static void encode(PoolsSyncPacket m, FriendlyByteBuf buf) {
        buf.writeDouble(m.body);
        buf.writeDouble(m.ki);
        buf.writeDouble(m.stamina);
        buf.writeDouble(m.special);
        buf.writeDouble(m.guardMeter);
    }

    public static PoolsSyncPacket decode(FriendlyByteBuf buf) {
        return new PoolsSyncPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(PoolsSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientPlayerData.get().applyPools(m.body, m.ki, m.stamina, m.special, m.guardMeter);   // client-only class
    }
}
