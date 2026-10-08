package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Charging techniques and holding transformations (CX-23). */
public final class KiChargePackets {
    private KiChargePackets() {}

    /** Client to server: the technique key went down (with the technique) or came up. */
    public record Input(boolean down, String technique) {
        public static void encode(Input m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.down);
            buf.writeUtf(m.technique, 64);
        }

        public static Input decode(FriendlyByteBuf buf) {
            return new Input(buf.readBoolean(), buf.readUtf(64));
        }

        public static void handle(Input m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (m.down) {
                if (InputGuard.allow(p, InputGuard.Kind.TECHNIQUE)) com.dbzenith.skill.KiCharge.press(p, m.technique);
            } else {
                com.dbzenith.skill.KiCharge.release(p);
            }
        }
    }

    /**
     * Server to the clients near a player: their charge (kind of hold, colour, 0..1 grown, the damage multiplier it has
     * reached, the longest charge in ticks); a fraction below 0 means it stopped.
     */
    public record State(int entityId, int kind, int color, float fraction, float power, int maxTicks) {
        public static void encode(State m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeByte(m.kind);
            buf.writeInt(m.color);
            buf.writeFloat(m.fraction);
            buf.writeFloat(m.power);
            buf.writeVarInt(m.maxTicks);
        }

        public static State decode(FriendlyByteBuf buf) {
            return new State(buf.readVarInt(), buf.readByte(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
        }

        public static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientKiCharge.set(m);                           // client-only class
        }
    }
}
