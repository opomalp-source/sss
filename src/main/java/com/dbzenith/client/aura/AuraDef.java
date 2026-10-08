package com.dbzenith.client.aura;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One aura's look, read from {@code assets/<namespace>/auras/<id>.json} (CX-24). Every size is a multiple of the
 * fighter's height; every share is of the shell's radius. Anything left out takes the default below. An aura is one
 * body (its shape, bulges, sway and pulse) worn as a list of {@link Layer}s drawn in order, outermost first, plus its
 * particles and how it reacts to what the fighter does ({@link React}). A file can start from another with
 * {@code "extends": "<id>"}. See docs/AURA_GUIDE.md.
 */
public final class AuraDef {
    public final String id;
    /** The file as read (after {@code extends}), kept so /dbzaura set can change one value and read it again. */
    public final JsonObject source;
    /** The forms that wear this aura. */
    public final List<String> forms;
    // the shape: an egg, rounded at the bottom, widest low down, tapering to the top
    public final float width, height, bottom, widest, taper, tip;
    /** One tall flame above the head (share of the height it adds) and a wider base round the feet (share of the radius). */
    public final float peak, flare;
    // slow bulges in the shell itself, rising
    public final int lobeCount;
    public final float lobeSize, lobeRise;
    // motion of the whole body
    public final float pulse, pulseSpeed, sway, speed;
    /** Drawn in this order: put the outermost first. */
    public final List<Layer> layers;
    // particles drifting through the aura while it burns: none, sparkle, ember, spark
    public final String particles;
    public final float particleRate, particleSize;
    public final int particleColor;
    // energy motes rising from the ground while charging
    public final float moteRate, moteSize;
    public final int moteColor;
    public final React react;
    // later phases: light and the ground
    public final int light;
    public final String ground;
    /** The colour the aura reads as (the edge of its first shell), for tests and tints. */
    public final int edge;
    public final boolean jagged;

    /** One shell of the aura. */
    public static final class Layer {
        public static final int SHELL = 0, GLOW = 1, HAZE = 2;
        /** shell: the flame body; glow: a bright band just outside the shell it wraps; haze: a soft inner fill, no hard outline. */
        public final int kind;
        /** Added onto what is behind it (light), or blended over it (keeps colour in daylight). */
        public final boolean additive;
        /**
         * Size against the aura's shape (width, height), and how far it sits up (share of the height). A glow's scale is
         * against the layer it wraps instead, and it shares that layer's outline.
         */
        public final float scale, heightScale, lift;
        /** For a glow: the layer whose outline it follows (index; -1 the next shell after it). */
        public final int wraps;
        public final int core, mid, edge, rim;
        public final float coreAlpha, edgeAlpha, rimStrength, rimWidth, opacity;
        public final boolean jagged;
        public final int spikeCount;
        public final float spikeSize, spikeSharpness, spikeLean;
        /** How much of the body's bulges and sway this layer takes, and its own extra seed. */
        public final float lobes, sway, seed;
        /** Its own pace: noise scroll upward, flicker of the spikes, light streaks, and a time multiplier. */
        public final float scroll, flicker, streaks, speed;

        Layer(JsonObject j, AuraDef d, int index, int count) {
            String k = GsonHelper.getAsString(j, "kind", "shell");
            kind = k.equals("glow") ? GLOW : k.equals("haze") ? HAZE : SHELL;
            additive = "add".equals(GsonHelper.getAsString(j, "blend", kind == GLOW ? "add" : "normal"));
            scale = GsonHelper.getAsFloat(j, "scale", kind == GLOW ? 1.05f : kind == HAZE ? 0.72f : 1f);
            heightScale = GsonHelper.getAsFloat(j, "heightScale", kind == HAZE ? 0.5f + 0.5f * scale : scale);
            lift = GsonHelper.getAsFloat(j, "lift", 0f);
            wraps = GsonHelper.getAsInt(j, "wraps", -1);
            jagged = "jagged".equals(GsonHelper.getAsString(j, "silhouette", d.jagged ? "jagged" : "lobed"));

            JsonObject c = GsonHelper.getAsJsonObject(j, "colors", new JsonObject());
            edge = color(c, "edge", 0x40A0FF);
            mid = color(c, "mid", mix(edge, 0xFFFFFF, 0.6f));
            core = color(c, "core", 0xFFFFFF);
            rim = color(c, "rim", mix(edge, 0xFFFFFF, 0.4f));

            JsonObject a = GsonHelper.getAsJsonObject(j, "alpha", new JsonObject());
            coreAlpha = GsonHelper.getAsFloat(a, "core", kind == HAZE ? 0.25f : 0.85f);
            edgeAlpha = GsonHelper.getAsFloat(a, "edge", kind == HAZE ? 0.5f : 1f);
            opacity = GsonHelper.getAsFloat(j, "opacity", kind == GLOW ? 0.7f : 1f);

            JsonObject r = GsonHelper.getAsJsonObject(j, "rim", new JsonObject());
            rimStrength = GsonHelper.getAsFloat(r, "strength", kind == HAZE ? 0f : 0.5f);
            rimWidth = GsonHelper.getAsFloat(r, "width", 0.05f);

            JsonObject s = GsonHelper.getAsJsonObject(j, "spikes", new JsonObject());
            spikeCount = Math.max(1, GsonHelper.getAsInt(s, "count", jagged ? 26 : 7));
            spikeSize = GsonHelper.getAsFloat(s, "size", jagged ? 0.16f : 0.06f);
            spikeSharpness = GsonHelper.getAsFloat(s, "sharpness", jagged ? 0.85f : 0f);
            spikeLean = GsonHelper.getAsFloat(s, "lean", jagged ? 1.6f : 0.6f);

            lobes = GsonHelper.getAsFloat(j, "lobes", kind == HAZE ? 1.4f : 1f);
            sway = GsonHelper.getAsFloat(j, "sway", kind == HAZE ? 1.5f : 1f);
            seed = GsonHelper.getAsFloat(j, "seed", kind == HAZE ? 17.3f : 0f);

            JsonObject m = GsonHelper.getAsJsonObject(j, "motion", new JsonObject());
            scroll = GsonHelper.getAsFloat(m, "scroll", jagged ? 1.2f : 0.35f);
            flicker = GsonHelper.getAsFloat(m, "flicker", jagged ? 0.35f : 0.08f);
            streaks = GsonHelper.getAsFloat(m, "streaks", jagged ? 0.45f : 0.2f);
            speed = GsonHelper.getAsFloat(m, "speed", kind == HAZE ? 1.7f : 1f);
        }

        /** For a glow, whose scale is against the layer it wraps: how far in from its own outline that layer's lies. */
        public float band() {
            return Math.max(0.01f, 1f - 1f / Math.max(1.0001f, scale));
        }
    }

    /** How the aura answers what the fighter does. Each is a share added on top of the calm aura. */
    public static final class React {
        // charging (and powering up a form): bigger, taller, wilder, brighter, shaking
        public final float chargeScale, chargeHeight, chargeWild, chargeGlow, chargeShake;
        // moving fast or flying: the flames stream back, this many body heights per block a tick
        public final float trail, trailMax;
        // hit or attacking: a short flare
        public final float hitScale, hitBright;
        // a new aura bursting out
        public final float burstScale, burstBright;

        React(JsonObject j) {
            JsonObject c = GsonHelper.getAsJsonObject(j, "charge", new JsonObject());
            chargeScale = GsonHelper.getAsFloat(c, "scale", 0.22f);
            chargeHeight = GsonHelper.getAsFloat(c, "height", 0.3f);
            chargeWild = GsonHelper.getAsFloat(c, "wild", 1.2f);
            chargeGlow = GsonHelper.getAsFloat(c, "glow", 0.35f);
            chargeShake = GsonHelper.getAsFloat(c, "shake", 0.025f);
            JsonObject m = GsonHelper.getAsJsonObject(j, "move", new JsonObject());
            trail = GsonHelper.getAsFloat(m, "trail", 1.6f);
            trailMax = GsonHelper.getAsFloat(m, "max", 0.9f);
            JsonObject h = GsonHelper.getAsJsonObject(j, "hit", new JsonObject());
            hitScale = GsonHelper.getAsFloat(h, "scale", 0.1f);
            hitBright = GsonHelper.getAsFloat(h, "bright", 0.7f);
            JsonObject b = GsonHelper.getAsJsonObject(j, "burst", new JsonObject());
            burstScale = GsonHelper.getAsFloat(b, "scale", 0.45f);
            burstBright = GsonHelper.getAsFloat(b, "bright", 1.2f);
        }
    }

    private AuraDef(String id, JsonObject j) {
        this.id = id;
        this.source = j;
        List<String> f = new ArrayList<>();
        JsonArray arr = GsonHelper.getAsJsonArray(j, "forms", new JsonArray());
        for (JsonElement e : arr) f.add(e.getAsString());
        forms = List.copyOf(f);
        jagged = "jagged".equals(GsonHelper.getAsString(j, "silhouette", "lobed"));

        JsonObject s = GsonHelper.getAsJsonObject(j, "shape", new JsonObject());
        width = GsonHelper.getAsFloat(s, "width", 1.4f);
        height = GsonHelper.getAsFloat(s, "height", 2.2f);
        bottom = GsonHelper.getAsFloat(s, "bottom", -0.06f);
        widest = GsonHelper.getAsFloat(s, "widest", 0.3f);
        taper = GsonHelper.getAsFloat(s, "taper", 1.6f);
        tip = GsonHelper.getAsFloat(s, "tip", 0.75f);
        peak = GsonHelper.getAsFloat(s, "peak", 0f);
        flare = GsonHelper.getAsFloat(s, "flare", 0f);

        JsonObject l = GsonHelper.getAsJsonObject(j, "lobes", new JsonObject());
        lobeCount = Math.max(1, GsonHelper.getAsInt(l, "count", 5));
        lobeSize = GsonHelper.getAsFloat(l, "size", jagged ? 0.05f : 0.12f);
        lobeRise = GsonHelper.getAsFloat(l, "rise", 0.35f);

        JsonObject m = GsonHelper.getAsJsonObject(j, "motion", new JsonObject());
        pulse = GsonHelper.getAsFloat(m, "pulse", 0.03f);
        pulseSpeed = GsonHelper.getAsFloat(m, "pulseSpeed", 2.4f);
        sway = GsonHelper.getAsFloat(m, "sway", 0.04f);
        speed = GsonHelper.getAsFloat(m, "speed", 1f);

        JsonArray la = GsonHelper.getAsJsonArray(j, "layers", null);
        if (la == null || la.isEmpty()) {                                      // the default: glow, shell, inner haze
            la = new JsonArray();
            JsonObject glow = new JsonObject();
            glow.addProperty("kind", "glow");
            la.add(glow);
            la.add(new JsonObject());
            JsonObject haze = new JsonObject();
            haze.addProperty("kind", "haze");
            la.add(haze);
        }
        List<Layer> ls = new ArrayList<>();
        for (int i = 0; i < la.size(); i++) ls.add(new Layer(la.get(i).getAsJsonObject(), this, i, la.size()));
        layers = List.copyOf(ls);
        int firstShell = 0;
        for (Layer y : layers) if (y.kind == Layer.SHELL) { firstShell = y.edge; break; }
        edge = layers.isEmpty() ? 0x40A0FF : firstShell;

        JsonObject p = GsonHelper.getAsJsonObject(j, "particles", new JsonObject());
        particles = GsonHelper.getAsString(p, "type", "none");
        particleRate = GsonHelper.getAsFloat(p, "rate", 1f);
        particleSize = GsonHelper.getAsFloat(p, "size", 1f);
        particleColor = color(p, "color", mix(edge, 0xFFFFFF, 0.7f));

        JsonObject mo = GsonHelper.getAsJsonObject(j, "motes", new JsonObject());
        moteRate = GsonHelper.getAsFloat(mo, "rate", 1f);
        moteSize = GsonHelper.getAsFloat(mo, "size", 1f);
        moteColor = color(mo, "color", mix(edge, 0xFFFFFF, 0.55f));

        react = new React(GsonHelper.getAsJsonObject(j, "react", new JsonObject()));
        light = GsonHelper.getAsInt(j, "light", 10);
        ground = GsonHelper.getAsString(j, "ground", "dust");
    }

    public static AuraDef parse(String id, JsonObject json) {
        return new AuraDef(id, json);
    }

    /** The layer a glow follows: the one it names, else the next shell after it, else the one before. */
    public Layer wrapped(int glowIndex) {
        Layer g = layers.get(glowIndex);
        if (g.wraps >= 0 && g.wraps < layers.size() && g.wraps != glowIndex) return layers.get(g.wraps);
        for (int i = glowIndex + 1; i < layers.size(); i++) if (layers.get(i).kind != Layer.GLOW) return layers.get(i);
        for (int i = glowIndex - 1; i >= 0; i--) if (layers.get(i).kind != Layer.GLOW) return layers.get(i);
        return null;
    }

    // ------------------------------------------------------------------ extends

    /**
     * Resolves {@code "extends"}: the parent's file underneath, this one's values on top, objects merged key by key and
     * anything else (lists included) replaced. {@code raw} holds every file as read; a loop or a missing parent is
     * reported and the file is taken as it stands.
     */
    public static JsonObject resolve(String id, Map<String, JsonObject> raw, java.util.function.Consumer<String> problem) {
        return resolve(id, raw, problem, 0);
    }

    private static JsonObject resolve(String id, Map<String, JsonObject> raw, java.util.function.Consumer<String> problem, int depth) {
        JsonObject own = raw.get(id);
        if (own == null) return null;
        if (!own.has("extends")) return own;
        String parent = GsonHelper.getAsString(own, "extends");
        if (depth > 8) {
            problem.accept("aura " + id + ": extends goes round in a loop");
            return own;
        }
        JsonObject base = resolve(parent, raw, problem, depth + 1);
        if (base == null) {
            problem.accept("aura " + id + ": extends " + parent + ", which isn't there");
            return own;
        }
        JsonObject out = merge(base, own);
        out.remove("extends");
        if (!own.has("forms")) out.remove("forms");                         // a child never inherits who wears the parent
        return out;
    }

    /** {@code top} over {@code base}, objects merged key by key, a fresh tree (neither is changed). */
    public static JsonObject merge(JsonObject base, JsonObject top) {
        JsonObject out = base.deepCopy();
        for (Map.Entry<String, JsonElement> e : top.entrySet()) {
            JsonElement was = out.get(e.getKey());
            if (was != null && was.isJsonObject() && e.getValue().isJsonObject()) {
                out.add(e.getKey(), merge(was.getAsJsonObject(), e.getValue().getAsJsonObject()));
            } else {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ colours

    /** "#RRGGBB", "RRGGBB" or a number. */
    static int color(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsInt() & 0xFFFFFF;
        String s = e.getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        return Integer.parseInt(s, 16) & 0xFFFFFF;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
