package com.dbzenith.network;

import com.dbzenith.ki.DashHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: dash with the current movement input. Validated by {@link DashHandler}. {@code predicted}: the client
 * already made the plain dash itself (CX-19 phase 7), so the server doesn't push the same velocity a round trip later.
 */
public record DashPacket(float forward, float strafe, boolean predicted) {
    public static void encode(DashPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.strafe);
        buf.writeBoolean(msg.predicted);
    }

    public static DashPacket decode(FriendlyByteBuf buf) {
        return new DashPacket(buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(DashPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (!InputGuard.allow(player, InputGuard.Kind.DASH)) return;
        boolean sane = Float.isFinite(msg.forward) && Float.isFinite(msg.strafe) && Math.abs(msg.forward) <= 1.01f && Math.abs(msg.strafe) <= 1.01f;
        if (InputGuard.sane(player, sane, "dash " + msg.forward + "," + msg.strafe)) DashHandler.dash(player, msg.forward, msg.strafe, msg.predicted);
    }
}
