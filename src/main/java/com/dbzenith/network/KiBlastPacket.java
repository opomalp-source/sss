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
        if (p != null && msg.down && p.isShiftKeyDown() && com.dbzenith.combat.Limbs.openWound(p)) {   // Sneak + Ki Blast with an open stump: sear it shut (the limbs rework)
            searWound(p);
            return;
        }
        if (p != null && (msg.down ? InputGuard.allow(p, InputGuard.Kind.KI_BLAST) : p.isAlive())) com.dbzenith.combat.engine.KiCombat.key(p, msg.down);
    }

    /** Searing a wound shut with your own ki: a tenth of your ki, and it hurts. */
    private static void searWound(ServerPlayer p) {
        var d = com.dbzenith.data.ModCapabilities.get(p).orElse(null);
        if (d == null) return;
        double cost = d.getDerived().maxKi() * 0.1;
        if (d.getKi() < cost && !p.getAbilities().instabuild) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.sear_no_ki"), true);
            return;
        }
        if (!com.dbzenith.combat.Limbs.sear(p)) return;
        if (!p.getAbilities().instabuild) d.setKi(d.getKi() - cost);
        d.setBody(Math.max(1, d.getBody() - d.getDerived().maxBody() * 0.03));
        com.dbzenith.combat.BodyHealth.mirror(p, d);
        d.markDirty();
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.seared"), true);
    }
}
