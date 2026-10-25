package com.dbzenith.living;

import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Names;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.nemesis.Progression;
import com.dbzenith.nemesis.Trait;
import com.dbzenith.npc.ai.AiProfile;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.Faction;
import com.dbzenith.npc.ai.Personality;
import com.dbzenith.race.Variant;
import com.dbzenith.skill.Technique;
import com.dbzenith.stats.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who lives in a town: making residents (a named character with an occupation, a home and a temperament fit for its
 * trade, from its seed), giving them bodies, and keeping a town peopled. A resident's body that is knocked out or
 * killed goes the Nemesis way (it recovers, or it is gone); recovered residents come home here, and a body lost
 * with its chunk is replaced. The dead are mourned; newcomers move in after a while to keep the town within its limit.
 */
public final class Residents {
    /** Who moves into a new town, in order (cut to the town's population limit and its beds). */
    static final List<Occupation> PLAN = List.of(Occupation.MASTER, Occupation.STUDENT, Occupation.GUARD, Occupation.MERCHANT, Occupation.COOK,
            Occupation.CITIZEN, Occupation.STUDENT, Occupation.CITIZEN, Occupation.CITIZEN, Occupation.GUARD, Occupation.CITIZEN, Occupation.CITIZEN,
            Occupation.STUDENT, Occupation.CITIZEN);
    /** When each place first ticked for entities without a break (bodies load a little after their chunk). */
    private static final Map<UUID, Long> TICKING_SINCE = new HashMap<>();

    private Residents() {}

    /** A resident's AI profile: the townsfolk's side, its own temperament, human (fighters may climb the human forms). */
    public static AiProfile profile(NamedFighter c) {
        Occupation o = Occupation.of(c);
        Personality p = c == null ? Personality.BALANCED : c.personality;
        Race race = Race.HUMAN;
        Variant variant = Variant.HUMAN;
        if (c != null) {
            try {
                if (!c.race.isEmpty()) race = Race.valueOf(c.race.toUpperCase(java.util.Locale.ROOT));
                if (!c.variant.isEmpty()) variant = Variant.valueOf(c.variant.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new AiProfile(Faction.TOWNSFOLK, List.of(p), o.fighter ? race : null, o.fighter ? variant : null, o.fighter,
                AiProfile.Tier.NORMAL, o == Occupation.MASTER, AiProfile.PLAIN_AURA);
    }

    /** The temperaments a trade draws. */
    static List<Personality> temperaments(Occupation o) {
        return switch (o) {
            case MASTER -> List.of(Personality.PATIENT, Personality.HONORABLE, Personality.TACTICAL);
            case GUARD -> List.of(Personality.DEFENSIVE, Personality.HONORABLE, Personality.BALANCED);
            case STUDENT -> List.of(Personality.AGGRESSIVE, Personality.BALANCED, Personality.RECKLESS, Personality.HONORABLE, Personality.ARROGANT);
            case TRAVELER -> List.of(Personality.TACTICAL, Personality.BALANCED, Personality.RECKLESS);
            default -> List.of(Personality.BALANCED, Personality.COWARDLY, Personality.PATIENT, Personality.DEFENSIVE, Personality.HONORABLE);
        };
    }

    /** A fighter level for a trade (masters strong, students green; the rest never fight). */
    static int levelFor(Occupation o, long seed) {
        int spread = AiRandom.pick(seed, 5, 21, 5);
        return switch (o) {
            case MASTER -> 28 + spread * 2;
            case GUARD -> 12 + spread;
            case TRAVELER -> 15 + spread * 2;
            case STUDENT -> 4 + spread;
            default -> 1 + spread / 2;
        };
    }

    /** What it wants from life, from its trade and temperament. */
    static NamedFighter.Goal goalFor(NamedFighter c, Occupation o) {
        return switch (o) {
            case MASTER -> NamedFighter.Goal.FIND_STUDENT;
            case GUARD -> NamedFighter.Goal.PROTECT_TOWN;
            case STUDENT -> c.has(Trait.AMBITIOUS) || c.has(Trait.COMPETITIVE) ? NamedFighter.Goal.STRONGEST : NamedFighter.Goal.IMPRESS_MASTER;
            case MERCHANT, COOK -> NamedFighter.Goal.BUSINESS;
            case TRAVELER -> NamedFighter.Goal.EXPLORE;
            case CITIZEN -> c.has(Trait.CURIOUS) ? NamedFighter.Goal.EXPLORE : NamedFighter.Goal.REST;
            case SCIENTIST -> NamedFighter.Goal.RESEARCH;
        };
    }

    /** Makes a resident of {@code s} (not yet in the world): a named character with its trade, home and nature. */
    public static NamedFighter create(NemesisData d, Settlement s, Occupation o, BlockPos home, long now, long seed) {
        UUID town = s == null ? new UUID(0x7A11D0E5L, 0x5EEDL) : s.id;
        UUID id = new UUID(seed * 0x9E3779B97F4A7C15L ^ town.getMostSignificantBits(), seed ^ town.getLeastSignificantBits());
        seed = seed * 0x2545F4914F6CDD1DL ^ town.getMostSignificantBits() ^ Long.rotateLeft(town.getLeastSignificantBits(), 17);   // each town its own people
        NamedFighter c = new NamedFighter(id);
        c.type = "dbzenith:resident";
        List<NamedFighter> living = new ArrayList<>();                                   // the dead give their names back
        for (NamedFighter x : d.all()) if (x.active()) living.add(x);
        c.name = Names.pick("resident", seed ^ town.getLeastSignificantBits(), living);
        c.race = "human";
        c.variant = "human";
        List<Personality> ps = temperaments(o);
        c.personality = ps.get(AiRandom.pick(seed, 6, 21, ps.size()));
        c.traits.addAll(Names.bornTraits(seed ^ 0x51ED, c.personality));
        if (o == Occupation.MERCHANT && AiRandom.chance(seed, 7, 21, 0.5)) c.traits.add(Trait.GREEDY);
        c.occupation = o.id();
        c.settlement = s == null ? null : s.id;
        c.home = home.asLong();
        c.look = AiRandom.pick(seed, 8, 21, 16);
        c.level = levelFor(o, seed);
        c.growth = Progression.growthOf(c);
        c.goal = goalFor(c, o);
        for (Technique t : o.techniques) if (!c.learned.contains(t.id())) c.learned.add(t.id());
        c.created = c.lastSeen = now;
        c.dimension = s == null ? "" : s.dimension;
        c.pos = home.asLong();
        c.remember(new Memory(Memory.Kind.PROMOTED, now, null, "resident"));
        d.add(c);
        return c;
    }

    /** Gives a resident a body at {@code at}. */
    public static Resident spawnBody(ServerLevel level, NamedFighter c, BlockPos at) {
        Resident r = com.dbzenith.npc.ModNpcs.RESIDENT.get().create(level);
        if (r == null) return null;
        r.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, AiRandom.pick(c.id.getMostSignificantBits(), level.getGameTime(), 22, 360), 0);
        r.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        r.setSkin(Resident.skinFor(c));
        Nemesis.bind(c, r, true);
        level.addFreshEntity(r);
        return r;
    }

    /** Peoples a new town: one resident per planned trade, two to a home, up to {@code limit}. Returns them. */
    public static List<NamedFighter> populate(ServerLevel level, Settlement s, int limit) {
        NemesisData d = Nemesis.data();
        List<Poi> homes = s.of(Poi.Kind.HOME);
        List<NamedFighter> out = new ArrayList<>();
        List<Occupation> plan = planOf(s);
        int n = Math.min(Math.min(limit, plan.size()), homes.size() * 2);
        long now = level.getGameTime();
        for (int i = 0; i < n; i++) {
            Poi home = homes.get(i / 2);
            NamedFighter c = create(d, s, plan.get(i), home.pos(), now, i + 1);
            spawnBody(level, c, home.pos());
            out.add(c);
        }
        return out;
    }

    /** Who moves into a settlement, in order: its own population (a planned city's) or the default town's. */
    static List<Occupation> planOf(Settlement s) {
        if (s.population.isEmpty()) return PLAN;
        List<Occupation> out = new ArrayList<>();
        for (String id : s.population) {
            Occupation o = Occupation.byId(id);
            if (o != null) out.add(o);
        }
        return out.isEmpty() ? PLAN : out;
    }

    /** How many live in a settlement: a planned one by its own population (scaled by living.settlementDensity), else townPopulation. */
    public static int limitOf(Settlement s) {
        if (s.population.isEmpty()) return LivingConfig.townPopulation();
        return (int) Math.round(planOf(s).size() * LivingConfig.density() / 100.0);
    }

    /**
     * Peoples a settlement that world generation built (docs/WORLD_STRUCTURES.md): residents by its plan, two to a home,
     * no bodies yet (each appears at home once its home is loaded, {@link #tick}). Returns them.
     */
    public static List<NamedFighter> settle(Settlement s, long now) {
        NemesisData d = Nemesis.data();
        List<NamedFighter> out = new ArrayList<>();
        if (d == null) return out;
        List<Poi> homes = s.of(Poi.Kind.HOME);
        List<Occupation> plan = planOf(s);
        int n = Math.min(Math.min(limitOf(s), plan.size()), homes.size() * 2);
        for (int i = 0; i < n; i++) out.add(create(d, s, plan.get(i), homes.get(i / 2).pos(), now, i + 1));
        return out;
    }

    /** The residents of a town (alive, in the town's books). */
    public static List<NamedFighter> of(Settlement s) {
        List<NamedFighter> out = new ArrayList<>();
        NemesisData d = Nemesis.data();
        if (d == null) return out;
        for (NamedFighter c : d.all()) if (c.resident() && s.id.equals(c.settlement) && c.status != NamedFighter.Status.DEAD) out.add(c);
        return out;
    }

    /** The home in a town with the most room (fewest residents), or null if every bed is taken. */
    static Poi roomiest(Settlement s) {
        List<NamedFighter> people = of(s);
        Poi home = null;
        int fewest = 2;
        for (Poi h : s.of(Poi.Kind.HOME)) {
            int n = 0;
            for (NamedFighter c : people) if (c.home == h.pos().asLong()) n++;
            if (n < fewest) {
                fewest = n;
                home = h;
            }
        }
        return home;
    }

    /** A resident brought in by command or egg, with no one behind it yet: a citizen of the nearest town (if it has room). */
    static void adopt(Resident r, long now) {
        LivingData ld = LivingData.get(r.getServer());
        Settlement s = ld.nearest(r.level().dimension().location().toString(), r.blockPosition(), 128);
        Poi home = s == null ? null : roomiest(s);
        if (home == null) s = null;
        NamedFighter c = create(Nemesis.data(), s, Occupation.CITIZEN, home == null ? r.blockPosition() : home.pos(), now, r.getUUID().getMostSignificantBits());
        if (s == null) c.dimension = r.level().dimension().location().toString();
        r.setSkin(Resident.skinFor(c));
        Nemesis.bind(c, r, true);
    }

    /** Who shares its home (itself included). */
    static List<NamedFighter> housemates(NamedFighter c) {
        List<NamedFighter> out = new ArrayList<>();
        NemesisData d = Nemesis.data();
        if (d == null) return out;
        for (NamedFighter o : d.all()) if (o.resident() && o.home == c.home && java.util.Objects.equals(o.settlement, c.settlement) && o.status != NamedFighter.Status.DEAD) out.add(o);
        return out;
    }

    /**
     * Every few seconds: residents who recovered come home; a body lost with its chunk is replaced (its place has
     * been ticking a while and it never showed up); a town short of people takes in a newcomer now and then.
     */
    static void tick(net.minecraft.server.MinecraftServer server, LivingData ld, long now) {
        NemesisData d = Nemesis.data();
        if (d == null) return;
        for (NamedFighter c : new ArrayList<>(d.all())) {
            if (!c.resident() || c.settlement == null) continue;
            Settlement s = ld.settlement(c.settlement);
            if (s == null) continue;
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation(s.dimension)));
            if (level == null) continue;
            if ((c.status == NamedFighter.Status.RECOVERING || c.status == NamedFighter.Status.AWAY) && c.returnAt > c.lastSeen + LivingConfig.recoverTicks()) {
                c.returnAt = c.lastSeen + LivingConfig.recoverTicks();                      // townsfolk recover at home, sooner
            }
            if ((c.status == NamedFighter.Status.RECOVERING || c.status == NamedFighter.Status.AWAY) && now >= c.returnAt) {
                c.status = NamedFighter.Status.PRESENT;
                c.body = null;
            }
            if (c.status != NamedFighter.Status.PRESENT) {
                TICKING_SINCE.remove(c.id);
                continue;
            }
            BlockPos home = c.home == 0 ? s.center : BlockPos.of(c.home);
            if (c.body == null) {
                if (level.isPositionEntityTicking(home)) spawnBody(level, c, home);
                continue;
            }
            BlockPos last = BlockPos.of(c.pos);
            if (!level.isPositionEntityTicking(last)) {
                TICKING_SINCE.remove(c.id);
                continue;
            }
            long since = TICKING_SINCE.computeIfAbsent(c.id, k -> now);
            if (now - since > 400 && c.lastSeen < since) {                                // its place has been live a while: the body is gone
                TICKING_SINCE.remove(c.id);
                c.body = null;
                spawnBody(level, c, home);
            }
        }
        if (now % 24000 < 200) newcomers(server, ld, d, now);
        d.setDirty();
    }

    /** Once a day: a town below its limit (deaths) takes in one newcomer, into the emptiest home. */
    static void newcomers(net.minecraft.server.MinecraftServer server, LivingData ld, NemesisData d, long now) {
        for (Settlement s : ld.settlements()) {
            List<NamedFighter> people = of(s);
            int limit = limitOf(s);
            if (people.size() >= limit || s.alarmed(now)) continue;
            long day = Math.floorDiv(now, 24000L);
            if (s.lastNewcomerDay == day) continue;
            s.lastNewcomerDay = day;
            Poi home = null;
            int fewest = Integer.MAX_VALUE;
            for (Poi h : s.of(Poi.Kind.HOME)) {
                int n = 0;
                for (NamedFighter c : people) if (c.home == h.pos().asLong()) n++;
                if (n < fewest) {
                    fewest = n;
                    home = h;
                }
            }
            if (home == null || fewest >= 2) continue;
            Occupation need = Occupation.CITIZEN;                                          // the trade the town lacks first
            List<Occupation> plan = planOf(s);
            for (Occupation o : plan) {
                long have = people.stream().filter(c -> Occupation.of(c) == o).count();
                long want = plan.subList(0, Math.min(limit, plan.size())).stream().filter(x -> x == o).count();
                if (have < want) {
                    need = o;
                    break;
                }
            }
            NamedFighter c = create(d, s, need, home.pos(), now, now ^ s.id.getMostSignificantBits());
            c.remember(new Memory(Memory.Kind.MET, now, null, "moved to " + s.name));
            c.status = NamedFighter.Status.PRESENT;                                        // its body comes once its home is loaded
            Rumors.add(ld, Rumor.Kind.NEWCOMER, c.id, c.name, null, "", s.name, s.id, now, List.of(c));
        }
    }
}
