package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client (CX-19 phase 9): a line for the combat log. */
public record CombatLogPacket(Component line) {
    public static void encode(CombatLogPacket m, FriendlyByteBuf buf) {
        buf.writeComponent(m.line);
    }

    public static CombatLogPacket decode(FriendlyByteBuf buf) {
        return new CombatLogPacket(buf.readComponent());
    }

    public static void handle(CombatLogPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ui.CombatLogOverlay.add(m.line);   // client-only class, loaded only here
    }
}
