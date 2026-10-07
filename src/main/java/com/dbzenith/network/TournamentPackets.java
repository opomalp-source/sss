package com.dbzenith.network;

import com.dbzenith.tournament.Tournament;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** World Martial Arts Tournament packets (CX-17c): the bracket for the Announcer's screen, and entering. */
public final class TournamentPackets {
    private TournamentPackets() {}

    /** One name on the board: a player's name, or a roster id. */
    public record Name(boolean player, String text) {}

    /**
     * Server to client: the tournament as it stands. {@code phase} is -1 when none is on; {@code drawn} says whether
     * {@code names} are the eight slots of the bracket or just those signed up so far.
     */
    public record State(boolean open, int phase, int seconds, int match, boolean drawn, List<Name> names, int[] winners, String problem) {
        public static State of(ServerPlayer p, boolean open) {
            Tournament t = Tournament.current();
            String why = Tournament.problem(p);
            if (t == null) return new State(open, -1, 0, 0, false, List.of(), new int[7], why == null ? "" : why);
            boolean drawn = !t.slots().isEmpty();
            List<Name> names = new ArrayList<>();
            for (Tournament.Entrant e : drawn ? t.slots() : t.signedUp()) {
                names.add(e.isPlayer() ? new Name(true, e.playerName()) : new Name(false, e.npc().id()));
            }
            int[] w = new int[7];
            for (int i = 0; i < 7; i++) w[i] = t.winner(i);
            return new State(open, t.phase().ordinal(), Math.max(0, t.timer() / 20), t.match(), drawn, names, w, why == null ? "" : why);
        }

        public static void encode(State m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.open);
            buf.writeVarInt(m.phase + 1);
            buf.writeVarInt(m.seconds);
            buf.writeVarInt(m.match);
            buf.writeBoolean(m.drawn);
            buf.writeVarInt(m.names.size());
            for (Name n : m.names) {
                buf.writeBoolean(n.player);
                buf.writeUtf(n.text, 64);
            }
            for (int w : m.winners) buf.writeVarInt(w + 1);
            buf.writeUtf(m.problem, 96);
        }

        public static State decode(FriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int phase = buf.readVarInt() - 1, seconds = buf.readVarInt(), match = buf.readVarInt();
            boolean drawn = buf.readBoolean();
            int n = Math.min(8, buf.readVarInt());
            List<Name> names = new ArrayList<>();
            for (int i = 0; i < n; i++) names.add(new Name(buf.readBoolean(), buf.readUtf(64)));
            int[] w = new int[7];
            for (int i = 0; i < 7; i++) w[i] = buf.readVarInt() - 1;
            return new State(open, phase, seconds, match, drawn, names, w, buf.readUtf(96));
        }

        public static void handle(State msg, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientHooks.tournament(msg);                 // client-only class, loaded only here
        }
    }

    /** Client to server: sign me up. */
    public record Join() {
        public static void encode(Join m, FriendlyByteBuf buf) {}

        public static Join decode(FriendlyByteBuf buf) {
            return new Join();
        }

        public static void handle(Join msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && Tournament.join(p)) Tournament.open(p);
        }
    }
}
