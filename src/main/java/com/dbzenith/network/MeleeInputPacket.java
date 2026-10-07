package com.dbzenith.network;

import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: a melee press (CX-19): the button and the way the player pushes. Looking up or (in the air) down is
 * read on the server from the player's own rotation; everything else is decided there.
 */
public record MeleeInputPacket(boolean heavy, byte push) {
    public static void encode(MeleeInputPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.heavy);
        buf.writeByte(m.push);
    }

    public static MeleeInputPacket decode(FriendlyByteBuf buf) {
        return new MeleeInputPacket(buf.readBoolean(), buf.readByte());
    }

    public static void handle(MeleeInputPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (!InputGuard.allow(p, InputGuard.Kind.MELEE) || !InputGuard.sane(p, m.push >= 0 && m.push <= 3, "melee push " + m.push)) return;
        Move.Dir push = switch (m.push) {
            case 1 -> Move.Dir.FORWARD;
            case 2 -> Move.Dir.BACK;
            case 3 -> Move.Dir.SIDE;
            default -> Move.Dir.NEUTRAL;
        };
        float pitch = p.getXRot();
        CombatEngine.press(p, new Moves.Input(m.heavy ? Move.Button.HEAVY : Move.Button.LIGHT, push, pitch < -35, pitch > 40, p.onGround()));
    }

    /** The push from movement input: 0 neutral, 1 forward, 2 back, 3 sideways. */
    public static byte push(float forward, float strafe) {
        if (Math.abs(forward) < 0.3f && Math.abs(strafe) < 0.3f) return 0;
        if (Math.abs(forward) >= Math.abs(strafe)) return (byte) (forward > 0 ? 1 : 2);
        return 3;
    }
}
