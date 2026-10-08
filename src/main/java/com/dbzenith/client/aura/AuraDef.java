package com.dbzenith.client.aura;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * One aura's look, read from {@code assets/<namespace>/auras/<id>.json} (CX-24). Every size is a multiple of the
 * fighter's height; every share is of the shell's radius. Anything left out takes the default below, and the
 * {@code silhouette} ("lobed" or "jagged") picks the defaults for the spikes. See docs/AURA_GUIDE.md.
 */
public final class AuraDef {
    public final String id;
    /** The forms that wear this aura. */
    public final List<String> forms;
    public final boolean jagged;
    // colours, from the middle out, and the thin rim on the outline
    public final int core, mid, edge, rim;
    public final float coreAlpha, edgeAlpha, rimStrength, rimWidth;
    // the shape: an egg, rounded at the bottom, widest low down, tapering to the top
    public final float width, height, bottom, widest, taper, tip;
    // slow bulges in the shell itself, rising
    public final int lobeCount;
    public final float lobeSize, lobeRise;
    // the outline cut into lobes or spikes
    public final int spikeCount;
    public final float spikeSize, spikeSharpness, spikeLean;
    // motion
    public final float scroll, flicker, pulse, streaks, sway;
    // the glow round the outside
    public final float glow, glowScale;
    // the inner layer: a smaller, paler shell moving faster
    public final float innerScale, innerAlpha, innerSpeed;
    public final int innerEdge;
    // particles drifting through the aura
    public final String particles;
    public final float particleRate, particleSize;
    public final int particleColor;
    // later phases: light and the ground
    public final int light;
    public final String ground;

    private AuraDef(String id, JsonObject j) {
        this.id = id;
        List<String> f = new ArrayList<>();
        JsonArray arr = GsonHelper.getAsJsonArray(j, "forms", new JsonArray());
        for (JsonElement e : arr) f.add(e.getAsString());
        forms = List.copyOf(f);
        jagged = "jagged".equals(GsonHelper.getAsString(j, "silhouette", "lobed"));

        JsonObject c = GsonHelper.getAsJsonObject(j, "colors", new JsonObject());
        edge = color(c, "edge", 0x40A0FF);
        mid = color(c, "mid", mix(edge, 0xFFFFFF, 0.6f));
        core = color(c, "core", 0xFFFFFF);
        rim = color(c, "rim", mix(edge, 0xFFFFFF, 0.4f));

        JsonObject a = GsonHelper.getAsJsonObject(j, "alpha", new JsonObject());
        coreAlpha = GsonHelper.getAsFloat(a, "core", 0.85f);
        edgeAlpha = GsonHelper.getAsFloat(a, "edge", 1f);

        JsonObject r = GsonHelper.getAsJsonObject(j, "rim", new JsonObject());
        rimStrength = GsonHelper.getAsFloat(r, "strength", 0.5f);
        rimWidth = GsonHelper.getAsFloat(r, "width", 0.05f);

        JsonObject s = GsonHelper.getAsJsonObject(j, "shape", new JsonObject());
        width = GsonHelper.getAsFloat(s, "width", 1.4f);
        height = GsonHelper.getAsFloat(s, "height", 2.2f);
        bottom = GsonHelper.getAsFloat(s, "bottom", -0.06f);
        widest = GsonHelper.getAsFloat(s, "widest", 0.3f);
        taper = GsonHelper.getAsFloat(s, "taper", 1.6f);
        tip = GsonHelper.getAsFloat(s, "tip", 0.75f);

        JsonObject l = GsonHelper.getAsJsonObject(j, "lobes", new JsonObject());
        lobeCount = Math.max(1, GsonHelper.getAsInt(l, "count", 5));
        lobeSize = GsonHelper.getAsFloat(l, "size", jagged ? 0.05f : 0.12f);
        lobeRise = GsonHelper.getAsFloat(l, "rise", 0.35f);

        JsonObject k = GsonHelper.getAsJsonObject(j, "spikes", new JsonObject());
        spikeCount = Math.max(1, GsonHelper.getAsInt(k, "count", jagged ? 26 : 7));
        spikeSize = GsonHelper.getAsFloat(k, "size", jagged ? 0.16f : 0.06f);
        spikeSharpness = GsonHelper.getAsFloat(k, "sharpness", jagged ? 0.85f : 0f);
        spikeLean = GsonHelper.getAsFloat(k, "lean", jagged ? 1.6f : 0.6f);

        JsonObject m = GsonHelper.getAsJsonObject(j, "motion", new JsonObject());
        scroll = GsonHelper.getAsFloat(m, "scroll", jagged ? 1.2f : 0.35f);
        flicker = GsonHelper.getAsFloat(m, "flicker", jagged ? 0.35f : 0.08f);
        pulse = GsonHelper.getAsFloat(m, "pulse", 0.03f);
        streaks = GsonHelper.getAsFloat(m, "streaks", jagged ? 0.45f : 0.2f);
        sway = GsonHelper.getAsFloat(m, "sway", 0.04f);

        JsonObject g = GsonHelper.getAsJsonObject(j, "glow", new JsonObject());
        glow = GsonHelper.getAsFloat(g, "strength", 0.7f);
        glowScale = GsonHelper.getAsFloat(g, "scale", 1.08f);

        JsonObject in = GsonHelper.getAsJsonObject(j, "inner", new JsonObject());
        innerScale = GsonHelper.getAsFloat(in, "scale", 0.72f);
        innerAlpha = GsonHelper.getAsFloat(in, "alpha", 0.5f);
        innerSpeed = GsonHelper.getAsFloat(in, "speed", 1.7f);
        innerEdge = color(in, "edge", mid);

        JsonObject p = GsonHelper.getAsJsonObject(j, "particles", new JsonObject());
        particles = GsonHelper.getAsString(p, "type", "none");
        particleRate = GsonHelper.getAsFloat(p, "rate", 1f);
        particleSize = GsonHelper.getAsFloat(p, "size", 1f);
        particleColor = color(p, "color", mix(edge, 0xFFFFFF, 0.7f));

        light = GsonHelper.getAsInt(j, "light", 10);
        ground = GsonHelper.getAsString(j, "ground", "dust");
    }

    public static AuraDef parse(String id, JsonObject json) {
        return new AuraDef(id, json);
    }

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
