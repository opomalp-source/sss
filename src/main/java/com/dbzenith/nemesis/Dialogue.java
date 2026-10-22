package com.dbzenith.nemesis;

import com.dbzenith.DBZenith;
import com.dbzenith.npc.ai.AiRandom;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What named characters say, from data packs ({@code data/<ns>/nemesis_dialogue/*.json}, reloaded with /reload):
 * each file holds {@code "lines"}, entries with an {@code id}, the situation they are {@code on} (see {@link On}),
 * optional conditions {@code when} and the variations to {@code say}. A line is only picked when its conditions hold
 * for the recorded facts (fights, who won last, whether the player spared it, the form the player is in, the form it
 * unlocked since...), so a character only refers to what really happened. Among the lines that fit, the more specific
 * are likelier; the last eight said to that player are skipped. Placeholders: {player} {npc} {wins} {losses} {fights}
 * {days} {form} {npcform} {technique} {newtech} {rank} {rival}. See docs/NEMESIS.md for the full format.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dialogue {
    /** When a line is said. */
    public enum On {
        GREET_FIRST, GREET_AGAIN, RETURN_REVENGE, RETURN_RIVAL, FOUND_TRAINING, AMBUSH, INTERVENE, ALLY_ARRIVES,
        NPC_WINS, NPC_LOSES, NPC_SPARES, FLEE, PLAYER_TRANSFORMS, NPC_TRANSFORMS, TALK, SPAR_END;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One entry: when it applies and what it can say. */
    record Entry(String id, On on, JsonObject when, List<String> say, int specificity) {}

    /** The facts a line may test and quote. */
    public static final class Context {
        public NamedFighter npc;
        public Bond bond;
        public String player = "", playerForm = "base", npcForm = "", newTechnique = "", rival = "";
        public boolean stronger, newForm, newTech;
        public double days = -1;
    }

    private static Map<On, List<Entry>> entries = Map.of();

    private Dialogue() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "nemesis_dialogue") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                load(files.values());
            }
        });
    }

    /** Reads dialogue files (public for tests). */
    public static int load(Iterable<JsonElement> files) {
        Map<On, List<Entry>> out = new java.util.EnumMap<>(On.class);
        int n = 0;
        for (JsonElement f : files) {
            if (!f.isJsonObject()) continue;
            for (JsonElement el : GsonHelper.getAsJsonArray(f.getAsJsonObject(), "lines", new JsonArray())) {
                JsonObject e = el.getAsJsonObject();
                On on;
                try {
                    on = On.valueOf(GsonHelper.getAsString(e, "on").toUpperCase(Locale.ROOT));
                } catch (RuntimeException ex) {
                    continue;
                }
                List<String> say = new ArrayList<>();
                for (JsonElement s : GsonHelper.getAsJsonArray(e, "say", new JsonArray())) say.add(s.getAsString());
                if (say.isEmpty()) continue;
                JsonObject when = GsonHelper.getAsJsonObject(e, "when", new JsonObject());
                out.computeIfAbsent(on, k -> new ArrayList<>()).add(new Entry(GsonHelper.getAsString(e, "id", on.id() + n), on, when, say, when.size()));
                n++;
            }
        }
        entries = out;
        com.mojang.logging.LogUtils.getLogger().info("Nemesis: {} dialogue entries", n);
        return n;
    }

    public static boolean loaded() {
        return !entries.isEmpty();
    }

    /** Whether an entry's conditions hold. */
    static boolean fits(JsonObject w, Context c) {
        Bond b = c.bond;
        NamedFighter n = c.npc;
        int fights = b == null ? 0 : b.wins + b.losses + b.draws;
        for (String key : w.keySet()) {
            JsonElement v = w.get(key);
            boolean ok = switch (key) {
                case "relation" -> b != null && anyOf(v, b.state.id());
                case "personality" -> anyOf(v, n.personality.id());
                case "traits" -> {
                    boolean any = false;
                    for (Trait t : n.traits) any |= anyOf(v, t.id());
                    yield any;
                }
                case "race" -> anyOf(v, n.race);
                case "rank" -> anyOf(v, n.rank().id());
                case "fightsMin" -> fights >= v.getAsInt();
                case "fightsMax" -> fights <= v.getAsInt();
                case "npcWinsMin" -> b != null && b.wins >= v.getAsInt();
                case "npcLossesMin" -> b != null && b.losses >= v.getAsInt();
                case "lastResult" -> b != null && v.getAsString().equals(b.lastResult > 0 ? "npc_won" : b.lastResult < 0 ? "npc_lost" : "none");
                case "spared" -> b != null && (b.sparedThem > 0) == v.getAsBoolean();
                case "sparedPlayer" -> b != null && (b.sparedByThem > 0) == v.getAsBoolean();
                case "escaped" -> b != null && (b.escapes > 0) == v.getAsBoolean();
                case "allyDefeated" -> b != null && (b.alliesLost > 0) == v.getAsBoolean();
                case "playerForm" -> v.getAsString().equals("any") ? !"base".equals(c.playerForm) : v.getAsString().equals(c.playerForm);
                case "newForm" -> c.newForm == v.getAsBoolean();
                case "newTechnique" -> c.newTech == v.getAsBoolean();
                case "stronger" -> c.stronger == v.getAsBoolean();
                case "daysMin" -> c.days >= v.getAsDouble();
                case "hasRival" -> !c.rival.isEmpty() == v.getAsBoolean();
                default -> true;                                                    // unknown keys are ignored (newer files)
            };
            if (!ok) return false;
        }
        return true;
    }

    private static boolean anyOf(JsonElement v, String s) {
        if (v.isJsonArray()) {
            for (JsonElement e : v.getAsJsonArray()) if (e.getAsString().equalsIgnoreCase(s)) return true;
            return false;
        }
        return v.getAsString().equalsIgnoreCase(s);
    }

    /** A line for this situation, or null. Picks deterministically (by the character, the player and the count). */
    public static Line pick(On on, Context c, long salt) {
        List<Entry> list = entries.getOrDefault(on, List.of());
        Set<String> recent = c.bond == null ? Set.of() : new HashSet<>(c.bond.recentLines);
        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Entry e : list) {
            if (!fits(e.when, c)) continue;
            for (int i = 0; i < e.say.size(); i++) {
                String id = e.id + "#" + i;
                if (recent.contains(id)) continue;
                ids.add(id);
                texts.add(e.say.get(i));
                weights.add(1.0 + 2.0 * e.specificity);
            }
        }
        if (ids.isEmpty()) return null;
        double total = 0;
        for (double w : weights) total += w;
        double r = AiRandom.unit(c.npc.id.getMostSignificantBits() ^ salt, c.bond == null ? 0 : c.bond.encounters * 31L + c.bond.recentLines.size(), on.ordinal()) * total;
        for (int i = 0; i < ids.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return new Line(ids.get(i), texts.get(i));
        }
        return new Line(ids.get(ids.size() - 1), texts.get(texts.size() - 1));
    }

    /** A chosen line: its id (so it isn't repeated soon) and its template. */
    public record Line(String id, String text) {}

    /** Fills a template's placeholders (form, technique and rank names translated on each client). */
    public static MutableComponent format(String text, Context c) {
        MutableComponent out = Component.empty();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf('{', i);
            if (open < 0) {
                out.append(text.substring(i));
                break;
            }
            int close = text.indexOf('}', open);
            if (close < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text.substring(i, open));
            out.append(value(text.substring(open + 1, close), c));
            i = close + 1;
        }
        return out;
    }

    static Component value(String key, Context c) {
        Bond b = c.bond;
        return switch (key) {
            case "player" -> Component.literal(c.player);
            case "npc" -> Component.literal(c.npc.name);
            case "wins" -> Component.literal(String.valueOf(b == null ? 0 : b.wins));
            case "losses" -> Component.literal(String.valueOf(b == null ? 0 : b.losses));
            case "fights" -> Component.literal(String.valueOf(b == null ? 0 : b.wins + b.losses + b.draws));
            case "days" -> Component.literal(String.valueOf(Math.max(1, Math.round(c.days))));
            case "form" -> Component.translatable("form.dbzenith." + c.playerForm);
            case "npcform" -> Component.translatable("form.dbzenith." + (c.npcForm.isEmpty() ? "base" : c.npcForm));
            case "technique" -> techniqueName(b == null ? "" : b.favouriteTechnique());
            case "newtech" -> techniqueName(c.newTechnique);
            case "rank" -> Component.translatable(c.npc.rank().translationKey());
            case "rival" -> Component.literal(c.rival);
            default -> Component.literal("{" + key + "}");
        };
    }

    static Component techniqueName(String id) {
        var t = com.dbzenith.skill.Techniques.byId(id);
        if (t != null) return t.name();
        if (id.startsWith("ki:")) return Component.translatable("nemesis.dbzenith.ki_blasts");
        return Component.translatable("nemesis.dbzenith.your_tricks");
    }

    /** Says a line: to the player it is meant for, and everyone else within earshot (32 blocks). */
    public static boolean say(Entity speaker, On on, Context c, ServerPlayer to) {
        if (!NemesisConfig.dialogue() || speaker == null) return false;
        Line line = pick(on, c, to == null ? 0 : to.getUUID().getLeastSignificantBits());
        if (line == null) return false;
        if (c.bond != null) c.bond.said(line.id());
        Component msg = Component.literal(c.npc.fullName()).withStyle(ChatFormatting.GOLD).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(format(line.text(), c).withStyle(ChatFormatting.WHITE));
        if (speaker.level() instanceof net.minecraft.server.level.ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                if (p == to || p.distanceToSqr(speaker) < 32 * 32) p.sendSystemMessage(msg);
            }
        }
        return true;
    }
}
