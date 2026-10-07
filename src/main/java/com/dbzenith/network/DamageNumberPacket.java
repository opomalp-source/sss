package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server to clients near a hit (CX-19e): a damage number to pop up over {@code entityId}. */
public record DamageNumberPacket(int entityId, float amount, int flags, int color) {
    /** Flags on top of {@link ImpactPacket#CRIT}, {@link ImpactPacket#COUNTER} and {@link ImpactPacket#ZHIT}. */
    public static final int GUARDED = 8, KI = 16, HEAVY = 32;

    private static final double RANGE = 48;

    public void send(ServerLevel level, Entity at) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.getX(), at.getY(), at.getZ(), RANGE, level.dimension())), this);
    }

    public static void encode(DamageNumberPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeFloat(m.amount);
        buf.writeByte(m.flags);
        buf.writeInt(m.color);
    }

    public static DamageNumberPacket decode(FriendlyByteBuf buf) {
        return new DamageNumberPacket(buf.readVarInt(), buf.readFloat(), buf.readUnsignedByte(), buf.readInt());
    }

    public static void handle(DamageNumberPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.fx.DamagePopups.number(m.entityId, m.amount, m.flags, m.color);   // client-only class, loaded only here
    }
}
