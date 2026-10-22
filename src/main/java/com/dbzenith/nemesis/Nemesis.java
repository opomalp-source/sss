package com.dbzenith.nemesis;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.AiProfile;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.Faction;
import com.dbzenith.npc.ai.NpcBrain;
import com.dbzenith.npc.ai.OpponentProfile;
import com.dbzenith.npc.ai.Power;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Nemesis system's heart (docs/NEMESIS.md): named characters ({@link NamedFighter}, kept in {@link NemesisData})
 * and their bodies ({@code KiFighter} carrying the character's id). Who becomes a character (bosses and Saiyan
 * Warriors on sight; ordinary fighters when they beat a player or survive one), what they remember of every fight,
 * how relationships move, and what happens to them after (they leave, recover, return, survive death, die, retire).
 * Everything is decided on the server; the combat code calls in with one line per event.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Nemesis {
    /** Bodies of characters loaded now (weak: unloading or discarding drops them). */
    private static final Set<KiFighter> BODIES = Collections.newSetFromMap(new WeakHashMap<>());
    /** Canon characters: one identity per type for the bosses, kept under their own names. */
    private static final Map<String, String> CANON = Map.of("tyrant_lord", "Frieza", "rampage_brute", "Broly");
    private static final Map<String, String> ROSTER = Map.of("mr_satan", "Mr. Satan", "spopovich", "Spopovich", "pintar", "Pintar",
            "jewel", "Jewel", "nam", "Nam", "ranfan", "Ranfan", "yamu", "Yamu");

    private Nemesis() {}

    public static boolean enabled() {
        return NemesisConfig.enabled();
    }

    static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static NemesisData data() {
        MinecraftServer s = server();
        return s == null ? null : NemesisData.get(s);
    }

    /** The character a body belongs to, or null. */
    public static NamedFighter of(KiFighter k) {
        if (k == null || k.nemesisId() == null || k.level().isClientSide) return null;
        NemesisData d = data();
        return d == null ? null : d.get(k.nemesisId());
    }

    /** A snapshot of the loaded bodies. */
    public static List<KiFighter> bodies() {
        return new ArrayList<>(BODIES);
    }

    static String typeId(KiFighter k) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(k.getType());
        return key == null ? "" : key.toString();
    }

    static String path(KiFighter k) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(k.getType());
        return key == null ? "" : key.getPath();
    }

    /** Whether a body is named the moment it fights (canon bosses, Saiyan Warriors, the tournament roster). */
    static boolean namedOnSight(KiFighter k) {
        String p = path(k);
        return CANON.containsKey(p) || ROSTER.containsKey(p) || p.equals("saiyan_warrior");
    }

    public static UUID canonId(String type) {
        return UUID.nameUUIDFromBytes(("dbzenith:nemesis:canon:" + type).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Whether this character counts {@code p} as a friend (never attacks them). Safe anywhere (false on clients). */
    public static boolean friendlyTo(KiFighter k, Player p) {
        if (k.level().isClientSide || !enabled()) return false;
        NamedFighter c = of(k);
        Bond b = c == null ? null : c.players.get(p.getUUID());
        return b != null && b.state.friendly();
    }

    // ------------------------------------------------------------------ becoming a character

    /** The character for this body, making one if it should be named on sight; null for an ordinary fighter. */
    public static NamedFighter identify(KiFighter k) {
        if (!enabled() || k.level().isClientSide) return null;
        NamedFighter c = of(k);
        if (c != null) return c;
        if (!namedOnSight(k)) return null;
        String p = path(k);
        NemesisData d = data();
        if (CANON.containsKey(p) || ROSTER.containsKey(p)) {                       // one per world, under its own name
            UUID id = canonId(p);
            c = d.get(id);
            if (c == null) {
                c = create(k, id, CANON.containsKey(p) ? CANON.get(p) : ROSTER.get(p));
                c.canon = true;
                d.add(c);
            } else if (c.status == NamedFighter.Status.DEAD) {
                return null;                                                       // gone: this one is a nameless stand-in
            }
            int summoned = k.fighterLevel();
            c.level = Math.max(c.level, summoned);                                 // grown, but a boss fight stays near the summon's scale
            bind(c, k, false);
            k.showAs(c.fullName(), Math.min(c.level, (int) Math.ceil(summoned * 1.3) + 1), false);
            return c;
        }
        return promote(k, "sight");
    }

    /** An ordinary fighter becomes a named character (it beat a player, or survived one). */
    public static NamedFighter promote(KiFighter k, String why) {
        if (!enabled() || k.level().isClientSide) return null;
        NamedFighter c = of(k);
        if (c != null) return c;
        if (!why.equals("sight") && !NemesisConfig.promote()) return null;
        if (k instanceof com.dbzenith.tournament.TournamentFighter && !ROSTER.containsKey(path(k))) return null;
        NemesisData d = data();
        if (d == null) return null;
        if (d.activeCount() >= NemesisConfig.maxCharacters() && !namedOnSight(k)) return null;
        c = create(k, UUID.randomUUID(), null);
        c.remember(new Memory(Memory.Kind.PROMOTED, k.level().getGameTime(), null, why));
        d.add(c);
        bind(c, k, false);
        return c;
    }

    static NamedFighter create(KiFighter k, UUID id, String canonName) {
        long now = k.level().getGameTime();
        NamedFighter c = new NamedFighter(id);
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        AiProfile prof = k.aiProfile();
        NemesisData d = data();
        c.type = typeId(k);
        c.name = canonName != null ? canonName : Names.pick(path(k), seed, d == null ? List.of() : d.all());
        c.race = prof.race() == null ? "" : prof.race().name().toLowerCase(java.util.Locale.ROOT);
        c.variant = prof.variant() == null ? "" : prof.variant().name().toLowerCase(java.util.Locale.ROOT);
        c.personality = k.brain().personality();
        c.traits.addAll(Names.bornTraits(seed, c.personality));
        c.level = k.fighterLevel();
        c.growth = Progression.growthOf(c);
        c.created = c.lastSeen = now;
        c.dimension = k.level().dimension().location().toString();
        c.pos = k.blockPosition().asLong();
        int eq = Power.equivalentLevel(c.level);
        for (var f : com.dbzenith.npc.ai.NpcForms.ladder(prof, eq)) c.forms.add(f.id());
        c.epithet = Names.epithet(c);
        return c;
    }

    /** Makes {@code k} the body of {@code c}: its name, level, temperament, traits and techniques. */
    public static void bind(NamedFighter c, KiFighter k, boolean fresh) {
        k.setNemesisId(c.id);
        c.body = k.getUUID();
        c.status = NamedFighter.Status.PRESENT;
        c.lastSeen = k.level().getGameTime();
        c.dimension = k.level().dimension().location().toString();
        c.pos = k.blockPosition().asLong();
        k.brain().setPersonality(c.personality);
        k.brain().setShifts(c.shifts());
        List<Technique> learned = new ArrayList<>();
        for (String id : c.learned) {
            Technique t = Techniques.byId(id);
            if (t != null) learned.add(t);
        }
        k.setLearned(learned);
        k.showAs(c.fullName(), c.level, fresh);
        BODIES.add(k);
        NemesisData d = data();
        if (d != null) d.setDirty();
    }

    // ------------------------------------------------------------------ fights (from the combat AI)

    /** A fighter engaged a foe (NpcBrain): the meeting is remembered, it greets them, and recalls their habits. */
    public static void onFightStart(KiFighter k, LivingEntity foe, OpponentProfile profile) {
        if (!enabled() || k.level().isClientSide) return;
        NamedFighter c = identify(k);
        if (c == null) return;
        long now = k.level().getGameTime();
        NemesisData d = data();
        if (foe instanceof ServerPlayer p) {
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            d.index(c, p.getUUID());
            boolean first = b.encounters == 0;
            double days = first ? -1 : (now - b.lastEncounter) / 24000.0;
            boolean fresh = first || now - b.lastEncounter > 1200;                     // the same fight picked up again is not a new meeting
            if (fresh) {
                b.encounters++;
                c.battles++;
            }
            b.lastEncounter = now;
            PlayerData pd = ModCapabilities.get(p).orElse(null);
            if (pd != null) b.playerLevelSeen = Math.max(b.playerLevelSeen, KiFighter.levelFor(StatCalculator.fullPower(pd)));
            b.powerShown = Math.round(Power.of(k));
            b.placeShown = place(k);
            b.statusShown = "fighting";
            Memory lastMeet = c.last(Memory.Kind.FOUGHT, p.getUUID());
            if (first) c.remember(new Memory(Memory.Kind.MET, now, p.getUUID(), ""));
            else if (fresh && (lastMeet == null || now - lastMeet.tick() > 1200)) c.remember(new Memory(Memory.Kind.FOUGHT, now, p.getUUID(), ""));
            if (first) b.shift(0, 3);
            if (k instanceof com.dbzenith.tournament.TournamentFighter) b.shift(0, c.has(Trait.COMPETITIVE) ? 10 : 6);   // the tournament: competition
            if (b.habits != null) {                                                   // it remembers how you fight (the cleverer, the more)
                double weight = 0.25 + 0.6 * c.temper().discipline;
                profile.seed(b.habits, weight);
                k.brain().note("remembers " + p.getGameProfile().getName() + "'s style");
            }
            if (k.leaveAt == 0 && !k.greeted) {                                       // encounters greet on arrival themselves
                Dialogue.Context ctx = context(c, b, p);
                ctx.days = days;
                Dialogue.say(k, first ? Dialogue.On.GREET_FIRST : Dialogue.On.GREET_AGAIN, ctx, p);
            }
            k.greeted = true;
            refresh(c, b, now);
        } else if (foe instanceof KiFighter other && of(other) != null) {
            NamedFighter o = of(other);
            Bond b = c.character(o);
            b.encounters++;
            b.lastEncounter = now;
            b.shift(0, 4);
        }
        d.setDirty();
    }

    /** The fight with a foe is over (NpcBrain.endFight): habits kept; a retreat or the player getting away remembered. */
    public static void onFightEnd(KiFighter k, UUID foeId, OpponentProfile profile, NpcBrain.Outcome how) {
        if (!enabled() || k.level().isClientSide || foeId == null) return;
        NamedFighter c = of(k);
        if (c == null) return;
        long now = k.level().getGameTime();
        Bond b = c.players.get(foeId);
        if (b != null) {
            b.statusShown = how == NpcBrain.Outcome.FLED ? "fled" : "fought";
            double[] h = profile.habits();
            if (profile.samples() > 10) {
                if (b.habits == null || b.habits.length != h.length) b.habits = h;
                else for (int i = 0; i < h.length; i++) b.habits[i] = b.habits[i] * 0.5 + h[i] * 0.5;
            }
            profile.techniques().forEach((id, n) -> {
                for (int i = 0; i < Math.min(n, 4); i++) b.sawTechnique(id);
            });
            if (how == NpcBrain.Outcome.FLED) {
                b.retreats++;
                c.remember(new Memory(Memory.Kind.RETREATED, now, foeId, ""));
                b.shift(c.has(Trait.VENGEFUL) ? -6 : -2, 5);
                c.reputation = Math.max(0, c.reputation - 5);
                ServerPlayer p = k.getServer().getPlayerList().getPlayer(foeId);
                if (p != null) Dialogue.say(k, Dialogue.On.FLEE, context(c, b, p), p);
            } else if (how == null && Power.health(k) > 0.5) {
                ServerPlayer p = k.getServer().getPlayerList().getPlayer(foeId);
                if (p == null || p.level() != k.level() || p.distanceTo(k) > 32) {        // they got away
                    b.escapes++;
                    c.remember(new Memory(Memory.Kind.PLAYER_ESCAPED, now, foeId, ""));
                    b.shift(-2, 3);
                }
            }
            refresh(c, b, now);
        }
        Progression.gain(c, 25, now);
        k.leaveAt = k.leaveAt == 0 ? now + 100 : Math.min(k.leaveAt, now + 100);    // its business here is done
        data().setDirty();
    }

    // ------------------------------------------------------------------ knock-outs, sparing, deaths (from KnockOut and the death event)

    /** Someone was knocked out (KnockOut): a win or a loss for the characters involved. */
    public static void onKnockedOut(LivingEntity v, DamageSource src) {
        if (!enabled() || v.level().isClientSide) return;
        long now = v.level().getGameTime();
        Entity by = src.getEntity();
        if (v instanceof KiFighter k && by instanceof ServerPlayer p && k.sparWith == null) {
            k.downedBy = p.getUUID();                                                  // remembered for when it gets away (the game forgets in five seconds)
            NamedFighter c = of(k);
            if (c == null) c = identify(k);
            if (c == null) return;
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            data().index(c, p.getUUID());
            b.losses++;
            b.lastResult = -1;
            c.defeats++;
            c.reputation = Math.max(0, c.reputation - 10);
            var t = c.temper();
            double ph = Power.health(p);
            c.remember(new Memory(Memory.Kind.LOST, now, p.getUUID(), ph > 0.85 ? "humiliated" : place(k)));
            double dAff = t.honor > 0.7 ? 5 : t.pride > 0.8 || c.has(Trait.VENGEFUL) ? -12 : -4;
            double dRiv = 10 + (ph < 0.3 ? 8 : 0) + (c.has(Trait.COMPETITIVE) ? 4 : 0);
            if (ph > 0.85) {                                                           // barely scratched them: humiliation
                dAff -= t.pride * 10;
                dRiv += 6;
            }
            b.shift(dAff, dRiv);
            Progression.gain(c, c.race.equals("saiyan") ? 70 : 40, now);                  // a Saiyan grows from a beating (zenkai)
            c.goal = c.has(Trait.VENGEFUL) || t.pride > 0.8 ? NamedFighter.Goal.REVENGE : NamedFighter.Goal.TRAIN;
            c.goalTarget = p.getUUID();
            Dialogue.say(k, Dialogue.On.NPC_LOSES, context(c, b, p), p);
            allyDefeated(c, k, p, now);
            refresh(c, b, now);
            data().setDirty();
        } else if (v instanceof ServerPlayer p && by instanceof KiFighter k && k.sparWith == null) {
            if (k.isNoAi()) return;                                                    // a statue is no fighter
            NamedFighter c = of(k);
            if (c == null) c = identify(k);
            if (c == null) c = promote(k, "beat");
            if (c == null) return;
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            data().index(c, p.getUUID());
            b.wins++;
            b.lastResult = 1;
            c.victories++;
            double rel = Power.of(p) / Math.max(1, Power.of(k));
            c.reputation += (int) Math.round(20 * Math.max(0.3, Math.min(3, rel)));
            c.remember(new Memory(Memory.Kind.WON, now, p.getUUID(), place(k)));
            var t = c.temper();
            b.shift(t.pride > 0.8 ? -6 : t.honor > 0.6 ? 5 : 0, 6);
            Progression.gain(c, 40, now);
            Dialogue.Context ctx = context(c, b, p);
            Dialogue.say(k, Dialogue.On.NPC_WINS, ctx, p);
            if (t.honor >= 0.65 || b.state == Relationship.RESPECTFUL || b.state == Relationship.FRIENDLY_RIVAL || b.state.friendly()) {
                k.spared = p.getUUID();                                                // it lets them live
                k.setTarget(null);
                b.sparedByThem++;
                c.remember(new Memory(Memory.Kind.SPARED, now, p.getUUID(), ""));
                Dialogue.say(k, Dialogue.On.NPC_SPARES, context(c, b, p), p);
                if (k.leaveAt == 0) k.leaveAt = now + 80;
            }
            refresh(c, b, now);
            data().setDirty();
        }
    }

    /** Whether a fighter spares a downed player (the grip system asks before finishing them). */
    public static boolean spares(KiFighter k, LivingEntity downed) {
        return k.sparWith != null || k.spared != null && k.spared.equals(downed.getUUID()) || downed instanceof Player p && friendlyTo(k, p);
    }

    /** The player defeated one of this character's allies (a faction-mate or a character it likes). */
    static void allyDefeated(NamedFighter fallen, KiFighter body, ServerPlayer p, long now) {
        for (KiFighter other : bodies()) {
            if (other == body || !other.isAlive() || other.distanceToSqr(body) > 32 * 32) continue;
            NamedFighter o = of(other);
            if (o == null) continue;
            Bond toFallen = o.characters.get(fallen.id);
            boolean ally = Faction.allied(other, body) || toFallen != null && toFallen.affinity > 20;
            if (!ally) continue;
            Bond b = o.player(p.getUUID(), p.getGameProfile().getName());
            data().index(o, p.getUUID());
            b.alliesLost++;
            b.encounters = Math.max(1, b.encounters);
            b.shift(o.has(Trait.LOYAL) ? -16 : -8, o.has(Trait.LOYAL) ? 12 : 6);
            o.remember(new Memory(Memory.Kind.ALLY_DEFEATED, now, p.getUUID(), fallen.name));
        }
    }

    /**
     * A downed fighter bled out without being finished (KnockOut): the player left it alive. A character (and an
     * ordinary fighter downed by a player: it becomes one) gets away and remembers being spared. Returns true if it
     * was handled (the body has left).
     */
    public static boolean onBledOut(LivingEntity v, boolean hadNoAi) {
        if (!enabled() || !(v instanceof KiFighter k) || v.level().isClientSide || hadNoAi) return false;   // a statue is no fighter
        if (k instanceof com.dbzenith.tournament.TournamentFighter) return false;
        LivingEntity by = k.downedBy != null && k.getServer() != null ? k.getServer().getPlayerList().getPlayer(k.downedBy) : v.getLastHurtByMob();
        NamedFighter c = of(k);
        if (c == null && by instanceof ServerPlayer) c = identify(k);
        if (c == null && by instanceof ServerPlayer && AiRandom.chance(k.getUUID().getMostSignificantBits(), k.tickCount, 63, 0.25)) c = promote(k, "spared");   // some get away; most don't
        if (c == null) return false;
        long now = v.level().getGameTime();
        if (by instanceof ServerPlayer p) {
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            data().index(c, p.getUUID());
            b.sparedThem++;
            c.remember(new Memory(Memory.Kind.SPARED_BY, now, p.getUUID(), ""));
            var t = c.temper();
            double dAff = t.honor > 0.7 ? 25 : c.has(Trait.FRIENDLY) ? 20 : t.pride > 0.8 ? -10 : c.has(Trait.VENGEFUL) ? 4 : 12;
            b.shift(dAff, 5);
            if (t.pride > 0.8 && b.sparedThem >= 2 && !c.has(Trait.VENGEFUL)) evolve(c, Trait.VENGEFUL, "spared twice: humiliated", now);
            if (t.honor > 0.6 && b.sparedThem >= 2 && !c.has(Trait.FRIENDLY) && !c.has(Trait.VENGEFUL)) evolve(c, Trait.FRIENDLY, "spared twice: grateful", now);
            refresh(c, b, now);
        }
        depart(c, k, NamedFighter.Status.RECOVERING, "escapes");
        return true;
    }

    /** A character's body died. Finished by a player or another fighter, it may survive anyway (and come back). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !enabled()) return;
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide) return;
        if (v instanceof ServerPlayer p && event.getSource().getEntity() instanceof KiFighter k && !com.dbzenith.combat.KnockOut.isDown(p)) {
            NamedFighter c = of(k);                                                    // killed outright (knock-outs off): still a win
            Memory won = c == null ? null : c.last(Memory.Kind.WON, p.getUUID());
            if (c != null && (won == null || v.level().getGameTime() - won.tick() > 200)) {
                onKnockedOut(p, event.getSource());
            }
            return;
        }
        if (!(v instanceof KiFighter k)) return;
        NamedFighter c = of(k);
        if (c == null) return;
        long now = v.level().getGameTime();
        Entity by = event.getSource().getEntity();
        BODIES.remove(k);
        c.body = null;
        resolveDeath(c, by, now, NemesisConfig.survivalChance());
        data().setDirty();
    }

    /**
     * Whether a character survives its death (it comes back, scarred and vengeful) or is gone for good, at this chance
     * (more for canon characters, less for deaths no fighter dealt; never after three escapes). Public for tests.
     */
    public static boolean resolveDeath(NamedFighter c, Entity by, long now, double baseChance) {
        boolean byFighter = by instanceof LivingEntity;
        double chance = baseChance * (c.canon ? 1.4 : 1) * (byFighter ? 1 : 0.5);
        boolean lives = c.survivedDeaths < 3 && AiRandom.chance(c.id.getMostSignificantBits(), c.battles * 7L + c.survivedDeaths, 61, chance);
        if (by instanceof ServerPlayer p) {
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            data().index(c, p.getUUID());
            if (b.lastResult != -1 || c.last(Memory.Kind.LOST, p.getUUID()) == null) {
                b.losses++;
                b.lastResult = -1;
                c.defeats++;
            }
            b.shift(lives ? -35 : 0, lives ? 20 : 0);
            refresh(c, b, now);
        }
        if (lives) {
            c.survivedDeaths++;
            c.remember(new Memory(Memory.Kind.SURVIVED_DEATH, now, by == null ? null : by.getUUID(), by == null ? "" : by.getName().getString()));
            if (!c.has(Trait.VENGEFUL) && AiRandom.chance(c.id.getLeastSignificantBits(), c.survivedDeaths, 62, 0.6)) evolve(c, Trait.VENGEFUL, "came back from death", now);
            c.goal = NamedFighter.Goal.REVENGE;
            c.goalTarget = by == null ? null : by.getUUID();
            c.status = NamedFighter.Status.RECOVERING;
            c.returnAt = now + (long) (returnDelay(c) * 1.6);
            c.epithet = Names.epithet(c);
        } else {
            c.status = NamedFighter.Status.DEAD;
            c.remember(new Memory(Memory.Kind.DIED, now, by == null ? null : by.getUUID(), by == null ? "" : by.getName().getString()));
            for (Bond b : c.players.values()) b.statusShown = "dead";
        }
        return lives;
    }

    // ------------------------------------------------------------------ what they see (from AiEvents)

    /** A watching character saw a foe use a technique or take a form. */
    public static void observed(KiFighter k, LivingEntity actor, String technique, String form) {
        if (!enabled() || k.level().isClientSide || !(actor instanceof ServerPlayer p)) return;
        NamedFighter c = of(k);
        if (c == null) return;
        Bond b = c.players.get(p.getUUID());
        if (b == null) return;
        long now = k.level().getGameTime();
        if (technique != null && !technique.isEmpty()) {
            if (!b.techniquesSeen.containsKey(technique)) c.remember(new Memory(Memory.Kind.SAW_TECHNIQUE, now, p.getUUID(), technique));
            b.sawTechnique(technique);
        }
        if (form != null && !form.isEmpty() && !"base".equals(form) && b.formsSeen.add(form)) {
            c.remember(new Memory(Memory.Kind.SAW_FORM, now, p.getUUID(), form));
            b.strongestFormSeen = form;
            b.shift(0, 4);
            Dialogue.Context ctx = context(c, b, p);
            ctx.playerForm = form;
            Dialogue.say(k, Dialogue.On.PLAYER_TRANSFORMS, ctx, p);
        }
        data().setDirty();
    }

    /** The character took a form in front of its foe: the player has now seen it. */
    public static void ownForm(KiFighter k, String form) {
        if (!enabled() || k.level().isClientSide) return;
        NamedFighter c = of(k);
        if (c == null || "base".equals(form)) return;
        c.forms.add(form);
        if (k.getTarget() instanceof ServerPlayer p) {
            Bond b = c.players.get(p.getUUID());
            if (b != null && b.formsShown.add(form)) {
                Dialogue.Context ctx = context(c, b, p);
                ctx.npcForm = form;
                Dialogue.say(k, Dialogue.On.NPC_TRANSFORMS, ctx, p);
            }
        }
        data().setDirty();
    }

    /** A blow landed (AiEvents): helping a character, interrupting its training, starting or breaking a spar. */
    public static void onBlow(Entity attacker, LivingEntity victim) {
        if (!enabled() || !(attacker instanceof ServerPlayer p)) return;
        long now = victim.level().getGameTime();
        if (victim instanceof KiFighter k && of(k) != null) {
            NamedFighter c = of(k);
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            data().index(c, p.getUUID());
            if (k.training) {                                                          // its training interrupted
                k.training = false;
                k.setAiFlag(KiFighter.AI_CHARGING, false);
                if (!b.state.friendly()) {
                    k.spared = null;
                    k.setTarget(p);
                }
                c.remember(new Memory(Memory.Kind.INTERRUPTED, now, p.getUUID(), ""));
                b.shift(-10, 5);
            }
            if (b.state.friendly() && k.aiMayFight() && k.sparWith == null && k.getTarget() != p) {      // a friend struck: a spar
                k.sparWith = p.getUUID();
                k.setTarget(p);
                refresh(c, b, now);
            } else if (k.sparWith == null && k.lastSparEnd > 0 && now - k.lastSparEnd < 100 && b.state.friendly()) {   // hit after the spar ended
                c.remember(new Memory(Memory.Kind.BETRAYED, now, p.getUUID(), "struck after a spar"));
                b.shift(-35, 10);
                k.lastSparEnd = 0;
                refresh(c, b, now);
            }
            data().setDirty();
            return;
        }
        if (victim instanceof net.minecraft.world.entity.Mob m && m.getTarget() instanceof KiFighter ally && of(ally) != null && ally != attacker) {
            NamedFighter c = of(ally);                                                 // the player helps a character against its foe
            Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
            data().index(c, p.getUUID());
            if (now - b.lastEncounter > 100 || b.helped == 0) {
                b.helped++;
                b.encounters = Math.max(1, b.encounters);
                c.remember(new Memory(Memory.Kind.HELPED_BY, now, p.getUUID(), m.getName().getString()));
                b.shift(12, 0);
                refresh(c, b, now);
                data().setDirty();
            }
        }
    }

    // ------------------------------------------------------------------ relationships and leaving

    /** Re-reads a bond's state; a change is remembered (and the epithet follows the deeds). */
    static void refresh(NamedFighter c, Bond b, long now) {
        Relationship before = b.state;
        b.state = Relationship.next(b.state, b.affinity, b.rivalry, b.encounters, b.spars);
        if (b.state != before) {
            UUID who = null;
            for (Map.Entry<UUID, Bond> e : c.players.entrySet()) if (e.getValue() == b) who = e.getKey();
            c.remember(new Memory(Memory.Kind.RELATION_CHANGED, now, who, before.id() + ">" + b.state.id()));
            if (b.state == Relationship.ALLY || b.state == Relationship.TRAINING_PARTNER) c.remember(new Memory(Memory.Kind.BEFRIENDED, now, who, ""));
            if (before.friendly() && !b.state.friendly() && b.affinity < 0) c.remember(new Memory(Memory.Kind.BETRAYED, now, who, ""));
            if (b.state.rival() && b.rivalry > 50) c.goal = NamedFighter.Goal.RIVALRY;
        }
        c.epithet = Names.epithet(c);
    }

    /** A character's temperament changes after a major experience (it keeps its personality; it gains a trait). */
    static void evolve(NamedFighter c, Trait t, String why, long now) {
        if (c.traits.size() >= 3 || !c.traits.add(t)) return;
        if (t == Trait.FRIENDLY) c.traits.remove(Trait.VENGEFUL);
        if (t == Trait.VENGEFUL) c.traits.remove(Trait.FRIENDLY);
        c.remember(new Memory(Memory.Kind.TALKED, now, null, "became " + t.id() + ": " + why));
        c.growth = Progression.growthOf(c);
        c.epithet = Names.epithet(c);
    }

    /** Ticks until it comes back (believable: hours to days; vengeful sooner, the beaten longer). */
    static long returnDelay(NamedFighter c) {
        long min = NemesisConfig.returnMinTicks(), max = Math.max(min, NemesisConfig.returnMaxTicks());
        double r = AiRandom.unit(c.id.getLeastSignificantBits(), c.battles * 13L + c.defeats, 71);
        double f = c.has(Trait.VENGEFUL) ? 0.7 : c.has(Trait.CALM) ? 1.2 : 1;
        return (long) ((min + (max - min) * r) * f);
    }

    /** Its body leaves the world (in a burst, as fighters vanish): the character is away until it returns. */
    public static void depart(NamedFighter c, KiFighter k, NamedFighter.Status status, String why) {
        long now = k.level().getGameTime();
        if (k.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, k.getX(), k.getY() + 1, k.getZ(), 14, 0.4, 0.8, 0.4, 0.04);
            sl.playSound(null, k.getX(), k.getY(), k.getZ(), com.dbzenith.registry.ModSounds.VANISH.get(), SoundSource.HOSTILE, 0.8f, 1.2f);
        }
        c.status = status;
        c.body = null;
        c.lastSeen = now;
        c.dimension = k.level().dimension().location().toString();
        c.pos = k.blockPosition().asLong();
        c.returnAt = now + returnDelay(c) * (status == NamedFighter.Status.RECOVERING ? 3 : 2) / 2;
        for (Bond b : c.players.values()) if (b.lastEncounter >= now - 2400) b.statusShown = status == NamedFighter.Status.RECOVERING ? "recovering" : "away";
        if (c.goal == NamedFighter.Goal.WANDER) c.goal = NamedFighter.Goal.TRAIN;
        retireIfDone(c, now);
        BODIES.remove(k);
        Encounters.left(k);
        k.discard();
        data().setDirty();
    }

    /** Beaten too often by a player it has come to respect, or hopelessly outclassed: it retires. */
    static void retireIfDone(NamedFighter c, long now) {
        if (c.canon) return;
        for (Bond b : c.players.values()) {
            if (b.losses >= 7 && b.losses > b.wins * 3 && b.affinity > 10) {
                c.status = NamedFighter.Status.RETIRED;
                c.remember(new Memory(Memory.Kind.RETIRED, now, null, "at peace"));
                for (Bond o : c.players.values()) o.statusShown = "retired";
                return;
            }
        }
    }

    // ------------------------------------------------------------------ bodies in the world

    /** A body joins the world: bound to its character, or removed if it is a stale copy or its character is gone. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof KiFighter k) || event.getLevel().isClientSide() || k.nemesisId() == null) return;
        if (!enabled()) return;
        NemesisData d = data();
        if (d == null) return;
        NamedFighter c = d.get(k.nemesisId());
        if (c == null || !c.active() || c.body != null && !c.body.equals(k.getUUID())) {
            event.setCanceled(true);                                                  // a duplicate, or someone dead or retired
            return;
        }
        bind(c, k, false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled()) return;
        MinecraftServer s = event.getServer();
        int t = s.getTickCount();
        if (t % 20 == 0) tickBodies(s);
        if (t % 600 == 300) Encounters.tick(s);
        if (t % 100 == 50) Encounters.tickFights(s);
        if (t % 1000 == 500) WorldSim.tick(s);
    }

    /** Once a second: where the bodies are; spars ending; bodies leaving once their business is done. */
    static void tickBodies(MinecraftServer s) {
        NemesisData d = NemesisData.get(s);
        for (KiFighter k : bodies()) {
            if (k.isRemoved() || !k.isAlive()) {
                BODIES.remove(k);
                continue;
            }
            NamedFighter c = d.get(k.nemesisId());
            if (c == null) continue;
            long now = k.level().getGameTime();
            c.lastSeen = now;
            c.pos = k.blockPosition().asLong();
            c.dimension = k.level().dimension().location().toString();
            if (k.sparWith != null) Spars.tick(c, k, now);
            if (k.protecting != null) Encounters.guard(c, k, s);
            if (k.training) training(c, k, now);
            if (c.resident()) k.leaveAt = 0;                                           // townsfolk are at home here
            if (k.leaveAt > 0 && now >= k.leaveAt && (k.getTarget() == null || k.spared != null) && !com.dbzenith.combat.KnockOut.isDown(k)) {
                depart(c, k, NamedFighter.Status.AWAY, "leaves");
            }
        }
    }

    /** A character found training: its aura up; a word for the player who comes near. */
    static void training(NamedFighter c, KiFighter k, long now) {
        k.setAiFlag(KiFighter.AI_CHARGING, true);
        if (now % 40 == 0) k.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (k.greeted) return;
        for (ServerPlayer p : ((ServerLevel) k.level()).players()) {
            Bond b = c.players.get(p.getUUID());
            if (b == null || p.distanceTo(k) > 10) continue;
            k.greeted = true;
            Dialogue.say(k, Dialogue.On.FOUND_TRAINING, context(c, b, p), p);
            return;
        }
    }

    /** A player talks to a character (right-click): it answers, once every few seconds; the first time, they have met. */
    public static void talk(KiFighter k, ServerPlayer p) {
        NamedFighter c = of(k);
        if (c == null || !enabled()) return;
        long now = k.level().getGameTime();
        if (now - k.lastTalk < 60) return;
        k.lastTalk = now;
        Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
        data().index(c, p.getUUID());
        if (b.encounters == 0) {
            b.encounters = 1;
            b.lastEncounter = now;
            c.remember(new Memory(Memory.Kind.MET, now, p.getUUID(), ""));
            refresh(c, b, now);
        }
        Dialogue.say(k, Dialogue.On.TALK, context(c, b, p), p);
        data().setDirty();
    }

    // ------------------------------------------------------------------ dialogue facts

    public static Dialogue.Context context(NamedFighter c, Bond b, ServerPlayer p) {
        Dialogue.Context ctx = new Dialogue.Context();
        ctx.npc = c;
        ctx.bond = b;
        ctx.player = p == null ? (b == null ? "" : b.name) : p.getGameProfile().getName();
        PlayerData pd = p == null ? null : ModCapabilities.get(p).orElse(null);
        ctx.playerForm = pd == null ? "base" : pd.getFormId();
        if (p != null) ctx.stronger = c.level > KiFighter.levelFor(pd == null ? 0 : StatCalculator.fullPower(pd));
        long since = b == null ? Long.MIN_VALUE : b.lastEncounter;
        Memory form = null, tech = null;
        for (Memory m : c.history) {
            if (m.tick() < since) continue;
            if (m.kind() == Memory.Kind.UNLOCKED_FORM) form = m;
            if (m.kind() == Memory.Kind.LEARNED_TECHNIQUE) tech = m;
        }
        ctx.newForm = form != null;
        ctx.npcForm = form != null ? form.detail() : Progression.topForm(c);
        ctx.newTech = tech != null;
        ctx.newTechnique = tech != null ? tech.detail() : "";
        Bond top = null;
        String topName = "";
        for (Map.Entry<UUID, Bond> e : c.characters.entrySet()) if (top == null || e.getValue().rivalry > top.rivalry) {
            top = e.getValue();
            topName = e.getValue().name;
        }
        ctx.rival = top != null && top.rivalry >= 25 ? topName : "";
        return ctx;
    }

    static String place(Entity e) {
        String dim = e.level().dimension().location().getPath();
        return dim + " " + e.blockPosition().getX() + ", " + e.blockPosition().getZ();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Encounters.forget(event.getEntity().getUUID());
    }
}
