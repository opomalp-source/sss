package com.dbzenith.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Great Ape grows on the player's own head (v0.134, after the user's pictures), as plain data with no
 * Minecraft classes, so both the model ({@link ApeHeadModel}) and the texture painter ({@code tools/GreatApeGen.java},
 * run with the compiled classes on its class path) read the same boxes and the same texture layout.
 * <p>Units are model pixels in the head's own space (the player head is the box -4..4, -8..0, -4..4; its face at
 * z = -4, y down): a heavy brow over the eyes, a blunt muzzle with a dark nose, a row of bared fangs over a jaw that
 * drops open, fur tufts at the cheeks and pointed ears. The texture is laid out on a {@link #TEX_W} x {@link #TEX_H}
 * grid (painted at {@link #PAINT} times that).
 */
public final class ApeShape {
    public static final int TEX_W = 64, TEX_H = 32, PAINT = 4;
    /** What a box is covered in. */
    public static final int FUR = 0, SKIN = 1, DARK = 2, TOOTH = 3, BROW = 4, MOUTH = 5;

    /** A part: its parent ("" for the root), its pivot (in the parent's space), its rest rotation (radians). */
    public record Part(String name, String parent, float px, float py, float pz, float rx, float ry, float rz) {}

    /** A box of a part: its corner and size, its material, mirrored texture (the left side), and where its texture sits. */
    public record Box(String part, float x, float y, float z, float w, float h, float d, int mat, boolean mirror, int u, int v) {
        /** The texture region it takes: 2(w + d) wide, h + d tall (rounded up). */
        public int texW() {
            return (int) Math.ceil(2 * (w + d));
        }

        public int texH() {
            return (int) Math.ceil(h + d);
        }
    }

    public static final Map<String, Part> PARTS = new LinkedHashMap<>();
    public static final List<Box> BOXES = new ArrayList<>();

    private static final List<float[]> RAW = new ArrayList<>();
    private static final List<Object[]> RAW_META = new ArrayList<>();

    private ApeShape() {}

    private static void part(String name, String parent, float px, float py, float pz, float rx, float ry, float rz) {
        PARTS.put(name, new Part(name, parent, px, py, pz, rx, ry, rz));
    }

    private static void box(String part, float x, float y, float z, float w, float h, float d, int mat) {
        RAW.add(new float[]{x, y, z, w, h, d});
        RAW_META.add(new Object[]{part, mat, false});
    }

    /** A box and its mirror on the other side (x flipped), for a part and its left twin. */
    private static void pair(String right, String left, float x, float y, float z, float w, float h, float d, int mat) {
        box(right, x, y, z, w, h, d, mat);
        RAW.add(new float[]{-x - w, y, z, w, h, d});
        RAW_META.add(new Object[]{left, mat, true});
    }

    static {
        part("snout", "", 0, 0, 0, 0, 0, 0);
        box("snout", -4.4f, -6.5f, -5.0f, 8.8f, 1.1f, 1.1f, BROW);                // the heavy brow over the eyes
        box("snout", -3.3f, -3.6f, -7.2f, 6.6f, 2.0f, 3.4f, SKIN);                 // the muzzle
        box("snout", -1.6f, -3.75f, -7.5f, 3.2f, 0.9f, 0.7f, DARK);                // the nose
        box("snout", -3.0f, -1.6f, -6.9f, 6.0f, 0.8f, 3.0f, TOOTH);               // the upper fangs
        box("snout", -2.8f, -1.6f, -6.7f, 5.6f, 1.8f, 2.8f, MOUTH);               // inside the mouth (seen when it opens)
        part("right_cheek", "snout", -4.0f, -4.6f, -3.0f, 0, 0, 0.12f);
        part("left_cheek", "snout", 4.0f, -4.6f, -3.0f, 0, 0, -0.12f);
        pair("right_cheek", "left_cheek", -0.9f, 0, -1.6f, 1.0f, 3.8f, 3.4f, FUR);   // tufts of fur at the cheeks
        part("jaw", "snout", 0, -0.8f, -3.9f, 0, 0, 0);
        box("jaw", -3.1f, 0, -3.2f, 6.2f, 1.4f, 3.3f, SKIN);                       // the jaw and chin
        box("jaw", -2.8f, -0.6f, -2.85f, 5.6f, 0.6f, 2.65f, TOOTH);                 // the lower fangs (behind the upper ones when shut)
        part("right_ear", "snout", -4.0f, -5.4f, 0.5f, 0, -0.2f, 0.45f);
        part("left_ear", "snout", 4.0f, -5.4f, 0.5f, 0, 0.2f, -0.45f);
        pair("right_ear", "left_ear", -2.6f, -1.8f, -0.5f, 2.6f, 2.8f, 1.0f, SKIN);   // pointed ears
        pack();
    }

    /** Shelf-packs every box's texture region (tallest first) on the layout grid. A mirrored twin shares its pair's region. */
    private static void pack() {
        int n = RAW.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            float[] p = RAW.get(a), q = RAW.get(b);
            return Float.compare(q[4] + q[5], p[4] + p[5]);
        });
        int[] us = new int[n], vs = new int[n];
        int x = 0, y = 0, row = 0;
        for (int i : order) {
            if ((Boolean) RAW_META.get(i)[2]) continue;                              // twins: below
            float[] r = RAW.get(i);
            int w = (int) Math.ceil(2 * (r[3] + r[5])), h = (int) Math.ceil(r[4] + r[5]);
            if (x + w > TEX_W) {
                x = 0;
                y += row;
                row = 0;
            }
            if (y + h > TEX_H) throw new IllegalStateException("the Great Ape's texture layout is full");
            us[i] = x;
            vs[i] = y;
            x += w;
            row = Math.max(row, h);
        }
        for (int i = 0; i < n; i++) {
            if ((Boolean) RAW_META.get(i)[2]) {                                      // a twin follows the box added just before it
                us[i] = us[i - 1];
                vs[i] = vs[i - 1];
            }
            float[] r = RAW.get(i);
            Object[] m = RAW_META.get(i);
            BOXES.add(new Box((String) m[0], r[0], r[1], r[2], r[3], r[4], r[5], (Integer) m[1], (Boolean) m[2], us[i], vs[i]));
        }
    }
}
