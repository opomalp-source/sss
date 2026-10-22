package com.dbzenith.nemesis;

import com.dbzenith.npc.ai.AiRandom;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The world going on without the player, abstractly (no entities, no chunks): once an in-game hour (1,000 ticks) up to
 * {@link #BATCH} characters are brought up to date, in turn:
 * <ul>
 *   <li>they train for the time that passed ({@link Progression#trainingXp}, within the growth caps);</li>
 *   <li>old feelings fade a little unless they are vengeful;</li>
 *   <li>now and then they meet another character: enemies (by faction or by feeling) fight a duel decided by level
 *       and a deterministic roll; allies train together; an ally who has come to hate the other turns on them;</li>
 *   <li>every third in-game day the eight best-known hold a tournament.</li>
 * </ul>
 * It all lands in their history (and so in their dialogue: "I've been fighting {rival} lately").
 */
public final class WorldSim {
    static final int BATCH = 16;

    private WorldSim() {}

    static void tick(MinecraftServer s) {
        if (!NemesisConfig.worldSim()) return;
        step(NemesisData.get(s), s.overworld().getGameTime());
    }

    /** One step (public for tests and /dbz nemesis simulate). */
    public static void step(NemesisData d, long now) {
        List<NamedFighter> active = new ArrayList<>();
        for (NamedFighter c : d.all()) if (c.active() && !c.resident()) active.add(c);           // townsfolk live their own lives (living.LivingSim)
        if (active.isEmpty()) return;
        active.sort(Comparator.comparing(c -> c.id));
        for (int i = 0; i < Math.min(BATCH, active.size()); i++) {
            NamedFighter c = active.get(Math.floorMod(d.simCursor++, active.size()));
            update(d, c, active, now);
        }
        long day = Math.floorDiv(now, 24000L);
        if (day % 3 == 0 && day != d.lastTournamentDay && active.size() >= 4) {
            d.lastTournamentDay = day;
            tournament(active, now);
        }
        d.setDirty();
    }

    static void update(NemesisData d, NamedFighter c, List<NamedFighter> all, long now) {
        long since = c.lastSimulated == 0 ? 1000 : Math.min(24000, now - c.lastSimulated);
        c.lastSimulated = now;
        if (since <= 0) return;
        double hours = since / 1000.0;
        if (c.body == null) {                                                         // away: training
            int levels = Progression.gain(c, Progression.trainingXp(c) * hours, now);
            Memory last = c.last(Memory.Kind.TRAINED, null);
            if (levels > 0 && (last == null || now - last.tick() > 24000)) c.remember(new Memory(Memory.Kind.TRAINED, now, null, String.valueOf(c.level)));
        }
        for (Bond b : c.players.values()) {                                           // feelings fade with time apart
            if (now - b.lastEncounter < 3 * 24000) continue;
            b.rivalry *= c.has(Trait.VENGEFUL) ? 0.995 : 0.985;
            if (!c.has(Trait.VENGEFUL) || b.affinity > 0) b.affinity *= 0.99;
        }
        if (all.size() < 2 || !AiRandom.chance(c.id.getMostSignificantBits(), now / 1000, 91, 0.2)) return;
        NamedFighter o = all.get(AiRandom.pick(c.id.getLeastSignificantBits(), now / 1000, 92, all.size()));
        if (o == c) return;
        meet(c, o, now);
    }

    /** Two characters cross paths. */
    static void meet(NamedFighter a, NamedFighter b, long now) {
        Bond ab = a.character(b), ba = b.character(a);
        boolean enemies = factionsClash(a, b) || ab.affinity < -20 || ba.affinity < -20;
        if (ab.state.friendly() && ab.affinity < -30) {                               // a friendship gone sour: betrayal
            a.remember(new Memory(Memory.Kind.BETRAYED, now, b.id, b.name));
            b.remember(new Memory(Memory.Kind.BETRAYED, now, a.id, a.name));
            ab.shift(-20, 10);
            ba.shift(-25, 10);
            enemies = true;
        }
        if (enemies || a.has(Trait.COMPETITIVE) || b.has(Trait.COMPETITIVE)) {
            duel(a, b, now);
            return;
        }
        ab.encounters++;                                                              // allies train together
        ba.encounters++;
        ab.spars++;
        ba.spars++;
        ab.shift(6, 2);
        ba.shift(6, 2);
        Progression.gain(a, 30, now);
        Progression.gain(b, 30, now);
        a.remember(new Memory(Memory.Kind.SPARRED, now, b.id, b.name));
        b.remember(new Memory(Memory.Kind.SPARRED, now, a.id, a.name));
    }

    static boolean factionsClash(NamedFighter a, NamedFighter b) {
        var pa = Progression.profile(a).faction();
        var pb = Progression.profile(b).faction();
        return pa.enemyOf(pb);
    }

    /** A duel decided by level and a fair roll. */
    static void duel(NamedFighter a, NamedFighter b, long now) {
        double edge = (a.level - b.level) / Math.max(3.0, 0.2 * (a.level + b.level) / 2);
        double pA = 1 / (1 + Math.exp(-edge));
        boolean aWins = AiRandom.unit(a.id.getMostSignificantBits() ^ b.id.getLeastSignificantBits(), now / 1000, 93) < pA;
        NamedFighter w = aWins ? a : b, l = aWins ? b : a;
        Bond wl = w.character(l), lw = l.character(w);
        wl.encounters++;
        lw.encounters++;
        wl.wins++;
        lw.losses++;
        wl.lastResult = 1;
        lw.lastResult = -1;
        wl.shift(w.temper().honor > 0.6 ? 3 : -2, 8);
        lw.shift(l.has(Trait.VENGEFUL) ? -12 : -3, 10);
        w.reputation += 8;
        l.reputation = Math.max(0, l.reputation - 4);
        Progression.gain(w, 35, now);
        Progression.gain(l, l.race.equals("saiyan") ? 50 : 30, now);
        w.remember(new Memory(Memory.Kind.NPC_WON, now, l.id, l.name));
        l.remember(new Memory(Memory.Kind.NPC_LOST, now, w.id, w.name));
        w.epithet = Names.epithet(w);
        l.epithet = Names.epithet(l);
    }

    /** The eight best-known fight it out; the winner's name spreads. */
    static void tournament(List<NamedFighter> active, long now) {
        List<NamedFighter> field = new ArrayList<>(active);
        field.sort(Comparator.comparingInt((NamedFighter c) -> -c.reputation).thenComparing(c -> c.id));
        field = new ArrayList<>(field.subList(0, Math.min(8, field.size())));
        int round = 0;
        while (field.size() > 1) {
            List<NamedFighter> next = new ArrayList<>();
            for (int i = 0; i + 1 < field.size(); i += 2) {
                NamedFighter a = field.get(i), b = field.get(i + 1);
                double edge = (a.level - b.level) / Math.max(3.0, 0.2 * (a.level + b.level) / 2);
                boolean aWins = AiRandom.unit(a.id.getMostSignificantBits(), now / 24000 + round, 95) < 1 / (1 + Math.exp(-edge));
                NamedFighter w = aWins ? a : b, l = aWins ? b : a;
                w.character(l).shift(0, 4);
                l.character(w).shift(0, 6);
                l.remember(new Memory(Memory.Kind.TOURNAMENT, now, w.id, "lost to " + w.name));
                next.add(w);
            }
            if (field.size() % 2 == 1) next.add(field.get(field.size() - 1));
            field = next;
            round++;
        }
        NamedFighter champ = field.get(0);
        champ.reputation += 40;
        champ.remember(new Memory(Memory.Kind.TOURNAMENT, now, null, "won"));
        champ.epithet = Names.epithet(champ);
    }

    /** Tests: what two characters think of each other. */
    public static Map.Entry<Bond, Bond> bonds(NamedFighter a, NamedFighter b) {
        return Map.entry(a.character(b), b.character(a));
    }

    static UUID id(NamedFighter c) {
        return c.id;
    }
}
