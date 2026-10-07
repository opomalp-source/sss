package com.dbzenith.client.motion;

import com.dbzenith.DBZenith;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * The motion engine's data (CX-18), from resource packs, reloaded with F3+T:
 * <ul>
 *   <li>{@code motion/clips/**.json}: clips (see {@link Clip}), named by their path ("walk", "fly/hover");</li>
 *   <li>{@code motion/sets/*.json}: animation sets (see {@link AnimSet});</li>
 *   <li>{@code motion/profiles.json}: which set each entity uses, and the race or form it counts as;</li>
 *   <li>{@code motion/tuning.json}: overrides for {@link Tuning}.</li>
 * </ul>
 */
public final class MotionData extends SimplePreparableReloadListener<MotionData.Loaded> {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    public static final MotionData INSTANCE = new MotionData();

    /** What an entity is animated as: its set, and the race / form overrides it counts as (any may be null). */
    public record Profile(String set, String race, String form) {}

    public static final class Loaded {
        final Map<String, Clip> clips = new HashMap<>();
        final Map<String, AnimSet> sets = new HashMap<>();
        final Map<String, Profile> profiles = new HashMap<>();
        Profile player = new Profile("fighter", null, null);
        Profile fallback = new Profile("fighter", null, null);
        JsonObject tuning;
    }

    private static Loaded current = new Loaded();
    private static int version;

    private MotionData() {}

    public static Loaded data() {
        return current;
    }

    /** Bumped on every reload, so figures look their clips up again. */
    public static int version() {
        return version;
    }

    public static Clip clip(String id) {
        return id == null ? null : current.clips.get(id);
    }

    public static Map<String, AnimSet> sets() {
        return current.sets;
    }

    public static Profile profileFor(net.minecraft.world.entity.Entity e) {
        if (e instanceof net.minecraft.world.entity.player.Player) return current.player;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        Profile p = id == null ? null : current.profiles.get(id.toString());
        return p != null ? p : current.fallback;
    }

    @Override
    protected Loaded prepare(ResourceManager rm, ProfilerFiller profiler) {
        Loaded l = new Loaded();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources("motion/clips", f -> f.getPath().endsWith(".json")).entrySet()) {
            String path = e.getKey().getPath();
            String name = path.substring("motion/clips/".length(), path.length() - ".json".length());
            String id = e.getKey().getNamespace().equals(DBZenith.MOD_ID) ? name : e.getKey().getNamespace() + ":" + name;
            JsonObject json = read(e.getValue(), e.getKey());
            if (json != null) {
                try {
                    l.clips.put(id, Clip.parse(id, json));
                } catch (RuntimeException ex) {
                    LOG.error("Bad motion clip {}: {}", e.getKey(), ex.toString());
                }
            }
        }
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources("motion/sets", f -> f.getPath().endsWith(".json")).entrySet()) {
            String path = e.getKey().getPath();
            String id = path.substring("motion/sets/".length(), path.length() - ".json".length());
            JsonObject json = read(e.getValue(), e.getKey());
            if (json != null) l.sets.put(id, AnimSet.parse(id, json));
        }
        ResourceLocation profiles = new ResourceLocation(DBZenith.MOD_ID, "motion/profiles.json");
        for (Resource r : rm.getResourceStack(profiles)) {
            JsonObject json = read(r, profiles);
            if (json == null) continue;
            if (json.has("player")) l.player = profile(json.getAsJsonObject("player"));
            if (json.has("default")) l.fallback = profile(json.getAsJsonObject("default"));
            for (Map.Entry<String, com.google.gson.JsonElement> en : GsonHelper.getAsJsonObject(json, "entities", new JsonObject()).entrySet()) {
                l.profiles.put(en.getKey(), profile(en.getValue().getAsJsonObject()));
            }
        }
        ResourceLocation tuning = new ResourceLocation(DBZenith.MOD_ID, "motion/tuning.json");
        for (Resource r : rm.getResourceStack(tuning)) {
            JsonObject json = read(r, tuning);
            if (json != null) l.tuning = json;
        }
        return l;
    }

    private static Profile profile(JsonObject o) {
        return new Profile(GsonHelper.getAsString(o, "set", "fighter"), o.has("race") ? GsonHelper.getAsString(o, "race") : null,
                o.has("form") ? GsonHelper.getAsString(o, "form") : null);
    }

    private static JsonObject read(Resource r, ResourceLocation where) {
        try (Reader reader = r.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception ex) {
            LOG.error("Could not read {}: {}", where, ex.toString());
            return null;
        }
    }

    @Override
    protected void apply(Loaded l, ResourceManager rm, ProfilerFiller profiler) {
        current = l;
        if (l.tuning != null) Tuning.apply(l.tuning);
        version++;
        LOG.info("Motion engine: {} clips, {} sets, {} entity profiles", l.clips.size(), l.sets.size(), l.profiles.size());
    }
}
