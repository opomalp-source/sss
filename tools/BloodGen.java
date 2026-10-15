import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Blood textures (the knocked-out rework), all white with alpha so the game tints them in each race's colour, and the
 * items that go with wounds:
 * <ul>
 *   <li>textures/entity/blood_drop.png: 16x16, a round drop with a soft edge and a brighter wet highlight;</li>
 *   <li>textures/entity/blood_splat_1..3.png: 64x64 splatters, a pooled middle with a ragged rim, satellite drops and
 *       a few streaks thrown out from it (three seeds);</li>
 *   <li>textures/item/bandage.png, sewing_kit.png: 16x16 item icons in the game's flat pixel style.</li>
 * </ul>
 * Usage: java tools/BloodGen.java
 */
public final class BloodGen {
    static final String ENTITY = "src/main/resources/assets/dbzenith/textures/entity/";
    static final String ITEM = "src/main/resources/assets/dbzenith/textures/item/";

    public static void main(String[] args) throws Exception {
        drop();
        for (int i = 1; i <= 3; i++) splat(i, new Random(0xB100D + i * 7919L));
        bandage();
        sewingKit();
        System.out.println("BloodGen done");
    }

    static void drop() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double dx = (x + 0.5 - 8) / 7.0, dy = (y + 0.5 - 8) / 7.0, d = Math.sqrt(dx * dx + dy * dy);
            if (d > 1) continue;
            int a = (int) (255 * Math.min(1, (1 - d) * 3.5));
            double hl = Math.max(0, 1 - Math.hypot(dx + 0.35, dy + 0.35) / 0.35);   // a wet glint
            int v = (int) (200 + 55 * hl);
            img.setRGB(x, y, a << 24 | v << 16 | v << 8 | v);
        }
        ImageIO.write(img, "png", new File(ENTITY + "blood_drop.png"));
    }

    static void splat(int n, Random r) throws Exception {
        int S = 64;
        double[][] a = new double[S][S];
        double cx = S / 2.0, cy = S / 2.0;
        double[] lobes = new double[12];
        for (int i = 0; i < lobes.length; i++) lobes[i] = 0.75 + r.nextDouble() * 0.5;
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {             // the pool, its rim ragged by lobes
            double dx = x + 0.5 - cx, dy = y + 0.5 - cy, ang = Math.atan2(dy, dx), d = Math.hypot(dx, dy);
            double t = (ang + Math.PI) / (2 * Math.PI) * lobes.length;
            int i0 = (int) t % lobes.length, i1 = (i0 + 1) % lobes.length;
            double f = t - Math.floor(t), rad = 13 * (lobes[i0] * (1 - f) + lobes[i1] * f);
            if (d < rad) a[y][x] = Math.min(1, (rad - d) / 1.6);
        }
        int sats = 10 + r.nextInt(8);                                          // satellite drops
        for (int i = 0; i < sats; i++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = 16 + r.nextDouble() * 13, rad = 1 + r.nextDouble() * 2.6;
            blob(a, cx + Math.cos(ang) * dist, cy + Math.sin(ang) * dist, rad);
        }
        int streaks = 3 + r.nextInt(3);                                        // streaks thrown out
        for (int i = 0; i < streaks; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            for (double k = 11; k < 26 + r.nextDouble() * 5; k += 0.6) blob(a, cx + Math.cos(ang) * k, cy + Math.sin(ang) * k, Math.max(0.6, 2.2 - k * 0.07));
        }
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
            double v = a[y][x];
            if (v <= 0) continue;
            double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
            int shade = (int) (215 + 40 * Math.max(0, 1 - Math.hypot(dx + 4, dy + 4) / 9));   // a wet sheen near the middle
            int al = (int) (255 * Math.min(1, v));
            img.setRGB(x, y, al << 24 | shade << 16 | shade << 8 | shade);
        }
        ImageIO.write(img, "png", new File(ENTITY + "blood_splat_" + n + ".png"));
    }

    static void blob(double[][] a, double bx, double by, double rad) {
        for (int y = (int) (by - rad - 1); y <= by + rad + 1; y++) for (int x = (int) (bx - rad - 1); x <= bx + rad + 1; x++) {
            if (x < 0 || y < 0 || x >= a.length || y >= a.length) continue;
            double d = Math.hypot(x + 0.5 - bx, y + 0.5 - by);
            if (d < rad) a[y][x] = Math.max(a[y][x], Math.min(1, (rad - d) / 0.9));
        }
    }

    /** A rolled bandage, white with grey folds, a strip unrolling, a red cross on the roll. */
    static void bandage() throws Exception {
        String[] art = {
                "................",
                "................",
                "......kkkkk.....",
                ".....kwwwwwk....",
                "....kwwggwwwk...",
                "...kwwgkkgwwwk..",
                "...kwgkrrkgwwk..",
                "...kwgrrrrgwwk..",
                "...kwgkrrkgwwk..",
                "...kwwgkkgwwwkk.",
                "....kwwggwwwwwwk",
                ".....kwwwwwgwwwk",
                "......kkkkkkgwwk",
                "............kggk",
                ".............kk.",
                "................"};
        write(art, ITEM + "bandage.png");
    }

    /** A sewing kit: a little tin with a needle and a reel of orange thread. */
    static void sewingKit() throws Exception {
        String[] art = {
                "................",
                "..........k.....",
                ".........ksk....",
                "........ksk.....",
                ".......ksk......",
                "...kkkksk.......",
                "..kooooko.......",
                "..koOOOokk......",
                "..kooooobbk.....",
                "..kkkkkkbbbk....",
                "...kbbbbbbbbk...",
                "..kbBBBBBBBBbk..",
                "..kbBbbbbbbBbk..",
                "..kbBBBBBBBBbk..",
                "...kkkkkkkkkk...",
                "................"};
        write(art, ITEM + "sewing_kit.png");
    }

    static void write(String[] art, String path) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = switch (art[y].charAt(x)) {
                case 'k' -> 0xFF2A2420;
                case 'w' -> 0xFFF4F0E8;
                case 'g' -> 0xFFC8C0B4;
                case 'r' -> 0xFFC8242A;
                case 's' -> 0xFFD8DCE4;
                case 'o' -> 0xFFE8862A;
                case 'O' -> 0xFFF4B060;
                case 'b' -> 0xFF4A6A9A;
                case 'B' -> 0xFF6E90C4;
                default -> 0;
            };
            img.setRGB(x, y, c);
        }
        ImageIO.write(img, "png", new File(path));
    }
}
