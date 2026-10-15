package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server (the knocked-out rework): the grip key on a downed fighter ({@link #GRIP}, with the direction held,
 * which picks the finisher), or the attack key while carrying someone ({@link #THROW}).
 */
public record DownedActionPacket(int action, int targetId, int input) {
    public static final int GRIP = 0, THROW = 1;

    public static void encode(DownedActionPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.action);
        buf.writeVarInt(m.targetId);
        buf.writeByte(m.input);
    }

    public static DownedActionPacket decode(FriendlyByteBuf buf) {
        return new DownedActionPacket(buf.readByte(), buf.readVarInt(), buf.readByte());
    }

    public static void handle(DownedActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        if (m.action == THROW) {
            com.dbzenith.combat.Carry.attacked(p);
            return;
        }
        Entity t = p.level().getEntity(m.targetId);
        if (m.action == GRIP && t instanceof LivingEntity v && com.dbzenith.combat.KnockOut.isDown(v)) {
            if (!com.dbzenith.combat.Grip.start(p, v, com.dbzenith.combat.Grip.Finisher.forInput(m.input)) && p.distanceTo(v) > com.dbzenith.combat.Grip.REACH)
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.grip_too_far"), true);
        }
    }
}
