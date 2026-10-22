package com.dbzenith.living;

import com.dbzenith.DBZenith;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Trait;
import com.dbzenith.npc.ai.AiRandom;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Daily schedules, from data packs ({@code data/<ns>/schedules/<occupation>.json}, reloaded with /reload): blocks of
 * hours (0-24, the clock of the Minecraft day, where sunrise is 6) and what to do in them ({@link Activity}). Each
 * resident lives its occupation's day its own way ({@link #activity}):
 * <ul>
 *   <li>an early bird or a late riser: its day shifted by up to an hour (from its id);</li>
 *   <li>its temperament: the shy go home instead of mixing, the competitive and ambitious train instead, the curious
 *       wander in their free time;</li>
 *   <li>its mood: tired residents go home early; worried ones stay in;</li>
 *   <li>now and then a day off (one day in seven, its own): work becomes free time;</li>
 *   <li>trouble in town overrides it all (fighters defend, the rest go home).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Schedules {
    /** What a resident is doing. */
    public enum Activity {
        SLEEP, HOME, WORK, TRAIN, SOCIAL, EAT, WANDER, PATROL, SHOP, TRAVEL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "living.dbzenith.activity." + id();
        }
    }

    record Block(int from, int to, Activity activity) {
        boolean covers(int h) {
            return from <= to ? h >= from && h < to : h >= from || h < to;
        }
    }

    private static Map<Occupation, List<Block>> schedules = new EnumMap<>(Occupation.class);

    private Schedules() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "schedules") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                Map<Occupation, List<Block>> out = new EnumMap<>(Occupation.class);
                for (var e : files.entrySet()) {
                    Occupation o = Occupation.byId(e.getKey().getPath());
                    if (o == null || !e.getValue().isJsonObject()) continue;
                    List<Block> blocks = new ArrayList<>();
                    for (JsonElement b : GsonHelper.getAsJsonArray(e.getValue().getAsJsonObject(), "blocks", new JsonArray())) {
                        JsonObject j = b.getAsJsonObject();
                        try {
                            blocks.add(new Block(GsonHelper.getAsInt(j, "from"), GsonHelper.getAsInt(j, "to"),
                                    Activity.valueOf(GsonHelper.getAsString(j, "do").toUpperCase(Locale.ROOT))));
                        } catch (RuntimeException ignored) {
                        }
                    }
                    out.put(o, blocks);
                }
                schedules = out;
                com.mojang.logging.LogUtils.getLogger().info("Living world: {} schedules", out.size());
            }
        });
    }

    public static boolean loaded() {
        return !schedules.isEmpty();
    }

    /** The hour of the day (0-23; sunrise is 6). */
    public static int hour(Level level) {
        return hour(level.getDayTime());
    }

    public static int hour(long dayTime) {
        return (int) ((Math.floorMod(dayTime, 24000L) / 1000 + 6) % 24);
    }

    /** What its occupation's day says for this hour (its personal shift applied), before temperament and mood. */
    public static Activity planned(NamedFighter c, int hour) {
        List<Block> blocks = schedules.get(Occupation.of(c));
        if (blocks == null || blocks.isEmpty()) return hour >= 22 || hour < 6 ? Activity.SLEEP : Activity.WANDER;
        int shift = AiRandom.pick(c.id.getMostSignificantBits(), 0, 41, 3) - 1;            // -1, 0 or +1 hour
        int h = Math.floorMod(hour - shift, 24);
        for (Block b : blocks) if (b.covers(h)) return b.activity;
        return Activity.WANDER;
    }

    /** Whether this is its day off (one day in seven, its own; guards keep their watch). */
    public static boolean dayOff(NamedFighter c, long day) {
        return Occupation.of(c) != Occupation.GUARD && AiRandom.chance(c.id.getLeastSignificantBits(), day, 42, 1.0 / 7);
    }

    /** What it does now: its day, shaped by temperament, mood, a day off and trouble in town. */
    public static Activity activity(NamedFighter c, long dayTime, Mood mood, boolean alarm) {
        Occupation occ = Occupation.of(c);
        if (alarm) return occ.fighter ? Activity.PATROL : Activity.HOME;
        int hour = hour(dayTime);
        Activity a = planned(c, hour);
        long day = Math.floorDiv(dayTime, 24000L);
        boolean dayOff = dayOff(c, day);
        if (dayOff && (a == Activity.WORK || a == Activity.TRAIN && occ == Occupation.STUDENT)) a = hour < 14 ? Activity.WANDER : Activity.SOCIAL;
        var t = c.temper();
        if (a == Activity.SOCIAL && c.has(Trait.SHY)) a = Activity.HOME;
        if (a == Activity.SOCIAL && occ.fighter && (c.has(Trait.COMPETITIVE) || c.has(Trait.AMBITIOUS) || t.discipline > 0.8)) a = Activity.TRAIN;
        if (a == Activity.HOME && c.has(Trait.CURIOUS) && hour < 21) a = Activity.WANDER;
        if (mood == Mood.TIRED && a != Activity.SLEEP && hour >= 19) a = Activity.HOME;
        if (mood == Mood.WORRIED && (a == Activity.WANDER || a == Activity.SOCIAL)) a = Activity.HOME;
        if (a == Activity.WORK && occ.workplace == Poi.Kind.DOJO) a = Activity.TRAIN;
        if (a == Activity.WORK && occ.workplace == Poi.Kind.PATROL) a = Activity.PATROL;
        return a;
    }
}
