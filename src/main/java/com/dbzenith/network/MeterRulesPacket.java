package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: the PvP meters' rules (CX-20), as merged JSON, sent on joining and after a reload. */
public record MeterRulesPacket(String json) {
    public static void encode(MeterRulesPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.json, 32767);
    }

    public static MeterRulesPacket decode(FriendlyByteBuf buf) {
        return new MeterRulesPacket(buf.readUtf(32767));
    }

    public static void handle(MeterRulesPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.combat.meter.MeterRules.apply(m.json);
    }
}
