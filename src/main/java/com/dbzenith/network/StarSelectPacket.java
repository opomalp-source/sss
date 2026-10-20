package com.dbzenith.network;

import com.dbzenith.galaxy.StarNavigation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Star navigation (the space rework, phase 2). Client to server: a star of the sky was targeted ({@link #TARGET}),
 * made the waypoint ({@link #WAYPOINT}) or opened in the atlas ({@link #MAP}); the server checks it really is a star of
 * the player's sky. Server to client ({@link Info}): what can be read of it from here.
 */
public record StarSelectPacket(String id, int mode) {
    public static final int TARGET = 0, WAYPOINT = 1, MAP = 2, CLEAR = 3;

    public static void encode(StarSelectPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.id, 40);
        b.writeVarInt(m.mode);
    }

    public static StarSelectPacket decode(FriendlyByteBuf b) {
        return new StarSelectPacket(b.readUtf(40), b.readVarInt());
    }

    public static void handle(StarSelectPacket m, Supplier<NetworkEvent.Context> c) {
        var p = c.get().getSender();
        if (p != null) StarNavigation.request(p, m.id, m.mode);
    }

    /** The reply: the star's readable information, or an empty tag when the target was refused or cleared. */
    public record Info(CompoundTag data) {
        public static void encode(Info m, FriendlyByteBuf b) {
            b.writeNbt(m.data);
        }

        public static Info decode(FriendlyByteBuf b) {
            var t = b.readNbt();
            return new Info(t == null ? new CompoundTag() : t);
        }

        public static void handle(Info m, Supplier<NetworkEvent.Context> c) {
            com.dbzenith.client.space.StarSky.info(m.data);
        }
    }
}
