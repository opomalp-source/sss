package com.dbzenith.client.hair;

/**
 * One lock of hair at rest, in head pixels (x: the wearer's left, y: down, z: back; the head is the box -4..4,
 * -8..0, -4..4 with the face at -z). Always {@link #KNOTS} knots, root first, each x, y, z, width (across the scalp)
 * and thickness (away from it). {@code stiff} is how hard the lock springs back to this shape (0 hangs free, 1 never
 * moves); {@code weight} how much gravity and wind move it.
 */
public record HairLock(double[] k, float stiff, float weight) {
    public static final int KNOTS = 7;

    public double x(int i) { return k[i * 5]; }
    public double y(int i) { return k[i * 5 + 1]; }
    public double z(int i) { return k[i * 5 + 2]; }
    public double w(int i) { return k[i * 5 + 3]; }
    public double t(int i) { return k[i * 5 + 4]; }

    /** How long it is, root to tip. */
    public double length() {
        double total = 0;
        for (int i = 1; i < KNOTS; i++) total += Math.sqrt(sq(x(i) - x(i - 1)) + sq(y(i) - y(i - 1)) + sq(z(i) - z(i - 1)));
        return total;
    }

    /** Any polyline of knots (x, y, z, w, t each), respaced evenly into {@link #KNOTS} knots up to {@code part} of its length. */
    public static double[] resample(double[] src, double part) {
        int n = src.length / 5;
        double[] at = new double[n];
        for (int i = 1; i < n; i++) {
            int o = i * 5, p = o - 5;
            at[i] = at[i - 1] + Math.sqrt(sq(src[o] - src[p]) + sq(src[o + 1] - src[p + 1]) + sq(src[o + 2] - src[p + 2]));
        }
        double total = at[n - 1];
        double[] out = new double[KNOTS * 5];
        int seg = 0;
        for (int s = 0; s < KNOTS; s++) {
            double want = total * part * s / (KNOTS - 1);
            while (seg < n - 2 && at[seg + 1] < want) seg++;
            double span = at[Math.min(n - 1, seg + 1)] - at[seg];
            double f = span < 1e-9 ? 0 : Math.max(0, Math.min(1, (want - at[seg]) / span));
            int a = seg * 5, b = Math.min(n - 1, seg + 1) * 5;
            for (int c = 0; c < 5; c++) out[s * 5 + c] = src[a + c] + (src[b + c] - src[a + c]) * f;
        }
        return out;
    }

    static double sq(double v) {
        return v * v;
    }
}
