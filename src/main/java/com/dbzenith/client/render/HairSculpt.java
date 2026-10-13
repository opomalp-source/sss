package com.dbzenith.client.render;

import com.dbzenith.transform.Form;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * One look of a hairstyle as a set of locks (head pixels: -Z is the face, -Y is up), before it becomes blocks. Looks
 * are authored with {@link #spike}, and one look turns into another lock by lock: each lock of the new look takes
 * over the old lock rooted nearest it and swings round its root to the new angle and length, while locks only the new
 * look has grow out from the scalp, and locks it lacks draw back in. So a transformation lifts the hair and a
 * Super Saiyan 3 mane grows from the roots, each lock a moment after the last.
 */
public final class HairSculpt {
    /** A lock: how much it gives in the wind, and its knots (x, y, z, width, depth). */
    record Lock(double flex, double[] k) {
        int knots() {
            return k.length / 5;
        }

        double rx() { return k[0]; }
        double ry() { return k[1]; }
        double rz() { return k[2]; }
    }

    private static final int SAMPLES = 7;
    HairGeometry.Cap cap = HairGeometry.Cap.FULL;
    final List<Lock> locks = new ArrayList<>();

    void add(double flex, double[] data) {
        locks.add(new Lock(flex, data.clone()));
    }

    /** Records what an authoring function builds (the hand-placed base styles). */
    static HairSculpt record(Consumer<HairGeometry.Builder> author) {
        HairSculpt s = new HairSculpt();
        HairGeometry.Builder b = new HairGeometry.Builder(Form.HairStyle.NONE);
        b.capture = s;
        author.accept(b);
        return s;
    }

    /** How much fan locks vary in length, one to the next (0: all as given). */
    double jagged = .34;

    HairSculpt cap(HairGeometry.Cap profile) {
        cap = profile;
        return this;
    }

    HairSculpt copy() {
        HairSculpt s = new HairSculpt();
        s.cap = cap;
        for (Lock l : locks) s.locks.add(new Lock(l.flex, l.k.clone()));
        return s;
    }

    // ------------------------------------------------------------------ authoring

    /** Unit direction: {@code el} degrees above the horizontal, {@code az} degrees round from the back (+90: the left). */
    static double[] dir(double el, double az) {
        double e = Math.toRadians(el), a = Math.toRadians(az);
        return new double[]{Math.sin(a) * Math.cos(e), -Math.sin(e), Math.cos(a) * Math.cos(e)};
    }

    /**
     * A lock from the scalp at (x, y, z): heading {@code el} degrees up and {@code az} round, {@code len} long and
     * {@code w} wide at the root, bending by {@code curl} degrees of elevation over its length (negative: it droops).
     */
    HairSculpt spike(double x, double y, double z, double el, double az, double len, double w, double curl) {
        return spike(x, y, z, el, az, len, w, curl, 0, len > 12 ? .28 : .1);
    }

    /** The same, also turning {@code swing} degrees round as it goes, with its own give in the wind. */
    HairSculpt spike(double x, double y, double z, double el, double az, double len, double w, double curl, double swing, double flex) {
        return spike(x, y, z, el, az, len, w, curl, swing, flex, 1, .62);
    }

    /**
     * The full form: the bend comes on as {@code t^bendPower} along the lock (below 1: it bends early, near the root,
     * then runs straight), and {@code taper} is how much narrower the tip is than the root.
     */
    HairSculpt spike(double x, double y, double z, double el, double az, double len, double w, double curl, double swing, double flex,
                     double bendPower, double taper) {
        int n = len > 14 ? 7 : len > 8 ? 5 : 4;
        double[] k = new double[n * 5];
        double px = x, py = y, pz = z, step = len / (n - 1);
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            int o = i * 5;
            k[o] = px;
            k[o + 1] = py;
            k[o + 2] = pz;
            double narrow = 1 - taper * Math.pow(t, 1.25);
            k[o + 3] = w * narrow;
            k[o + 4] = w * .8 * (1 - taper * .75 * Math.pow(t, 1.25));
            double mid = Math.min(1, t + .5 / (n - 1));
            double[] d = dir(el + curl * Math.pow(mid, bendPower), az + swing * mid);
            px += d[0] * step;
            py += d[1] * step;
            pz += d[2] * step;
        }
        add(flex, k);
        return this;
    }

    /**
     * A lock of a Super Saiyan fan: {@code theta} degrees from upright across the face (+: to the left), leaning
     * {@code lean} degrees back, rooted where that line from the middle of the head at depth {@code z} meets the scalp.
     */
    HairSculpt fan(double z, double theta, double lean, double len, double w, double curl, double taper) {
        double t = Math.toRadians(theta), l = Math.toRadians(lean);
        double cx = Math.sin(t), cy = -Math.cos(t);
        double r = Math.min(cy < -1e-3 ? 3.3 / -cy : 99, Math.abs(cx) > 1e-3 ? 4.3 / Math.abs(cx) : 99);
        double rx = cx * r, ry = Math.max(-8.55, -5.3 + cy * r);
        double dx = Math.sin(t) * Math.cos(l), dy = -Math.cos(t) * Math.cos(l), dz = Math.sin(l);
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -dy)))), az = Math.toDegrees(Math.atan2(dx, dz));
        double h = Math.sin(theta * 7.31 + z * 3.7 + lean) * 43758.5453;
        double jag = 1 + jagged * ((h - Math.floor(h)) - .5);                 // neighbours differ: a ragged outline
        return spike(rx, ry, z, el, az, len * jag, w, curl, 0, .1, 1, taper);
    }

    /** {@link #fan} on both sides. */
    HairSculpt fans(double z, double theta, double lean, double len, double w, double curl, double taper) {
        fan(z, theta, lean, len, w, curl, taper);
        return fan(z, -theta, lean, len, w, curl, taper);
    }

    /** {@link #spike} on both sides: (x, az) and (-x, -az). */
    HairSculpt pair(double x, double y, double z, double el, double az, double len, double w, double curl) {
        spike(x, y, z, el, az, len, w, curl);
        return spike(-x, y, z, el, -az, len, w, curl);
    }

    HairSculpt pair(double x, double y, double z, double el, double az, double len, double w, double curl, double swing, double flex) {
        spike(x, y, z, el, az, len, w, curl, swing, flex);
        return spike(-x, y, z, el, -az, len, w, curl, -swing, flex);
    }

    HairSculpt pair(double x, double y, double z, double el, double az, double len, double w, double curl, double swing, double flex,
                    double bendPower, double taper) {
        spike(x, y, z, el, az, len, w, curl, swing, flex, bendPower, taper);
        return spike(-x, y, z, el, -az, len, w, curl, -swing, flex, bendPower, taper);
    }

    // ------------------------------------------------------------------ whole-look changes

    /** Every lock thinner by {@code width} and shorter by {@code length} (God's slimmer hair). */
    HairSculpt slimmed(double width, double length) {
        HairSculpt s = copy();
        for (Lock l : s.locks) {
            double[] k = l.k;
            for (int i = 1; i < l.knots(); i++) {
                int o = i * 5;
                k[o] = k[0] + (k[o] - k[0]) * length;
                k[o + 1] = k[1] + (k[o + 1] - k[1]) * length;
                k[o + 2] = k[2] + (k[o + 2] - k[2]) * length;
            }
            for (int i = 0; i < l.knots(); i++) {
                k[i * 5 + 3] *= width;
                k[i * 5 + 4] *= width;
            }
        }
        return s;
    }

    /** Locks that rise are drawn out upward by {@code factor} (Beast's towering crest). */
    HairSculpt towering(double factor) {
        HairSculpt s = copy();
        for (Lock l : s.locks) {
            double[] k = l.k;
            int last = (l.knots() - 1) * 5;
            if (k[last + 1] - k[1] > -2) continue;
            for (int i = 1; i < l.knots(); i++) {
                int o = i * 5;
                k[o + 1] = k[1] + (k[o + 1] - k[1]) * factor;
                k[o + 2] = k[2] + (k[o + 2] - k[2]) * (1 + (factor - 1) * .5);
            }
        }
        return s;
    }

    /** Everything scaled about the crown (a bigger, wilder version). */
    HairSculpt scaled(double factor, double widthFactor) {
        HairSculpt s = copy();
        for (Lock l : s.locks) {
            double[] k = l.k;
            for (int i = 1; i < l.knots(); i++) {
                int o = i * 5;
                k[o] = k[0] + (k[o] - k[0]) * factor;
                k[o + 1] = k[1] + (k[o + 1] - k[1]) * factor;
                k[o + 2] = k[2] + (k[o + 2] - k[2]) * factor;
            }
            for (int i = 0; i < l.knots(); i++) {
                k[i * 5 + 3] *= widthFactor;
                k[i * 5 + 4] *= widthFactor;
            }
        }
        return s;
    }

    /** Another look's locks added to this one. */
    HairSculpt with(HairSculpt other) {
        HairSculpt s = copy();
        for (Lock l : other.locks) s.locks.add(new Lock(l.flex, l.k.clone()));
        return s;
    }

    // ------------------------------------------------------------------ blocks

    List<HairMesh.Segment> build() {
        HairGeometry.Builder b = new HairGeometry.Builder(Form.HairStyle.NONE);
        b.cap(cap);
        for (Lock l : locks) if (l.knots() >= 2) b.tuft(l.flex, l.k);
        return b.build();
    }

    // ------------------------------------------------------------------ one look into another

    /** {@code from} turned {@code progress} of the way into {@code to}. */
    static HairSculpt morph(HairSculpt from, HairSculpt to, float progress) {
        if (progress <= 0) return from;
        if (progress >= 1) return to;
        HairSculpt out = new HairSculpt();
        out.cap = progress < .5f ? from.cap : to.cap;
        int na = from.locks.size(), nb = to.locks.size();
        int[] partner = new int[nb];
        Arrays.fill(partner, -1);
        boolean[] taken = new boolean[na];
        // pair each new lock with the nearest old one, closest pairs first
        List<double[]> pairs = new ArrayList<>();
        for (int j = 0; j < nb; j++) for (int i = 0; i < na; i++) {
            Lock a = from.locks.get(i), b = to.locks.get(j);
            double d = Math.sqrt(sq(a.rx() - b.rx()) + sq(a.ry() - b.ry()) + sq(a.rz() - b.rz()));
            if (d < 4.2) pairs.add(new double[]{d, i, j});
        }
        pairs.sort((p, q) -> Double.compare(p[0], q[0]));
        for (double[] p : pairs) {
            int i = (int) p[1], j = (int) p[2];
            if (taken[i] || partner[j] >= 0) continue;
            taken[i] = true;
            partner[j] = i;
        }
        for (int j = 0; j < nb; j++) {
            float q = local(progress, j);
            Lock b = to.locks.get(j);
            if (partner[j] >= 0) out.locks.add(blend(from.locks.get(partner[j]), b, q));
            else if (q > .03f) out.locks.add(grown(b, q));                   // new: grows out from the root
        }
        for (int i = 0; i < na; i++) {
            if (taken[i]) continue;
            float q = 1 - local(progress, 1000 + i);
            if (q > .03f) out.locks.add(grown(from.locks.get(i), q));          // gone: draws back in
        }
        return out;
    }

    /** Each lock moves on its own a little after or before the others. */
    private static float local(float progress, int index) {
        double h = Math.sin(index * 12.9898 + 4.1414) * 43758.5453;
        float delay = (float) (.32 * (h - Math.floor(h)));
        return Math.max(0, Math.min(1, (progress - delay) / (1 - .32f)));
    }

    /** A lock grown {@code g} of the way out along its own path from the root (or drawn back in), its end tapering. */
    private static Lock grown(Lock l, float g) {
        double[] full = resample(l.k);
        double[] k = resample(full, g);
        for (int i = 0; i < SAMPLES; i++) {
            double tip = i / (double) (SAMPLES - 1);
            double narrow = 1 - .4 * tip * (1 - g);                                // a young lock ends in a point
            k[i * 5 + 3] *= narrow;
            k[i * 5 + 4] *= narrow;
        }
        return new Lock(l.flex, k);
    }

    private static Lock blend(Lock a, Lock b, float q) {
        double[] ka = resample(a.k), kb = resample(b.k), k = new double[SAMPLES * 5];
        double lenA = length(ka), lenB = length(kb);
        if (lenB > lenA * 1.6) {                                              // much longer: turn onto the new path, then grow along it
            double part = lenA / lenB + (1 - lenA / lenB) * Math.max(0, (q - .3) / .7);
            kb = resample(kb, part);
            q = Math.min(1, q / .3f);
        }
        double rx = lerp(ka[0], kb[0], q), ry = lerp(ka[1], kb[1], q), rz = lerp(ka[2], kb[2], q);
        for (int i = 0; i < SAMPLES; i++) {
            int o = i * 5;
            double ax = ka[o] - ka[0], ay = ka[o + 1] - ka[1], az = ka[o + 2] - ka[2];
            double bx = kb[o] - kb[0], by = kb[o + 1] - kb[1], bz = kb[o + 2] - kb[2];
            double la = Math.sqrt(ax * ax + ay * ay + az * az), lb = Math.sqrt(bx * bx + by * by + bz * bz);
            double[] d = slerp(ax, ay, az, la, bx, by, bz, lb, q);
            double len = lerp(la, lb, q);
            k[o] = rx + d[0] * len;
            k[o + 1] = ry + d[1] * len;
            k[o + 2] = rz + d[2] * len;
            k[o + 3] = lerp(ka[o + 3], kb[o + 3], q);
            k[o + 4] = lerp(ka[o + 4], kb[o + 4], q);
        }
        return new Lock(lerp(a.flex, b.flex, q), k);
    }

    /** The direction {@code q} of the way round from one offset to another (swinging about the root, not through the head). */
    private static double[] slerp(double ax, double ay, double az, double la, double bx, double by, double bz, double lb, float q) {
        if (la < 1e-6 && lb < 1e-6) return new double[]{0, -1, 0};
        if (la < 1e-6) return new double[]{bx / lb, by / lb, bz / lb};
        if (lb < 1e-6) return new double[]{ax / la, ay / la, az / la};
        ax /= la; ay /= la; az /= la;
        bx /= lb; by /= lb; bz /= lb;
        double dot = Math.max(-1, Math.min(1, ax * bx + ay * by + az * bz));
        double theta = Math.acos(dot);
        if (theta < 1e-4) return new double[]{ax, ay, az};
        if (Math.PI - theta < 1e-3) {                                        // straight opposite: go round over the top
            bx = bx * .98;
            by = by * .98 - .2;
            double l = Math.sqrt(bx * bx + by * by + bz * bz);
            bx /= l; by /= l; bz /= l;
            dot = ax * bx + ay * by + az * bz;
            theta = Math.acos(Math.max(-1, Math.min(1, dot)));
        }
        double s = Math.sin(theta), wa = Math.sin((1 - q) * theta) / s, wb = Math.sin(q * theta) / s;
        return new double[]{ax * wa + bx * wb, ay * wa + by * wb, az * wa + bz * wb};
    }

    /** A lock's knots respaced evenly along its length, always {@link #SAMPLES} of them. */
    static double[] resample(double[] k) {
        return resample(k, 1);
    }

    /** The first {@code part} of a lock (by length), respaced into {@link #SAMPLES} knots. */
    static double[] resample(double[] k, double part) {
        int n = k.length / 5;
        double[] at = new double[n];
        for (int i = 1; i < n; i++) {
            int o = i * 5, p = (i - 1) * 5;
            at[i] = at[i - 1] + Math.sqrt(sq(k[o] - k[p]) + sq(k[o + 1] - k[p + 1]) + sq(k[o + 2] - k[p + 2]));
        }
        double total = at[n - 1];
        double[] out = new double[SAMPLES * 5];
        int seg = 0;
        for (int s = 0; s < SAMPLES; s++) {
            double want = total * part * s / (SAMPLES - 1);
            while (seg < n - 2 && at[seg + 1] < want) seg++;
            double span = at[seg + 1] - at[seg];
            double t = span < 1e-9 ? 0 : Math.max(0, Math.min(1, (want - at[seg]) / span));
            for (int c = 0; c < 5; c++) out[s * 5 + c] = lerp(k[seg * 5 + c], k[(seg + 1) * 5 + c], t);
        }
        return out;
    }


    /** How long a lock is, along its knots. */
    static double length(double[] k) {
        double total = 0;
        for (int i = 5; i < k.length; i += 5) total += Math.sqrt(sq(k[i] - k[i - 5]) + sq(k[i + 1] - k[i - 4]) + sq(k[i + 2] - k[i - 3]));
        return total;
    }
    private static double sq(double v) {
        return v * v;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
