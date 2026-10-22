package com.dbzenith.nemesis;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Nemesis History screen's data: the client asks when the screen opens ({@link Request}), the server answers
 * with the characters that know this player ({@link Data}), only what the player has discovered: the forms they saw
 * it use, the power it showed last, where and how it was last seen, their shared record and the memories that involve
 * them. Nothing else (its real level, its techniques, its plans) leaves the server. Sent on request only.
 */
public final class NemesisPackets {
    static final int MAX = 40, MAX_HISTORY = 10;

    private NemesisPackets() {}

    /** One character as the player knows it. */
    public record Entry(UUID id, String name, String type, String race, int rank, int relation, int rivalry, int affinity,
                        int wins, int losses, int sparedThem, int sparedByThem, int encounters, List<String> forms, long power,
                        String status, String place, double daysAgo, List<String> history) {}

    public record Request() {
        public static void encode(Request m, FriendlyByteBuf buf) {}

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        public static void handle(Request m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || !NemesisConfig.enabled()) return;
            if (!com.dbzenith.network.InputGuard.allow(p, com.dbzenith.network.InputGuard.Kind.GALAXY)) return;   // the same budget as other screen requests
            com.dbzenith.network.ModNetwork.sendTo(p, new Data(entries(p)));
        }
    }

    /** What the player knows of the characters that know them, most important first. */
    public static List<Entry> entries(ServerPlayer p) {
        NemesisData d = NemesisData.get(p.server);
        long now = p.level().getGameTime();
        List<NamedFighter> list = d.knowing(p.getUUID());
        list.sort(Comparator.comparingDouble((NamedFighter c) -> -(c.players.get(p.getUUID()).rivalry + Math.abs(c.players.get(p.getUUID()).affinity)
                + c.players.get(p.getUUID()).encounters * 5)));
        List<Entry> out = new ArrayList<>();
        for (NamedFighter c : list) {
            if (out.size() >= MAX) break;
            Bond b = c.players.get(p.getUUID());
            if (b.encounters <= 0 && b.helped <= 0 && b.alliesLost <= 0) continue;
            List<String> hist = new ArrayList<>();
            var it = c.history.descendingIterator();
            while (it.hasNext() && hist.size() < MAX_HISTORY) {
                Memory m = it.next();
                boolean aboutThem = p.getUUID().equals(m.who());
                boolean known = m.kind() == Memory.Kind.UNLOCKED_FORM && b.formsShown.contains(m.detail()) || m.kind() == Memory.Kind.TOURNAMENT
                        || m.kind() == Memory.Kind.RANK_UP;
                if (!aboutThem && !known) continue;
                hist.add(m.kind().id() + "|" + m.detail() + "|" + String.format(java.util.Locale.ROOT, "%.1f", (now - m.tick()) / 24000.0));
            }
            String status = b.statusShown.isEmpty() ? "unknown" : b.statusShown;
            if (status.equals("fighting") && now - b.lastEncounter > 1200) status = "fought";   // a fight that ended without a word
            out.add(new Entry(c.id, c.fullName(), c.type, c.race, c.rank().ordinal(), b.state.ordinal(), (int) Math.round(b.rivalry),
                    (int) Math.round(b.affinity), b.wins, b.losses, b.sparedThem, b.sparedByThem, b.encounters, new ArrayList<>(b.formsShown),
                    b.powerShown, status, b.placeShown, b.lastEncounter < 0 ? -1 : (now - b.lastEncounter) / 24000.0, hist));
        }
        return out;
    }

    public record Data(List<Entry> entries) {
        public static void encode(Data m, FriendlyByteBuf buf) {
            buf.writeVarInt(Math.min(MAX, m.entries.size()));
            for (int i = 0; i < Math.min(MAX, m.entries.size()); i++) {
                Entry e = m.entries.get(i);
                buf.writeUUID(e.id());
                buf.writeUtf(e.name(), 64);
                buf.writeUtf(e.type(), 96);
                buf.writeUtf(e.race(), 32);
                buf.writeVarInt(e.rank());
                buf.writeVarInt(e.relation());
                buf.writeVarInt(e.rivalry());
                buf.writeInt(e.affinity());
                buf.writeVarInt(e.wins());
                buf.writeVarInt(e.losses());
                buf.writeVarInt(e.sparedThem());
                buf.writeVarInt(e.sparedByThem());
                buf.writeVarInt(e.encounters());
                buf.writeVarInt(Math.min(12, e.forms().size()));
                for (int k = 0; k < Math.min(12, e.forms().size()); k++) buf.writeUtf(e.forms().get(k), 64);
                buf.writeVarLong(Math.max(0, e.power()));
                buf.writeUtf(e.status(), 32);
                buf.writeUtf(e.place(), 96);
                buf.writeDouble(e.daysAgo());
                buf.writeVarInt(Math.min(MAX_HISTORY, e.history().size()));
                for (int k = 0; k < Math.min(MAX_HISTORY, e.history().size()); k++) buf.writeUtf(e.history().get(k), 160);
            }
        }

        public static Data decode(FriendlyByteBuf buf) {
            int n = Math.min(MAX, buf.readVarInt());
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                UUID id = buf.readUUID();
                String name = buf.readUtf(64), type = buf.readUtf(96), race = buf.readUtf(32);
                int rank = buf.readVarInt(), rel = buf.readVarInt(), riv = buf.readVarInt(), aff = buf.readInt();
                int wins = buf.readVarInt(), losses = buf.readVarInt(), st = buf.readVarInt(), sb = buf.readVarInt(), enc = buf.readVarInt();
                int nf = Math.min(12, buf.readVarInt());
                List<String> forms = new ArrayList<>();
                for (int k = 0; k < nf; k++) forms.add(buf.readUtf(64));
                long power = buf.readVarLong();
                String status = buf.readUtf(32), place = buf.readUtf(96);
                double days = buf.readDouble();
                int nh = Math.min(MAX_HISTORY, buf.readVarInt());
                List<String> hist = new ArrayList<>();
                for (int k = 0; k < nh; k++) hist.add(buf.readUtf(160));
                list.add(new Entry(id, name, type, race, rank, rel, riv, aff, wins, losses, st, sb, enc, forms, power, status, place, days, hist));
            }
            return new Data(list);
        }

        public static void handle(Data m, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.screen.RivalsScreen.receive(m.entries());     // client-only class, loaded only here
        }
    }
}
