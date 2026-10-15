package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to clients (CX-53): a fighter's limbs: each one's health (0..100, right arm, left arm, right leg, left leg),
 * which are cut off, which are prosthetics, and which are bleeding (bit masks, same order; bleeding: the knocked-out
 * rework), and each lost limb's wound (2 bits a limb: open, bandaged, seared, healed; the limbs rework).
 */
public record LimbsPacket(int entityId, byte[] health, int severed, int prosthetic, int bleeding, int stumps) {
    public static void encode(LimbsPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeByteArray(m.health);
        buf.writeByte(m.severed);
        buf.writeByte(m.prosthetic);
        buf.writeByte(m.bleeding);
        buf.writeByte(m.stumps);
    }

    public static LimbsPacket decode(FriendlyByteBuf buf) {
        return new LimbsPacket(buf.readVarInt(), buf.readByteArray(8), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte() & 0xFF);
    }

    public static void handle(LimbsPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.combat.Limbs.onPacket(m);
    }
}
