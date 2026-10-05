package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerDataEvents;
import com.dbzenith.race.Milestones;
import com.dbzenith.race.Variant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The milestone path choice: the server opens the screen, the client answers with a path. */
public final class PathPackets {
    private PathPackets() {}

    /** Server to client: open the path choice. */
    public record Open() {
        public static void encode(Open m, FriendlyByteBuf buf) {
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open();
        }

        public static void handle(Open m, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientHooks.openPathChoice();
        }
    }

    /** Client to server: the chosen path. */
    public record Choose(String variant) {
        public static void encode(Choose m, FriendlyByteBuf buf) {
            buf.writeUtf(m.variant, 64);
        }

        public static Choose decode(FriendlyByteBuf buf) {
            return new Choose(buf.readUtf(64));
        }

        public static void handle(Choose m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModCapabilities.get(player).ifPresent(d -> {
                Variant v = Variant.byId(m.variant, d.getRace());
                if (Milestones.choosePath(d, v)) {
                    player.displayClientMessage(Component.translatable("message.dbzenith.path_chosen", Component.translatable(v.translationKey())), false);
                    PlayerDataEvents.sync(player);
                }
            });
        }
    }
}
