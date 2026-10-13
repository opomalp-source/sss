package com.dbzenith.client.hair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A head of hair at rest: the fitted scalp and the locks over it. One look turns into another lock by lock
 * ({@link #morph}): each new lock takes over the old one rooted nearest it and swings round its root to its new angle
 * and length (a lock that must grow much longer first turns onto its new path and then grows along it); locks only
 * the new look has grow out from the scalp along their own path, pointed while young; locks it lacks draw back in.
 * Each lock moves a moment after the last.
 */
public record HairShape(HairCap cap, List<HairLock> locks) {
    public static final HairShape BALD = new HairShape(HairCap.NONE, List.of());

    public boolean isEmpty() {
        return locks.isEmpty();
    }

    /** {@code from} turned {@code progress} of the way into {@code to}. */
    public static HairShape morph(HairShape from, HairShape to, float progress) {
        if (progress <= 0) return from;
        if (progress >= 1) return to;
        List<HairLock> out = new ArrayList<>();
        int na = from.locks.size(), nb = to.locks.size();
        int[] partner = new int[nb];
        Arrays.fill(partner, -1);
        boolean[] taken = new boolean[na];
        List<double[]> pairs = new ArrayList<>();
        for (int j = 0; j < nb; j++) for (int i = 0; i < na; i++) {
            HairLock a = from.locks.get(i), b = to.locks.get(j);
            double d = Math.sqrt(HairLock.sq(a.x(0) - b.x(0)) + HairLock.sq(a.y(0) - b.y(0)) + HairLock.sq(a.z(0) - b.z(0)));
            if (d < 3.6) pairs.add(new double[]{d, i, j});
        }
        pairs.sort((p, q) -> Double.compare(p[0], q[0]));
        for (double[] p : pairs) {
            int i = (int) p[1], j = (int) p[2];
            if (taken[i] || partner[j] >= 0) continue;
            taken[i] = true;
            partner[j] = i;
        }
        for (int j = 0; j < nb; j++) {
            float q = local(progress, to.locks.get(j));
            HairLock b = to.locks.get(j);
            if (partner[j] >= 0) out.add(blend(from.locks.get(partner[j]), b, q));
            else if (q > .03f) out.add(grown(b, Math.min(1, q)));
        }
        for (int i = 0; i < na; i++) {
            if (taken[i]) continue;
            float q = 1 - Math.min(1, local(progress, from.locks.get(i)));
            if (q > .03f) out.add(grown(from.locks.get(i), q));
        }
        return new HairShape(progress < .5f ? from.cap : to.cap, List.copyOf(out));
    }

    /**
     * How far one lock has got: the change sweeps from the forehead back to the nape (a wave through the hair, not all
     * at once), and each lock eases out past its new shape a little and settles back (it whips into place).
     */
    private static float local(float progress, HairLock l) {
        float front = (float) Math.max(0, Math.min(1, (l.z(0) + 4.4) / 8.8));      // 0 at the brow, 1 at the nape
        float x = Math.max(0, Math.min(1, (progress - .38f * front) / .62f));
        float c = 1.9f;                                                               // ease out with a small overshoot
        return 1 + (c + 1) * (float) Math.pow(x - 1, 3) + c * (float) Math.pow(x - 1, 2);
    }

    /** A lock grown {@code g} of the way out along its own path (or drawn back in), its end tapering to a point. */
    static HairLock grown(HairLock l, float g) {
        double[] k = HairLock.resample(l.k(), Math.max(.02, g));
        for (int i = 0; i < HairLock.KNOTS; i++) {
            double tip = i / (double) (HairLock.KNOTS - 1);
            double narrow = 1 - .45 * tip * (1 - g);
            k[i * 5 + 3] *= narrow;
            k[i * 5 + 4] *= narrow;
        }
        return new HairLock(k, l.stiff(), l.weight());
    }

    private static HairLock blend(HairLock a, HairLock b, float q) {
        double[] ka = a.k(), kb = b.k(), k = new double[HairLock.KNOTS * 5];
        double lenA = a.length(), lenB = b.length();
        if (lenB > lenA * 1.6) {                                              // much longer: turn onto the new path, then grow along it
            double part = lenA / lenB + (1 - lenA / lenB) * Math.max(0, (q - .3) / .7);
            kb = HairLock.resample(kb, part);
            q = Math.min(1, q / .3f);
        } else if (lenA > lenB * 1.6) {                                       // much shorter: draw in along the old path, then turn
            double part = lenB / lenA + (1 - lenB / lenA) * Math.max(0, 1 - q / .7);
            ka = HairLock.resample(ka, part);
            q = Math.max(0, (q - .7f) / .3f);
        }
        double rx = lerp(ka[0], kb[0], q), ry = lerp(ka[1], kb[1], q), rz = lerp(ka[2], kb[2], q);
        for (int i = 0; i < HairLock.KNOTS; i++) {
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
        return new HairLock(k, (float) lerp(a.stiff(), b.stiff(), q), (float) lerp(a.weight(), b.weight(), q));
    }

    /** The direction {@code q} of the way round from one offset to another: swinging about the root, never through the head. */
    private static double[] slerp(double ax, double ay, double az, double la, double bx, double by, double bz, double lb, float q) {
        if (la < 1e-6 && lb < 1e-6) return new double[]{0, -1, 0};
        if (la < 1e-6) return new double[]{bx / lb, by / lb, bz / lb};
        if (lb < 1e-6) return new double[]{ax / la, ay / la, az / la};
        ax /= la; ay /= la; az /= la;
        bx /= lb; by /= lb; bz /= lb;
        double dot = Math.max(-1, Math.min(1, ax * bx + ay * by + az * bz));
        double theta = Math.acos(dot);
        if (theta < 1e-4) return new double[]{ax, ay, az};
        if (Math.PI - theta < 1e-3) {
            by -= .2;
            double l = Math.sqrt(bx * bx + by * by + bz * bz);
            bx /= l; by /= l; bz /= l;
            theta = Math.acos(Math.max(-1, Math.min(1, ax * bx + ay * by + az * bz)));
        }
        double s = Math.sin(theta), wa = Math.sin((1 - q) * theta) / s, wb = Math.sin(q * theta) / s;
        return new double[]{ax * wa + bx * wb, ay * wa + by * wb, az * wa + bz * wb};
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
