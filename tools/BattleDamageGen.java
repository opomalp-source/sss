import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Battle damage overlays for the player model (CX-47), in the player skin layout, at 64 and 128 pixels
 * (textures/entity/battle/ and battle_hd/):
 * <ul>
 *   <li>{@code tears_<1..3>}: where the clothes are ripped open (white; drawn tinted with the fighter's skin tone, so
 *       skin shows through whatever is worn);</li>
 *   <li>{@code marks_<1..3>}: the frayed dark edges of the rips with a few hanging threads, scratches, bruises and grime;</li>
 *   <li>{@code dust}: dust and dirt from crashing into the ground, faded in and out by the game.</li>
 * </ul>
 * Each level keeps everything of the one before and adds more. Usage: java tools/BattleDamageGen.java
 */
public final class BattleDamageGen {
    /** Faces of the model the damage goes on (x, y, w, h in 64-pixel skin units), and how much each gets. */
    static final int[][] FACES = {
            {20, 20, 8, 12, 3}, {32, 20, 8, 12, 3}, {16, 20, 4, 12, 1}, {28, 20, 4, 12, 1},          // body front, back, sides
            {0, 20, 4, 12, 1}, {4, 20, 4, 12, 2}, {8, 20, 4, 12, 1}, {12, 20, 4, 12, 1},             // right leg
            {16, 52, 4, 12, 1}, {20, 52, 4, 12, 2}, {24, 52, 4, 12, 1}, {28, 52, 4, 12, 1},          // left leg
            {40, 20, 4, 12, 1}, {44, 20, 4, 12, 1}, {48, 20, 4, 12, 1}, {52, 20, 4, 12, 1},          // right arm
            {32, 52, 4, 12, 1}, {36, 52, 4, 12, 1}, {40, 52, 4, 12, 1}, {44, 52, 4, 12, 1},          // left arm
    };
    static final int[] FACE = {8, 8, 8, 8};                                                          // the head's front

    record Mark(int kind, int level, int face, double x, double y, double r, double a, long seed) {}

    static final int TEAR = 0, SCRATCH = 1, BRUISE = 2, GRIME = 3;

    public static void main(String[] args) throws Exception {
        String root = "src/main/resources/assets/dbzenith/textures/entity/";
        List<Mark> marks = plan();
        for (int scale : new int[]{1, 2}) {
            String dir = root + (scale == 1 ? "battle/" : "battle_hd/");
            new File(dir).mkdirs();
            for (int level = 1; level <= 3; level++) {
                BufferedImage tears = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_ARGB);
                BufferedImage edges = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_ARGB);
                for (Mark m : marks) if (m.level <= level) draw(m, scale, tears, edges);
                ImageIO.write(tears, "png", new File(dir + "tears_" + level + ".png"));
                ImageIO.write(edges, "png", new File(dir + "marks_" + level + ".png"));
            }
            ImageIO.write(dust(scale), "png", new File(dir + "dust.png"));
        }
        System.out.println("BattleDamageGen: " + marks.size() + " marks");
    }

    /** Where everything goes: the same plan for both sizes, each mark given the level it first shows at. */
    static List<Mark> plan() {
        Random r = new Random(0xBA77);
        List<Mark> out = new ArrayList<>();
        for (int f = 0; f < FACES.length; f++) {
            int[] face = FACES[f];
            int weight = face[4];
            for (int i = 0; i < weight * 2 + 1; i++) {                            // rips: few at first, more as it goes on
                int level = i == 0 ? 1 : i <= weight ? 2 : 3;
                if (r.nextDouble() < 0.4) continue;
                double rad = 0.7 + r.nextDouble() * (face[2] > 4 ? 1.3 : 0.8) + (level - 1) * 0.2;
                out.add(new Mark(TEAR, level, f, face[0] + 1 + r.nextDouble() * (face[2] - 2), face[1] + 1.5 + r.nextDouble() * (face[3] - 3), rad, r.nextDouble() * Math.PI, r.nextLong()));
            }
            for (int i = 0; i < weight + 1; i++) {                                // scratches
                int level = 1 + r.nextInt(3);
                out.add(new Mark(SCRATCH, level, f, face[0] + r.nextDouble() * face[2], face[1] + r.nextDouble() * face[3], 1.2 + r.nextDouble() * 1.6,
                        Math.PI * (0.15 + r.nextDouble() * 0.3) * (r.nextBoolean() ? 1 : -1), r.nextLong()));
            }
            for (int i = 0; i < weight; i++)                                       // bruises
                out.add(new Mark(BRUISE, 2 + r.nextInt(2), f, face[0] + r.nextDouble() * face[2], face[1] + r.nextDouble() * face[3], 1.0 + r.nextDouble() * 0.9, 0, r.nextLong()));
            for (int level = 1; level <= 3; level++)                               // grime, thicker each level
                out.add(new Mark(GRIME, level, f, 0, 0, 0, 0, r.nextLong()));
        }
        out.add(new Mark(SCRATCH, 3, -1, FACE[0] + 5.6, FACE[1] + 4.6, 1.4, -0.7, 7));        // a cut on the cheek
        out.add(new Mark(BRUISE, 3, -1, FACE[0] + 1.8, FACE[1] + 2.4, 0.9, 0, 11));               // and a bruise over the eye
        return out;
    }

    static int[] faceOf(Mark m) {
        return m.face < 0 ? FACE : FACES[m.face];
    }

    static void draw(Mark m, int s, BufferedImage tears, BufferedImage edges) {
        int[] f = faceOf(m);
        int x0 = f[0] * s, y0 = f[1] * s, x1 = (f[0] + f[2]) * s, y1 = (f[1] + f[3]) * s;
        Random r = new Random(m.seed);
        switch (m.kind) {
            case TEAR -> {
                double[] lobes = {r.nextDouble() * 0.35, r.nextDouble() * 0.25, r.nextDouble() * 0.2, r.nextDouble() * Math.PI * 2};
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    double dx = (x + 0.5) / s - m.x, dy = (y + 0.5) / s - m.y;
                    double ang = Math.atan2(dy, dx), d = Math.hypot(dx * 1.1, dy * 0.8);    // a little taller than wide
                    double edge = m.r * (1 + lobes[0] * Math.sin(ang * 3 + lobes[3]) + lobes[1] * Math.sin(ang * 5 + 1.3) + lobes[2] * Math.sin(ang * 9 + 0.4));
                    edge += (hash(x, y, m.seed) - 0.5) * 0.45;                             // ragged
                    if (d < edge) {
                        tears.setRGB(x, y, 0xFFFFFFFF);
                        double shade = (edge - d) / edge;
                        if (shade < 0.25) blend(edges, x, y, 0x5A3A2A, (int) (90 * (1 - shade * 4)));   // the skin's shadow under the cloth's lip
                    } else if (d < edge + 0.75) blend(edges, x, y, 0x241A12, 235);          // frayed cloth edge
                }
                int threads = r.nextInt(3);                                                // hanging threads at the bottom
                for (int k = 0; k < threads; k++) {
                    double tx = m.x + (r.nextDouble() - 0.5) * m.r * 1.2;
                    double ty = m.y + m.r * 0.8;
                    int len = s * (1 + r.nextInt(2));
                    for (int i = 0; i < len; i++) {
                        int px = (int) (tx * s), py = (int) (ty * s) + i;
                        if (px >= x0 && px < x1 && py >= y0 && py < y1) blend(edges, px, py, 0x2E241A, 200);
                    }
                }
            }
            case SCRATCH -> {
                int steps = (int) (m.r * s * 3);
                double ca = Math.cos(m.a), sa = Math.sin(m.a);
                for (int i = 0; i <= steps; i++) {
                    double t = i / (double) steps - 0.5;
                    int px = (int) Math.floor((m.x + ca * t * m.r * 2) * s), py = (int) Math.floor((m.y + sa * t * m.r * 2) * s);
                    if (px < x0 || px >= x1 || py < y0 || py >= y1) continue;
                    double mid = 1 - Math.abs(t) * 2;
                    blend(edges, px, py, 0x8A2420, (int) (80 + 80 * mid));
                    if (s > 1 && mid > 0.3) blend(edges, Math.min(x1 - 1, px + 1), py, 0xB84038, 70);
                }
            }
            case BRUISE -> {
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    double d = Math.hypot((x + 0.5) / s - m.x, (y + 0.5) / s - m.y) / m.r;
                    if (d < 1) blend(edges, x, y, d < 0.5 ? 0x5A2E52 : 0x7A4A3A, (int) (75 * (1 - d * d)));
                }
            }
            case GRIME -> {
                Random g = new Random(m.seed);
                int n = (int) (f[2] * f[3] * s * s * (0.012 + 0.012 * m.level));
                for (int i = 0; i < n; i++) {
                    int px = x0 + g.nextInt(x1 - x0), py = y0 + g.nextInt(y1 - y0);
                    blend(edges, px, py, 0x4A3C2C, 40 + g.nextInt(50));
                }
            }
        }
    }

    static BufferedImage dust(int s) {
        BufferedImage img = new BufferedImage(64 * s, 64 * s, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(0xD057);
        for (int[] f : FACES) {
            int x0 = f[0] * s, y0 = f[1] * s, w = f[2] * s, h = f[3] * s;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                double low = y / (double) h;                                          // more toward the bottom (the legs especially)
                double n = hash(x0 + x, y0 + y, 99) * 0.6 + hash((x0 + x) / 2, (y0 + y) / 2, 7) * 0.4;
                if (n < 0.45 - 0.2 * low) continue;
                int c = r.nextBoolean() ? 0x9A8A70 : 0x7A6A54;
                img.setRGB(x0 + x, y0 + y, (int) Math.min(200, (n - 0.3) * 260) << 24 | c);
            }
        }
        for (int y = FACE[1] * s; y < (FACE[1] + FACE[3]) * s; y++) for (int x = FACE[0] * s; x < (FACE[0] + FACE[2]) * s; x++)
            if (hash(x, y, 5) > 0.82) img.setRGB(x, y, 90 << 24 | 0x8A7A62);              // a smudge or two on the face
        return img;
    }

    static double hash(int x, int y, long seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    /** Paints {@code rgb} at {@code alpha} over what is there (alpha compositing). */
    static void blend(BufferedImage img, int x, int y, int rgb, int alpha) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight() || alpha <= 0) return;
        int dst = img.getRGB(x, y);
        double sa = Math.min(255, alpha) / 255.0, da = (dst >>> 24) / 255.0;
        double oa = sa + da * (1 - sa);
        if (oa <= 0) return;
        int out = 0;
        for (int sh = 0; sh <= 16; sh += 8) {
            double sc = (rgb >> sh & 255), dc = (dst >> sh & 255);
            out |= (int) Math.round((sc * sa + dc * da * (1 - sa)) / oa) << sh;
        }
        img.setRGB(x, y, (int) Math.round(oa * 255) << 24 | out);
    }
}
