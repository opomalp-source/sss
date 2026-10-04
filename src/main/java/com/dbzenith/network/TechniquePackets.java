package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client to server requests from the Techniques screen. Everything is validated in {@link TechniqueLibrary}. */
public final class TechniquePackets {
    private static final int MAX_DECK_IDS = 32;

    private TechniquePackets() {}

    public record Learn(String techniqueId) {
        public static void encode(Learn msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.techniqueId, 64);
        }

        public static Learn decode(FriendlyByteBuf buf) {
            return new Learn(buf.readUtf(64));
        }

        public static void handle(Learn msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ModCapabilities.get(player).ifPresent(d -> TechniqueLibrary.learnWithTp(d, Techniques.byId(msg.techniqueId)));
        }
    }

    public record SetDeck(List<String> ids) {
        public static void encode(SetDeck msg, FriendlyByteBuf buf) {
            buf.writeVarInt(Math.min(msg.ids.size(), MAX_DECK_IDS));
            for (int i = 0; i < Math.min(msg.ids.size(), MAX_DECK_IDS); i++) buf.writeUtf(msg.ids.get(i), 64);
        }

        public static SetDeck decode(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), MAX_DECK_IDS);
            List<String> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readUtf(64));
            return new SetDeck(ids);
        }

        public static void handle(SetDeck msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ModCapabilities.get(player).ifPresent(d -> TechniqueLibrary.setDeck(d, msg.ids));
        }
    }
}
