package com.dbzenith.network;

import com.dbzenith.galaxy.space.PlanetMaps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A planet's map from its real ground (the space rework, phase 4): server to client, deflated RGB texels for one
 * planet of the client's system. With {@code span} 0 it is the whole planet (Earth's around the player's departure
 * point {@code ax}, {@code az}); otherwise the regional map of {@code [ax, ax + span) × [az, az + span)}. Requested by
 * the client ({@link Request}) when it first draws them.
 */
public record PlanetMapPacket(String system, int index, int ax, int az, int span, int width, int height, byte[] pixels) {
    private static final int MAX_BYTES = 1 << 20;

    public static void encode(PlanetMapPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.system, 40);
        b.writeVarInt(m.index);
        b.writeInt(m.ax);
        b.writeInt(m.az);
        b.writeVarInt(m.span);
        b.writeVarInt(m.width);
        b.writeVarInt(m.height);
        b.writeByteArray(m.pixels);
    }

    public static PlanetMapPacket decode(FriendlyByteBuf b) {
        return new PlanetMapPacket(b.readUtf(40), b.readVarInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(MAX_BYTES));
    }

    public static void handle(PlanetMapPacket m, Supplier<NetworkEvent.Context> c) {
        com.dbzenith.client.space.OrbitalTextures.receive(m);
    }

    /** Client to server: the map of planet {@code index} of the system the client is in (or its region at {@code x0}, {@code z0}), please. */
    public record Request(String system, int index, int x0, int z0, int span) {
        public static void encode(Request m, FriendlyByteBuf b) {
            b.writeUtf(m.system, 40);
            b.writeVarInt(m.index);
            b.writeInt(m.x0);
            b.writeInt(m.z0);
            b.writeVarInt(m.span);
        }

        public static Request decode(FriendlyByteBuf b) {
            return new Request(b.readUtf(40), b.readVarInt(), b.readInt(), b.readInt(), b.readVarInt());
        }

        public static void handle(Request m, Supplier<NetworkEvent.Context> c) {
            var p = c.get().getSender();
            if (p != null) PlanetMaps.request(p, m.system, m.index, m.x0, m.z0, m.span);
        }
    }
}
