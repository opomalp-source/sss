package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: toggle my PvP mode (CX-19). The server decides (cooldown, fights, safe zones). */
public record PvpTogglePacket() {
    public static void encode(PvpTogglePacket msg, FriendlyByteBuf buf) {}

    public static PvpTogglePacket decode(FriendlyByteBuf buf) {
        return new PvpTogglePacket();
    }

    public static void handle(PvpTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (InputGuard.allow(p, InputGuard.Kind.TOGGLE)) com.dbzenith.combat.PvpRules.toggle(p, null);
    }
}
