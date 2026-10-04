package com.dbzenith.world;

import com.dbzenith.data.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Scars and tattoos: free, purely visual marks drawn over the skin (see {@code client.render.CosmeticsLayer}). */
public final class Cosmetics {
    public static final List<String> SCARS = List.of("none", "eye", "cheek", "chest");
    public static final List<String> TATTOOS = List.of("none", "arm", "back", "chest");

    private Cosmetics() {}

    /** Client to server: choose a scar and a tattoo (indices into {@link #SCARS} / {@link #TATTOOS}). */
    public record Packet(int scar, int tattoo) {
        public static void encode(Packet msg, FriendlyByteBuf buf) {
            buf.writeByte(msg.scar);
            buf.writeByte(msg.tattoo);
        }

        public static Packet decode(FriendlyByteBuf buf) {
            return new Packet(buf.readUnsignedByte(), buf.readUnsignedByte());
        }

        public static void handle(Packet msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.scar >= SCARS.size() || msg.tattoo >= TATTOOS.size()) return;
            ModCapabilities.get(player).ifPresent(d -> d.setCosmetics(msg.scar, msg.tattoo));
        }
    }
}
