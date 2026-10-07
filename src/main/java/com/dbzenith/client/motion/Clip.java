package com.dbzenith.client.motion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One animation clip (CX-18), loaded from {@code assets/<ns>/motion/clips/<id>.json}. Keys sit at normalised times
 * (0..1 of the clip) and set any channels of a bone; each channel is its own track, so a key may set only the bend.
 * Between keys the value follows the key's ease; "smooth" (the default for loops) is a Catmull-Rom curve through the
 * neighbouring keys, so cycles flow without corners.
 * <p>
 * Fields: {@code length} (ticks, for time-synced clips), {@code loop}, {@code sync} ("time", or "stride": a gait whose
 * cycle follows the ground covered), {@code stride} (blocks per cycle; 0 works it out from the leg swing so feet never
 * slide), {@code symmetric} (the left limbs are the right ones half a cycle later, mirrored), {@code ease} (the default).
 * Per key: {@code t}, {@code rot} [pitch, yaw, roll], {@code pos} [x, y, z], {@code bend}, {@code ease}.
 */
public final class Clip {
    public enum Sync { TIME, STRIDE }

    static final byte SMOOTH = 0, LINEAR = 1, IN = 2, OUT = 3, INOUT = 4, STEP = 5;
    /** Leg length in blocks (12 model pixels), for working out a stride from the leg swing. */
    static final float LEG = 0.75f;

    public final String id;
    public final boolean loop;
    public final Sync sync;
    public final float length;
    public final float stride;
    final float[][] times = new float[Bone.COUNT * Bone.CHANNELS][];
    final float[][] values = new float[Bone.COUNT * Bone.CHANNELS][];
    final byte[][] eases = new byte[Bone.COUNT * Bone.CHANNELS][];

    private Clip(String id, boolean loop, Sync sync, float length, float stride) {
        this.id = id;
        this.loop = loop;
        this.sync = sync;
        this.length = Math.max(1f, length);
        this.stride = stride;
    }

    /** Whether this clip keys that slot at all. */
    public boolean keys(int slot) {
        return times[slot] != null;
    }

    /** Adds this clip's pose at normalised time {@code t}, scaled by {@code w}, into {@code out}. */
    public void sample(float t, Pose out, float w) {
        if (w <= 0f) return;
        if (loop) t -= (float) Math.floor(t);
        else t = Math.max(0f, Math.min(1f, t));
        for (int s = 0; s < times.length; s++) {
            if (times[s] != null) out.v[s] += value(s, t) * w;
        }
    }

    /** Peak-to-peak swing of the right leg (degrees): how far a gait reaches, for its stride. */
    public float legSwing() {
        float[] v = values[Bone.RIGHT_LEG.at(Bone.PITCH)];
        if (v == null) return 0f;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float x : v) {
            lo = Math.min(lo, x);
            hi = Math.max(hi, x);
        }
        return hi - lo;
    }

    /** Ground covered by one full cycle at full swing, for a figure of normal size (blocks). */
    public float naturalStride() {
        if (stride > 0f) return stride;
        float half = (float) Math.toRadians(legSwing() / 2f);
        return Math.max(0.4f, 4f * LEG * (float) Math.sin(half));
    }

    private float value(int s, float t) {
        float[] ts = times[s], vs = values[s];
        byte[] es = eases[s];
        int n = ts.length;
        if (n == 1) return vs[0];
        int i = -1;
        for (int k = 0; k < n; k++) if (ts[k] <= t) i = k;
        float t0, t1, v1, v2;
        int i1, i2;
        if (i < 0) {                                                       // before the first key
            if (!loop) return vs[0];
            i1 = n - 1;
            i2 = 0;
            t0 = ts[n - 1] - 1f;
            t1 = ts[0];
        } else if (i == n - 1) {                                           // after the last key
            if (!loop) return vs[n - 1];
            i1 = n - 1;
            i2 = 0;
            t0 = ts[n - 1];
            t1 = ts[0] + 1f;
        } else {
            i1 = i;
            i2 = i + 1;
            t0 = ts[i];
            t1 = ts[i + 1];
        }
        v1 = vs[i1];
        v2 = vs[i2];
        float u = t1 - t0 <= 1e-6f ? 1f : (t - t0) / (t1 - t0);
        u = Math.max(0f, Math.min(1f, u));
        return switch (es[i1]) {
            case LINEAR -> v1 + (v2 - v1) * u;
            case STEP -> v1;
            case IN -> v1 + (v2 - v1) * (1f - (float) Math.cos(u * Math.PI / 2));
            case OUT -> v1 + (v2 - v1) * (float) Math.sin(u * Math.PI / 2);
            case INOUT -> v1 + (v2 - v1) * (0.5f - 0.5f * (float) Math.cos(u * Math.PI));
            default -> {                                                    // Catmull-Rom through the neighbours
                float v0 = vs[neighbour(i1, -1, n)], v3 = vs[neighbour(i2, 1, n)];
                float u2 = u * u, u3 = u2 * u;
                yield 0.5f * (2f * v1 + (-v0 + v2) * u + (2f * v0 - 5f * v1 + 4f * v2 - v3) * u2 + (-v0 + 3f * v1 - 3f * v2 + v3) * u3);
            }
        };
    }

    private int neighbour(int k, int step, int n) {
        int j = k + step;
        if (loop) return Math.floorMod(j, n);
        return Math.max(0, Math.min(n - 1, j));
    }

    // ------------------------------------------------------------------ loading

    private static byte ease(String s, byte fallback) {
        if (s == null) return fallback;
        return switch (s) {
            case "linear" -> LINEAR;
            case "in" -> IN;
            case "out" -> OUT;
            case "inout" -> INOUT;
            case "step" -> STEP;
            default -> SMOOTH;
        };
    }

    private record Key(float t, float value, byte ease) {}

    public static Clip parse(String id, JsonObject json) {
        boolean loop = GsonHelper.getAsBoolean(json, "loop", true);
        Sync sync = "stride".equals(GsonHelper.getAsString(json, "sync", "time")) ? Sync.STRIDE : Sync.TIME;
        Clip c = new Clip(id, loop, sync, GsonHelper.getAsFloat(json, "length", 20f), GsonHelper.getAsFloat(json, "stride", 0f));
        byte fallback = ease(GsonHelper.getAsString(json, "ease", loop ? "smooth" : "inout"), SMOOTH);
        boolean symmetric = GsonHelper.getAsBoolean(json, "symmetric", false);
        JsonObject bones = GsonHelper.getAsJsonObject(json, "bones", new JsonObject());
        @SuppressWarnings("unchecked")
        List<Key>[] tracks = new List[Bone.COUNT * Bone.CHANNELS];
        for (Map.Entry<String, JsonElement> e : bones.entrySet()) {
            Bone b = Bone.byId(e.getKey());
            if (b == null) continue;
            for (JsonElement k : e.getValue().getAsJsonArray()) {
                JsonObject key = k.getAsJsonObject();
                float t = GsonHelper.getAsFloat(key, "t", 0f);
                byte ease = ease(GsonHelper.getAsString(key, "ease", null), fallback);
                if (key.has("rot")) {
                    JsonArray r = key.getAsJsonArray("rot");
                    for (int i = 0; i < 3; i++) add(tracks, b.at(Bone.PITCH + i), new Key(t, r.get(i).getAsFloat(), ease));
                }
                if (key.has("pos")) {
                    JsonArray p = key.getAsJsonArray("pos");
                    for (int i = 0; i < 3; i++) add(tracks, b.at(Bone.X + i), new Key(t, p.get(i).getAsFloat(), ease));
                }
                if (key.has("bend")) add(tracks, b.at(Bone.BEND), new Key(t, key.get("bend").getAsFloat(), ease));
            }
        }
        if (symmetric) {                                                    // left = right, half a cycle on, mirrored
            for (Bone b : new Bone[]{Bone.RIGHT_ARM, Bone.RIGHT_LEG}) {
                Bone l = b.twin();
                for (int ch = 0; ch < Bone.CHANNELS; ch++) {
                    List<Key> src = tracks[b.at(ch)];
                    if (src == null || tracks[l.at(ch)] != null) continue;
                    boolean flip = ch == Bone.YAW || ch == Bone.ROLL || ch == Bone.X;
                    List<Key> dst = new ArrayList<>();
                    for (Key k : src) dst.add(new Key((k.t + 0.5f) % 1f, flip ? -k.value : k.value, k.ease));
                    dst.sort((a, z) -> Float.compare(a.t, z.t));
                    tracks[l.at(ch)] = dst;
                }
            }
        }
        for (int s = 0; s < tracks.length; s++) {
            List<Key> keys = tracks[s];
            if (keys == null || keys.isEmpty()) continue;
            keys.sort((a, z) -> Float.compare(a.t, z.t));
            int n = keys.size();
            c.times[s] = new float[n];
            c.values[s] = new float[n];
            c.eases[s] = new byte[n];
            for (int i = 0; i < n; i++) {
                c.times[s][i] = keys.get(i).t;
                c.values[s][i] = keys.get(i).value;
                c.eases[s][i] = keys.get(i).ease;
            }
        }
        return c;
    }

    private static void add(List<Key>[] tracks, int slot, Key k) {
        if (tracks[slot] == null) tracks[slot] = new ArrayList<>();
        tracks[slot].add(k);
    }
}
