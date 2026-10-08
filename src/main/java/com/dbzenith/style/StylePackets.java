package com.dbzenith.style;

import com.dbzenith.data.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The packets of fighting styles (CX-20). */
public final class StylePackets {
    private StylePackets() {}

    /** Server to client: every style and master, as JSON (on joining and after a reload). */
    public record Data(String styles, String masters) {
        public static void encode(Data m, FriendlyByteBuf buf) {
            buf.writeUtf(m.styles, 1 << 20);
            buf.writeUtf(m.masters, 1 << 18);
        }

        public static Data decode(FriendlyByteBuf buf) {
            return new Data(buf.readUtf(1 << 20), buf.readUtf(1 << 18));
        }

        public static void handle(Data m, Supplier<NetworkEvent.Context> ctx) {
            Styles.apply(m.styles, m.masters);
        }
    }

    /** Server to every client drawing a player: the style chosen for each slot ("slot=style,..."). */
    public record Slots(int entityId, String code) {
        public static void encode(Slots m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeUtf(m.code, 2048);
        }

        public static Slots decode(FriendlyByteBuf buf) {
            return new Slots(buf.readVarInt(), buf.readUtf(2048));
        }

        public static void handle(Slots m, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientStyles.setSlots(m.entityId, m.code);       // client-only class
        }
    }

    /** Server to client: open a master's screen (after the talk was counted). */
    public record OpenMaster(int entityId, String master) {
        public static void encode(OpenMaster m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeUtf(m.master, 64);
        }

        public static OpenMaster decode(FriendlyByteBuf buf) {
            return new OpenMaster(buf.readVarInt(), buf.readUtf(64));
        }

        public static void handle(OpenMaster m, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientStyles.openMaster(m.entityId, m.master);
        }
    }

    /** Client to server: equip a style in a slot ("" for the default), learn a style from a master, train with one. */
    public record Action(Kind kind, String a, String b, int entityId) {
        public enum Kind { EQUIP, LEARN, TRAIN }

        public static void encode(Action m, FriendlyByteBuf buf) {
            buf.writeEnum(m.kind);
            buf.writeUtf(m.a, 64);
            buf.writeUtf(m.b, 64);
            buf.writeVarInt(m.entityId);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readEnum(Kind.class), buf.readUtf(64), buf.readUtf(64), buf.readVarInt());
        }

        public static void handle(Action m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            ModCapabilities.get(p).ifPresent(d -> {
                switch (m.kind) {
                    case EQUIP -> StyleLogic.equip(p, d, m.a, m.b);
                    case LEARN -> StyleLogic.learn(p, d, m.entityId, m.a);
                    case TRAIN -> StyleLogic.train(p, d, m.entityId);
                }
            });
        }
    }
}
