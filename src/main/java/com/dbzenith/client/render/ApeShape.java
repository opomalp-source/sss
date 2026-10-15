package com.dbzenith.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Great Ape's shape (the Great Ape rework), as plain data with no Minecraft classes, so both the model
 * ({@link GreatApeModel}) and the texture painter ({@code tools/GreatApeGen.java}, run with the compiled classes on its
 * class path) read the same boxes and the same texture layout.
 * <p>Units are model pixels, y down, feet at y = 24, -z forward. A hunched, massive ape: short bent legs and long
 * feet, a heavy pelvis, a torso leaning forward with a barrel chest and a hump of muscle behind the neck, long arms
 * in three parts reaching the knees, a big head with a heavy brow, a muzzle, fangs and a jaw that opens, and a tail
 * in four segments. The texture is laid out on a {@link #TEX_W} x {@link #TEX_H} grid (painted at {@link #PAINT}
 * times that).
 */
public final class ApeShape {
    public static final int TEX_W = 128, TEX_H = 64, PAINT = 4;
    /** What a box is covered in. */
    public static final int FUR = 0, SKIN = 1, DARK = 2, TOOTH = 3, CLAW = 4;

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
        // the trunk
        part("pelvis", "", 0, 14, 1.5f, 0, 0, 0);
        box("pelvis", -5.5f, -3, -3.5f, 11, 5, 7, FUR);
        part("torso", "pelvis", 0, -3, 0, 0.42f, 0, 0);
        box("torso", -6, -6, -4, 12, 6, 8, FUR);                                  // belly
        box("torso", -4, -5.5f, -4.4f, 8, 5, 0.6f, SKIN);                           // the bare belly
        box("torso", -8, -13, -5, 16, 8, 10, FUR);                                  // the barrel chest
        box("torso", -5.5f, -12, -5.6f, 11, 6, 0.8f, SKIN);                         // the bare chest
        box("torso", -5, -15, -2, 10, 3, 6, FUR);                                   // the hump behind the neck
        // the head
        part("head", "torso", 0, -13, -4, -0.42f, 0, 0);
        box("head", -4.5f, -8, -6, 9, 8, 8, FUR);                                   // skull (the face painted on its front)
        box("head", -5, -6.8f, -6.8f, 10, 1.4f, 1.5f, FUR);                         // brow ridge
        box("head", -3.5f, -3.6f, -9, 7, 3.6f, 3.5f, SKIN);                         // muzzle
        box("head", -1.5f, -3.8f, -9.5f, 3, 1.2f, 0.6f, DARK);                      // nose
        box("head", -3, -9.6f, -4.5f, 6, 1.8f, 5, FUR);                             // the spiky crown
        box("head", -3, -0.2f, -8.9f, 0.9f, 1.4f, 0.9f, TOOTH);                    // upper fangs
        box("head", 2.1f, -0.2f, -8.9f, 0.9f, 1.4f, 0.9f, TOOTH);
        part("jaw", "head", 0, -0.5f, -5.5f, 0, 0, 0);
        box("jaw", -3.2f, 0, -3.3f, 6.4f, 1.8f, 3.3f, SKIN);
        box("jaw", -2.6f, -1, -3.1f, 0.8f, 1, 0.8f, TOOTH);                          // lower fangs
        box("jaw", 1.8f, -1, -3.1f, 0.8f, 1, 0.8f, TOOTH);
        part("right_ear", "head", -4.5f, -5.5f, -2, 0, 0, -0.25f);
        part("left_ear", "head", 4.5f, -5.5f, -2, 0, 0, 0.25f);
        pair("right_ear", "left_ear", -1.5f, -1.5f, -0.5f, 1.5f, 2.5f, 1, SKIN);
        part("right_cheek", "head", -4.5f, -3.5f, -3.5f, 0, 0, 0.3f);
        part("left_cheek", "head", 4.5f, -3.5f, -3.5f, 0, 0, -0.3f);
        pair("right_cheek", "left_cheek", -1.5f, -1, -2, 1.5f, 4, 4, FUR);        // fur tufts at the cheeks
        // the arms: shoulder, elbow, wrist
        part("right_arm", "torso", -9, -11.5f, -1.5f, -0.42f, 0, 0.12f);
        part("left_arm", "torso", 9, -11.5f, -1.5f, -0.42f, 0, -0.12f);
        pair("right_arm", "left_arm", -3, -1.5f, -3, 6, 10, 6, FUR);
        part("right_forearm", "right_arm", 0, 8.5f, 0, -0.15f, 0, 0);
        part("left_forearm", "left_arm", 0, 8.5f, 0, -0.15f, 0, 0);
        pair("right_forearm", "left_forearm", -3.3f, 0, -3.3f, 6.6f, 9, 6.6f, FUR);
        part("right_hand", "right_forearm", 0, 9, 0, 0, 0, 0);
        part("left_hand", "left_forearm", 0, 9, 0, 0, 0, 0);
        pair("right_hand", "left_hand", -3, 0, -3.5f, 6, 3, 6.5f, SKIN);
        pair("right_hand", "left_hand", -2.8f, 2.6f, -3.9f, 5.6f, 0.9f, 1.2f, CLAW);   // knuckles and nails
        // the legs: hip, knee, ankle
        part("right_leg", "pelvis", -3.5f, 1, 0, -0.25f, 0, 0.05f);
        part("left_leg", "pelvis", 3.5f, 1, 0, -0.25f, 0, -0.05f);
        pair("right_leg", "left_leg", -3, 0, -3, 6, 5.5f, 6, FUR);
        part("right_shin", "right_leg", 0, 5, 0, 0.35f, 0, 0);
        part("left_shin", "left_leg", 0, 5, 0, 0.35f, 0, 0);
        pair("right_shin", "left_shin", -2.6f, 0, -2.6f, 5.2f, 3.5f, 5.2f, FUR);
        part("right_foot", "right_shin", 0, 3.2f, 0, -0.1f, 0, 0);
        part("left_foot", "left_shin", 0, 3.2f, 0, -0.1f, 0, 0);
        pair("right_foot", "left_foot", -3, 0, -5.5f, 6, 1.3f, 8, SKIN);
        // the tail: four segments, curling
        part("tail", "pelvis", 0, -0.5f, 3.4f, -0.5f, 0, 0);
        box("tail", -1.3f, -1.3f, 0, 2.6f, 2.6f, 6, FUR);
        part("tail2", "tail", 0, 0, 5.6f, 0.3f, 0, 0);
        box("tail2", -1.2f, -1.2f, 0, 2.4f, 2.4f, 6, FUR);
        part("tail3", "tail2", 0, 0, 5.6f, 0.35f, 0, 0);
        box("tail3", -1.1f, -1.1f, 0, 2.2f, 2.2f, 6, FUR);
        part("tail4", "tail3", 0, 0, 5.6f, 0.4f, 0, 0);
        box("tail4", -1, -1, 0, 2, 2, 5.5f, FUR);
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
