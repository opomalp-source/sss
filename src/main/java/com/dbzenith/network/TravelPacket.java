package com.dbzenith.network;

import com.dbzenith.registry.ModItems;
import com.dbzenith.world.Planet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: fly to a planet. Requires holding a Space Pod; everything else is checked in {@link Planet#travel}. */
public record TravelPacket(Planet planet) {
    public static void encode(TravelPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.planet);
    }

    public static TravelPacket decode(FriendlyByteBuf buf) {
        return new TravelPacket(buf.readEnum(Planet.class));
    }

    public static void handle(TravelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        boolean holding = player.getMainHandItem().is(ModItems.SPACE_POD.get()) || player.getOffhandItem().is(ModItems.SPACE_POD.get());
        if (holding) Planet.travel(player, msg.planet);
    }
}
