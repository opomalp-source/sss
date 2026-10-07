package com.dbzenith.client.motion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Which clip plays for each {@link State} (CX-18), from {@code motion/sets/<id>.json}:
 * <pre>
 * { "parent": "fighter",
 *   "clips": { "idle": "idle_breathe", "walk": "walk", ... },
 *   "overrides": { "race:namekian": { "idle": "idle_crossed" }, "form:super_saiyan": { "idle": "idle_ssj" } } }
 * </pre>
 * Lookup order: the entity's override, its form's, its race's, this set's clips, then the parent set the same way.
 */
public final class AnimSet {
    public final String id;
    final String parent;
    final Map<String, String> clips = new HashMap<>();
    final Map<String, Map<String, String>> overrides = new HashMap<>();

    private AnimSet(String id, String parent) {
        this.id = id;
        this.parent = parent;
    }

    /** The clip id for a state, or null. {@code keys} are override keys in priority order ("entity:..", "form:..", "race:.."). */
    public String clipFor(State s, String[] keys, Map<String, AnimSet> sets, int depth) {
        for (String k : keys) {
            if (k == null) continue;
            Map<String, String> o = overrides.get(k);
            if (o != null && o.containsKey(s.key)) return o.get(s.key);
        }
        String own = clips.get(s.key);
        if (own != null) return own;
        AnimSet p = parent == null || depth > 8 ? null : sets.get(parent);
        return p == null ? null : p.clipFor(s, keys, sets, depth + 1);
    }

    public static AnimSet parse(String id, JsonObject json) {
        AnimSet set = new AnimSet(id, json.has("parent") ? GsonHelper.getAsString(json, "parent") : null);
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "clips", new JsonObject()).entrySet()) {
            set.clips.put(e.getKey(), e.getValue().getAsString());
        }
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "overrides", new JsonObject()).entrySet()) {
            Map<String, String> m = new HashMap<>();
            for (Map.Entry<String, JsonElement> c : e.getValue().getAsJsonObject().entrySet()) m.put(c.getKey(), c.getValue().getAsString());
            set.overrides.put(e.getKey(), m);
        }
        return set;
    }
}
