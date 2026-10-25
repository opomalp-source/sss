package com.dbzenith.living;

import com.dbzenith.DBZenith;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.npc.ai.AiRandom;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Rumors (docs/LIVING_WORLD.md): real events become news. The Nemesis system's notable memories (a character beat or
 * lost to a player, was spared, survived death, reached a new form, won a tournament, beat another character, fled)
 * and the town's own (an attack, a death, a newcomer, a famous visitor) are recorded with who, whom, what and where.
 * Who hears first: the townsfolk of the place it happened. Then it travels: residents pass on what they know when they
 * chat, and each day a town hears a little news from elsewhere through those whose trade brings it (merchants hear
 * everything, guards hear of threats, the master of fighters). What they say about it comes from data packs
 * ({@code data/<ns>/rumors/*.json}: lines per kind, "<kind>_you" when told to the player it is about).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Rumors {
    /** How many rumors one resident keeps in mind. */
    static final int KNOWLEDGE = 16;
    /** How far from a town an event counts as "near" it (and the town hears at once). */
    static final int NEAR = 160;
    private static Map<String, List<String>> lines = new HashMap<>();

    private Rumors() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "rumors") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                Map<String, List<String>> out = new HashMap<>();
                for (JsonElement f : files.values()) {
                    if (!f.isJsonObject()) continue;
                    var obj = GsonHelper.getAsJsonObject(f.getAsJsonObject(), "lines", new com.google.gson.JsonObject());
                    for (var e : obj.entrySet()) {
                        List<String> l = out.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
                        if (e.getValue().isJsonArray()) for (JsonElement s : e.getValue().getAsJsonArray()) l.add(s.getAsString());
                    }
                }
                lines = out;
            }
        });
    }

    public static boolean loaded() {
        return !lines.isEmpty();
    }

    // ------------------------------------------------------------------ making rumors

    /** Records a rumor; the {@code knowers} know it from the start. */
    public static Rumor add(LivingData ld, Rumor.Kind kind, UUID subject, String subjectName, UUID object, String objectName, String detail,
                            UUID origin, long now, Collection<NamedFighter> knowers) {
        if (!LivingConfig.rumors()) return null;
        for (Rumor r : ld.rumors()) {                                                     // the same news twice in a short while is one rumor
            if (r.kind() == kind && java.util.Objects.equals(r.subject(), subject) && java.util.Objects.equals(r.object(), object)
                    && r.detail().equals(detail) && now - r.tick() < 6000) return r;
        }
        Rumor r = new Rumor(UUID.randomUUID(), kind, subject, subjectName, object, objectName, detail, origin, now);
        ld.add(r);
        for (NamedFighter c : knowers) learn(c, r);
        return r;
    }

    /** {@code c} now knows {@code r}. Returns whether it was news to it. */
    public static boolean learn(NamedFighter c, Rumor r) {
        if (c.knowledge.contains(r.id())) return false;
        c.knowledge.addLast(r.id());
        while (c.knowledge.size() > KNOWLEDGE) c.knowledge.removeFirst();
        return true;
    }

    /** A character's memory, as news: the notable ones become rumors heard first in the nearest town. */
    static void fromMemory(NamedFighter c, Memory m) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !LivingConfig.enabled() || !LivingConfig.rumors()) return;
        LivingData ld = LivingData.get(server);
        String who = m.who() == null ? "" : name(c, m.who());
        Rumor.Kind kind = switch (m.kind()) {
            case WON -> m.who() != null && c.players.containsKey(m.who()) ? Rumor.Kind.BEAT_PLAYER : null;
            case LOST -> m.who() != null && c.players.containsKey(m.who()) ? Rumor.Kind.LOST_TO_PLAYER : null;
            case SPARED_BY -> Rumor.Kind.HUMILIATED;
            case SURVIVED_DEATH -> Rumor.Kind.SURVIVED_DEATH;
            case UNLOCKED_FORM -> Rumor.Kind.NEW_FORM;
            case TOURNAMENT -> "won".equals(m.detail()) ? Rumor.Kind.TOURNAMENT_WIN : null;
            case NPC_WON -> Rumor.Kind.DUEL;
            case RETREATED -> Rumor.Kind.RETREATED;
            case RANK_UP -> Rumor.Kind.STRONG_FIGHTER;
            case DIED -> c.resident() ? Rumor.Kind.MOURNING : Rumor.Kind.FELL;
            default -> null;
        };
        if (m.kind() == Memory.Kind.LOST && m.who() != null && c.players.containsKey(m.who())) {
            ld.addFame(m.who(), 5 + c.rank().ordinal() * 5);                               // beating a known fighter makes your name
            Fame.check(server, ld, m.who(), who, m.tick());
        }
        if (kind == null || c.resident() && kind == Rumor.Kind.RETREATED) return;
        BlockPos at = BlockPos.of(c.pos);
        Settlement near = c.settlement != null ? ld.settlement(c.settlement) : ld.nearest(c.dimension, at, NEAR);
        String detail = kind == Rumor.Kind.NEW_FORM ? m.detail() : kind == Rumor.Kind.STRONG_FIGHTER ? c.rank().name().toLowerCase(Locale.ROOT) : "";
        UUID object = kind == Rumor.Kind.DUEL ? m.who() : kind == Rumor.Kind.STRONG_FIGHTER || kind == Rumor.Kind.NEW_FORM ? null : m.who();
        add(ld, kind, c.id, c.name, object, object == null ? "" : who, detail, near == null ? null : near.id, m.tick(),
                near == null ? List.of() : Residents.of(near));
    }

    /** The name a character knows someone by (a player's, or another character's). */
    static String name(NamedFighter c, UUID id) {
        Bond b = c.players.get(id);
        if (b != null && !b.name.isEmpty()) return b.name;
        Bond o = c.characters.get(id);
        if (o != null && !o.name.isEmpty()) return o.name;
        NemesisData d = Nemesis.data();
        NamedFighter x = d == null ? null : d.get(id);
        return x == null ? "someone" : x.name;
    }

    // ------------------------------------------------------------------ spreading

    /** Whether a trade brings this kind of news (merchants hear everything; guards of threats; masters of fighters). */
    static boolean hears(Occupation o, Rumor.Kind k) {
        return switch (o) {
            case MERCHANT, TRAVELER -> true;
            case GUARD -> k == Rumor.Kind.BEAT_PLAYER || k == Rumor.Kind.TOWN_ATTACKED || k == Rumor.Kind.DUEL || k == Rumor.Kind.SURVIVED_DEATH
                    || k == Rumor.Kind.FELL;
            case MASTER, STUDENT -> k == Rumor.Kind.NEW_FORM || k == Rumor.Kind.TOURNAMENT_WIN || k == Rumor.Kind.STRONG_FIGHTER
                    || k == Rumor.Kind.TRAINING_HARD || k == Rumor.Kind.DUEL || k == Rumor.Kind.PLAYER_FAMOUS || k == Rumor.Kind.LOST_TO_PLAYER;
            case SCIENTIST -> k == Rumor.Kind.NEW_FORM || k == Rumor.Kind.PLAYER_FAMOUS || k == Rumor.Kind.STRONG_FIGHTER || k == Rumor.Kind.TOWN_ATTACKED;   // curious about power
            case COOK, CITIZEN, FARMER -> k == Rumor.Kind.PLAYER_FAMOUS || k == Rumor.Kind.MOURNING || k == Rumor.Kind.NEWCOMER || k == Rumor.Kind.TOWN_ATTACKED;
        };
    }

    /**
     * Once a day per town: news from elsewhere arrives with those whose trade brings it, two pieces at most (newest
     * first, only what nobody in town knows yet, nothing older than a week).
     */
    static void daily(LivingData ld, long now) {
        for (Settlement s : ld.settlements()) {
            List<NamedFighter> people = Residents.of(s);
            if (people.isEmpty()) continue;
            List<Rumor> fresh = new ArrayList<>(ld.rumors());
            fresh.sort((a, b) -> Long.compare(b.tick(), a.tick()));
            int told = 0;
            for (Rumor r : fresh) {
                if (told >= 2 || now - r.tick() > 7 * 24000) break;
            if (r.tick() > now) continue;
                if (s.id.equals(r.origin()) || people.stream().anyMatch(c -> c.knowledge.contains(r.id()))) continue;
                boolean any = false;
                for (NamedFighter c : people) if (hears(Occupation.of(c), r.kind())) any |= learn(c, r);
                if (any) told++;
            }
        }
    }

    /** Two residents chat: each passes on one thing the other hasn't heard (the newest). Returns what {@code a} told. */
    public static Rumor exchange(LivingData ld, NamedFighter a, NamedFighter b) {
        Rumor told = newest(ld, a, b);
        Rumor back = newest(ld, b, a);
        if (told != null) learn(b, told);
        if (back != null) learn(a, back);
        return told;
    }

    /** The newest thing {@code teller} knows that {@code listener} doesn't. */
    static Rumor newest(LivingData ld, NamedFighter teller, NamedFighter listener) {
        var it = teller.knowledge.descendingIterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (listener != null && listener.knowledge.contains(id)) continue;
            Rumor r = ld.rumor(id);
            if (r != null) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------ telling

    /** The line a resident says for a rumor (to {@code listener}, by name; "_you" lines when it is about them). */
    public static String line(Rumor r, UUID listener, String listenerName, LivingData ld, long now, long seed) {
        boolean you = listener != null && (listener.equals(r.subject()) || listener.equals(r.object()));
        List<String> pool = you ? lines.getOrDefault(r.kind().id() + "_you", lines.get(r.kind().id())) : lines.get(r.kind().id());
        if (pool == null || pool.isEmpty()) return r.subjectName() + "... you hear things.";
        String s = pool.get(AiRandom.pick(seed, now / 200, 51, pool.size()));
        Settlement at = ld.settlement(r.origin());
        long days = Math.max(0, (now - r.tick()) / 24000);
        return s.replace("{subject}", r.subjectName()).replace("{object}", r.objectName()).replace("{player}", listenerName)
                .replace("{detail}", pretty(r.detail())).replace("{place}", at == null ? "out in the wilds" : "near " + at.name)
                .replace("{when}", days == 0 ? "today" : days == 1 ? "yesterday" : days < 5 ? "a few days ago" : "a while back");
    }

    /** "super_saiyan_2" as "Super Saiyan 2". */
    static String pretty(String id) {
        if (id == null || id.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String w : id.split("[_:]")) {
            if (w.isEmpty() || w.equals("dbzenith")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }
}
