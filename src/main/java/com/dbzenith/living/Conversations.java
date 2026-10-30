package com.dbzenith.living;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.nemesis.Trait;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.stats.StatCalculator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Branching conversations with townsfolk (docs/LIVING_WORLD.md), from data packs
 * ({@code data/<ns>/conversations/*.json}, reloaded with /reload). A file holds {@code "nodes"}: each node has what the
 * resident {@code say}s (variants, each with optional conditions {@code when}; the most specific that holds is
 * likeliest) and the player's {@code options} (each with its {@code text}, conditions, actions {@code do} and the node
 * it leads to, {@code next}; none ends the conversation). Nodes with the same id in a later file replace earlier ones;
 * {@code "extend": {"node": [options...]}} adds options to a node instead. {@code "barks"} are things residents say
 * unprompted (greeting a famous player, gossip overheard), keyed by situation.
 * <p>
 * Everything is decided on the server: the client is sent the line and the options it may pick, and a choice is
 * checked again (the session, the resident still there and in reach, the option offered, its conditions still true)
 * before anything happens. What was chosen is remembered (bond flags, affinity, memories).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Conversations {
    record Line(JsonObject when, List<String> text, int specificity) {}

    record Option(String id, String text, JsonObject when, List<String> actions, String next) {}

    record Node(String id, List<Line> lines, List<Option> options) {}

    /** One player's conversation: with which body, where it stands, what they may pick. */
    static final class Session {
        final int entity;
        final UUID npc;
        String node;
        List<Option> offered = List.of();
        long touched;
        int steps;

        Session(int entity, UUID npc, String node, long now) {
            this.entity = entity;
            this.npc = npc;
            this.node = node;
            this.touched = now;
        }
    }

    static final int MAX_OPTIONS = 12, MAX_STEPS = 64;
    static final double REACH = 8;
    private static Map<String, Node> nodes = new HashMap<>();
    private static Map<String, List<Line>> barks = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Conversations() {}

    // ------------------------------------------------------------------ loading

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "conversations") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                Map<String, Node> n = new LinkedHashMap<>();
                Map<String, List<Line>> b = new HashMap<>();
                Map<String, List<Option>> extend = new HashMap<>();
                Map<String, List<Line>> extendSay = new HashMap<>();                         // more lines for another file's nodes (Namek's, v0.166.0)
                List<ResourceLocation> keys = new ArrayList<>(files.keySet());
                keys.sort(java.util.Comparator.comparing(ResourceLocation::toString));
                for (ResourceLocation key : keys) {
                    JsonElement f = files.get(key);
                    if (!f.isJsonObject()) continue;
                    try {
                        JsonObject o = f.getAsJsonObject();
                        for (var e : GsonHelper.getAsJsonObject(o, "nodes", new JsonObject()).entrySet()) n.put(e.getKey(), node(e.getKey(), e.getValue().getAsJsonObject()));
                        for (var e : GsonHelper.getAsJsonObject(o, "extend", new JsonObject()).entrySet())
                            for (JsonElement x : e.getValue().getAsJsonArray()) extend.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(option(x.getAsJsonObject()));
                        for (var e : GsonHelper.getAsJsonObject(o, "extend_say", new JsonObject()).entrySet())
                            extendSay.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(lines(e.getValue()));
                        for (var e : GsonHelper.getAsJsonObject(o, "barks", new JsonObject()).entrySet())
                            b.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(lines(e.getValue()));
                    } catch (RuntimeException ex) {
                        com.mojang.logging.LogUtils.getLogger().warn("Bad conversation file {}: {}", key, ex.toString());
                    }
                }
                extend.forEach((id, opts) -> {
                    Node x = n.get(id);
                    if (x == null) return;
                    List<Option> all = new ArrayList<>(x.options());
                    all.addAll(opts);
                    n.put(id, new Node(id, x.lines(), all));
                });
                extendSay.forEach((id, more) -> {
                    Node x = n.get(id);
                    if (x == null) return;
                    List<Line> all = new ArrayList<>(x.lines());
                    all.addAll(more);
                    n.put(id, new Node(id, all, x.options()));
                });
                nodes = n;
                barks = b;
                com.mojang.logging.LogUtils.getLogger().info("Living world: {} conversation nodes, {} bark kinds", n.size(), b.size());
            }
        });
    }

    static Node node(String id, JsonObject j) {
        List<Option> opts = new ArrayList<>();
        for (JsonElement x : GsonHelper.getAsJsonArray(j, "options", new JsonArray())) opts.add(option(x.getAsJsonObject()));
        return new Node(id, lines(j.get("say")), opts);
    }

    static List<Line> lines(JsonElement say) {
        List<Line> out = new ArrayList<>();
        if (say == null) return out;
        if (say.isJsonPrimitive()) {
            out.add(new Line(new JsonObject(), List.of(say.getAsString()), 0));
            return out;
        }
        for (JsonElement e : say.getAsJsonArray()) {
            if (e.isJsonPrimitive()) {
                out.add(new Line(new JsonObject(), List.of(e.getAsString()), 0));
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            JsonObject when = GsonHelper.getAsJsonObject(o, "when", new JsonObject());
            List<String> text = new ArrayList<>();
            JsonElement t = o.get("text");
            if (t != null && t.isJsonArray()) for (JsonElement s : t.getAsJsonArray()) text.add(s.getAsString());
            else if (t != null) text.add(t.getAsString());
            out.add(new Line(when, text, weight(when)));
        }
        return out;
    }

    /** How specific a line is: situations that must win (wanted, an alert, a first meeting, being woken) count most. */
    static int weight(JsonObject when) {
        int w = 0;
        for (String k : when.keySet()) w += switch (k) {
            case "wanted", "alarm", "activity" -> 4;
            case "first" -> 3;
            default -> 1;
        };
        return w;
    }

    static Option option(JsonObject o) {
        List<String> actions = new ArrayList<>();
        JsonElement d = o.get("do");
        if (d != null && d.isJsonArray()) for (JsonElement s : d.getAsJsonArray()) actions.add(s.getAsString());
        else if (d != null) actions.add(d.getAsString());
        return new Option(GsonHelper.getAsString(o, "id", ""), GsonHelper.getAsString(o, "text"), GsonHelper.getAsJsonObject(o, "when", new JsonObject()),
                actions, o.has("next") ? GsonHelper.getAsString(o, "next") : null);
    }

    public static boolean loaded() {
        return !nodes.isEmpty();
    }

    /** Tests: whether a node exists. */
    public static boolean has(String node) {
        return nodes.containsKey(node);
    }

    // ------------------------------------------------------------------ the facts a line may test

    /** Everything conditions test and placeholders quote, for one resident and one player. */
    public static final class Ctx {
        public NamedFighter npc;
        public Bond bond;
        public ServerPlayer player;
        public Resident body;
        public Settlement town;
        public LivingData living;
        public Mood mood = Mood.RELAXED;
        public long now;
        public int fame;
        public boolean wanted, first;
        /** The rumor it would tell this player next (newest it knows that it hasn't told them), or null. */
        public Rumor rumor;
        /** Text an action added (a rumor told), shown after the next line. */
        public String extra = "";
    }

    static Ctx context(Resident r, NamedFighter c, Bond b, ServerPlayer p) {
        Ctx x = new Ctx();
        x.npc = c;
        x.bond = b;
        x.player = p;
        x.body = r;
        x.living = LivingData.get(p.server);
        x.town = x.living.settlement(c.settlement);
        x.mood = Mood.of(c, r.level());
        x.now = r.level().getGameTime();
        x.fame = x.living.fame(p.getUUID());
        x.wanted = x.town != null && x.town.wanted.contains(p.getUUID());
        x.rumor = untold(x);
        return x;
    }

    static Rumor untold(Ctx x) {
        var it = x.npc.knowledge.descendingIterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (x.bond.told.contains(id.toString())) continue;
            Rumor r = x.living.rumor(id);
            if (r != null) return r;
        }
        return null;
    }

    /** Whether every condition holds. Unknown keys fail (a typo never makes a line fit everywhere). */
    static boolean holds(JsonObject when, Ctx x) {
        for (var e : when.entrySet()) if (!test(e.getKey(), e.getValue(), x)) return false;
        return true;
    }

    static boolean test(String key, JsonElement v, Ctx x) {
        NamedFighter c = x.npc;
        Bond b = x.bond;
        return switch (key) {
            case "occupation" -> any(v, Occupation.of(c).id());
            case "race" -> any(v, c.race);                                                      // Namekians speak as Namekians (v0.166.0)
            case "style" -> x.town != null && any(v, x.town.style);
            case "mood" -> any(v, x.mood.id());
            case "trait" -> {
                for (Trait t : c.traits) if (any(v, t.id())) yield true;
                yield false;
            }
            case "not_trait" -> {
                for (Trait t : c.traits) if (any(v, t.id())) yield false;
                yield true;
            }
            case "personality" -> any(v, c.personality.id());
            case "goal" -> any(v, c.goal.name().toLowerCase(Locale.ROOT));
            case "relation" -> any(v, b.state.name().toLowerCase(Locale.ROOT));
            case "affinity_min" -> b.affinity >= v.getAsDouble();
            case "affinity_max" -> b.affinity <= v.getAsDouble();
            case "first" -> x.first == v.getAsBoolean();
            case "talks_min" -> b.talks >= v.getAsInt();
            case "fame_min" -> x.fame >= v.getAsInt();
            case "fame_max" -> x.fame <= v.getAsInt();
            case "hour_min" -> Schedules.hour(x.body.level()) >= v.getAsInt();
            case "hour_max" -> Schedules.hour(x.body.level()) < v.getAsInt();
            case "alarm" -> (x.town != null && x.town.alarmed(x.now)) == v.getAsBoolean();
            case "wanted" -> x.wanted == v.getAsBoolean();
            case "has_rumor" -> (x.rumor != null) == v.getAsBoolean();
            case "flag" -> b.flags.contains(v.getAsString());
            case "not_flag" -> !b.flags.contains(v.getAsString());
            case "fighter" -> Occupation.of(c).fighter == v.getAsBoolean();
            case "stronger" -> (c.level > playerLevel(x.player)) == v.getAsBoolean();
            case "fought" -> (b.wins + b.losses + b.draws > 0) == v.getAsBoolean();
            case "activity" -> any(v, x.body.activity.id());
            case "has_town" -> (x.town != null) == v.getAsBoolean();
            case "companion" -> Companions.of(c, x.player.getUUID()) == v.getAsBoolean();
            case "companion_mode" -> Companions.of(c, x.player.getUUID()) && any(v, Companions.mode(c).id());
            case "refusal" -> any(v, Companions.refusal(c, b, x.player, x.town, x.now));
            case "lesson" -> any(v, Lessons.step(b).name().toLowerCase(Locale.ROOT));
            case "lesson_refusal" -> any(v, Lessons.refusal(c, b, x.player, x.town));
            case "errand" -> any(v, errandState(x));
            case "traveler" -> (Occupation.of(c) == Occupation.TRAVELER) == v.getAsBoolean();
            default -> false;
        };
    }

    static boolean any(JsonElement v, String value) {
        if (v.isJsonArray()) {
            for (JsonElement e : v.getAsJsonArray()) if (e.getAsString().equalsIgnoreCase(value)) return true;
            return false;
        }
        return v.getAsString().equalsIgnoreCase(value);
    }

    static int playerLevel(ServerPlayer p) {
        return KiFighter.levelFor(ModCapabilities.get(p).map(StatCalculator::fullPower).orElse(0L));
    }

    /** The line that fits, the most specific likeliest (deterministic: who, what, the moment). */
    static String pick(List<Line> lines, Ctx x, long salt) {
        List<Line> fit = new ArrayList<>();
        int best = -1;
        for (Line l : lines) if (!l.text().isEmpty() && holds(l.when(), x)) {
            fit.add(l);
            best = Math.max(best, l.specificity());
        }
        if (fit.isEmpty()) return null;
        int b = best;
        List<Line> top = fit.stream().filter(l -> l.specificity() >= b).toList();           // the most specific that fit
        Line l = top.get(AiRandom.pick(x.npc.id.getMostSignificantBits(), x.now / 100 + salt, 52, top.size()));
        return l.text().get(AiRandom.pick(x.npc.id.getLeastSignificantBits(), x.now / 100 + salt, 53, l.text().size()));
    }

    /** Fills a line's placeholders. */
    static String fill(String s, Ctx x) {
        NamedFighter c = x.npc;
        String out = s.replace("{player}", x.player.getGameProfile().getName()).replace("{npc}", c.name)
                .replace("{occupation}", Occupation.of(c).id()).replace("{town}", x.town == null ? "these parts" : x.town.name)
                .replace("{mood}", x.mood.id()).replace("{fame}", String.valueOf(x.fame)).replace("{level}", String.valueOf(c.level))
                .replace("{hour}", String.valueOf(Schedules.hour(x.body.level())));
        if (out.contains("{master}")) out = out.replace("{master}", master(x));
        if (out.contains("{rival}")) out = out.replace("{rival}", rival(c));
        if (out.contains("{rumor}")) out = out.replace("{rumor}", tell(x));
        if (out.contains("{lesson}")) out = out.replace("{lesson}", Lessons.lessonName(c, x.bond, x.player));
        if (out.contains("{errand}")) out = out.replace("{errand}", errandText(x));
        if (out.contains("{errand_progress}")) out = out.replace("{errand_progress}", errandProgress(x));
        if (out.contains("{reward}")) out = out.replace("{reward}", errandReward(x));
        int i;
        while ((i = out.indexOf("{where:")) >= 0) {
            int j = out.indexOf('}', i);
            if (j < 0) break;
            out = out.substring(0, i) + where(x, out.substring(i + 7, j)) + out.substring(j + 1);
        }
        return out;
    }

    static String master(Ctx x) {
        if (x.town == null) return "a master";
        for (NamedFighter o : Residents.of(x.town)) if (Occupation.of(o) == Occupation.MASTER) return o.name;
        return "nobody, these days";
    }

    static String rival(NamedFighter c) {
        Bond top = null;
        for (Bond b : c.characters.values()) if (top == null || b.rivalry > top.rivalry) top = b;
        return top == null || top.rivalry < 15 ? "nobody in particular" : top.name;
    }

    /** Where this player stands on this resident's errand: offer, mine (under way), ready (to turn in), full (too many), none. */
    static String errandState(Ctx x) {
        Errand e = Errands.takenFrom(x.living, x.npc.id, x.player.getUUID());
        if (e != null) {
            Errands.update(x.living, e, x.player);
            return e.done() ? "ready" : "mine";
        }
        if (Errands.offeredBy(x.living, x.npc.id) == null) return "none";
        return Errands.of(x.living, x.player.getUUID()).size() >= 3 ? "full" : "offer";
    }

    static Errand errand(Ctx x) {
        Errand e = Errands.takenFrom(x.living, x.npc.id, x.player.getUUID());
        return e != null ? e : Errands.offeredBy(x.living, x.npc.id);
    }

    /** The errand in the resident's words, from the data's "errand_<kind>" barks (else a plain summary). */
    static String errandText(Ctx x) {
        Errand e = errand(x);
        if (e == null) return "";
        String key = "errand_" + e.kind.id() + (e.kind == Errand.Kind.DEFEAT_THREAT && e.target == null ? "_monsters" : "");
        List<Line> l = barks.get(key);
        String s = l == null ? null : pick(l, x, e.kind.ordinal());
        if (s == null) return Errands.summary(e);
        BlockPos at = BlockPos.of(e.place);
        return fill(s.replace("{errand}", "").replace("{target}", e.targetName).replace("{count}", String.valueOf(e.count)).replace("{item}", Errands.itemName(e.item))
                .replace("{town2}", e.kind == Errand.Kind.DELIVER && !e.item.isEmpty() ? " in " + e.item : "").replace("{x}", String.valueOf(at.getX())).replace("{z}", String.valueOf(at.getZ())), x);
    }

    static String errandProgress(Ctx x) {
        Errand e = errand(x);
        return e == null ? "" : Math.min(e.progress, e.count) + "/" + e.count;
    }

    static String errandReward(Ctx x) {
        Errand e = errand(x);
        return e == null ? "" : String.valueOf(e.rewardTp);
    }

    /** Tells the player the next rumor (and remembers it was told them). */
    static String tell(Ctx x) {
        Rumor r = x.rumor;
        if (r == null) return "Nothing worth repeating, I'm afraid.";
        x.bond.told.addLast(r.id().toString());
        while (x.bond.told.size() > 24) x.bond.told.removeFirst();
        x.rumor = untold(x);
        return Rumors.line(r, x.player.getUUID(), x.player.getGameProfile().getName(), x.living, x.now, x.npc.id.getMostSignificantBits());
    }

    /** Directions to the town's nearest place of a kind: "north-east, about 20 blocks". */
    static String where(Ctx x, String kind) {
        if (x.town == null) return "I couldn't tell you";
        Poi.Kind k;
        try {
            k = Poi.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "I couldn't tell you";
        }
        BlockPos from = x.body.blockPosition(), best = null;
        for (Poi p : x.town.of(k)) if (best == null || p.pos().distSqr(from) < best.distSqr(from)) best = p.pos();
        if (best == null) return "nowhere in " + x.town.name;
        int dx = best.getX() - from.getX(), dz = best.getZ() - from.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (dist < 6) return "right here";
        String ns = dz < -dist / 3 ? "north" : dz > dist / 3 ? "south" : "", ew = dx > dist / 3 ? "east" : dx < -dist / 3 ? "west" : "";
        return (ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew) + ", about " + (dist / 5 * 5 + 5) + " blocks";
    }

    // ------------------------------------------------------------------ the conversation

    /** A player talks to a resident: a conversation opens at its root (falls back to a Nemesis line with no data). */
    public static void open(Resident r, ServerPlayer p) {
        NamedFighter c = r.character();
        if (c == null) return;
        long now = r.level().getGameTime();
        if (!loaded() || !nodes.containsKey("root") || !LivingConfig.enabled()) {
            Nemesis.talk(r, p);
            return;
        }
        Session old = SESSIONS.get(p.getUUID());
        if (old != null && old.entity == r.getId() && now - old.touched < 10) return;      // a double click
        Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
        NemesisData d = Nemesis.data();
        d.index(c, p.getUUID());
        boolean first = b.talks == 0 && b.encounters == 0;
        if (b.encounters == 0) {
            b.encounters = 1;
            b.lastEncounter = now;
            c.remember(new Memory(Memory.Kind.MET, now, p.getUUID(), ""));
        }
        b.talks++;
        close(p, false);
        Errands.onTalk(LivingData.get(p.server), c, p);                                 // a message for them, a quarrel to settle
        Session s = new Session(r.getId(), c.id, "root", now);
        SESSIONS.put(p.getUUID(), s);
        r.talkingTo = p.getUUID();
        r.getNavigation().stop();
        if (r.isSleeping()) r.stopSleeping();
        Ctx x = context(r, c, b, p);
        x.first = first;
        show(p, r, s, x);
        d.setDirty();
    }

    /** Sends the player the node's line and the options that apply. */
    static void show(ServerPlayer p, Resident r, Session s, Ctx x) {
        Node n = nodes.get(s.node);
        if (n == null) {
            close(p, true);
            return;
        }
        String line = pick(n.lines(), x, s.steps);
        String text = line == null ? "..." : fill(line, x);
        if (!x.extra.isEmpty()) text = text + "\n" + x.extra;
        List<Option> offered = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Option o : n.options()) {
            if (offered.size() >= MAX_OPTIONS) break;
            if (!holds(o.when(), x)) continue;
            offered.add(o);
            labels.add(fill(o.text(), x));
        }
        if (offered.isEmpty()) {                                                         // always a way out
            offered.add(new Option("bye", "Goodbye.", new JsonObject(), List.of(), null));
            labels.add("Goodbye.");
        }
        s.offered = offered;
        s.touched = x.now;
        String subtitle = Occupation.of(x.npc).id() + "|" + x.mood.id() + "|" + x.bond.state.name().toLowerCase(Locale.ROOT) + "|" + (x.town == null ? "" : x.town.name);
        com.dbzenith.network.ModNetwork.sendTo(p, new ConversationPackets.Show(r.getId(), x.npc.name, subtitle, text, labels));
    }

    /** The player picked option {@code index}: checked again, then done. */
    public static void choose(ServerPlayer p, int entity, int index) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.entity != entity || index < 0 || index >= s.offered.size()) return;
        if (!(p.level().getEntity(entity) instanceof Resident r) || !r.isAlive() || r.distanceTo(p) > REACH || r.getTarget() != null
                || r.nemesisId() == null || !r.nemesisId().equals(s.npc)) {
            close(p, true);
            return;
        }
        NamedFighter c = r.character();
        if (c == null) {
            close(p, true);
            return;
        }
        Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
        Ctx x = context(r, c, b, p);
        Option o = s.offered.get(index);
        if (!holds(o.when(), x) || ++s.steps > MAX_STEPS) {                               // no longer true: shown again as it is now
            show(p, r, s, x);
            return;
        }
        boolean end = o.next() == null || o.next().equals("end");
        for (String a : o.actions()) end |= act(a, o, x, s);
        Nemesis.data().setDirty();
        LivingData.get(p.server).setDirty();
        if (end) {
            if (!x.extra.isEmpty()) com.dbzenith.network.ModNetwork.sendTo(p, new ConversationPackets.Show(r.getId(), c.name, "", x.extra, List.of()));
            close(p, x.extra.isEmpty());
            return;
        }
        s.node = o.next();
        show(p, r, s, x);
    }

    /** One action of a chosen option. Returns true if it ends the conversation. */
    static boolean act(String a, Option o, Ctx x, Session s) {
        String[] kv = a.split(":", 2);
        String arg = kv.length > 1 ? kv[1] : "";
        NamedFighter c = x.npc;
        Bond b = x.bond;
        switch (kv[0]) {
            case "flag" -> b.flags.add(arg);
            case "unflag" -> b.flags.remove(arg);
            case "affinity" -> shift(c, b, x, parse(arg), 0);
            case "rivalry" -> shift(c, b, x, 0, parse(arg));
            case "mood" -> {
                Mood m = Mood.byId(arg);
                if (m != null) Mood.set(c, m, x.now, 2400);
            }
            case "tell_rumor" -> x.extra = tell(x);
            case "remember" -> c.remember(new Memory(Memory.Kind.TALKED, x.now, x.player.getUUID(), arg.isEmpty() ? o.id() : arg));
            case "spar" -> {
                if (x.body.aiMayFight() && !x.wanted && x.body.sparWith == null) {
                    x.body.talkingTo = null;
                    x.body.sparWith = x.player.getUUID();
                    x.body.setTarget(x.player);
                    return true;
                }
            }
            case "recruit" -> {
                if (Companions.refusal(c, b, x.player, x.town, x.now).equals("none")) Companions.join(c, x.player, x.now);
            }
            case "command" -> {
                Companions.Mode m = Companions.Mode.byId(arg);
                if (m != null && Companions.of(c, x.player.getUUID())) Companions.command(c, x.body, m);
            }
            case "dismiss" -> {
                if (Companions.of(c, x.player.getUUID())) Companions.leave(x.player.server, c, "living.dbzenith.companion.parted", x.now);
            }
            case "lesson_start" -> Lessons.start(x.body, c, b, x.player);
            case "lesson_test" -> {
                if (Lessons.test(x.body, c, b, x.player)) return true;
            }
            case "lesson_quit" -> Lessons.quit(b);
            case "errand_accept" -> Errands.accept(x.living, c, x.player, x.now);
            case "errand_turn_in" -> Errands.turnIn(x.living, c, x.player, x.now);
            case "errand_abandon" -> Errands.abandon(x.living, c, x.player);
            case "close" -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    static void shift(NamedFighter c, Bond b, Ctx x, double affinity, double rivalry) {
        b.shift(affinity, rivalry);
        com.dbzenith.nemesis.Relationship before = b.state;
        b.state = com.dbzenith.nemesis.Relationship.next(b.state, b.affinity, b.rivalry, b.encounters, b.spars);
        if (b.state != before) c.remember(new Memory(Memory.Kind.RELATION_CHANGED, x.now, x.player.getUUID(), b.state.name().toLowerCase(Locale.ROOT)));
    }

    static double parse(String s) {
        try {
            return Double.parseDouble(s.replace("+", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Ends a player's conversation ({@code tell}: let their screen know). */
    public static void close(ServerPlayer p, boolean tell) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        if (p.level().getEntity(s.entity) instanceof Resident r && p.getUUID().equals(r.talkingTo)) r.talkingTo = null;
        if (tell) com.dbzenith.network.ModNetwork.sendTo(p, new ConversationPackets.Show(s.entity, "", "", "", List.of()));
    }

    /** Conversations that walked off or went quiet end (each second). */
    static void tick(net.minecraft.server.MinecraftServer server, long now) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                SESSIONS.remove(id);
                continue;
            }
            boolean gone = !(p.level().getEntity(s.entity) instanceof Resident r) || !r.isAlive() || r.distanceTo(p) > REACH + 4 || r.getTarget() != null;
            if (gone || now - s.touched > 2400) close(p, true);
        }
    }

    /** Whether the player is in a conversation with that body (tests). */
    public static boolean talking(ServerPlayer p, Resident r) {
        Session s = SESSIONS.get(p.getUUID());
        return s != null && s.entity == r.getId();
    }

    /** The body a player is talking with, or null. */
    public static Integer entity(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        return s == null ? null : s.entity;
    }

    /** The options the player is offered now (tests), by label id. */
    public static List<String> offered(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        List<String> out = new ArrayList<>();
        if (s != null) for (Option o : s.offered) out.add(o.id());
        return out;
    }

    /** A bark (unprompted line) for a situation, filled, or null if none fits. */
    static String bark(String kind, Resident r, NamedFighter c, ServerPlayer p) {
        List<Line> l = barks.get(kind);
        if (l == null || l.isEmpty()) return null;
        Bond b = c.players.get(p.getUUID());
        Ctx x = context(r, c, b == null ? new Bond() : b, p);                       // a stranger is no bond yet
        String s = pick(l, x, kind.hashCode());
        return s == null ? null : fill(s, x);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) close(p, false);
    }
}
