package com.dbzenith.living;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The conversation screen's traffic: the server shows a line and the options the player may pick ({@link Show}; an
 * empty one closes the screen); the client answers with the number picked ({@link Choose}) or that it left
 * ({@link Leave}). The server decides everything ({@link Conversations}).
 */
public final class ConversationPackets {
    static final int MAX_OPTIONS = Conversations.MAX_OPTIONS;

    private ConversationPackets() {}

    /** What the resident says now and what the player may answer. {@code subtitle}: occupation|mood|relation|town. */
    public record Show(int entity, String name, String subtitle, String text, List<String> options) {
        public static void encode(Show m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.name, 64);
            buf.writeUtf(m.subtitle, 160);
            buf.writeUtf(m.text, 1024);
            buf.writeVarInt(Math.min(MAX_OPTIONS, m.options.size()));
            for (int i = 0; i < Math.min(MAX_OPTIONS, m.options.size()); i++) buf.writeUtf(m.options.get(i), 160);
        }

        public static Show decode(FriendlyByteBuf buf) {
            int e = buf.readVarInt();
            String name = buf.readUtf(64), sub = buf.readUtf(160), text = buf.readUtf(1024);
            int n = Math.min(MAX_OPTIONS, buf.readVarInt());
            List<String> opts = new ArrayList<>();
            for (int i = 0; i < n; i++) opts.add(buf.readUtf(160));
            return new Show(e, name, sub, text, opts);
        }

        public static void handle(Show m, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.screen.DialogueScreen.receive(m);                    // client-only class, loaded only here
        }
    }

    /** The player picked an option. */
    public record Choose(int entity, int index) {
        public static void encode(Choose m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeVarInt(m.index);
        }

        public static Choose decode(FriendlyByteBuf buf) {
            return new Choose(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Choose m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !com.dbzenith.network.InputGuard.allow(p, com.dbzenith.network.InputGuard.Kind.TOGGLE)) return;
            Conversations.choose(p, m.entity, m.index);
        }
    }

    /** The player closed the screen. */
    public record Leave() {
        public static void encode(Leave m, FriendlyByteBuf buf) {}

        public static Leave decode(FriendlyByteBuf buf) {
            return new Leave();
        }

        public static void handle(Leave m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Conversations.close(p, false);
        }
    }
}
