package com.dbzenith.network;

import com.dbzenith.skill.Technique;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to every client tracking a player (and the player): play a one-shot body animation for an action the
 * client cannot see coming on its own (a technique fired, a dash). {@code data} is a duration in ticks where it matters.
 */
public record AnimEventPacket(int entityId, int kind, int data) {
    public static final int BLAST = 0;
    public static final int VOLLEY = 1;
    public static final int BEAM = 2;
    public static final int THROW = 3;
    public static final int WAVE = 4;
    public static final int FOCUS = 5;
    public static final int DASH = 6;

    /** The animation that fits how a technique is cast. */
    public static AnimEventPacket forTechnique(Entity caster, Technique t) {
        return forTechnique(caster.getId(), t);
    }

    public static AnimEventPacket forTechnique(int entityId, Technique t) {
        int kind;
        int data = 0;
        if (t.style() == Technique.Style.SELF) {
            kind = t.effect() == Technique.Effect.EXPLOSIVE_WAVE || t.effect() == Technique.Effect.STUN_AREA ? WAVE : FOCUS;
        } else if (t.style() == Technique.Style.BEAM) {
            kind = BEAM;
            data = t.lifeTicks();
        } else if (t.holdTicks() > 0) {
            kind = THROW;
            data = t.holdTicks();
        } else {
            kind = t.count() > 1 ? VOLLEY : BLAST;
        }
        return new AnimEventPacket(entityId, kind, data);
    }

    public static void encode(AnimEventPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.kind);
        buf.writeVarInt(msg.data);
    }

    public static AnimEventPacket decode(FriendlyByteBuf buf) {
        return new AnimEventPacket(buf.readVarInt(), buf.readByte(), Math.min(buf.readVarInt(), 400));
    }

    public static void handle(AnimEventPacket msg, Supplier<NetworkEvent.Context> ctx) {
        com.dbzenith.client.anim.AnimController.onEvent(msg);
    }
}
