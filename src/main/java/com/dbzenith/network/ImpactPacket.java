package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server to clients near a hit: where something struck, how hard and which way, so each client can draw the flash,
 * shockwave and debris, shake its camera by distance and freeze the fighters for a few frames if it is one of them.
 */
public record ImpactPacket(float x, float y, float z, float dx, float dy, float dz, int kind, float scale, int color,
                           int attackerId, int victimId, int flags, int hitstop) {
    public static final int PUNCH = 0;
    public static final int HEAVY = 1;
    public static final int SPIKE = 2;
    public static final int GUARD = 3;
    public static final int KI_HIT = 4;
    public static final int EXPLOSION = 5;
    public static final int PARRY = 6;
    public static final int GUARD_BREAK = 7;
    public static final int DEFLECT = 8;

    private static final double RANGE = 96;

    /** Flags (CX-19e): what kind of blow it was, for its look and sound. */
    public static final int CRIT = 1, COUNTER = 2, ZHIT = 4;

    /** Hitstop ticks for a kind of impact (before the config's scale): light 2, heavy 4, more for criticals and counters. */
    public static int hitstopFor(int kind, int flags) {
        int t = switch (kind) {
            case PUNCH -> 2;
            case HEAVY, SPIKE -> 4;
            case GUARD -> 1;
            case PARRY -> 6;
            case GUARD_BREAK -> 5;
            case DEFLECT -> 2;
            default -> 0;
        };
        if (t == 0) return 0;
        if ((flags & CRIT) != 0) t += 1;
        if ((flags & COUNTER) != 0) t += 2;
        return t;
    }

    /** This impact with its flags and the hitstop (ticks; -1 lets the client pick by kind). */
    public ImpactPacket withFeel(int flags, int hitstop) {
        return new ImpactPacket(x, y, z, dx, dy, dz, kind, scale, color, attackerId, victimId, flags, hitstop);
    }

    /** A fist (or foot) landing on {@code victim}: placed on the victim's side facing the attacker. */
    public static ImpactPacket melee(Entity attacker, Entity victim, int kind) {
        Vec3 center = victim.getBoundingBox().getCenter();
        Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1, 0, 1);
        if (toAttacker.lengthSqr() > 1e-4) center = center.add(toAttacker.normalize().scale(victim.getBbWidth() * 0.55));
        Vec3 dir = attacker.getLookAngle();
        float size = Math.max(1f, victim.getBbHeight() / 1.8f);
        return new ImpactPacket((float) center.x, (float) center.y, (float) center.z, (float) dir.x, (float) dir.y, (float) dir.z,
                kind, size, 0xFFFFFF, attacker.getId(), victim.getId(), 0, -1);
    }

    public static ImpactPacket at(Vec3 pos, Vec3 dir, int kind, float scale, int color, int attackerId) {
        return new ImpactPacket((float) pos.x, (float) pos.y, (float) pos.z, (float) dir.x, (float) dir.y, (float) dir.z,
                kind, scale, color, attackerId, -1, 0, -1);
    }

    /** To every player within range of the impact. */
    public void send(ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(x, y, z, RANGE, level.dimension())), this);
    }

    public static void encode(ImpactPacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.x);
        buf.writeFloat(m.y);
        buf.writeFloat(m.z);
        buf.writeFloat(m.dx);
        buf.writeFloat(m.dy);
        buf.writeFloat(m.dz);
        buf.writeByte(m.kind);
        buf.writeFloat(m.scale);
        buf.writeInt(m.color);
        buf.writeVarInt(m.attackerId + 1);
        buf.writeVarInt(m.victimId + 1);
        buf.writeByte(m.flags);
        buf.writeByte(m.hitstop + 1);
    }

    public static ImpactPacket decode(FriendlyByteBuf buf) {
        return new ImpactPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readByte(), Math.min(buf.readFloat(), 16f), buf.readInt(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readByte(), Math.min(buf.readByte(), 20) - 1);
    }

    public static void handle(ImpactPacket msg, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.fx.ImpactFx.onImpact(msg);
    }
}
