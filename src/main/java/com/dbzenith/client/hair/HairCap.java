package com.dbzenith.client.hair;

import java.util.ArrayList;
import java.util.List;

/**
 * The fitted scalp under the locks: a thin shell over the top of the head, down the sides and back, ending at a
 * hairline. {@code fringe} is how far down the forehead it comes (head pixels, -8 is the top of the head),
 * {@code peak} how much deeper it dips in the middle (a widow's peak), {@code temples} how far it recedes at the
 * corners of the brow, {@code sides} where it ends over the ears, {@code back} where it ends at the nape.
 * Built once into quads ({@link #quads}: 4 corners x, y, z then the normal, per face).
 */
public record HairCap(double fringe, double peak, double temples, double sides, double back, float[] quads) {
    private static final double OUT = 4.42, IN = 3.98, TOP = -8.45, UNDER = -7.92;
    /** No scalp at all (bald, or a style that brings its own). */
    public static final HairCap NONE = new HairCap(0, 0, 0, 0, 0, new float[0]);

    public static HairCap of(double fringe, double peak, double temples, double sides, double back) {
        List<double[]> boxes = new ArrayList<>();
        boxes.add(new double[]{-OUT, TOP, -OUT, OUT, UNDER, OUT});                       // the crown
        int cols = 8;
        double step = OUT * 2 / cols;
        for (int c = 0; c < cols; c++) {
            double x0 = -OUT + c * step, x1 = x0 + step, mx = Math.abs((x0 + x1) / 2);
            double front = fringe + peak * Math.max(0, 1 - mx / 1.7) - temples * smooth(1.2, 3.4, mx);
            if (front > UNDER) boxes.add(new double[]{x0, UNDER - .01, -OUT, x1, front, -IN});   // the brow
            double nape = back + ((c & 1) == 0 ? .5 : 0);
            if (nape > UNDER) boxes.add(new double[]{x0, UNDER - .01, IN, x1, nape, OUT});       // the back
        }
        for (int s : new int[]{-1, 1}) for (int c = 0; c < cols; c++) {
            double z0 = -OUT + c * step, z1 = z0 + step, f = (c + .5) / cols;
            double temple = fringe - temples;
            double end = f < .35 ? temple + (sides - temple) * smooth(0, .35, f) : f < .7 ? sides : sides + (back - sides) * smooth(.7, 1, f);
            double x0 = s < 0 ? -OUT : IN, x1 = s < 0 ? -IN : OUT;
            if (end > UNDER) boxes.add(new double[]{x0, UNDER - .01, z0, x1, end, z1});     // the sides
        }
        List<Float> q = new ArrayList<>();
        for (double[] b : boxes) box(q, b);
        float[] quads = new float[q.size()];
        for (int i = 0; i < quads.length; i++) quads[i] = q.get(i);
        return new HairCap(fringe, peak, temples, sides, back, quads);
    }

    private static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** A box's six faces: 4 corners and the outward normal each. */
    private static void box(List<Float> q, double[] b) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        face(q, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(q, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(q, 0, 0, -1, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
        face(q, 0, 0, 1, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);
        face(q, -1, 0, 0, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);
        face(q, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
    }

    private static void face(List<Float> q, float nx, float ny, float nz, double... p) {
        for (double v : p) q.add((float) v);
        q.add(nx);
        q.add(ny);
        q.add(nz);
    }
}
