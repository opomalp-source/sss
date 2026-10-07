package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Server to client (CX-19 phase 7): the combat move files, so the client can start a move's animation at once. */
public record MovesSyncPacket(Map<String, String> moves) {
    public static void encode(MovesSyncPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.moves.size());
        m.moves.forEach((id, json) -> {
            buf.writeUtf(id, 128);
            buf.writeUtf(json, 8192);
        });
    }

    public static MovesSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) out.put(buf.readUtf(128), buf.readUtf(8192));
        return new MovesSyncPacket(out);
    }

    public static void handle(MovesSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.Prediction.setMoves(m.moves);   // client-only class, loaded only here
    }
}
