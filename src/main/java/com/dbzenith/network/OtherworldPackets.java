package com.dbzenith.network;

import com.dbzenith.world.Otherworld;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The other world (CX-12): the judge opens your case; you ask to go back among the living. */
public final class OtherworldPackets {
    private OtherworldPackets() {}

    /** Server to client: your case before Enma. */
    public record Judgement(boolean dead, int secondsLeft) {
        public static void encode(Judgement msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.dead);
            buf.writeVarInt(msg.secondsLeft);
        }

        public static Judgement decode(FriendlyByteBuf buf) {
            return new Judgement(buf.readBoolean(), buf.readVarInt());
        }

        public static void handle(Judgement msg, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientHooks.openJudgement(msg.dead, msg.secondsLeft);   // client-only class, loaded only here
        }
    }

    /** Client to server: send me back. Checked in {@link Otherworld#returnToLife}. */
    public record Return() {
        public static void encode(Return msg, FriendlyByteBuf buf) {}

        public static Return decode(FriendlyByteBuf buf) {
            return new Return();
        }

        public static void handle(Return msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) Otherworld.returnToLife(player);
        }
    }
}
