import com.dbzenith.client.render.ApeShape;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Paints the Great Ape (the Great Ape rework) on the layout of {@code ApeShape}: fur in strands that fall down every
 * side, light on top and shadowed below, a bare tan face, chest, belly, hands and feet, dark nose, ivory fangs, dark
 * nails, and the face: a heavy brow, yellow eyes with red irises and slit pupils inside a tan mask. Three furs (the
 * moon ape's brown, the golden ape's gold, the legendary ape's green-gold), and the eyes again on their own
 * (great_ape_eyes, drawn glowing). Painted at ApeShape.PAINT times the layout.
 * <p>Usage, after compiling: java -cp build/classes/java/main tools/GreatApeGen.java
 */
public final class GreatApeGen {
    static final int S = ApeShape.PAINT;

    record Fur(String file, int base, int light, int dark) {}

    static final Fur[] FURS = {
            new Fur("great_ape", 0x5A3018, 0x7C4826, 0x2A1408),
            new Fur("great_ape_golden", 0xD8A22A, 0xFAD870, 0x8A5A10),
            new Fur("great_ape_legendary", 0x9CA82C, 0xD2DC6E, 0x525C12),
    };
    static final int SKIN = 0xC8946A, SKIN_DARK = 0x8E5E3C, NOSE = 0x2A1612, TOOTH = 0xF0E6D0, CLAW = 0x3A2C24;
    static final int SCLERA = 0xF2D45A, IRIS = 0xD81E14, PUPIL = 0x1A0606;

    public static void main(String[] args) throws Exception {
        String dir = "src/main/resources/assets/dbzenith/textures/entity/";
        for (Fur fur : FURS) ImageIO.write(paint(fur, false), "png", new File(dir + fur.file + ".png"));
        ImageIO.write(paint(FURS[0], true), "png", new File(dir + "great_ape_eyes.png"));
        System.out.println("GreatApeGen: " + ApeShape.BOXES.size() + " boxes, " + ApeShape.TEX_W * S + "x" + ApeShape.TEX_H * S);
    }

    static BufferedImage paint(Fur fur, boolean eyesOnly) {
        BufferedImage img = new BufferedImage(ApeShape.TEX_W * S, ApeShape.TEX_H * S, BufferedImage.TYPE_INT_ARGB);
        int seed = 1;
        for (ApeShape.Box b : ApeShape.BOXES) {
            if (b.mirror()) continue;                                               // twins share their pair's paint
            seed++;
            float w = b.w(), h = b.h(), d = b.d();
            float u = b.u(), v = b.v();
            // the six faces: top, bottom, -x side, front, +x side, back (Minecraft's cube layout)
            face(img, fur, b, u + d, v, w, d, 0, seed, eyesOnly);
            face(img, fur, b, u + d + w, v, w, d, 1, seed, eyesOnly);
            face(img, fur, b, u, v + d, d, h, 2, seed, eyesOnly);
            face(img, fur, b, u + d, v + d, w, h, 3, seed, eyesOnly);
            face(img, fur, b, u + d + w, v + d, d, h, 4, seed, eyesOnly);
            face(img, fur, b, u + d + w + d, v + d, w, h, 5, seed, eyesOnly);
        }
        return img;
    }

    /** One face: {@code side} 0 top, 1 bottom, 2..5 the sides (3 the front). */
    static void face(BufferedImage img, Fur fur, ApeShape.Box b, float fu, float fv, float fw, float fh, int side, int seed, boolean eyesOnly) {
        int x0 = Math.round(fu * S), y0 = Math.round(fv * S), x1 = Math.round((fu + fw) * S), y1 = Math.round((fv + fh) * S);
        boolean skull = b.part().equals("head") && b.w() == 9 && b.d() == 8;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
            float lx = (x - x0 + 0.5f) / S, ly = (y - y0 + 0.5f) / S;              // model pixels across and down the face
            float ty = (y - y0 + 0.5f) / Math.max(1, y1 - y0);                    // 0 top .. 1 bottom
            int c;
            if (skull && side == 3) {
                int face = face(lx, ly, fur, x, y, seed, eyesOnly);
                if (eyesOnly) {
                    if (face != 0) img.setRGB(x, y, face);
                    continue;
                }
                c = face != 0 ? face : fur(fur, x, y, seed, side, ty);
            } else {
                if (eyesOnly) continue;
                c = switch (b.mat()) {
                    case ApeShape.SKIN -> skin(x, y, seed, side, ty, b, lx, ly);
                    case ApeShape.DARK -> shade(NOSE, side == 0 ? 1.3f : 1f);
                    case ApeShape.TOOTH -> shade(TOOTH, side == 1 ? 0.8f : 1f - 0.15f * ty);
                    case ApeShape.CLAW -> shade(CLAW, 1f - 0.2f * ty);
                    default -> fur(fur, x, y, seed, side, ty);
                };
            }
            boolean edge = x == x0 || y == y0 || x == x1 - 1 || y == y1 - 1;      // a soft darker line round furred faces, the painted look
            if (edge && b.mat() == ApeShape.FUR) c = shade(c, 0.9f);
            img.setRGB(x, y, 0xFF000000 | c);
        }
    }

    /**
     * Fur in staggered tufts falling down the sides: each tuft lighter at its root, a soft shadow under its tip, fine
     * strands through it; lit above and shadowed below; the top in looser tufts.
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
        if (side >= 2) base = mix(mix(fur.light, fur.base, Math.min(1, ty * 2.5f + 0.2f)), fur.dark, Math.max(0, ty - 0.6f) * 0.9f);   // lit at the top, shadowed below
        return shade(base, k);
    }

    /** Bare skin: a little mottled, darker below; the chest's pecs and the belly's ridges shaded in. */
    static int skin(int x, int y, int seed, int side, float ty, ApeShape.Box b, float lx, float ly) {
        float k = (float) (0.92 + 0.12 * hash(x / 3, y / 3, seed * 5));
        int c = mix(SKIN, SKIN_DARK, side == 1 ? 0.6f : side == 0 ? 0f : ty * 0.45f);
        if (side == 3 && b.part().equals("torso") && b.w() == 11) {                // the chest: two pecs
            float mid = Math.abs(lx - b.w() / 2);
            if (mid < 0.35f || ly > b.h() * 0.72f) c = mix(c, SKIN_DARK, 0.55f);
            else if (ly < b.h() * 0.3f) c = mix(c, 0xE6B48A, 0.25f);
        }
        if (side == 3 && b.part().equals("torso") && b.w() == 8) {                 // the belly: ridges
            float mid = Math.abs(lx - b.w() / 2);
            if (mid < 0.25f || Math.abs((ly % 1.6f) - 0.8f) < 0.12f) c = mix(c, SKIN_DARK, 0.4f);
        }
        if (b.part().endsWith("hand") && side == 3 && Math.abs((lx % 1.5f) - 0.75f) < 0.12f) c = mix(c, SKIN_DARK, 0.6f);   // fingers
        if (b.part().endsWith("foot") && side == 0 && lx > 0 && Math.abs((lx % 1.5f) - 0.75f) < 0.12f && ly < 2.5f) c = mix(c, SKIN_DARK, 0.6f);   // toes
        return shade(c, k);
    }

    /**
     * The face on the skull's front (9 x 8): a tan mask round the eyes and down to the muzzle, the eyes yellow with
     * red irises and slit pupils, shadowed under the brow. Returns 0 where the fur shows. With {@code eyesOnly}, only
     * the glowing parts (the irises bright, the yellow faint).
     */
    static int face(float lx, float ly, Fur fur, int x, int y, int seed, boolean eyesOnly) {
        for (int e = 0; e < 2; e++) {                                             // the eyes
            float cx = e == 0 ? 2.75f : 6.25f, cy = 3.5f;
            float dx = (lx - cx) / 1.25f, dy = (ly - cy) / 0.75f;
            float r = dx * dx + dy * dy;
            if (r < 1) {
                float ix = lx - cx, iy = ly - cy;
                boolean iris = ix * ix / 0.36f + iy * iy / 0.42f < 1;
                boolean pupil = Math.abs(ix) < 0.13f && Math.abs(iy) < 0.55f;
                if (eyesOnly) return pupil ? 0 : iris ? 0xFFFF3A28 : 0x60F2D45A;
                if (pupil) return PUPIL;
                if (iris) return mix(IRIS, 0xFF6A40, Math.max(0, -iy) * 0.6f);
                return mix(SCLERA, 0xB89020, Math.max(0, (ly - cy + 0.75f) / 1.5f) * 0.4f + (dy < -0.4f ? 0.35f : 0));   // shadowed under the brow
            }
            if (r < 1.5f && !eyesOnly) return shade(SKIN_DARK, 0.6f);              // dark lids round them
        }
        if (eyesOnly) return 0;
        // the mask: tan from under the brow down, round the eyes, narrowing to the muzzle
        float mx = Math.abs(lx - 4.5f);
        boolean mask = ly > 2.3f && mx < 4.2f - Math.max(0, ly - 5.5f) * 0.6f;
        if (mask) {
            float k = (float) (0.92 + 0.1 * hash(x / 3, y / 3, seed));
            int c = mix(SKIN, SKIN_DARK, Math.max(0, ly - 5) * 0.12f);
            if (ly < 2.9f) c = mix(c, SKIN_DARK, 0.5f);                            // the brow's shadow
            if (Math.abs(mx - 0.9f) < 0.15f && ly > 4.5f) c = mix(c, SKIN_DARK, 0.4f);   // the creases down to the nose
            return shade(c, k);
        }
        return 0;
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
