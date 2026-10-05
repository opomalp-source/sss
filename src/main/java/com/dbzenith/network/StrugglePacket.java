package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to a player locked in a beam struggle: who is winning ({@code balance} -1 losing .. 1 winning, from this
 * player's side) and both beams' colours, for the tug-of-war bar. {@code active} false ends it.
 */
public record StrugglePacket(boolean active, float balance, int mine, int theirs) {
    public static void encode(StrugglePacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.active);
        buf.writeFloat(m.balance);
        buf.writeInt(m.mine);
        buf.writeInt(m.theirs);
    }

    public static StrugglePacket decode(FriendlyByteBuf buf) {
        return new StrugglePacket(buf.readBoolean(), Math.max(-1, Math.min(1, buf.readFloat())), buf.readInt(), buf.readInt());
    }

    public static void handle(StrugglePacket m, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.ClientStruggle.update(m);
    }
}
