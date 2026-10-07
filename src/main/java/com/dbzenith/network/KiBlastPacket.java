package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the Ki Blast key went down or up (CX-19). The server times the hold. */
public record KiBlastPacket(boolean down) {
    public static void encode(KiBlastPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.down);
    }

    public static KiBlastPacket decode(FriendlyByteBuf buf) {
        return new KiBlastPacket(buf.readBoolean());
    }

    public static void handle(KiBlastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        // a release always goes through (a dropped one would leave the charge running); presses are limited
        if (p != null && (msg.down ? InputGuard.allow(p, InputGuard.Kind.KI_BLAST) : p.isAlive())) com.dbzenith.combat.engine.KiCombat.key(p, msg.down);
    }
}
