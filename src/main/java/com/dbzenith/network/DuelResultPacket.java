package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client (CX-19 phase 9): a duel's result, from this player's side. {@code outcome}: 0 draw, 1 won, 2 lost,
 * 3 watched. Stats: damage, hits, longest combo, perfect guards, vanishes.
 */
public record DuelResultPacket(int outcome, String reason, String me, String foe, int myWins, int foeWins, double[] myStats, double[] foeStats,
                               int rating, int ratingDelta, int bestOf, String rules, boolean watcher) {
    public static void encode(DuelResultPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.outcome);
        buf.writeUtf(m.reason, 32);
        buf.writeUtf(m.me, 32);
        buf.writeUtf(m.foe, 32);
        buf.writeVarInt(m.myWins);
        buf.writeVarInt(m.foeWins);
        for (int i = 0; i < 5; i++) buf.writeDouble(i < m.myStats.length ? m.myStats[i] : 0);
        for (int i = 0; i < 5; i++) buf.writeDouble(i < m.foeStats.length ? m.foeStats[i] : 0);
        buf.writeVarInt(m.rating);
        buf.writeInt(m.ratingDelta);
        buf.writeVarInt(m.bestOf);
        buf.writeUtf(m.rules, 16);
        buf.writeBoolean(m.watcher);
    }

    public static DuelResultPacket decode(FriendlyByteBuf buf) {
        int outcome = buf.readByte();
        String reason = buf.readUtf(32), me = buf.readUtf(32), foe = buf.readUtf(32);
        int mw = buf.readVarInt(), fw = buf.readVarInt();
        double[] a = new double[5], b = new double[5];
        for (int i = 0; i < 5; i++) a[i] = buf.readDouble();
        for (int i = 0; i < 5; i++) b[i] = buf.readDouble();
        return new DuelResultPacket(outcome, reason, me, foe, mw, fw, a, b, buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readUtf(16), buf.readBoolean());
    }

    public static void handle(DuelResultPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.screen.DuelResultScreen.open(m);   // client-only class, loaded only here
    }
}
