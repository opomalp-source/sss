package com.dbzenith.network;

import com.dbzenith.fusion.FusionDance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The Fusion Dance and the Potara (12c): the session as the dancers and onlookers see it, and the dancers' beats. */
public final class FusionPackets {
    private FusionPackets() {}

    /**
     * Server to the two and everyone watching: a dance or Potara between {@code a} (who asked; on the left in the dance)
     * and {@code b}, begun at game time {@code start}, facing {@code yaw}; {@code marks} are the beats judged so far
     * ({@link FusionDance.Session#marks}). Sent again whenever a beat is judged.
     */
    public record Show(int a, int b, int kind, long start, float yaw, int marks) {
        public static void encode(Show msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.a);
            buf.writeVarInt(msg.b);
            buf.writeByte(msg.kind);
            buf.writeLong(msg.start);
            buf.writeFloat(msg.yaw);
            buf.writeVarInt(msg.marks);
        }

        public static Show decode(FriendlyByteBuf buf) {
            return new Show(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readLong(), buf.readFloat(), buf.readVarInt());
        }

        public static void handle(Show msg, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientFusion.show(msg);                       // client-only class, loaded only here
        }
    }

    /** Server to the same: the session is over (fused, or broken off). */
    public record End(int a, int b) {
        public static void encode(End msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.a);
            buf.writeVarInt(msg.b);
        }

        public static End decode(FriendlyByteBuf buf) {
            return new End(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(End msg, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientFusion.end(msg.a, msg.b);
        }
    }

    /** Client to server: a dancer hits the beat (or tries to). */
    public record Press() {
        public static void encode(Press msg, FriendlyByteBuf buf) {}

        public static Press decode(FriendlyByteBuf buf) {
            return new Press();
        }

        public static void handle(Press msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) FusionDance.press(player);
        }
    }
}
