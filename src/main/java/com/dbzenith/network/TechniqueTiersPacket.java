package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Server to client: what each technique costs from the special meter (CX-19), sent on joining and after a reload. */
public record TechniqueTiersPacket(Map<String, Double> meter) {
    public static void encode(TechniqueTiersPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.meter.size());
        m.meter.forEach((id, v) -> {
            buf.writeUtf(id, 64);
            buf.writeDouble(v);
        });
    }

    public static TechniqueTiersPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        Map<String, Double> out = new HashMap<>();
        for (int i = 0; i < n; i++) out.put(buf.readUtf(64), buf.readDouble());
        return new TechniqueTiersPacket(out);
    }

    public static void handle(TechniqueTiersPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientCombatState.setMeterCosts(m.meter);   // client-only class, loaded only here
    }
}
