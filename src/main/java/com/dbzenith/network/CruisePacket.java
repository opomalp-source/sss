package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * An in-system cruise (the space rework, phase 5), server to client: under way from {@code from} to {@code to}
 * (orbital blocks), {@code elapsed} of {@code duration} ticks; or ended ({@code active} false) with the player set
 * down at {@code to}, moving at {@code velocity}.
 */
public record CruisePacket(boolean active, Vec3 from, Vec3 to, int elapsed, int duration, String name, Vec3 velocity) {
    public static void encode(CruisePacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.active);
        for (Vec3 v : new Vec3[]{m.from, m.to, m.velocity}) {
            b.writeDouble(v.x);
            b.writeDouble(v.y);
            b.writeDouble(v.z);
        }
        b.writeVarInt(m.elapsed);
        b.writeVarInt(m.duration);
        b.writeUtf(m.name, 64);
    }

    public static CruisePacket decode(FriendlyByteBuf b) {
        boolean active = b.readBoolean();
        Vec3[] v = new Vec3[3];
        for (int i = 0; i < 3; i++) v[i] = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        return new CruisePacket(active, v[0], v[1], b.readVarInt(), b.readVarInt(), b.readUtf(64), v[2]);
    }

    public static void handle(CruisePacket m, Supplier<NetworkEvent.Context> c) {
        com.dbzenith.client.space.CruiseClient.receive(m);
    }

    /**
     * Client to server: engage (a cruise to planet {@code index} of this system, or with -1 hyperspeed to the
     * waypoint), or, while under way, disengage.
     */
    public record Engage(int index) {
        public static void encode(Engage m, FriendlyByteBuf b) {
            b.writeVarInt(m.index + 1);
        }

        public static Engage decode(FriendlyByteBuf b) {
            return new Engage(b.readVarInt() - 1);
        }

        public static void handle(Engage m, Supplier<NetworkEvent.Context> c) {
            var p = c.get().getSender();
            if (p != null && InputGuard.allow(p, InputGuard.Kind.GALAXY)) com.dbzenith.galaxy.space.Cruise.engage(p, m.index);
        }
    }
}
