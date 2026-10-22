package com.dbzenith.living;

import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.AiRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Travelers (docs/LIVING_WORLD.md): fighters of the road who go from town to town. On the road they are abstract (no
 * entity, Nemesis status AWAY, {@code travelTo} the town they head for or null for "elsewhere", {@code travelUntil}
 * when they get there; travel takes a quarter day per 500 blocks, at least); in a town they are residents for a day or
 * two (status PRESENT, lodging at the restaurant, {@code travelUntil} the day they move on), then walk out through a
 * gate (if anyone is about to see it) and are on the road again. They carry news: whatever they heard elsewhere they
 * tell the town on arrival, and they take its news with them. A world keeps {@code living.travelers} of them; they can
 * become companions (and stop travelling while they are).
 */
public final class Travelers {
    static final long DAY = 24000;

    private Travelers() {}

    public static boolean onRoad(NamedFighter c) {
        return Occupation.of(c) == Occupation.TRAVELER && c.status == NamedFighter.Status.AWAY;
    }

    public static List<NamedFighter> all() {
        List<NamedFighter> out = new ArrayList<>();
        NemesisData d = Nemesis.data();
        if (d != null) for (NamedFighter c : d.all()) if (c.resident() && Occupation.of(c) == Occupation.TRAVELER && c.active()) out.add(c);
        return out;
    }

    /** Every five seconds: arrivals, departures, and new travelers while the world has fewer than it should. */
    static void tick(MinecraftServer server, LivingData ld, long now) {
        if (ld.settlements().isEmpty()) return;
        List<NamedFighter> travelers = all();
        if (travelers.size() < LivingConfig.travelers() && !(server instanceof net.minecraft.gametest.framework.GameTestServer))   // none wander into test grounds
            travelers.add(create(ld, now, travelers.size()));
        for (NamedFighter c : travelers) {
            if (c.companionOf != null) continue;                                          // travelling with a player instead
            if (onRoad(c)) {
                if (now >= c.travelUntil) arrive(server, ld, c, now);
            } else if (c.status == NamedFighter.Status.PRESENT && c.travelUntil > 0 && now >= c.travelUntil) {
                leave(server, ld, c, now);
            }
        }
    }

    /** A new traveler, already on the road to one of the towns. */
    static NamedFighter create(LivingData ld, long now, int n) {
        List<Settlement> towns = new ArrayList<>(ld.settlements());
        Settlement to = towns.get(AiRandom.pick(now, n, 71, towns.size()));
        NamedFighter c = Residents.create(Nemesis.data(), null, Occupation.TRAVELER, to.center, now, now ^ (n * 0x51EDL) ^ 0x7A11L);
        c.settlement = null;
        c.status = NamedFighter.Status.AWAY;
        c.travelTo = to.id;
        c.travelUntil = now + DAY / 4 + AiRandom.pick(c.id.getMostSignificantBits(), now, 72, (int) (DAY / 2));
        c.remember(new Memory(Memory.Kind.MET, now, null, "on the road"));
        Nemesis.data().setDirty();
        return c;
    }

    /** It reaches its town: a resident for a day or two, with news from the road (and the town's news for it). */
    static void arrive(MinecraftServer server, LivingData ld, NamedFighter c, long now) {
        Settlement s = c.travelTo == null ? null : ld.settlement(c.travelTo);
        if (s == null) {                                                                  // back from elsewhere: a town to head for
            List<Settlement> towns = new ArrayList<>(ld.settlements());
            s = towns.get(AiRandom.pick(c.id.getLeastSignificantBits(), now, 73, towns.size()));
        }
        hearNews(ld, c, now);
        c.settlement = s.id;
        c.dimension = s.dimension;
        List<Poi> lodging = s.of(Poi.Kind.TAVERN);
        if (lodging.isEmpty()) lodging = s.of(Poi.Kind.PLAZA);
        c.home = (lodging.isEmpty() ? s.center : lodging.get(AiRandom.pick(c.id.getMostSignificantBits(), 0, 74, lodging.size())).pos()).asLong();
        c.status = NamedFighter.Status.PRESENT;
        c.travelTo = null;
        c.travelUntil = now + DAY + AiRandom.pick(c.id.getLeastSignificantBits(), now, 75, (int) DAY);
        c.returnAt = 0;
        c.body = null;
        List<NamedFighter> people = Residents.of(s);
        for (NamedFighter r : people) {                                                   // what it brings, what it takes
            if (r == c) continue;
            for (int i = 0; i < 3; i++) Rumors.exchange(ld, c, r);                     // a traveler has a lot to tell
        }
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(s.dimension)));
        List<Poi> gates = s.of(Poi.Kind.GATE);
        if (level != null && !gates.isEmpty()) {                                           // seen arriving at a gate if the town is about
            BlockPos gate = gates.get(AiRandom.pick(c.id.getMostSignificantBits(), now, 76, gates.size())).pos();
            if (level.isPositionEntityTicking(gate)) Residents.spawnBody(level, c, gate);
        }
        Nemesis.data().setDirty();
    }

    /** On the road a traveler hears the world's news (the newest few pieces it doesn't know). */
    static void hearNews(LivingData ld, NamedFighter c, long now) {
        List<Rumor> fresh = new ArrayList<>(ld.rumors());
        fresh.sort((a, b) -> Long.compare(b.tick(), a.tick()));
        List<Rumor> pick = new ArrayList<>();
        for (Rumor r : fresh) {
            if (pick.size() >= 4 || now - r.tick() > 7 * DAY) break;
            if (r.tick() <= now && !c.knowledge.contains(r.id())) pick.add(r);                // nothing from the future
        }
        for (int i = pick.size() - 1; i >= 0; i--) Rumors.learn(c, pick.get(i));       // oldest first: the newest is told first
    }

    /** Time to move on: it walks out through a gate if someone is about, or is simply gone. */
    static void leave(MinecraftServer server, LivingData ld, NamedFighter c, long now) {
        if (c.body != null) {
            for (ServerLevel l : server.getAllLevels()) {
                if (!(l.getEntity(c.body) instanceof Resident r)) continue;
                if (l.getNearestPlayer(r, 48) != null && !r.leaving && r.getTarget() == null) {
                    r.leaving = true;                                                      // its routine takes it to a gate
                    return;
                }
                if (r.leaving && l.getNearestPlayer(r, 48) != null && now < c.travelUntil + 2400) return;   // still walking out
                vanish(r);
            }
        }
        depart(ld, c, now);
    }

    /** Off it goes: on the road to another town (or elsewhere, when there is only one). */
    static void depart(LivingData ld, NamedFighter c, long now) {
        Settlement from = ld.settlement(c.settlement);
        List<Settlement> others = new ArrayList<>();
        for (Settlement s : ld.settlements()) if (from == null || !s.id.equals(from.id)) others.add(s);
        Settlement to = others.isEmpty() ? null : others.get(AiRandom.pick(c.id.getMostSignificantBits(), now, 77, others.size()));
        double dist = from == null || to == null ? 2000 : Math.sqrt(from.center.distSqr(to.center));
        c.status = NamedFighter.Status.AWAY;
        c.body = null;
        c.settlement = null;
        c.travelTo = to == null ? null : to.id;
        c.travelUntil = now + Math.max(DAY / 4, (long) (dist / 500 * DAY / 4)) + (to == null ? DAY / 2 : 0);
        Nemesis.data().setDirty();
    }

    /** A traveler's body leaves the world (at a gate, out of sight of the town). */
    static void vanish(Resident r) {
        if (r.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CLOUD, r.getX(), r.getY() + 1, r.getZ(), 10, 0.4, 0.8, 0.4, 0.03);
        r.discard();
    }

    /** The routine got it to the gate: away it goes. */
    static void atGate(Resident r) {
        NamedFighter c = r.character();
        if (c == null) return;
        vanish(r);
        depart(LivingData.get(r.getServer()), c, r.level().getGameTime());
    }

    /** Tests: sends a traveler on now. */
    public static void leaveNow(MinecraftServer server, NamedFighter c, long now) {
        depart(LivingData.get(server), c, now);
    }

    /** Tests: brings a traveler into town now. */
    public static void arriveNow(MinecraftServer server, NamedFighter c, long now) {
        arrive(server, LivingData.get(server), c, now);
    }

    static boolean isTraveler(KiFighter k) {
        return k instanceof Resident r && r.occupation() == Occupation.TRAVELER;
    }
}
