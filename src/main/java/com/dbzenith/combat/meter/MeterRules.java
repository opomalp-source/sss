package com.dbzenith.combat.meter;

import com.dbzenith.DBZenith;
import com.dbzenith.network.ModNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The PvP meters' numbers (CX-20), from {@code data/<ns>/combat/meters/*.json}: how each bar fills, drains and decays,
 * where Kaioken's stages and Ultra Instinct sit on the technique bar, the technique colours, and optional fixed places
 * for forms on the form bar (otherwise spread evenly). Files are merged in name order, {@code default} first, a later
 * file overriding the fields it gives. Clients get the merged JSON on joining and after a reload (they draw the studs).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeterRules {
    /** How one bar fills and empties. Points are bar percent (the bar is 0..100). */
    public record Bar(double perPercentDealt, double perPercentTaken, double perfectGuard, double vanish, double counter,
                      int comboEvery, double combo, double chargingPerSecond, double drainPerSecond, double drainPerTier,
                      double decayAfterSeconds, double decayPerSecond) {
        static Bar parse(JsonObject j) {
            return new Bar(d(j, "per_percent_dealt", 0), d(j, "per_percent_taken", 0), d(j, "perfect_guard", 0), d(j, "vanish", 0),
                    d(j, "counter", 0), GsonHelper.getAsInt(j, "combo_every", 0), d(j, "combo", 0), d(j, "charging_per_second", 0),
                    d(j, "drain_per_second", 0), d(j, "drain_per_tier", 0), d(j, "decay_after_seconds", 10), d(j, "decay_per_second", 0));
        }
    }

    /** A Kaioken stage on the technique bar. */
    public record KaiokenStep(int stage, double at) {}

    public record Rules(Bar form, Bar tech, Map<String, Double> formSteps, List<KaiokenStep> kaioken, double ultraInstinctAt,
                        int kaiokenColor, int signColor, int masteredColor) {}

    private static String json = "{}";
    private static Rules rules = parse(defaults());

    private MeterRules() {}

    public static Rules get() {
        return rules;
    }

    /** The merged JSON, as sent to clients. */
    public static String json() {
        return json;
    }

    /** Takes a merged JSON (the server's reload, or a client receiving it). */
    public static void apply(String merged) {
        try {
            JsonObject j = JsonParser.parseString(merged).getAsJsonObject();
            rules = parse(j);
            json = merged;
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("PvP meters: bad rules, keeping the old ones: {}", e.toString());
        }
    }

    static JsonObject defaults() {
        return new JsonObject();
    }

    static Rules parse(JsonObject j) {
        JsonObject f = GsonHelper.getAsJsonObject(j, "form", new JsonObject()), t = GsonHelper.getAsJsonObject(j, "technique", new JsonObject());
        Map<String, Double> steps = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(f, "steps", new JsonObject()).entrySet()) {
            steps.put(e.getKey(), e.getValue().getAsDouble());
        }
        List<KaiokenStep> kk = new ArrayList<>();
        if (t.has("kaioken")) {
            for (JsonElement e : t.getAsJsonArray("kaioken")) {
                JsonObject s = e.getAsJsonObject();
                kk.add(new KaiokenStep(GsonHelper.getAsInt(s, "stage"), GsonHelper.getAsDouble(s, "at")));
            }
        } else {
            kk.add(new KaiokenStep(2, 25));
            kk.add(new KaiokenStep(4, 40));
            kk.add(new KaiokenStep(10, 60));
            kk.add(new KaiokenStep(20, 80));
        }
        kk.sort((a, b) -> Integer.compare(a.stage(), b.stage()));
        JsonObject colors = GsonHelper.getAsJsonObject(t, "colors", new JsonObject());
        return new Rules(Bar.parse(f), Bar.parse(t), steps, List.copyOf(kk), d(t, "ultra_instinct_at", 100),
                color(colors, "kaioken", 0xFF2A1E), color(colors, "ultra_instinct_sign", 0xA9B1BD), color(colors, "ultra_instinct", 0xF4F8FF));
    }

    private static double d(JsonObject j, String key, double fallback) {
        return GsonHelper.getAsDouble(j, key, fallback);
    }

    private static int color(JsonObject j, String key, int fallback) {
        if (!j.has(key)) return fallback;
        String s = j.get(key).getAsString().replace("#", "");
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Merges b over a, object by object (b's fields win). */
    public static JsonObject merge(JsonObject a, JsonObject b) {
        JsonObject out = a.deepCopy();
        for (Map.Entry<String, JsonElement> e : b.entrySet()) {
            if (e.getValue().isJsonObject() && out.has(e.getKey()) && out.get(e.getKey()).isJsonObject()) {
                out.add(e.getKey(), merge(out.getAsJsonObject(e.getKey()), e.getValue().getAsJsonObject()));
            } else {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "combat/meters") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                TreeMap<String, JsonObject> sorted = new TreeMap<>((x, y) -> {
                    boolean dx = x.endsWith(":default"), dy = y.endsWith(":default");
                    return dx != dy ? (dx ? -1 : 1) : x.compareTo(y);
                });
                files.forEach((id, j) -> {
                    if (j.isJsonObject()) sorted.put(id.getNamespace() + ":" + id.getPath(), j.getAsJsonObject());
                });
                JsonObject merged = defaults();
                for (JsonObject j : sorted.values()) merged = merge(merged, j);
                MeterRules.apply(merged.toString());
                com.mojang.logging.LogUtils.getLogger().info("PvP meters: rules from {} file(s)", sorted.size());
            }
        });
    }

    /** Every client gets the rules on joining and after a reload. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        var packet = new com.dbzenith.network.MeterRulesPacket(json);
        if (event.getPlayer() != null) ModNetwork.sendTo(event.getPlayer(), packet);
        else for (ServerPlayer p : event.getPlayerList().getPlayers()) ModNetwork.sendTo(p, packet);
    }
}
