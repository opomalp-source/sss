package com.dbzenith.client.hair;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link HairShape} by hand. Head pixels: x is the wearer's left, y is down (the top of the head is -8), z is
 * back (the face is at -4). A lock leaves the scalp heading {@code el} degrees above the horizontal and {@code az}
 * degrees round from straight back (+90: to the left, 180: forward over the face), and bends by {@code curl} degrees
 * of elevation along its length (negative: it falls). Locks taper to a point.
 */
final class Sculpt {
    private HairCap cap = HairCap.NONE;
    private final List<HairLock> locks = new ArrayList<>();
    private float stiff = .55f, weight = 1f;
    /** Straight hair: locks end blunt and rounded instead of in a point. */
    boolean blunt;
    /** How much fan locks differ in length, one to the next (a ragged outline). */
    double jagged = .3;

    Sculpt cap(double fringe, double peak, double temples, double sides, double back) {
        cap = HairCap.of(fringe, peak, temples, sides, back);
        return this;
    }

    /** Stiffness (0 hangs free, 1 never moves) and weight of the locks that follow. */
    Sculpt feel(float stiff, float weight) {
        this.stiff = stiff;
        this.weight = weight;
        return this;
    }

    // ------------------------------------------------------------------ locks

    /** Unit direction {@code el} degrees up and {@code az} round from the back. */
    static double[] dir(double el, double az) {
        double e = Math.toRadians(el), a = Math.toRadians(az);
        return new double[]{Math.sin(a) * Math.cos(e), -Math.sin(e), Math.cos(a) * Math.cos(e)};
    }

    Sculpt lock(double x, double y, double z, double el, double az, double len, double w, double curl) {
        return lock(x, y, z, el, az, len, w, curl, 0, .5, 1);
    }

    /**
     * The full form: {@code swing} turns it round as it goes, {@code thick} is its thickness as a share of its width,
     * {@code bend} below 1 puts the bend near the root (it bends early, then runs straight).
     */
    Sculpt lock(double x, double y, double z, double el, double az, double len, double w, double curl, double swing, double thick, double bend) {
        int n = 12;
        double[] k = new double[(n + 1) * 5];
        double px = x, py = y, pz = z, step = len / n;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            int o = i * 5;
            k[o] = px;
            k[o + 1] = py;
            k[o + 2] = pz;
            double narrow = blunt ? 1 - .45 * Math.pow(t, 3) : Math.pow(1 - t, .85) * (1 + .6 * t) + .04;   // straight hair ends blunt; spikes are leaves
            k[o + 3] = w * narrow;
            k[o + 4] = w * thick * (1 - .55 * t);
            double mid = Math.min(1, t + .5 / n);
            double[] d = dir(el + curl * Math.pow(mid, bend), az + swing * mid);
            px += d[0] * step;
            py += d[1] * step;
            pz += d[2] * step;
        }
        locks.add(new HairLock(HairLock.resample(k, 1), stiff, weight));
        return this;
    }


    /** A straight-ish lock from (x, y, z) aimed at a point (ax, ay, az), reaching {@code reach} of the way there. */
    Sculpt toward(double x, double y, double z, double ax, double ay, double az, double reach, double w, double curl) {
        double dx = ax - x, dy = ay - y, dz = az - z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -dy / d)))), a = Math.toDegrees(Math.atan2(dx, dz));
        return lock(x, y, z, el - curl * .5, a, d * reach, w, curl);
    }
    /** {@link #lock} on both sides, mirrored. */
    Sculpt pair(double x, double y, double z, double el, double az, double len, double w, double curl) {
        lock(x, y, z, el, az, len, w, curl);
        return lock(-x, y, z, el, -az, len, w, curl);
    }

    Sculpt pair(double x, double y, double z, double el, double az, double len, double w, double curl, double swing, double thick, double bend) {
        lock(x, y, z, el, az, len, w, curl, swing, thick, bend);
        return lock(-x, y, z, el, -az, len, w, curl, -swing, thick, bend);
    }

    /**
     * A lock of a fan: {@code theta} degrees from upright across the face (+: to the left), leaning {@code lean}
     * degrees back, rooted where that line from the middle of the head at depth {@code z} meets the scalp.
     */
    Sculpt fan(double z, double theta, double lean, double len, double w, double curl) {
        double t = Math.toRadians(theta), l = Math.toRadians(lean);
        double cx = Math.sin(t), cy = -Math.cos(t);
        double r = Math.min(cy < -1e-3 ? 3.4 / -cy : 99, Math.abs(cx) > 1e-3 ? 4.3 / Math.abs(cx) : 99);
        double rx = cx * r, ry = Math.max(-8.5, -5.2 + cy * r);
        double dx = Math.sin(t) * Math.cos(l), dy = -Math.cos(t) * Math.cos(l), dz = Math.sin(l);
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -dy)))), az = Math.toDegrees(Math.atan2(dx, dz));
        double h = Math.sin(theta * 7.31 + z * 3.7 + lean * .9) * 43758.5453;
        double jag = 1 + jagged * ((h - Math.floor(h)) - .5);
        return lock(rx, ry, z, el, az, len * jag, w, curl, 0, .5, 1);
    }

    /** {@link #fan} on both sides. */
    Sculpt fans(double z, double theta, double lean, double len, double w, double curl) {
        fan(z, theta, lean, len, w, curl);
        return fan(z, -theta, lean, len, w, curl);
    }

    /** A lock given knot by knot (x, y, z, width, thickness each): the hand-placed base styles. */
    Sculpt knots(double... data) {
        locks.add(new HairLock(HairLock.resample(data, 1), stiff, weight));
        return this;
    }

    /** A straight lock from one point to another, bowing a little backward (hand-placed styles). */
    Sculpt straight(double x, double y, double z, double ex, double ey, double ez, double w, double d) {
        int steps = 4;
        double[] k = new double[(steps + 1) * 5];
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int o = i * 5;
            k[o] = x + (ex - x) * t;
            k[o + 1] = y + (ey - y) * t;
            k[o + 2] = z + (ez - z) * t + .24 * Math.sin(Math.PI * t);
            k[o + 3] = w * (1 - .68 * t);
            k[o + 4] = d * (1 - .52 * t);
        }
        return knots(k);
    }

    /** Everything another sculpt holds, added. */
    Sculpt with(HairShape other) {
        locks.addAll(other.locks());
        return this;
    }

    /** Whether {@link #build} also blends new locks between the hand-placed ones (an underlayer is always added). */
    boolean dense = true;

    HairShape build() {
        return new HairShape(cap, List.copyOf(densify(locks, dense)));
    }

    /**
     * Fills the hair out so it reads as a full head of hair, not a few big strands, and nothing shows through: between
     * every two neighbouring locks that flow the same way, a lock blended from both, tucked a little under them; under
     * every lock, a shorter, wider one near the roots. The hand-placed locks stay on top and keep the style's shape.
     */
    static List<HairLock> densify(List<HairLock> locks, boolean blended) {
        int n = locks.size();
        List<HairLock> under = new ArrayList<>(), between = new ArrayList<>();
        double[][] dir = new double[n][];
        for (int i = 0; i < n; i++) {
            HairLock l = locks.get(i);
            int e = HairLock.KNOTS - 1;
            double dx = l.x(e) - l.x(0), dy = l.y(e) - l.y(0), dz = l.z(e) - l.z(0), d = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
            dir[i] = new double[]{dx / d, dy / d, dz / d};
        }
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) {
            HairLock a = locks.get(i);
            int best = -1, second = -1;
            double bestD = 4.6, secondD = 4.6;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                HairLock b = locks.get(j);
                double d = Math.sqrt(HairLock.sq(a.x(0) - b.x(0)) + HairLock.sq(a.y(0) - b.y(0)) + HairLock.sq(a.z(0) - b.z(0)));
                double same = dir[i][0] * dir[j][0] + dir[i][1] * dir[j][1] + dir[i][2] * dir[j][2];
                double ratio = a.length() / Math.max(1e-3, b.length());
                if (same < .5 || ratio < .45 || ratio > 2.2) continue;
                if (d < bestD) { second = best; secondD = bestD; best = j; bestD = d; }
                else if (d < secondD) { second = j; secondD = d; }
            }
            for (int j : new int[]{best, second}) {
                if (!blended || j < 0 || !done.add(Math.min(i, j) * 100000L + Math.max(i, j))) continue;
                between.add(blend(a, locks.get(j)));
            }
            under.add(underlay(a));
        }
        List<HairLock> out = new ArrayList<>(under.size() + between.size() + n);
        out.addAll(under);                                                        // drawn first, underneath
        out.addAll(between);
        out.addAll(locks);
        return out;
    }

    /** A lock half-way between two, a little shorter and tucked under them. */
    private static HairLock blend(HairLock a, HairLock b) {
        double[] k = new double[HairLock.KNOTS * 5];
        for (int i = 0; i < k.length; i++) k[i] = (a.k()[i] + b.k()[i]) * .5;
        k = HairLock.resample(k, .9);
        tuck(k, .35);
        return new HairLock(k, (a.stiff() + b.stiff()) * .5f, (a.weight() + b.weight()) * .5f);
    }

    /** The volume under a lock: its first half, wider, tucked in toward the head. */
    private static HairLock underlay(HairLock l) {
        double[] k = HairLock.resample(l.k(), .55);
        for (int i = 0; i < HairLock.KNOTS; i++) {
            double w = k[i * 5 + 3] * 1.25;
            k[i * 5 + 3] = w;
            k[i * 5 + 4] = Math.max(k[i * 5 + 4] * 1.2, w * .5);                    // a body, not a flat band
        }
        tuck(k, .9);                                                               // well under the locks above
        return new HairLock(k, l.stiff(), l.weight());
    }

    /** Moves a lock's knots (not its root) toward the middle of the head by up to {@code by} pixels. */
    private static void tuck(double[] k, double by) {
        for (int i = 1; i < HairLock.KNOTS; i++) {
            int o = i * 5;
            double dx = k[o], dy = k[o + 1] + 4.2, dz = k[o + 2] - .6, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1e-6) continue;
            double f = by * i / (HairLock.KNOTS - 1.0) / d;
            k[o] -= dx * f;
            k[o + 1] -= dy * f;
            k[o + 2] -= dz * f;
        }
    }

    // ------------------------------------------------------------------ whole looks changed

    /** Every lock slimmer by {@code width} and shorter by {@code length} (God's hair). */
    static HairShape slimmed(HairShape s, double width, double length) {
        return map(s, (k, i) -> {
            if (i > 0) scaleFromRoot(k, i, length);
            k[i * 5 + 3] *= width;
            k[i * 5 + 4] *= width;
        }, 0, 1);
    }

    /** Everything grown about its roots by {@code factor}, widths by {@code wide}; stiffness and weight set. */
    static HairShape scaled(HairShape s, double factor, double wide, float stiff, float weight) {
        return map(s, (k, i) -> {
            if (i > 0) scaleFromRoot(k, i, factor);
            k[i * 5 + 3] *= wide;
            k[i * 5 + 4] *= wide;
        }, stiff, weight);
    }

    /** Locks that rise drawn out upward by {@code factor} (Beast's crest). */
    static HairShape towering(HairShape s, double factor) {
        List<HairLock> out = new ArrayList<>();
        for (HairLock l : s.locks()) {
            double[] k = l.k().clone();
            if (l.y(HairLock.KNOTS - 1) - l.y(0) < -2) {
                for (int i = 1; i < HairLock.KNOTS; i++) {
                    k[i * 5 + 1] = k[1] + (k[i * 5 + 1] - k[1]) * factor;
                    k[i * 5 + 2] = k[2] + (k[i * 5 + 2] - k[2]) * (1 + (factor - 1) * .4);
                }
            }
            out.add(new HairLock(k, l.stiff(), l.weight()));
        }
        return new HairShape(s.cap(), List.copyOf(out));
    }

    private interface KnotEdit {
        void edit(double[] k, int i);
    }

    private static HairShape map(HairShape s, KnotEdit e, float stiff, float weight) {
        List<HairLock> out = new ArrayList<>();
        for (HairLock l : s.locks()) {
            double[] k = l.k().clone();
            for (int i = 0; i < HairLock.KNOTS; i++) e.edit(k, i);
            out.add(new HairLock(k, stiff > 0 ? stiff : l.stiff(), weight > 0 ? weight : l.weight()));
        }
        return new HairShape(s.cap(), List.copyOf(out));
    }

    private static void scaleFromRoot(double[] k, int i, double f) {
        k[i * 5] = k[0] + (k[i * 5] - k[0]) * f;
        k[i * 5 + 1] = k[1] + (k[i * 5 + 1] - k[1]) * f;
        k[i * 5 + 2] = k[2] + (k[i * 5 + 2] - k[2]) * f;
    }
}
