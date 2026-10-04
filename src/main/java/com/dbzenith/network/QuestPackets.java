package com.dbzenith.network;

import com.dbzenith.quest.Quest;
import com.dbzenith.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Quest board packets: the server opens a giver's board; the client asks to accept or turn in. */
public final class QuestPackets {
    private QuestPackets() {}

    /** Server to client. */
    public record Open(Quest.Giver giver) {
        public static void encode(Open msg, FriendlyByteBuf buf) {
            buf.writeEnum(msg.giver);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(buf.readEnum(Quest.Giver.class));
        }

        public static void handle(Open msg, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientHooks.openQuestScreen(msg.giver); // client-only class, loaded only here
        }
    }

    /** Client to server. Validated in {@link QuestManager}. */
    public record Action(boolean turnIn, String questId) {
        public static void encode(Action msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.turnIn);
            buf.writeUtf(msg.questId, 64);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readBoolean(), buf.readUtf(64));
        }

        public static void handle(Action msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.turnIn) QuestManager.turnIn(player, msg.questId);
            else QuestManager.accept(player, msg.questId);
        }
    }
}
