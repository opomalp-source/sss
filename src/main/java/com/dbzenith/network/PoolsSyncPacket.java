package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to the owning client (CX-19 phase 7): the pools that change nearly every tick (body, ki, stamina, the special and
 * guard meters, and the PvP meters of CX-20), 48 bytes, in place of the whole player state (which goes only when something else changed).
 */
public record PoolsSyncPacket(double body, double ki, double stamina, double special, double guardMeter, double formMeter, double techMeter) {
    public static PoolsSyncPacket of(com.dbzenith.data.PlayerData d) {
        return new PoolsSyncPacket(d.getBody(), d.getKi(), d.getStamina(), d.getSpecial(), d.getGuardMeter(), d.getFormMeter(), d.getTechMeter());
    }

    public static void encode(PoolsSyncPacket m, FriendlyByteBuf buf) {
        buf.writeDouble(m.body);
        buf.writeDouble(m.ki);
        buf.writeDouble(m.stamina);
        buf.writeDouble(m.special);
        buf.writeDouble(m.guardMeter);
        buf.writeFloat((float) m.formMeter);
        buf.writeFloat((float) m.techMeter);
    }

    public static PoolsSyncPacket decode(FriendlyByteBuf buf) {
        return new PoolsSyncPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(PoolsSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientPlayerData.get().applyPools(m.body, m.ki, m.stamina, m.special, m.guardMeter, m.formMeter, m.techMeter);   // client-only class
    }
}
