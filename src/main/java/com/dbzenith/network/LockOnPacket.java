package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Lock-on (CX-19 phase 6), both ways: client to server, the target I locked onto (-1: none); server to client, lock
 * onto this (dev and tests, and a lock the server let go of).
 */
public record LockOnPacket(int entityId) {
    public static void encode(LockOnPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId + 1);
    }

    public static LockOnPacket decode(FriendlyByteBuf buf) {
        return new LockOnPacket(buf.readVarInt() - 1);
    }

    public static void handle(LockOnPacket m, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection().getReceptionSide().isServer()) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && !com.dbzenith.combat.engine.Targeting.set(p, m.entityId)) ModNetwork.sendTo(p, new LockOnPacket(-1));
        } else {
            com.dbzenith.client.LockOn.forceTarget(m.entityId);   // client-only class, loaded only here
        }
    }
}
