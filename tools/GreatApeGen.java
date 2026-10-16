import com.dbzenith.client.render.ApeShape;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Paints the Great Ape (v0.134: the player's own figure grown giant, after the user's pictures).
 * <ul>
 *   <li>{@code ape/body_<fur>.png}: a coat of fur in the player skin layout (256 x 256, four texels a model pixel):
 *       fur in tufts falling down every side, lit above and shadowed below; a bare tan chest with pecs and abs, bare
 *       hands with knuckles and feet with toes; on the head's front a tan face framed in fur with angry red eyes under
 *       a dark lid. The outer skin layer is left empty (it is hidden while an ape).</li>
 *   <li>{@code ape/parts_<fur>.png}: the face grown on the head ({@code ApeShape}): the brow, the muzzle with a dark
 *       nose and nostrils, ivory fangs (pointed, longer canines) over a dark mouth, the jaw with its lip and tongue, fur
 *       tufts at the cheeks and pointed ears (cut out of their boxes).</li>
 *   <li>{@code ape/eyes.png}: the irises alone, drawn glowing.</li>
 * </ul>
 * Three furs: the moon ape's brown, the golden ape's gold, the legendary ape's green-gold.
 * <p>Usage, after compiling: java -cp build/classes/java/main tools/GreatApeGen.java
 */
public final class GreatApeGen {
    static final int P = 4;                                                        // texels a model pixel

    record Fur(String name, int base, int light, int dark) {}

    static final Fur[] FURS = {
            new Fur("brown", 0x5A3018, 0x7C4826, 0x2A1408),
            new Fur("golden", 0xD8A22A, 0xFAD870, 0x8A5A10),
            new Fur("legendary", 0x9CA82C, 0xD2DC6E, 0x525C12),
    };
    static final int SKIN = 0xC8946A, SKIN_DARK = 0x8E5E3C, SKIN_LIGHT = 0xE2B48A, NOSE = 0x2A1612, TOOTH = 0xF2EAD6, TOOTH_DARK = 0xB8A88A;
    static final int MOUTH = 0x3A0C0A, TONGUE = 0xB0464A, IRIS = 0xE01810, IRIS_HOT = 0xFF7A3A, PUPIL = 0x1A0606;

    /** Paints one face of a texture region: side 0 top, 1 bottom, 2 the -x side, 3 front, 4 the +x side, 5 back. */
    interface Painter {
        /** ARGB (0 for none), at model pixels (lx, ly) across a face fw x fh; (x, y) the texel. */
        int paint(int side, float lx, float ly, float fw, float fh, int x, int y);
    }

    public static void main(String[] args) throws Exception {
        String dir = "src/main/resources/assets/dbzenith/textures/entity/ape/";
        new File(dir).mkdirs();
        for (Fur fur : FURS) {
            ImageIO.write(body(fur, false), "png", new File(dir + "body_" + fur.name + ".png"));
            ImageIO.write(parts(fur), "png", new File(dir + "parts_" + fur.name + ".png"));
        }
        ImageIO.write(body(FURS[0], true), "png", new File(dir + "eyes.png"));
        System.out.println("GreatApeGen: body 256x256, parts " + ApeShape.TEX_W * P + "x" + ApeShape.TEX_H * P + ", " + ApeShape.BOXES.size() + " boxes");
    }

    // ------------------------------------------------------------------ the body (player skin layout)

    static BufferedImage body(Fur fur, boolean eyesOnly) {
        BufferedImage img = new BufferedImage(64 * P, 64 * P, BufferedImage.TYPE_INT_ARGB);
        cube(img, 0, 0, 8, 8, 8, (s, lx, ly, fw, fh, x, y) -> eyesOnly ? (s == 3 ? eyeGlow(lx, ly) : 0) : head(fur, s, lx, ly, x, y));
        if (eyesOnly) return img;
        cube(img, 16, 16, 8, 12, 4, (s, lx, ly, fw, fh, x, y) -> torso(fur, s, lx, ly, fh, x, y));
        Painter arm = (s, lx, ly, fw, fh, x, y) -> arm(fur, s, lx, ly, fh, x, y);
        cube(img, 40, 16, 4, 12, 4, arm);
        cube(img, 32, 48, 4, 12, 4, arm);
        Painter leg = (s, lx, ly, fw, fh, x, y) -> leg(fur, s, lx, ly, fh, x, y);
        cube(img, 0, 16, 4, 12, 4, leg);
        cube(img, 16, 48, 4, 12, 4, leg);
        return img;
    }

    /** A cube's six faces at texture offset (u, v), in model pixels. */
    static void cube(BufferedImage img, float u, float v, float w, float h, float d, Painter p) {
        face(img, p, 0, u + d, v, w, d);
        face(img, p, 1, u + d + w, v, w, d);
        face(img, p, 2, u, v + d, d, h);
        face(img, p, 3, u + d, v + d, w, h);
        face(img, p, 4, u + d + w, v + d, d, h);
        face(img, p, 5, u + d + w + d, v + d, w, h);
    }

    static void face(BufferedImage img, Painter p, int side, float fu, float fv, float fw, float fh) {
        int x0 = Math.round(fu * P), y0 = Math.round(fv * P), x1 = Math.round((fu + fw) * P), y1 = Math.round((fv + fh) * P);
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
            int c = p.paint(side, (x - x0 + 0.5f) / P, (y - y0 + 0.5f) / P, fw, fh, x, y);
            if (c != 0) img.setRGB(x, y, c);
        }
    }

    static int head(Fur fur, int side, float lx, float ly, int x, int y) {
        if (side == 1) return opaque(shade(SKIN_DARK, 0.8f));                    // under the chin
        if (side != 3) return opaque(fur(fur, x, y, 2, side, side == 0 ? 0.3f : ly / 8));
        return opaque(faceFront(fur, lx, ly, x, y));
    }

    /**
     * The face (8 x 8): tan, framed in fur at the top and the sides; angry red eyes, slanted down to the middle, under
     * a dark lid; a furrow between them. Below the eyes it is covered by the muzzle.
     */
    static int faceFront(Fur fur, float lx, float ly, int x, int y) {
        float mx = Math.abs(lx - 4);
        if (ly < 1.6f || (ly < 2.8f && mx > 3.1f) || (mx > 3.55f && ly < 5.2f)) return fur(fur, x, y, 3, 3, ly / 8);
        int c = mix(SKIN, SKIN_DARK, Math.max(0, ly - 3) * 0.08f);
        c = shade(c, (float) (0.94 + 0.08 * hash(x / 3, y / 3, 7)));
        int eye = eye(lx, ly, false);
        if (eye != 0) return eye;
        for (int e = 0; e < 2; e++) {                                            // the heavy lid over each eye
            float cx = e == 0 ? 2.55f : 5.45f;
            float t = (lx - cx) / 1.05f;
            float k = inner(e, t);
            float top = 3.45f - 0.62f + 0.55f * k;
            if (Math.abs(t) < 1.2f && ly >= top - 0.5f && ly < top) return shade(mix(c, SKIN_DARK, 0.75f), 0.7f);
        }
        if (mx < 0.22f && ly > 2.5f && ly < 3.9f) c = mix(c, SKIN_DARK, 0.5f);  // the furrow
        if (Math.abs(mx - 0.9f) < 0.14f && ly > 3.9f) c = mix(c, SKIN_DARK, 0.35f);   // creases down to the muzzle
        return c;
    }

    /** How far toward the inner corner (the nose) a point across eye {@code e} is: 0 outer .. 1 inner. */
    static float inner(int e, float t) {
        float k = e == 0 ? (t + 1) / 2 : (1 - t) / 2;
        return Math.max(0, Math.min(1, k));
    }

    /** An eye's colour at (lx, ly) on the face, or 0 outside both. With {@code glow}, only the lit iris. */
    static int eye(float lx, float ly, boolean glow) {
        for (int e = 0; e < 2; e++) {
            float cx = e == 0 ? 2.55f : 5.45f, cy = 3.45f;
            float dx = lx - cx, t = dx / 1.05f;
            if (Math.abs(t) > 1) continue;
            float k = inner(e, t);
            float top = cy - 0.62f + 0.55f * k, bottom = cy + 0.5f - 0.3f * t * t;
            if (ly < top || ly > bottom) continue;
            boolean rim = ly - top < 0.18f || bottom - ly < 0.14f || Math.abs(t) > 0.88f;
            boolean pupil = Math.abs(dx) < 0.17f && Math.abs(ly - (top + bottom) / 2) < 0.32f;
            if (glow) return rim || pupil ? 0 : 0xFFFF3A24;
            if (rim) return opaque(0x2A0A08);
            if (pupil) return opaque(PUPIL);
            return opaque(mix(IRIS_HOT, IRIS, Math.min(1, (ly - top) / (bottom - top) * 1.4f)));   // hot at the top, deep red below
        }
        return 0;
    }

    static int eyeGlow(float lx, float ly) {
        return eye(lx, ly, true);
    }

    /** The trunk (8 x 12 x 4): furred, the chest and belly bare with pecs and abs. */
    static int torso(Fur fur, int side, float lx, float ly, float fh, int x, int y) {
        if (side != 3) return opaque(fur(fur, x, y, 5, side, side == 0 ? 0.2f : side == 1 ? 1f : ly / fh));
        boolean bare = lx > 0.75f && lx < 7.25f && ly > 0.9f && ly < 10.7f - Math.abs(lx - 4) * 0.25f;
        if (!bare) return opaque(fur(fur, x, y, 5, 3, ly / fh));
        float mx = Math.abs(lx - 4);
        int c = mix(SKIN, SKIN_DARK, ly / fh * 0.35f);
        c = shade(c, (float) (0.94 + 0.08 * hash(x / 3, y / 3, 11)));
        if (ly < 4.6f) {                                                         // the pecs: lit on top, shadowed beneath
            if (ly < 2.4f && mx > 0.6f) c = mix(c, SKIN_LIGHT, 0.35f);
            float under = 4.2f + 0.25f * (float) Math.cos((mx - 2) * 1.2f);
            if (ly > under && mx > 0.35f) c = mix(c, SKIN_DARK, 0.55f);
        }
        if (mx < 0.17f && ly > 1.2f && ly < 10f) c = mix(c, SKIN_DARK, 0.45f);  // the line down the middle
        if (mx < 2.1f && ly > 5f && ly < 10.2f) {                               // the abs
            for (float row : new float[]{6.3f, 7.9f, 9.4f}) {
                if (Math.abs(ly - row) < 0.14f) c = mix(c, SKIN_DARK, 0.4f);
                else if (ly < row && row - ly < 0.5f) c = mix(c, SKIN_LIGHT, 0.18f);
            }
            if (mx > 1.85f) c = mix(c, SKIN_DARK, 0.3f);
        }
        if (mx > 3f) c = mix(c, SKIN_DARK, (mx - 3) * 0.5f);                    // turning away at the sides
        return opaque(c);
    }

    /** An arm (4 x 12 x 4): furred, the hand bare with knuckles and fingers. */
    static int arm(Fur fur, int side, float lx, float ly, float fh, int x, int y) {
        if (side == 1) return opaque(shade(SKIN, 0.85f));                        // the palm and fingertips
        if (side == 0) return opaque(fur(fur, x, y, 9, 0, 0.2f));
        if (ly < 9.6f) return opaque(fur(fur, x, y, 9, side, ly / 9.6f));
        if (ly < 9.9f) return opaque(fur(fur, x, y, 9, side, 1f));               // the cuff of fur at the wrist
        int c = shade(mix(SKIN, SKIN_DARK, (ly - 9.6f) / 2.4f * 0.4f), (float) (0.94 + 0.08 * hash(x / 2, y / 2, 13)));
        if (Math.abs(ly - 10.7f) < 0.13f) c = mix(c, SKIN_DARK, 0.5f);           // the knuckles
        if (side == 3 && ly > 10.7f && Math.abs((lx % 1f) - 0.5f) > 0.4f) c = mix(c, SKIN_DARK, 0.55f);   // fingers
        return opaque(c);
    }

    /** A leg (4 x 12 x 4): furred, the foot bare with toes. */
    static int leg(Fur fur, int side, float lx, float ly, float fh, int x, int y) {
        if (side == 1) return opaque(shade(SKIN_DARK, 0.9f));                   // the sole
        if (side == 0) return opaque(fur(fur, x, y, 17, 0, 0.3f));
        if (ly < 11.3f) return opaque(fur(fur, x, y, 17, side, ly / 11.3f));               // bare only at the very foot, which boots always cover
        int c = shade(mix(SKIN, SKIN_DARK, 0.2f + (ly - 11.3f) * 0.3f), (float) (0.94 + 0.08 * hash(x / 2, y / 2, 17)));
        if (side == 3 && Math.abs((lx % 1f) - 0.5f) > 0.42f) c = mix(c, SKIN_DARK, 0.55f);   // toes
        return opaque(c);
    }

    // ------------------------------------------------------------------ the face's parts (ApeShape)

    static BufferedImage parts(Fur fur) {
        BufferedImage img = new BufferedImage(ApeShape.TEX_W * P, ApeShape.TEX_H * P, BufferedImage.TYPE_INT_ARGB);
        int seed = 20;
        for (ApeShape.Box b : ApeShape.BOXES) {
            if (b.mirror()) continue;                                               // twins share their pair's paint
            int sd = ++seed;
            cube(img, b.u(), b.v(), b.w(), b.h(), b.d(), (s, lx, ly, fw, fh, x, y) -> part(fur, b, s, lx, ly, fw, fh, x, y, sd));
        }
        return img;
    }

    static int part(Fur fur, ApeShape.Box b, int side, float lx, float ly, float fw, float fh, int x, int y, int seed) {
        boolean ear = b.part().endsWith("_ear"), jaw = b.part().equals("jaw");
        float ty = ly / fh;
        switch (b.mat()) {
            case ApeShape.FUR -> {                                                  // cheek tufts, ragged at the bottom
                if (side >= 2 && ly > fh - 0.35f - 0.6f * (float) hash(x / 3, 1, seed)) return 0;
                return opaque(fur(fur, x, y, seed, side, ty));
            }
            case ApeShape.BROW -> {
                if (side == 0) return opaque(fur(fur, x, y, seed, 0, 0.2f));
                if (side == 1) return opaque(shade(SKIN_DARK, 0.45f));
                int c = mix(SKIN, SKIN_DARK, 0.45f + ty * 0.3f);
                if (side == 3 && ly < 0.25f) c = mix(c, SKIN_LIGHT, 0.3f);
                if (side == 3 && Math.abs(lx - fw / 2) < 0.25f) c = mix(c, SKIN_DARK, 0.4f);
                return opaque(shade(c, (float) (0.94 + 0.08 * hash(x / 3, y / 3, seed))));
            }
            case ApeShape.DARK -> {                                                 // the nose, with two nostrils
                int c = side == 0 ? shade(NOSE, 1.6f) : NOSE;
                if (side == 3) {
                    for (float nx : new float[]{fw * 0.3f, fw * 0.7f}) {
                        float dx = (lx - nx) / 0.42f, dy = (ly - fh * 0.6f) / 0.28f;
                        if (dx * dx + dy * dy < 1) c = 0x0A0404;
                    }
                    if (ly < 0.2f) c = shade(NOSE, 1.5f);
                }
                return opaque(c);
            }
            case ApeShape.TOOTH -> {
                if (side != 3) return opaque(side == 1 || side == 0 ? TOOTH : shade(TOOTH, 0.85f));
                return opaque(fang(lx, ly, fw, fh, jaw));
            }
            case ApeShape.MOUTH -> {
                return opaque(shade(MOUTH, side == 3 ? 0.6f : 0.85f + 0.2f * ty));
            }
            default -> {                                                            // SKIN: the muzzle, the jaw, the ears
                if (ear) return ear(side, lx, ly, fw, fh, x, y, seed);
                int c = mix(SKIN, SKIN_DARK, side == 1 ? 0.6f : side == 0 ? 0f : ty * 0.35f);
                if (jaw && side == 0) {                                             // the floor of the mouth: a tongue
                    float tx = Math.abs(lx - fw / 2) / (fw * 0.32f);
                    return opaque(tx < 1 && ly > fh * 0.25f ? shade(TONGUE, 0.8f + 0.2f * (1 - tx)) : MOUTH);
                }
                if (side == 0) c = mix(c, SKIN_LIGHT, 0.3f);                       // the muzzle's top catches the light
                if (side == 3 && !jaw && ly > fh - 0.25f) c = mix(c, SKIN_DARK, 0.6f);   // the upper lip
                if (side == 3 && jaw && ly < 0.25f) c = mix(c, SKIN_DARK, 0.55f);       // the lower lip
                if (side == 3 && !jaw && Math.abs(lx - fw / 2) < 0.2f && ly < fh - 0.4f) c = mix(c, SKIN_DARK, 0.25f);   // the groove under the nose
                return opaque(shade(c, (float) (0.94 + 0.08 * hash(x / 3, y / 3, seed))));
            }
        }
    }

    /**
     * The fangs' front: pointed teeth over the dark mouth, the upper ones pointing down, the lower ones up, the canines
     * (second from each end) longer.
     */
    static int fang(float lx, float ly, float fw, float fh, boolean lower) {
        int n = 8;
        float span = fw / n;
        int i = Math.min(n - 1, (int) (lx / span));
        float f = (lx - i * span) / span - 0.5f;                                 // -0.5..0.5 across this tooth
        boolean canine = i == 1 || i == n - 2;
        float reach = canine ? 1f : 0.62f;                                        // how far down (or up) the tooth reaches
        float from = lower ? fh - ly : ly;                                        // from the gum
        float along = from / fh;
        float half = 0.5f * (1 - along / reach) + 0.04f;
        if (along > reach || Math.abs(f) > half) return 0x2A0808;
        int c = mix(TOOTH, TOOTH_DARK, along * 0.5f + Math.abs(f) * 0.6f);
        if (along < 0.12f) c = mix(c, 0x8A3A34, 0.5f);                            // the gum line
        return c;
    }

    /** A pointed ear cut out of its box: the point up and out, a darker hollow inside. */
    static int ear(int side, float lx, float ly, float fw, float fh, int x, int y, int seed) {
        if (side == 0) return 0;
        if (side == 2) return ly > fh - 0.35f ? opaque(SKIN_DARK) : 0;           // the outer edge: only its foot
        if (side == 4 || side == 1) return opaque(shade(SKIN_DARK, 0.9f));
        float ax = side == 3 ? 0.45f : fw - 0.45f;                                // the point, on the outer side
        float k = ly / fh;
        float left = side == 3 ? ax - 0.45f * k : ax - (fw - 0.45f) * k;
        float right = side == 3 ? ax + (fw - 0.45f) * k : ax + 0.45f * k;
        if (lx < left || lx > right) return 0;
        int c = mix(SKIN, SKIN_DARK, 0.15f + k * 0.2f);
        if (side == 3) {                                                          // the hollow
            float il = left + (right - left) * 0.25f, ir = right - (right - left) * 0.25f;
            if (k > 0.3f && lx > il && lx < ir) c = mix(c, 0x7A4438, 0.55f);
        }
        return opaque(shade(c, (float) (0.94 + 0.08 * hash(x / 2, y / 2, seed))));
    }

    // ------------------------------------------------------------------ fur and colour

    /**
     * Fur in staggered tufts falling down the sides: each tuft lighter at its root, a soft shadow under its tip, fine
     * strands through it; lit above and shadowed below ({@code ty} 0 top .. 1 bottom of the face); the top looser.
     */
    static int fur(Fur fur, int x, int y, int seed, int side, float ty) {
        int tuftH = 7, tuftW = 5;
        int col = Math.floorDiv(x, tuftW);
        int yy = y + (int) (hash(col, 3, seed) * tuftH);                        // each column of tufts staggered
        int row = Math.floorDiv(yy, tuftH);
        float t = (Math.floorMod(yy, tuftH) + 0.5f) / tuftH;                    // 0 root .. 1 tip of the tuft
        float across = (Math.floorMod(x, tuftW) + 0.5f) / tuftW - 0.5f;
        float tipCurve = 0.82f + 0.12f * (1 - 4 * across * across);            // tips hang lower in the middle (a V)
        float k = 1.06f - 0.16f * t;
        if (t > tipCurve) k *= 0.8f;                                            // the shadow under the tip
        k *= (float) (0.94 + 0.12 * hash(x, Math.floorDiv(y, 3), seed * 3));    // fine strands
        k *= (float) (0.95 + 0.1 * hash(col, row, seed * 11));                  // tufts a little different
        int base = side == 0 ? mix(fur.base, fur.light, 0.35f) : side == 1 ? mix(fur.base, fur.dark, 0.55f) : fur.base;
        if (side >= 2) base = mix(mix(fur.light, fur.base, Math.min(1, ty * 2.5f + 0.2f)), fur.dark, Math.max(0, ty - 0.6f) * 0.9f);
        return shade(base, k);
    }

    static int opaque(int c) {
        return 0xFF000000 | (c & 0xFFFFFF);
    }

    static int shade(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
        return r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static double hash(int x, int y, long seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
