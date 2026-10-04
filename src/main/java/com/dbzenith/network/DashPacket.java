package com.dbzenith.network;

import com.dbzenith.ki.DashHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: dash with the current movement input. Validated by {@link DashHandler}. */
public record DashPacket(float forward, float strafe) {
    public static void encode(DashPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.strafe);
    }

    public static DashPacket decode(FriendlyByteBuf buf) {
        return new DashPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(DashPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && Float.isFinite(msg.forward) && Float.isFinite(msg.strafe)) DashHandler.dash(player, msg.forward, msg.strafe);
    }
}
