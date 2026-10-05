import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * The mod's art, drawn in code (original designs; see ASSETS_TODO.md). Run from the project root:
 * {@code java tools/ArtGen.java}. Every texture is rebuilt from scratch each run, so edit here, never the PNGs.
 * <p>
 * Style: 16x16 items and blocks, selective dark outlines, light from the top left, hue-shifted ramps (shadows lean
 * cool, highlights lean warm), few colours per object.
 */
public class ArtGen {
    static final String RES = "src/main/resources/assets/dbzenith/textures/";

    public static void main(String[] args) throws IOException {
        Items.all();
        Blocks.all();
        Armor.all();
        Skins.all();
        Creatures.all();
        Gui.all();
        Fx.all();
        System.out.println("ArtGen done");
    }

    // ================================================================== colour helpers

    /** A shading ramp built from one base colour: index 0 darkest ... n-1 lightest, hue-shifted. */
    static int[] ramp(int base, int steps) {
        float[] hsb = java.awt.Color.RGBtoHSB((base >> 16) & 255, (base >> 8) & 255, base & 255, null);
        int[] out = new int[steps];
        for (int i = 0; i < steps; i++) {
            float t = steps == 1 ? 0.5f : (float) i / (steps - 1);           // 0 shadow .. 1 highlight
            float hue = hsb[0] + (t - 0.5f) * 0.06f * warmthDir(hsb[0]);    // shadows cooler, highlights warmer
            float sat = clamp(hsb[1] * (1.15f - 0.45f * t) + (t > 0.85f ? -0.1f : 0), 0, 1);
            float bri = clamp(hsb[2] * (0.45f + 0.75f * t), 0, 1);
            out[i] = 0xFF000000 | (java.awt.Color.HSBtoRGB(((hue % 1) + 1) % 1, sat, bri) & 0xFFFFFF);
        }
        return out;
    }

    /** Which way "warmer" is on the hue wheel from this hue (towards yellow). */
    static float warmthDir(float hue) {
        float toYellow = 1f / 6f - hue;
        if (toYellow > 0.5f) toYellow -= 1;
        if (toYellow < -0.5f) toYellow += 1;
        return Math.signum(toYellow);
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int mix(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = (a >>> 24);
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = (b >>> 24);
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int darker(int c, double f) {
        return mix(c, 0xFF000000 | mix(c, 0x101028, 1.0) & 0xFFFFFF, f) | 0xFF000000;
    }

    static int alpha(int c, int a) {
        return (c & 0xFFFFFF) | (a << 24);
    }

    // ================================================================== canvas

    static final class Canvas {
        final int w, h;
        final int[] px;

        Canvas(int w, int h) {
            this.w = w;
            this.h = h;
            px = new int[w * h];
        }

        boolean in(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h;
        }

        int get(int x, int y) {
            return in(x, y) ? px[y * w + x] : 0;
        }

        boolean opaque(int x, int y) {
            return (get(x, y) >>> 24) > 0;
        }

        Canvas set(int x, int y, int c) {
            if (in(x, y)) px[y * w + x] = c;
            return this;
        }

        Canvas rect(int x0, int y0, int x1, int y1, int c) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) set(x, y, c);
            return this;
        }

        Canvas hline(int x0, int x1, int y, int c) {
            return rect(x0, y, x1, y, c);
        }

        Canvas vline(int x, int y0, int y1, int c) {
            return rect(x, y0, x, y1, c);
        }

        Canvas line(int x0, int y0, int x1, int y1, int c) {
            int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
            while (true) {
                set(x0, y0, c);
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 >= dy) { err += dy; x0 += sx; }
                if (e2 <= dx) { err += dx; y0 += sy; }
            }
            return this;
        }

        /** Filled ellipse shaded as a lit sphere/egg with the ramp (light from the top left), plus a specular dot. */
        Canvas sphere(double cx, double cy, double rx, double ry, int[] ramp, boolean specular) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                    double d = nx * nx + ny * ny;
                    if (d > 1) continue;
                    double nz = Math.sqrt(1 - d);
                    double light = (-0.55 * nx - 0.65 * ny + 0.55 * nz);   // light dir normalised-ish (top left, front)
                    double t = clamp((float) (0.5 + light * 0.6), 0, 0.999f);
                    set(x, y, ramp[(int) (t * ramp.length)]);
                }
            if (specular) set((int) (cx - rx * 0.4), (int) (cy - ry * 0.45), 0xFFFFFFFF);
            return this;
        }

        /** Filled ellipse, flat colour. */
        Canvas ellipse(double cx, double cy, double rx, double ry, int c) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                    if (nx * nx + ny * ny <= 1) set(x, y, c);
                }
            return this;
        }

        /** Ring (ellipse outline band). */
        Canvas ring(double cx, double cy, double r, double thickness, int c) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                    if (Math.abs(d - r) <= thickness / 2) set(x, y, c);
                }
            return this;
        }

        /**
         * Selective outline: every transparent pixel touching an opaque one becomes a dark version of that
         * neighbour (so outlines take the colour of what they wrap, not flat black).
         */
        Canvas outline() {
            int[] copy = px.clone();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    if ((copy[y * w + x] >>> 24) != 0) continue;
                    int n = 0;
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] dd : dirs) {
                        int nx = x + dd[0], ny = y + dd[1];
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h && (copy[ny * w + nx] >>> 24) != 0) {
                            n = copy[ny * w + nx];
                            break;
                        }
                    }
                    if (n != 0) px[y * w + x] = darker(n, 0.72);
                }
            return this;
        }

        /** Darkens the right and bottom edge pixels of the shape a little (form shadow on flat-painted parts). */
        Canvas edgeShade(double amount) {
            int[] copy = px.clone();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = copy[y * w + x];
                    if ((c >>> 24) == 0) continue;
                    boolean right = x + 1 >= w || (copy[y * w + x + 1] >>> 24) == 0;
                    boolean below = y + 1 >= h || (copy[(y + 1) * w + x] >>> 24) == 0;
                    boolean left = x == 0 || (copy[y * w + x - 1] >>> 24) == 0;
                    boolean above = y == 0 || (copy[(y - 1) * w + x] >>> 24) == 0;
                    if (right || below) px[y * w + x] = darker(c, amount);
                    else if (left || above) px[y * w + x] = mix(c, 0xFFFFFFFF, amount * 0.6);
                }
            return this;
        }

        /** Applies {@code f} to every opaque pixel (x, y, colour) -> colour. */
        Canvas map(PixelFn f) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = px[y * w + x];
                    if ((c >>> 24) != 0) px[y * w + x] = f.at(x, y, c);
                }
            return this;
        }

        /** Paints pixels from a char map; unknown chars and '.' are skipped. */
        Canvas paint(int x0, int y0, java.util.Map<Character, Integer> pal, String... rows) {
            for (int y = 0; y < rows.length; y++)
                for (int x = 0; x < rows[y].length(); x++) {
                    Integer c = pal.get(rows[y].charAt(x));
                    if (c != null) set(x0 + x, y0 + y, c);
                }
            return this;
        }

        void save(String path) throws IOException {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            File out = new File(RES + path);
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            System.out.println("art " + path);
        }
    }

    interface PixelFn {
        int at(int x, int y, int c);
    }

    /** Deterministic value noise in [0,1). */
    static double noise(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65536.0;
    }

    // ================================================================== items

    static final class Items {
        static void all() throws IOException {
            senzu();
            moonOrb();
            scroll();
            weights("training_weights", 0xFF7A7F8A, 0xFF5A3A22, false);
            weights("heavy_training_weights", 0xFF4A4E58, 0xFF3A2416, true);
            radar();
            scouter();
            capsule();
            pod();
            sigil();
            totem();
            ring();
            gi("turtle", 0xFFF07820, 0xFF2852C8, 0xFF2852C8);
            gi("demon", 0xFF6A3A9A, 0xFFC02838, 0xFF2A1A2E);
            battleArmor();
        }

        static void senzu() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] g = ramp(0xFF4FA83A, 5);
            c.sphere(8, 8.5, 4.6, 3.6, g, false);
            c.map((x, y, col) -> x + y == 17 && y >= 6 && y <= 11 ? darker(col, 0.25) : col); // seam
            c.set(5, 6, mix(g[4], 0xFFFFFFFF, 0.6)).set(6, 6, g[4]);
            c.outline().save("item/senzu_bean.png");
        }

        static void moonOrb() throws IOException {
            Canvas c = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {   // soft halo
                double d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
                if (d > 5.6 && d < 7.9) c.set(x, y, alpha(0xFFD8E8FF, (int) (150 * (7.9 - d) / 2.3)));
            }
            Canvas orb = new Canvas(16, 16);
            orb.sphere(8, 8, 5.2, 5.2, ramp(0xFFA8B4E0, 5), true);
            int crater = 0xFF6A74A8;
            orb.set(9, 6, crater).set(10, 6, crater).set(9, 7, crater).set(6, 10, crater).set(10, 10, crater).set(11, 10, crater);
            orb.outline();
            for (int i = 0; i < c.px.length; i++) if ((orb.px[i] >>> 24) != 0) c.px[i] = orb.px[i];
            c.save("item/moon_orb.png");
        }

        static void scroll() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] paper = ramp(0xFFE8D2A0, 4);
            c.rect(4, 3, 11, 12, paper[2]);
            for (int y = 4; y <= 11; y += 2) c.hline(5, 10, y, paper[1]);          // writing lines
            c.hline(5, 8, 5, 0xFF5A4030).hline(5, 9, 7, 0xFF5A4030).hline(5, 7, 9, 0xFF5A4030);
            int[] roll = ramp(0xFFB08850, 4);
            c.rect(3, 2, 12, 3, roll[2]).hline(3, 12, 2, roll[3]);                 // top roll
            c.rect(3, 12, 12, 13, roll[1]).hline(3, 12, 13, roll[0]);              // bottom roll
            c.set(2, 2, roll[1]).set(13, 2, roll[1]).set(2, 13, roll[0]).set(13, 13, roll[0]);
            c.rect(9, 10, 10, 11, 0xFFC02020).set(9, 10, 0xFFE04848);              // seal
            c.set(10, 12, 0xFFC02020).set(9, 14, 0xFFA01818).set(11, 14, 0xFFA01818); // ribbon tails
            c.outline().save("item/technique_scroll.png");
        }

        static void weights(String name, int plate, int strap, boolean heavy) throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] s = ramp(strap, 4);
            int[] p = ramp(plate, 5);
            c.rect(3, 3, 12, 13, s[1]);                                           // vest body
            c.rect(3, 1, 5, 3, s[2]).rect(10, 1, 12, 3, s[2]);                     // shoulder straps
            c.rect(6, 3, 9, 5, 0);                                                // neck opening
            int rows = heavy ? 4 : 3;
            for (int r = 0; r < rows; r++) {
                int y = 5 + r * (heavy ? 2 : 3);
                c.rect(4, y, 11, y + 1, p[3]).hline(4, 11, y + 1, p[1]).set(4, y, p[4]);
                if (heavy) c.set(7, y, p[2]).set(8, y, p[2]);                     // plate seams
            }
            c.vline(7, 4, 13, s[0]);                                              // front closure
            c.outline().save("item/" + name + ".png");
        }

        static void radar() throws IOException {
            Canvas c = new Canvas(16, 16);
            c.sphere(8, 8.5, 6.5, 6.5, ramp(0xFFB8BCC8, 5), false);                  // metal body
            c.ellipse(8, 8.5, 4.6, 4.6, 0xFF0E3A1A);                                 // screen
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {            // grid
                double d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8.5);
                if (d < 4.6 && (x == 8 || y == 8 || (x + 1) % 3 == 0 && y % 3 == 2)) c.set(x, y, 0xFF1E7A34);
            }
            c.set(8, 8, 0xFF7CFF7C).set(10, 6, 0xFFFFD040).set(6, 10, 0xFFFFD040).set(11, 10, 0xFFFFD040);
            c.rect(7, 0, 9, 1, 0xFF9098A8).set(7, 0, 0xFFC8CCD8);                   // top button
            c.set(5, 5, 0x9060FF90);                                               // screen glare
            c.outline().save("item/dragon_radar.png");
        }

        static void scouter() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] m = ramp(0xFF5A6070, 4);
            c.ellipse(4, 11, 3, 3.2, m[2]).ellipse(4, 11, 1.6, 1.8, m[1]).set(3, 10, m[3]); // ear piece
            c.line(5, 9, 10, 5, m[2]).line(5, 10, 10, 6, m[1]);                       // arm
            c.rect(9, 3, 14, 8, 0xFF2A9A4A);                                          // lens
            c.rect(10, 4, 13, 7, 0xFF48D070).set(10, 4, 0xFFC8FFD8).set(11, 4, 0xFF98F0B0);
            c.hline(9, 14, 3, 0xFF1E6A34).vline(14, 3, 8, 0xFF1E6A34);
            c.outline().save("item/scouter.png");
        }

        static void capsule() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] w = ramp(0xFFE8ECF4, 5);
            for (int y = 5; y <= 10; y++) for (int x = 2; x <= 13; x++) {                // pill on its side: lit from above
                double ex = x < 5 ? (5 - x) / 3.0 : x > 10 ? (x - 10) / 3.0 : 0, ey = (y - 7.5) / 2.6;
                if (ex * ex + ey * ey > 1) continue;
                c.set(x, y, w[(int) clamp((float) (3.6 - (y - 5) * 0.75), 0, 4)]);
            }
            c.rect(7, 5, 8, 10, 0xFFE05030).vline(8, 5, 10, 0xFFB03820).set(7, 5, 0xFFFF8060); // band
            c.set(4, 6, 0xFFFFFFFF).set(11, 8, 0xFF4060C0).set(12, 8, 0xFF2840A0);      // shine + button
            c.outline().save("item/capsule.png");
        }

        static void pod() throws IOException {
            Canvas c = new Canvas(16, 16);
            c.line(4, 12, 2, 15, 0xFF6A7080).line(12, 12, 14, 15, 0xFF6A7080).line(8, 13, 8, 15, 0xFF6A7080); // legs
            c.sphere(8, 7.5, 6, 6, ramp(0xFFE0E4EC, 5), false);                       // shell
            c.ellipse(9, 7, 3, 2.6, 0xFF8A2030);                                      // window frame
            c.ellipse(9, 7, 2.2, 1.8, 0xFFE04060).set(8, 6, 0xFFFFB0C0);              // glass
            c.hline(3, 13, 11, 0xFFA8ACB8);                                           // hull seam
            c.outline().save("item/space_pod.png");
        }

        static void sigil() throws IOException {
            Canvas c = new Canvas(16, 16);
            c.sphere(8, 8, 6.4, 6.4, ramp(0xFFC89A30, 5), false);                     // gold disc
            c.ellipse(8, 8, 4.8, 4.8, 0xFF2A1238);
            int v = 0xFFB070F0, vd = 0xFF6A30A0;
            c.line(5, 5, 8, 10, v).line(11, 5, 8, 10, v).line(8, 4, 8, 11, vd);       // horned crest
            c.set(5, 4, v).set(11, 4, v).set(8, 11, 0xFFFFD0FF);
            c.outline().save("item/tyrant_sigil.png");
        }

        static void totem() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] wood = ramp(0xFF8A5530, 5);
            c.rect(5, 2, 10, 14, wood[2]);
            for (int y = 2; y <= 14; y++) for (int x = 5; x <= 10; x++) {
                if (noise(x, y, 7) > 0.8) c.set(x, y, wood[1]);
                if (x == 5) c.set(x, y, wood[3]);
                if (x == 10) c.set(x, y, wood[1]);
            }
            c.hline(4, 11, 7, wood[1]).hline(4, 11, 11, wood[1]);                  // carved bands
            c.set(6, 4, 0xFFFF3020).set(9, 4, 0xFFFF3020).set(6, 5, 0xFF901010).set(9, 5, 0xFF901010); // eyes
            c.hline(6, 9, 9, 0xFF2A1408).set(6, 9, 0xFFE8E0C8).set(9, 9, 0xFFE8E0C8); // fanged mouth
            c.rect(3, 1, 4, 3, wood[3]).rect(11, 1, 12, 3, wood[3]);                // horns
            c.outline().save("item/rage_totem.png");
        }

        static void ring() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] gold = ramp(0xFFE0B040, 5);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double d = Math.hypot((x + 0.5 - 8) / 1.0, (y + 0.5 - 9.5) / 0.9);
                if (d >= 3 && d <= 5.2) {
                    double ang = Math.atan2(y + 0.5 - 9.5, x + 0.5 - 8);
                    int idx = (int) clamp((float) (2.5 - Math.sin(ang + 0.8) * 2.2), 0, 4);
                    c.set(x, y, gold[idx]);
                }
            }
            c.sphere(8, 3.5, 2.4, 2.2, ramp(0xFF70D8FF, 4), true);                    // gem
            c.outline().save("item/promise_ring.png");
        }

        /** Gi set: top (with undershirt and belt), pants, boots (with wraps). */
        static void gi(String set, int cloth, int accent, int boots) throws IOException {
            int[] cl = ramp(cloth, 5), ac = ramp(accent, 4), bt = ramp(boots, 4);
            Canvas top = new Canvas(16, 16);
            top.paint(0, 0, java.util.Map.of('#', cl[2]),                                   // shirt silhouette
                    "................",
                    "....###..###....",
                    "..#####..#####..",
                    ".##############.",
                    "################",
                    "################",
                    "###.########.###",
                    "##..########..##",
                    "#...########...#",
                    "....########....",
                    "....########....",
                    "....########....",
                    "....########....",
                    "....########....");
            for (int y = 2; y <= 6; y++) for (int x = 6; x <= 9; x++) {                    // undershirt in the V
                if (Math.abs(x - 7.5) <= (6 - y) * 0.5 + 0.5) top.set(x, y, ac[2]);
            }
            top.line(4, 1, 7, 7, cl[3]).line(11, 1, 8, 7, cl[1]);                            // lapels
            top.rect(4, 11, 11, 12, ac[2]).hline(4, 11, 12, ac[1]).set(10, 13, ac[1]);       // belt + knot
            top.set(0, 8, ac[2]).set(1, 7, ac[2]).set(15, 8, ac[1]).set(14, 7, ac[1]);       // wrist bands
            top.map((x, y, col) -> col == cl[2] && x < 8 && noise(x, y, 3) > 0.7 ? cl[3] : col); // lit folds
            top.map((x, y, col) -> col == cl[2] && x >= 8 && noise(x, y, 4) > 0.65 ? cl[1] : col); // shaded folds
            top.edgeShade(0.25).outline().save("item/" + set + "_top.png");

            Canvas pants = new Canvas(16, 16);
            pants.rect(3, 2, 12, 5, cl[2]).rect(3, 5, 7, 14, cl[2]).rect(8, 5, 12, 14, cl[2]);
            pants.rect(3, 2, 12, 3, ac[2]).hline(3, 12, 3, ac[1]);                          // waist sash
            pants.vline(7, 6, 14, cl[1]).vline(8, 6, 14, cl[3]);                           // inner seams
            pants.map((x, y, col) -> noise(x, y, 5) > 0.85 && col == cl[2] ? cl[1] : col);
            pants.edgeShade(0.25).outline().save("item/" + set + "_pants.png");

            Canvas b = new Canvas(16, 16);
            for (int s = 0; s < 2; s++) {
                int x0 = s == 0 ? 2 : 9;
                b.rect(x0, 4, x0 + 4, 12, bt[2]).rect(x0, 12, x0 + 5, 14, bt[1]);
                b.hline(x0, x0 + 4, 6, bt[3]).hline(x0, x0 + 4, 8, bt[3]);              // wraps
                b.rect(x0, 3, x0 + 4, 4, ac[2]);                                       // cuff
            }
            b.edgeShade(0.2).outline().save("item/" + set + "_boots.png");
        }

        static void battleArmor() throws IOException {
            int[] w = ramp(0xFFE8ECF0, 5), y = ramp(0xFFD8B040, 4), d = ramp(0xFF2A2E48, 4);
            Canvas top = new Canvas(16, 16);
            top.rect(4, 4, 11, 13, d[2]);                                                    // undersuit
            top.sphere(8, 8, 4.6, 5, w, false);                                              // chest plate
            top.sphere(3, 4.5, 2.6, 2.2, y, false).sphere(13, 4.5, 2.6, 2.2, y, false);      // shoulder pads
            top.rect(6, 3, 9, 4, d[1]);                                                      // collar
            top.hline(4, 11, 12, w[1]).hline(5, 10, 13, d[1]);
            top.outline().save("item/battle_armor_top.png");

            Canvas pants = new Canvas(16, 16);
            pants.rect(3, 2, 12, 5, d[2]).rect(3, 5, 7, 14, d[2]).rect(8, 5, 12, 14, d[2]);
            pants.rect(3, 2, 12, 3, w[3]).rect(3, 4, 5, 6, w[2]).rect(10, 4, 12, 6, w[2]);  // belt + hip guards
            pants.vline(7, 6, 14, d[1]).vline(8, 6, 14, d[3]);
            pants.edgeShade(0.2).outline().save("item/battle_armor_pants.png");

            Canvas b = new Canvas(16, 16);
            for (int s = 0; s < 2; s++) {
                int x0 = s == 0 ? 2 : 9;
                b.rect(x0, 3, x0 + 4, 12, w[2]).rect(x0, 12, x0 + 5, 14, w[1]);
                b.rect(x0, 3, x0 + 4, 5, y[2]).hline(x0, x0 + 4, 5, y[1]);                 // gold tops
                b.vline(x0, 6, 12, w[3]);
            }
            b.edgeShade(0.2).outline().save("item/battle_armor_boots.png");
        }
    }

    // ================================================================== blocks

    static final class Blocks {
        static void all() throws IOException {
            for (int star = 1; star <= 7; star++) dragonBall(star);
            punchingBag();
            gravityChamber();
            timeChamberDoor();
            namekTree();
        }

        /** Namek trees: smooth pale bark, ring-cut ends, round blue-green leaf clusters (cutout gaps). */
        static void namekTree() throws IOException {
            int[] bark = ramp(0xFFD8DCC8, 5), leaf = ramp(0xFF3AB89A, 5);
            Canvas side = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int i = 2 + (int) Math.round(Math.sin(x * 0.9 + noise(x, 0, 401) * 2) * 0.8);
                if (noise(x, y / 3, 402) > 0.9) i--;                                                  // knots
                side.set(x, y, bark[Math.max(0, Math.min(4, i))]);
            }
            side.save("block/namek_log.png");
            Canvas top = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
                top.set(x, y, d > 7 ? bark[1] : ((int) d % 2 == 0 ? bark[3] : bark[2]));
            }
            top.save("block/namek_log_top.png");
            Canvas leaves = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int cx = (x / 4) * 4 + 2, cy = (y / 4) * 4 + 2 + ((x / 4) % 2) * 2 - 1;        // clustered round leaves
                double d = Math.hypot(x - cx, y - cy);
                if (d > 2.3 && noise(x, y, 403) > 0.35) continue;                                    // gaps
                int i = d < 1 ? 4 : d < 2 ? 3 : 2;
                if (noise(x, y, 404) > 0.85) i--;
                leaves.set(x, y, leaf[Math.max(0, i)]);
            }
            leaves.save("block/namek_leaves.png");
        }

        /** Star positions inside the 6x6 star face (local 0..5); the model shows that face on the four front sides. */
        static final int[][][] STARS = {
                {{2, 2}, {3, 2}, {2, 3}, {3, 3}},
                {{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 3}, {4, 3}, {3, 4}, {4, 4}},
                {{2, 1}, {1, 4}, {4, 4}},
                {{1, 1}, {4, 1}, {1, 4}, {4, 4}},
                {{1, 1}, {4, 1}, {1, 4}, {4, 4}, {2, 2}},
                {{1, 0}, {4, 0}, {0, 2}, {5, 2}, {1, 4}, {4, 4}},
                {{1, 0}, {4, 0}, {0, 2}, {5, 2}, {1, 4}, {4, 4}, {2, 2}},
        };

        static final int[] BALL = ramp(0xFFFF8A10, 6);

        /** Orange glass, a touch lighter at the top: every face is lit by the game, so no per-face sphere shading. */
        static int ballShade(int x, int y) {
            double t = 0.66 - (y - 4) * 0.03;                                     // smooth glass, no grain
            return BALL[(int) clamp((float) (t * 6), 0, 5)];
        }

        static void dragonBall(int star) throws IOException {
            if (star == 1) {                                                            // the shared shell
                Canvas shell = new Canvas(16, 16);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) shell.set(x, y, ballShade(x, y));
                shell.set(6, 6, 0xFFFFFFFF).set(7, 6, 0xFFFFE8B0).set(6, 7, 0xFFFFE8B0);          // gloss on the top
                shell.save("block/dragon_ball_shell.png");
            }
            Canvas c = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) c.set(x, y, ballShade(x, y));
            for (int[] s : STARS[star - 1]) {
                c.set(5 + s[0], 5 + s[1], 0xFFD81010);
                if (star > 2 && s[1] < 5) c.set(5 + s[0], 6 + s[1], mix(c.get(5 + s[0], 6 + s[1]), 0xFF901008, 0.35));
            }
            c.set(5, 5, mix(c.get(5, 5), 0xFFFFFFFF, 0.55));                                // glint
            c.save("block/dragon_ball_" + star + ".png");
        }

        /**
         * One sheet for the hanging bag: side x0-7 y0-11, top x8-15 y0-7, rope x8-9 y8-9, cap side x8-15 y10,
         * bottom cap side x8-13 y11.
         */
        static void punchingBag() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] r = ramp(0xFFB02828, 5), tape = ramp(0xFFE8E0C8, 3), dark = ramp(0xFF3A2A24, 3);
            for (int y = 0; y < 12; y++) for (int x = 0; x < 8; x++) {
                int base = x < 2 ? 3 : x > 5 ? 1 : 2;                                  // round body: lit left
                c.set(x, y, noise(x, y, 11) > 0.85 ? r[base - 1] : r[base]);
            }
            c.vline(3, 0, 11, r[1]).vline(4, 0, 11, r[3]);                             // stitched seam
            for (int y = 1; y < 12; y += 2) c.set(3, y, tape[1]);
            for (int x = 0; x < 8; x++) {
                c.set(x, 2, tape[x < 2 ? 2 : x > 5 ? 0 : 1]).set(x, 9, tape[x < 2 ? 2 : x > 5 ? 0 : 1]); // tape bands
            }
            for (int y = 0; y < 8; y++) for (int x = 8; x < 16; x++) {                  // top: leather disc with hook
                double d = Math.hypot(x + 0.5 - 12, y + 0.5 - 4);
                c.set(x, y, d < 1.5 ? dark[0] : d < 3 ? r[1] : r[2]);
            }
            c.rect(8, 8, 9, 9, dark[1]).set(8, 8, dark[2]);                            // rope
            for (int x = 8; x < 16; x++) c.set(x, 10, dark[x < 10 ? 2 : 1]);           // cap side
            for (int x = 8; x < 14; x++) c.set(x, 11, dark[x < 10 ? 2 : 0]);           // bottom cap side
            c.save("block/punching_bag.png");
        }

        static void gravityChamber() throws IOException {
            int[] steel = ramp(0xFF8A92A4, 5);
            Canvas side = new Canvas(16, 16);
            side.rect(0, 0, 15, 15, steel[2]);
            side.hline(0, 15, 0, steel[4]).vline(0, 0, 15, steel[3]).hline(0, 15, 15, steel[0]).vline(15, 0, 15, steel[1]);
            side.rect(2, 2, 13, 7, steel[1]);                                         // panel
            for (int y = 3; y <= 6; y++) side.hline(3, 12, y, y % 2 == 1 ? steel[0] : steel[2]);   // vents
            side.rect(2, 10, 13, 11, 0xFF2A1E40);                                    // status strip
            for (int x = 3; x <= 12; x += 3) side.set(x, 10, 0xFFB070FF).set(x, 11, 0xFF7040C0);
            for (int[] p : new int[][]{{1, 1}, {14, 1}, {1, 14}, {14, 14}, {1, 8}, {14, 8}}) side.set(p[0], p[1], steel[4]); // rivets
            side.rect(5, 13, 10, 14, steel[1]).hline(5, 10, 13, steel[0]);           // foot plate
            side.save("block/gravity_chamber_side.png");

            Canvas top = new Canvas(16, 16);
            top.rect(0, 0, 15, 15, steel[2]);
            top.hline(0, 15, 0, steel[4]).vline(0, 0, 15, steel[3]).hline(0, 15, 15, steel[0]).vline(15, 0, 15, steel[1]);
            top.rect(2, 2, 13, 8, 0xFF101820);                                        // screen
            top.rect(3, 3, 12, 7, 0xFF0E3A1A);
            int g = 0xFF5CFF7C;                                                        // "10G" readout
            top.paint(4, 3, java.util.Map.of('#', g),
                    "#.###.###",
                    "#.#.#.#..",
                    "#.#.#.#.#",
                    "#.#.#.#.#",
                    "#.###.###");
            top.set(12, 3, 0x80FFFFFF);
            top.ellipse(5, 11.5, 2, 2, 0xFFC02828).set(4, 10, 0xFFFF6060);             // big red dial
            top.rect(9, 10, 10, 11, 0xFF2852C8).rect(12, 10, 13, 11, 0xFF28A848);      // buttons
            top.rect(9, 13, 13, 13, steel[0]);
            top.save("block/gravity_chamber_top.png");

            Canvas bottom = new Canvas(16, 16);
            bottom.rect(0, 0, 15, 15, steel[1]).map((x, y, col) -> noise(x, y, 21) > 0.8 ? steel[0] : col);
            bottom.save("block/gravity_chamber_bottom.png");
        }

        static void timeChamberDoor() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] marble = ramp(0xFFF0EEE8, 4), gold = ramp(0xFFE0B040, 4);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double vein = Math.sin((x * 0.9 + y * 0.6) + noise(x, y, 31) * 2.5);
                c.set(x, y, vein > 0.93 ? marble[1] : marble[2 + (noise(x, y, 33) > 0.7 ? 1 : 0)]);
            }
            for (int y = 3; y < 16; y++) for (int x = 3; x <= 12; x++) {               // arched doorway
                double archTop = y < 7 ? Math.hypot(x + 0.5 - 8, y + 0.5 - 7) : 0;
                if (y >= 7 || archTop <= 4.5) {
                    double tw = noise(x, y, 41);
                    c.set(x, y, tw > 0.93 ? 0xFFFFFFFF : tw > 0.86 ? 0xFF8890C8 : mix(0xFF0A0A20, 0xFF241A48, (y - 3) / 13.0));
                }
            }
            for (int y = 2; y < 16; y++) for (int x = 2; x <= 13; x++) {               // gold trim around the arch
                boolean inside = y >= 7 ? x >= 3 && x <= 12 : Math.hypot(x + 0.5 - 8, y + 0.5 - 7) <= 4.5;
                boolean outer = y >= 7 ? x >= 2 && x <= 13 : Math.hypot(x + 0.5 - 8, y + 0.5 - 7) <= 5.6;
                if (outer && !inside) c.set(x, y, gold[x < 8 ? 3 : 1]);
            }
            c.set(8, 1, gold[3]).set(7, 1, gold[2]).set(9, 1, gold[1]);                   // keystone
            c.save("block/time_chamber_door.png");
        }
    }

    // ================================================================== box UV painting (skins and armor)

    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    interface FaceFn {
        /** Colour for local pixel (x, y) of a face that is w x h; 0 = leave transparent. */
        int at(Face face, int x, int y, int w, int h);
    }

    /** Paints a box in the standard Minecraft layout: origin (u, v), size w (x) x h (y) x d (z). */
    static void box(Canvas c, int u, int v, int w, int h, int d, FaceFn fn) {
        faceAt(c, Face.TOP, u + d, v, w, d, fn);
        faceAt(c, Face.BOTTOM, u + d + w, v, w, d, fn);
        faceAt(c, Face.RIGHT, u, v + d, d, h, fn);
        faceAt(c, Face.FRONT, u + d, v + d, w, h, fn);
        faceAt(c, Face.LEFT, u + d + w, v + d, d, h, fn);
        faceAt(c, Face.BACK, u + d + w + d, v + d, w, h, fn);
    }

    static void faceAt(Canvas c, Face f, int x0, int y0, int w, int h, FaceFn fn) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int col = fn.at(f, x, y, w, h);
            if (col != 0) c.set(x0 + x, y0 + y, col);
        }
    }

    /** Cloth with soft folds; side and back faces a shade darker. */
    static int cloth(int[] r, Face f, int x, int y, int seed) {
        int base = switch (f) { case FRONT, TOP -> 2; default -> 1; };
        double n = noise(x, y, seed);
        if (n > 0.93) base = Math.max(0, base - 1);                                   // sparse, soft folds
        else if (n < 0.04) base = Math.min(r.length - 1, base + 1);
        return r[base];
    }

    // ================================================================== armor layers (64x32, vanilla layout)

    static final class Armor {
        static void all() throws IOException {
            gi("turtle", 0xFFF07820, 0xFF2852C8, 0xFF2852C8, 0xFF2852C8);
            gi("demon", 0xFF6A3A9A, 0xFFC02838, 0xFF2A1A2E, 0xFF2A1A2E);
            battleArmor();
            scouter();
            weights();
        }

        /** Original school emblem for the back of a gi: a ring around a rising flame. */
        static boolean emblem(int x, int y) {
            double dx = x - 3.5, dy = y - 3.5;
            double r = Math.sqrt(dx * dx + dy * dy);
            boolean ring = r > 2.6 && r < 3.6;
            boolean flame = (x == 3 || x == 4) && y >= 2 && y <= 5 || (x == 2 && y == 4) || (x == 5 && y == 3);
            return ring || flame;
        }

        static void gi(String set, int clothCol, int accentCol, int bootCol, int under) throws IOException {
            int[] cl = ramp(clothCol, 5), ac = ramp(accentCol, 4), bt = ramp(bootCol, 4), un = ramp(under, 4);
            Canvas l1 = new Canvas(64, 32);
            box(l1, 16, 16, 8, 12, 4, (f, x, y, w, h) -> {                                    // chest: the top
                if (f == Face.TOP || f == Face.BOTTOM) return cloth(cl, f, x, y, 1);
                if (y >= 10) return y == 10 ? ac[2] : ac[1];                                    // belt
                if (f == Face.FRONT) {
                    double v = Math.abs(x - 3.5);
                    if (y <= 4 && v < (4.5 - y) * 0.9) return un[2];                            // undershirt in the V
                    if (y <= 6 && Math.abs(v - (4.5 - y) * 0.9) < 0.8) return x < 4 ? cl[3] : cl[1]; // lapel edge
                }
                if (f == Face.BACK && y >= 1 && y < 9 && emblem(x, y - 1)) return 0xFFF4F0E8;   // school emblem
                return cloth(cl, f, x, y, 1);
            });
            box(l1, 40, 16, 4, 12, 4, (f, x, y, w, h) -> {                                    // arms: short sleeves + wristbands
                if (f == Face.BOTTOM) return 0;
                if (f == Face.TOP) return cloth(cl, f, x, y, 2);
                if (y < 5) return y == 4 ? cl[0] : cloth(cl, f, x, y, 2);
                if (y >= 9 && y <= 11) return y == 9 ? ac[2] : ac[1];
                return 0;
            });
            box(l1, 0, 16, 4, 12, 4, (f, x, y, w, h) -> {                                     // boots (lower leg)
                if (f == Face.TOP) return 0;
                if (f == Face.BOTTOM) return bt[0];
                if (y < 6) return 0;
                if (y == 6) return ac[2];                                                      // cuff
                return (y % 2 == 0) ? bt[2] : bt[1];                                            // wraps
            });
            l1.save("models/armor/" + set + "_layer_1.png");

            Canvas l2 = new Canvas(64, 32);
            box(l2, 0, 16, 4, 12, 4, (f, x, y, w, h) -> {                                     // pants
                if (f == Face.TOP || f == Face.BOTTOM) return cloth(cl, f, x, y, 3);
                if (f == Face.LEFT && y > 1) return cl[1];                                     // inner seam side
                if (y >= 9 && (x + y) % 3 == 0) return cl[1];                                  // baggy folds at the ankle
                return cloth(cl, f, x, y, 3);
            });
            box(l2, 16, 16, 8, 12, 4, (f, x, y, w, h) -> {                                    // waist sash
                if (f == Face.TOP || f == Face.BOTTOM) return 0;
                if (y >= 9 && y <= 11) return y == 9 ? ac[2] : ac[1];
                return 0;
            });
            l2.save("models/armor/" + set + "_layer_2.png");
        }

        static void battleArmor() throws IOException {
            int[] w = ramp(0xFFE8ECF0, 5), gold = ramp(0xFFD8B040, 4), suit = ramp(0xFF2A2E48, 4);
            Canvas l1 = new Canvas(64, 32);
            box(l1, 16, 16, 8, 12, 4, (f, x, y, wd, h) -> {
                if (f == Face.BOTTOM) return suit[1];
                if (f == Face.TOP) return w[3];
                if (y >= 10) return suit[f == Face.FRONT ? 2 : 1];                              // undersuit at the waist
                if (f == Face.FRONT) {
                    if (y <= 1 && x >= 2 && x <= 5) return suit[2];                               // collar
                    if (y == 9) return w[1];
                    double shade = 3 - Math.abs(x - 3.5) * 0.35 - y * 0.12;
                    return w[(int) clamp((float) shade, 1, 4)];
                }
                if (f == Face.BACK) return y == 9 ? w[0] : w[2];
                return w[1];
            });
            box(l1, 40, 16, 4, 12, 4, (f, x, y, wd, h) -> {                                   // shoulder pads + undersuit sleeves
                if (f == Face.TOP) return gold[2];
                if (f == Face.BOTTOM) return suit[0];
                if (y < 3) return y == 2 ? gold[0] : gold[f == Face.FRONT || f == Face.RIGHT ? 3 : 1];
                return suit[f == Face.FRONT ? 2 : 1];
            });
            box(l1, 0, 16, 4, 12, 4, (f, x, y, wd, h) -> {                                    // boots
                if (f == Face.TOP) return 0;
                if (f == Face.BOTTOM) return w[0];
                if (y < 5) return 0;
                if (y <= 6) return gold[y == 5 ? 3 : 1];
                return f == Face.FRONT ? w[3] : w[2];
            });
            l1.save("models/armor/battle_armor_layer_1.png");

            Canvas l2 = new Canvas(64, 32);
            box(l2, 0, 16, 4, 12, 4, (f, x, y, wd, h) -> f == Face.FRONT ? suit[2] : suit[1]);
            box(l2, 16, 16, 8, 12, 4, (f, x, y, wd, h) -> {                                   // hip guards
                if (f == Face.TOP || f == Face.BOTTOM) return 0;
                if (y >= 9) return y == 9 ? w[3] : w[2];
                return 0;
            });
            l2.save("models/armor/battle_armor_layer_2.png");
        }

        static void scouter() throws IOException {
            Canvas l1 = new Canvas(64, 32);
            int[] m = ramp(0xFF5A6070, 4);
            box(l1, 0, 0, 8, 8, 8, (f, x, y, w, h) -> {
                if (f == Face.LEFT && y >= 3 && y <= 6 && x >= 2 && x <= 5) return y == 3 || x == 2 ? m[3] : m[1];   // earpiece
                if (f == Face.LEFT && y == 2 && x <= 2) return m[2];                            // arm to the lens
                if (f == Face.FRONT) {                                                         // lens over the left eye
                    if (y >= 2 && y <= 5 && x >= 5 && x <= 7) return y == 2 && x == 5 ? 0xFFC8FFD8 : y == 5 ? 0xFF1E6A34 : 0xFF48D070;
                    if (y == 6 && x == 7) return m[2];
                }
                return 0;
            });
            l1.save("models/armor/scouter_layer_1.png");
            new Canvas(64, 32).save("models/armor/scouter_layer_2.png");
        }

        static void weights() throws IOException {
            int[] p = ramp(0xFF6A707C, 5), s = ramp(0xFF5A3A22, 4);
            Canvas l1 = new Canvas(64, 32);
            box(l1, 16, 16, 8, 12, 4, (f, x, y, w, h) -> {
                if (f == Face.TOP) return x == 1 || x == 6 ? s[2] : 0;                          // straps over the shoulders
                if (f == Face.BOTTOM) return 0;
                if (f == Face.FRONT || f == Face.BACK) {
                    if (x == 1 || x == 6) return s[f == Face.FRONT ? 2 : 1];
                    if (y >= 2 && y <= 9) return (y - 2) % 3 == 2 ? p[0] : (y - 2) % 3 == 0 ? p[3] : p[2];  // plates
                    return 0;
                }
                return y >= 2 && y <= 9 ? p[1] : 0;
            });
            l1.save("models/armor/weights_layer_1.png");
            new Canvas(64, 32).save("models/armor/weights_layer_2.png");
        }
    }

    // ================================================================== character skins (64x64 player layout)

    /**
     * One character: a painter per body part (base layer) and optional overlay painters (hat, jacket, sleeves,
     * pants). Left limbs reuse the right-limb painters.
     */
    static final class Skin {
        FaceFn head, body, arm, leg, hat, jacket, sleeve, pants;

        void save(String path) throws IOException {
            Canvas c = new Canvas(64, 64);
            if (head != null) box(c, 0, 0, 8, 8, 8, head);
            if (body != null) box(c, 16, 16, 8, 12, 4, body);
            if (arm != null) { box(c, 40, 16, 4, 12, 4, arm); box(c, 32, 48, 4, 12, 4, mirror(arm)); }
            if (leg != null) { box(c, 0, 16, 4, 12, 4, leg); box(c, 16, 48, 4, 12, 4, mirror(leg)); }
            if (hat != null) box(c, 32, 0, 8, 8, 8, hat);
            if (jacket != null) box(c, 16, 32, 8, 12, 4, jacket);
            if (sleeve != null) { box(c, 40, 32, 4, 12, 4, sleeve); box(c, 48, 48, 4, 12, 4, mirror(sleeve)); }
            if (pants != null) { box(c, 0, 32, 4, 12, 4, pants); box(c, 0, 48, 4, 12, 4, mirror(pants)); }
            c.save(path);
        }

        /** The left limb: right and left faces swap, front and back read mirrored. */
        static FaceFn mirror(FaceFn fn) {
            return (f, x, y, w, h) -> switch (f) {
                case RIGHT -> fn.at(Face.LEFT, w - 1 - x, y, w, h);
                case LEFT -> fn.at(Face.RIGHT, w - 1 - x, y, w, h);
                case FRONT, BACK -> fn.at(f, w - 1 - x, y, w, h);
                default -> fn.at(f, x, y, w, h);
            };
        }
    }

    /** Skin with light from the front-left: front lit, sides and back a step darker. */
    static int flesh(int[] sk, Face f, int x, int y) {
        int i = switch (f) { case FRONT, TOP -> 3; case RIGHT -> 2; case LEFT, BACK -> 2; default -> 1; };
        if (f == Face.FRONT && x == 0) i--;
        return sk[Math.max(0, Math.min(sk.length - 1, i))];
    }

    /** A face: brows on row 3, eyes on row 4 (whites at the outer pixels), nose shade, mouth. */
    static int face(int[] sk, int x, int y, int eye, int brow, boolean whites) {
        if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) return brow;
        if (y == 4) {
            if (x == 2 || x == 5) return eye;
            if (whites && (x == 1 || x == 6)) return 0xFFF4F4F4;
        }
        if (y == 5 && (x == 3 || x == 4)) return sk[2];                                         // nose shadow
        if (y == 6 && (x == 3 || x == 4)) return darker(sk[1], 0.45);                           // mouth
        return sk[3];
    }

    static final class Skins {
        static void all() throws IOException {
            master();
            patrolOfficer();
            kiSoldier();
            androidUnit();
            sproutling();
            tyrantLord();
            rampageBrute();
            namekianWarrior();
            raceNamekian();
            raceFrostDemon();
            raceMajin();
            raceVampire();
            raceBioAndroid();
            raceTuffle();
            raceGenAlien();
            raceKai();
            raceCoreDemon();
            variantFrostDemon("metal_frost_demon", 0xFFD8E2EC, 0xFF6A7A90, 0xFF40E0FF);
            variantFrostDemon("mutant_frost_demon", 0xFF2A2230, 0xFFE84AB0, 0xFFFF2050);
            raceCorruptedMajin();
            raceDemonNamekian();
            body("lean", 0.55);
            body("athletic", 1.0);
            body("bulky", 1.45);
            outfit();
        }

        // ---- content expansion races and variants (CX-2). Eyes stay at x 2 and 5 so form eye colours line up.

        /** Pale noble: a high-collared black coat lined in red, a waistcoat, fangs at the lip. */
        static void raceVampire() throws IOException {
            int[] sk = ramp(0xFFE6E0EA, 5), coat = ramp(0xFF1A1420, 5), lining = ramp(0xFFA01028, 4), vest = ramp(0xFF5A1020, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y == 6 && (x == 2 || x == 5)) return 0xFFF8F8F8;                       // fangs
                    if (y == 6 && (x == 3 || x == 4)) return 0xFF6A1020;
                    return face(sk, x, y, 0xFFE01030, sk[1], true);
                }
                if (f == Face.TOP || y <= 1) return ramp(0xFF1C1418, 4)[f == Face.TOP ? 2 : 1];   // slicked dark hair line
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (x >= 3 && x <= 4 && y <= 8) return y <= 1 ? 0xFFF2F0F4 : vest[x == 3 ? 2 : 1];   // cravat and waistcoat
                    if ((x == 2 || x == 5) && y <= 9) return lining[2];
                }
                if (f == Face.BACK && y <= 1) return lining[1];                                  // collar
                return cloth(coat, f, x, y, 141);
            };
            s.arm = (f, x, y, w, h) -> y >= 10 ? (y == 10 ? lining[2] : flesh(sk, f, x, y)) : cloth(coat, f, x, y, 142);
            s.leg = (f, x, y, w, h) -> y >= 9 ? ramp(0xFF2A1A16, 4)[f == Face.FRONT ? 2 : 1] : cloth(ramp(0xFF24202A, 4), f, x, y, 143);
            s.save("entity/race/vampire.png");
        }

        /** Green carapace mottled with dark spots, black flexible joints, a pale armoured face with cheek grooves. */
        static void raceBioAndroid() throws IOException {
            int[] shell = ramp(0xFF5AB04A, 5), joint = ramp(0xFF20242A, 4), face = ramp(0xFFE8E4D8, 5), spot = ramp(0xFF1E3A1A, 3);
            Skin s = new Skin();
            FaceFn spotted = (f, x, y, w, h) -> noise(x * 3 + f.ordinal(), y * 5, 151) > 0.72 ? spot[1] : shell[f == Face.FRONT || f == Face.TOP ? 3 : 2];
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT && y >= 2) {
                    if ((x == 1 || x == 6) && y >= 5) return 0xFF7A3A9A;                         // cheek grooves
                    return face(face, x, y, 0xFFC02060, joint[1], true);
                }
                return spotted.at(f, x, y, w, h);
            };
            s.body = (f, x, y, w, h) -> f == Face.FRONT && x >= 2 && x <= 5 && y >= 6 ? joint[2] : spotted.at(f, x, y, w, h);
            s.arm = (f, x, y, w, h) -> y >= 4 && y <= 7 ? joint[f == Face.FRONT ? 2 : 1] : spotted.at(f, x, y, w, h);
            s.leg = (f, x, y, w, h) -> y >= 4 && y <= 8 ? joint[f == Face.FRONT ? 2 : 1] : spotted.at(f, x, y, w, h);
            s.save("entity/race/bio_android.png");
        }

        /** Pale and sharp-featured, in a white lab-armour suit with a red core gem. */
        static void raceTuffle() throws IOException {
            int[] sk = ramp(0xFFEDE0D6, 5), suit = ramp(0xFFE8ECF2, 5), trim = ramp(0xFF3A6AB0, 4), hair = ramp(0xFFC8CCD8, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.TOP || (f != Face.FRONT && f != Face.BOTTOM && y <= 2)) return hair[f == Face.TOP ? 3 : 2];
                if (f == Face.FRONT) return y <= 1 ? hair[2] : face(sk, x, y, 0xFF2A1020, hair[1], true);
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT && (x == 3 || x == 4) && (y == 3 || y == 4)) return x == 3 && y == 3 ? 0xFFFF8080 : 0xFFD01020;   // core gem
                if (y == 0 || y == 8) return trim[2];
                return cloth(suit, f, x, y, 161);
            };
            s.arm = (f, x, y, w, h) -> y >= 9 ? trim[f == Face.FRONT ? 3 : 2] : cloth(suit, f, x, y, 162);
            s.leg = (f, x, y, w, h) -> y >= 9 ? trim[1] : cloth(suit, f, x, y, 163);
            s.save("entity/race/tuffle.png");
        }

        /** Blue-grey alien skin with darker stripes, large dark eyes and a simple explorer harness. */
        static void raceGenAlien() throws IOException {
            int[] sk = ramp(0xFF7A9AC0, 5), stripe = ramp(0xFF3A4A70, 3), harness = ramp(0xFF4A3424, 4), metal = ramp(0xFFC8C8D0, 3);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y >= 3 && y <= 4 && (x == 1 || x == 2 || x == 5 || x == 6)) return x == 2 || x == 5 ? 0xFF101018 : 0xFF2A2A44;   // big eyes
                    return face(sk, x, y, 0xFF101018, stripe[1], false);
                }
                if (f == Face.BACK && y % 3 == 1 && y > 0) return stripe[1];                          // ridges down the back of the skull
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if ((f == Face.FRONT || f == Face.BACK) && (x == 2 || x == 5)) return harness[2];
                if (y == 8) return metal[1];
                if (f == Face.BACK && y % 3 == 0) return stripe[1];
                return flesh(sk, f, x, y);
            };
            s.arm = (f, x, y, w, h) -> y == 8 || y == 9 ? harness[1] : (y % 4 == 0 && f != Face.FRONT ? stripe[1] : flesh(sk, f, x, y));
            s.leg = (f, x, y, w, h) -> y >= 6 ? cloth(ramp(0xFF3A3A4A, 4), f, x, y, 171) : flesh(sk, f, x, y);
            s.save("entity/race/gen_alien.png");
        }

        /** Lavender divine skin, white tufted crown, layered robes with a sash and a collar of gold. */
        static void raceKai() throws IOException {
            int[] sk = ramp(0xFFD8B8EC, 5), robe = ramp(0xFF2C3A8A, 5), under = ramp(0xFFF4F2F8, 4), gold = ramp(0xFFE8C040, 3), sash = ramp(0xFF5AB0E8, 3);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.TOP) return x >= 3 && x <= 4 ? 0xFFF8F8FF : sk[3];                // white tuft
                if (f == Face.FRONT) return face(sk, x, y, 0xFF101018, sk[1], true);
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if (y == 0) return gold[f == Face.FRONT ? 2 : 1];
                if (y == 8 || y == 9) return sash[f == Face.FRONT ? 2 : 1];
                if (f == Face.FRONT && x >= 3 && x <= 4 && y <= 7) return under[2];
                return cloth(robe, f, x, y, 181);
            };
            s.arm = (f, x, y, w, h) -> y <= 6 ? cloth(robe, f, x, y, 182) : y <= 9 ? under[f == Face.FRONT ? 2 : 1] : flesh(sk, f, x, y);
            s.leg = (f, x, y, w, h) -> y >= 10 ? gold[1] : cloth(under, f, x, y, 183);
            s.save("entity/race/kai.png");
        }

        /** Crimson skin, black clawed garb with a burning red emblem, yellow eyes. */
        static void raceCoreDemon() throws IOException {
            int[] sk = ramp(0xFFC83030, 5), garb = ramp(0xFF181418, 5), ember = ramp(0xFFFF6A20, 3), gold = ramp(0xFFE8C040, 3);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y == 2 && x >= 2 && x <= 5) return 0xFF6A0A10;                        // heavy brow ridge
                    return face(sk, x, y, 0xFFFFD040, 0xFF6A0A10, false);
                }
                if (f == Face.TOP || y <= 1) return ramp(0xFF141010, 3)[1];                 // short black hair
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT && (x == 3 || x == 4) && y >= 2 && y <= 4) return ember[y == 3 ? 2 : 1];   // emblem
                if (y == 9) return gold[1];
                return cloth(garb, f, x, y, 191);
            };
            s.arm = (f, x, y, w, h) -> y >= 11 ? 0xFF101010 : y >= 6 ? flesh(sk, f, x, y) : cloth(garb, f, x, y, 192);
            s.leg = (f, x, y, w, h) -> cloth(garb, f, x, y, 193);
            s.save("entity/race/core_demon.png");
        }

        /** The Frost Demon look in a different metal or palette: skin, shell plates and eye colour. */
        static void variantFrostDemon(String name, int skin, int shellColour, int eyes) throws IOException {
            int[] white = ramp(skin, 5), shell = ramp(shellColour, 5);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.TOP || (f != Face.BOTTOM && y <= 1)) return shell[f == Face.TOP ? 3 : 2];
                if (f == Face.FRONT) {
                    if (y == 6 && (x == 3 || x == 4)) return shell[0];
                    if ((x == 0 || x == 7) && y >= 2 && y <= 3) return shell[1];
                    return face(white, x, y, eyes, white[1], true);
                }
                if (f == Face.BACK && y <= 3) return shell[1];
                return flesh(white, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT && y >= 1 && y <= 6 && x >= 1 && x <= 6) return shell[y == 6 ? 1 : x < 4 ? 3 : 2];
                if (f == Face.BACK && y >= 1 && y <= 4) return shell[1];
                return flesh(white, f, x, y);
            };
            s.arm = (f, x, y, w, h) -> f == Face.TOP || y <= 2 || (y >= 6 && y <= 9) ? shell[f == Face.FRONT ? 3 : 2] : flesh(white, f, x, y);
            s.leg = (f, x, y, w, h) -> y >= 5 && y <= 9 ? shell[f == Face.FRONT ? 3 : 2] : flesh(white, f, x, y);
            s.save("entity/race/" + name + ".png");
        }

        /** Grey, gaunt Majin with a cracked purple vest and black sclera. */
        static void raceCorruptedMajin() throws IOException {
            int[] sk = ramp(0xFF9A90A8, 5), vest = ramp(0xFF3A1A4A, 4), pants = ramp(0xFF4A4458, 4), belt = ramp(0xFFB070FF, 3);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y == 4 && (x == 1 || x == 6)) return 0xFF101010;                         // black sclera
                    return face(sk, x, y, 0xFFFF2040, sk[0], false);
                }
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if (y == 9) return belt[1];
                if (y >= 10) return cloth(pants, f, x, y, 201);
                if (f == Face.FRONT && x >= 2 && x <= 5) return noise(x, y, 202) > 0.85 ? sk[0] : flesh(sk, f, x, y);
                return cloth(vest, f, x, y, 203);
            };
            s.arm = (f, x, y, w, h) -> y >= 9 ? vest[f == Face.FRONT ? 2 : 1] : flesh(sk, f, x, y);
            s.leg = (f, x, y, w, h) -> y >= 9 ? belt[1] : cloth(pants, f, x, y, 204);
            s.save("entity/race/corrupted_majin.png");
        }

        /** Demon-clan Namekian: a darker, bluish green with a black and red gi. */
        static void raceDemonNamekian() throws IOException {
            int[] sk = ramp(0xFF3A8A6A, 5), pink = ramp(0xFFB070A0, 3), gi = ramp(0xFF1A1420, 5), sash = ramp(0xFFC01830, 3), pants = ramp(0xFF3A2A44, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) return face(sk, x, y, 0xFFFF2040, sk[0], true);
                if ((f == Face.RIGHT || f == Face.LEFT) && y >= 2 && y <= 4 && x >= 2 && x <= 3) return sk[0];
                if (f == Face.TOP && (x + y) % 4 == 0) return sk[1];
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> y >= 9 && y <= 10 ? sash[y == 9 ? 2 : 1] : cloth(gi, f, x, y, 211);
            s.arm = (f, x, y, w, h) -> {
                if (y < 4) return cloth(gi, f, x, y, 212);
                if ((f == Face.FRONT || f == Face.RIGHT) && y >= 5 && y <= 7) return pink[1];
                return flesh(sk, f, x, y);
            };
            s.leg = (f, x, y, w, h) -> y >= 10 ? 0xFF2A1A10 : cloth(pants, f, x, y, 213);
            s.save("entity/race/demon_namekian.png");
        }

        // ---- generated bodies (V2-D): greyscale flesh with muscle definition, tinted by skin tone at render time

        /** Light per face, as flesh() does it, in luminance. */
        static double faceLight(Face f) {
            return switch (f) { case FRONT -> 0.96; case TOP -> 1.0; case RIGHT -> 0.86; case LEFT -> 0.84; case BACK -> 0.8; case BOTTOM -> 0.68; };
        }

        static int lum(double v) {
            int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
            return 0xFF000000 | g << 16 | g << 8 | g;
        }

        /** A bare-chested body: pecs, abs, obliques, shoulder blades, deltoids, biceps, quads, calves; k = definition. */
        static void body(String name, double k) throws IOException {
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                double v = faceLight(f);
                if (f == Face.FRONT) {
                    if (y >= 5 && (x == 0 || x == 7)) v -= 0.05;                         // jaw
                    if (y == 7) v -= 0.06;                                                // chin shadow
                    if (y == 5 && (x == 3 || x == 4)) v -= 0.05;                          // nose
                }
                if ((f == Face.RIGHT || f == Face.LEFT) && y >= 3 && y <= 5 && x >= 3 && x <= 4) v -= 0.07; // ear
                return lum(v);
            };
            s.body = (f, x, y, w, h) -> {
                double v = faceLight(f);
                if (f == Face.FRONT) {
                    if (y >= 1 && y <= 3 && x >= 1 && x <= 6) v += 0.05 * k;              // pecs
                    if (y == 3 && (x == 3 || x == 4)) v -= 0.03 * k;
                    if (y == 4 && x >= 1 && x <= 6) v -= 0.11 * k;                        // under the pecs
                    if (y >= 5 && y <= 10 && x >= 2 && x <= 5) {                          // abs
                        if (y == 6 || y == 8) v -= 0.08 * k;
                        else v += 0.04 * k;
                        if (x == 3) v -= 0.03 * k;
                    }
                    if (y >= 5 && y <= 10 && (x == 1 || x == 6)) v -= 0.06 * k;          // obliques
                    if (y == 0 && (x == 2 || x == 5)) v -= 0.04 * k;                      // collarbones
                }
                if (f == Face.BACK) {
                    if (y >= 1 && y <= 4 && (x <= 2 || x >= 5)) v += 0.05 * k;            // shoulder blades
                    if (x == 3 || x == 4) v -= 0.04 * k;                                  // spine
                    if (y >= 6 && y <= 9 && (x == 0 || x == 7)) v -= 0.05 * k;            // lats
                }
                return lum(v);
            };
            s.arm = (f, x, y, w, h) -> {
                double v = faceLight(f);
                if (f != Face.TOP && f != Face.BOTTOM) {
                    if (y <= 2) v += 0.05 * k;                                            // deltoid
                    if (y == 3) v -= 0.07 * k;
                    if (f == Face.FRONT && y >= 4 && y <= 6) v += 0.06 * k;               // biceps
                    if (f == Face.BACK && y >= 4 && y <= 6) v += 0.03 * k;                // triceps
                    if (y == 7) v -= 0.06 * k;                                            // elbow
                    if (y >= 8 && y <= 10 && x == 1) v += 0.03 * k;                       // forearm
                }
                return lum(v);
            };
            s.leg = (f, x, y, w, h) -> {
                double v = faceLight(f);
                if (f == Face.FRONT && y >= 1 && y <= 5) v += 0.05 * k;                   // quads
                if (f == Face.FRONT && y == 6) v -= 0.07 * k;                             // knee
                if (f == Face.BACK && y >= 7 && y <= 9) v += 0.05 * k;                    // calves
                return lum(v);
            };
            s.save("entity/body/" + name + ".png");
        }

        /** Untinted overlay for the generated bodies: face, training pants with a belt, boots and wristbands. */
        static void outfit() throws IOException {
            int[] pants = ramp(0xFF26346E, 5), boots = ramp(0xFF7A4424, 4), band = ramp(0xFF26346E, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f != Face.FRONT) return 0;
                if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) return 0xFF3A2414;    // brows
                if (y == 4 && (x == 1 || x == 6)) return 0xFFF2F2F2;                         // eye whites
                if (y == 4 && (x == 2 || x == 5)) return 0xFF1E1610;                         // irises (form eyes draw over)
                if (y == 6 && (x == 3 || x == 4)) return 0xFF7A3E30;                         // mouth
                return 0;
            };
            s.body = (f, x, y, w, h) -> y == 10 ? 0xFF121A36 : y == 11 ? pants[f == Face.FRONT ? 3 : 2] : 0;
            s.arm = (f, x, y, w, h) -> f != Face.TOP && f != Face.BOTTOM && (y == 9 || y == 10) ? band[f == Face.FRONT ? 3 : y == 9 ? 2 : 1] : 0;
            s.leg = (f, x, y, w, h) -> {
                if (y >= 9 || f == Face.BOTTOM) return boots[f == Face.FRONT ? 3 : y == 9 ? 2 : 1];
                if (f == Face.TOP) return 0;
                int i = f == Face.FRONT ? 3 : f == Face.BACK ? 1 : 2;
                if (y == 6 && f == Face.FRONT) i--;                                           // a crease at the knee
                return pants[Math.max(0, i - (y == 8 ? 1 : 0))];
            };
            s.save("entity/body/outfit.png");
        }

        // ---- full race looks for players (optional, Life screen). Eyes stay at x 2 and 5 so form eye colours line up.

        static void raceNamekian() throws IOException {
            int[] sk = ramp(0xFF62B444, 5), pink = ramp(0xFFE09A9A, 3), gi = ramp(0xFF6A3A9A, 5), sash = ramp(0xFF40B0E0, 3), pants = ramp(0xFFE8E4F0, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) return face(sk, x, y, 0xFF101010, sk[1], true);
                if ((f == Face.RIGHT || f == Face.LEFT) && y >= 2 && y <= 4 && x >= 2 && x <= 3) return sk[1];
                if (f == Face.TOP && (x + y) % 4 == 0) return sk[2];                           // ridged crown
                return flesh(sk, f, x, y);
            };
            s.body = (f, x, y, w, h) -> y >= 9 && y <= 10 ? sash[y == 9 ? 2 : 1] : cloth(gi, f, x, y, 121);
            s.arm = (f, x, y, w, h) -> {
                if (y < 4) return cloth(gi, f, x, y, 122);
                if ((f == Face.FRONT || f == Face.RIGHT) && y >= 5 && y <= 7) return pink[1];
                return flesh(sk, f, x, y);
            };
            s.leg = (f, x, y, w, h) -> y >= 10 ? 0xFF5A3418 : cloth(pants, f, x, y, 123);
            s.save("entity/race/namekian.png");
        }

        static void raceFrostDemon() throws IOException {
            int[] white = ramp(0xFFF0EEF4, 5), shell = ramp(0xFF8A4AC8, 5);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.TOP || (f != Face.BOTTOM && y <= 1)) return shell[f == Face.TOP ? 3 : 2];   // dome
                if (f == Face.FRONT) {
                    if (y == 6 && (x == 3 || x == 4)) return 0xFF301040;                         // dark lips
                    if ((x == 0 || x == 7) && y >= 2 && y <= 3) return shell[1];                 // cheek plates
                    return face(white, x, y, 0xFFC01830, white[1], true);
                }
                if (f == Face.BACK && y <= 3) return shell[1];
                return flesh(white, f, x, y);
            };
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT && y >= 1 && y <= 6 && x >= 1 && x <= 6) return shell[y == 6 ? 1 : x < 4 ? 3 : 2]; // chest plate
                if (f == Face.BACK && y >= 1 && y <= 4) return shell[1];
                return flesh(white, f, x, y);
            };
            s.arm = (f, x, y, w, h) -> f == Face.TOP || y <= 2 || (y >= 6 && y <= 9) ? shell[f == Face.FRONT ? 3 : 2] : flesh(white, f, x, y);
            s.leg = (f, x, y, w, h) -> y >= 5 && y <= 9 ? shell[f == Face.FRONT ? 3 : 2] : flesh(white, f, x, y);
            s.save("entity/race/frost_demon.png");
        }

        static void raceMajin() throws IOException {
            int[] sk = ramp(0xFFF59AC0, 5), vest = ramp(0xFF2A2234, 4), pants = ramp(0xFFF2F0F4, 4), belt = ramp(0xFFE8C040, 3);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> f == Face.FRONT ? face(sk, x, y, 0xFF101010, sk[1], true) : flesh(sk, f, x, y);
            s.body = (f, x, y, w, h) -> {
                if (y == 9) return belt[f == Face.FRONT && (x == 3 || x == 4) ? 2 : 1];
                if (y >= 10) return cloth(pants, f, x, y, 131);
                if (f == Face.FRONT && x >= 2 && x <= 5) return flesh(sk, f, x, y);             // open vest
                return cloth(vest, f, x, y, 132);
            };
            s.arm = (f, x, y, w, h) -> y >= 9 ? pants[f == Face.FRONT ? 3 : 2] : flesh(sk, f, x, y); // white gloves
            s.leg = (f, x, y, w, h) -> y >= 9 ? belt[y == 9 ? 2 : 1] : cloth(pants, f, x, y, 133);   // baggy pants, gold boots
            s.save("entity/race/majin.png");
        }

        static String fighter(String name) {
            return "entity/fighter/" + name + ".png";
        }

        static void master() throws IOException {
            int[] sk = ramp(0xFFE0B48C, 5), gi = ramp(0xFFB02A2A, 5), sash = ramp(0xFF6A4020, 4), white = ramp(0xFFF0F0F0, 3);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y == 3 && x >= 0 && x <= 7 && x != 3 && x != 4) return white[1];         // bushy brows
                    if (y == 4 && (x == 1 || x == 2 || x == 5 || x == 6)) return darker(sk[1], 0.5); // narrowed eyes
                    if (y >= 5 && x >= 1 && x <= 6 && !(y == 6 && (x == 3 || x == 4))) return y == 5 && (x == 3 || x == 4) ? sk[2] : white[y == 5 ? 2 : 1]; // beard
                    return face(sk, x, y, 0xFF202020, white[1], false);
                }
                if ((f == Face.RIGHT || f == Face.LEFT) && y >= 4) return white[1];               // beard at the sides
                if (f == Face.BOTTOM) return white[0];
                return flesh(sk, f, x, y);                                                     // bald crown
            };
            s.hat = (f, x, y, w, h) -> {
                if (f == Face.TOP && x >= 3 && x <= 4 && y >= 3 && y <= 4) return white[2];     // topknot
                if (f == Face.BACK && y <= 1 && x >= 3 && x <= 4) return white[1];
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                if (y >= 9 && y <= 10) return y == 9 ? sash[2] : sash[1];
                if (f == Face.FRONT && y <= 4 && x >= 2 && x <= 5) return white[y == 4 ? 0 : 1]; // beard over the chest
                if (f == Face.FRONT && y <= 6 && Math.abs(x - 3.5) < (6.5 - y) * 0.6) return sk[2]; // open collar
                return cloth(gi, f, x, y, 61);
            };
            s.arm = (f, x, y, w, h) -> {
                if (f == Face.TOP) return gi[2];
                if (y < 5) return y == 4 ? gi[1] : cloth(gi, f, x, y, 62);
                if (f == Face.BOTTOM) return sk[1];
                return flesh(sk, f, x, y);
            };
            s.leg = (f, x, y, w, h) -> {
                if (y >= 10) return y == 10 ? sk[2] : sash[1];                                 // ankle + sandal
                if (f == Face.BOTTOM) return sash[0];
                return cloth(gi, f, x, y, 63);
            };
            s.save(fighter("martial_arts_master"));
        }

        static void patrolOfficer() throws IOException {
            int[] sk = ramp(0xFFD8A880, 5), navy = ramp(0xFF24387A, 5), plate = ramp(0xFFE8ECF0, 4), hair = ramp(0xFF5A3A20, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.TOP) return hair[2];
                if (f == Face.FRONT) return y <= 1 ? hair[2] : face(sk, x, y, 0xFF2A5AC0, hair[1], true);
                if (f == Face.BACK) return y <= 5 ? hair[1] : sk[2];
                if (f == Face.RIGHT || f == Face.LEFT) return y <= 2 || (y <= 4 && x >= 5) ? hair[1] : flesh(sk, f, x, y);
                return sk[1];
            };
            s.hat = (f, x, y, w, h) -> {                                                       // uniform cap with a visor
                if (f == Face.TOP) return navy[2];
                if (y <= 1) return f == Face.FRONT && y == 1 ? 0xFF101820 : navy[f == Face.FRONT ? 2 : 1];
                if (f == Face.FRONT && y == 2 && x >= 3 && x <= 4) return 0xFFE8C040;           // cap badge
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                if (y == 9) return 0xFF181820;                                                 // belt
                if (f == Face.FRONT && y == 9 && x >= 3 && x <= 4) return 0xFFE8C040;
                if ((f == Face.FRONT || f == Face.BACK) && y >= 1 && y <= 7 && x >= 1 && x <= 6) {
                    if (f == Face.FRONT && y == 2 && x == 5) return 0xFFFFD040;                 // star badge
                    return plate[y == 7 ? 0 : x < 4 ? 3 : 2];
                }
                return cloth(navy, f, x, y, 71);
            };
            s.arm = (f, x, y, w, h) -> {
                if (y >= 10) return f == Face.BOTTOM ? plate[1] : plate[2];                      // gloves
                return cloth(navy, f, x, y, 72);
            };
            s.leg = (f, x, y, w, h) -> y >= 8 ? (y == 8 ? 0xFF2A2A30 : 0xFF14141A) : cloth(navy, f, x, y, 73);
            s.save(fighter("patrol_officer"));
        }

        static void kiSoldier() throws IOException {
            int[] sk = ramp(0xFFE8C8A8, 5), suit = ramp(0xFF3A3E4A, 5), armor = ramp(0xFFE8E4D8, 4), pad = ramp(0xFFC8A040, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.TOP || (y <= 1 && f != Face.BOTTOM)) return 0xFF1A1A1E;           // cropped black hair
                if (f == Face.FRONT) return face(sk, x, y, 0xFF3A2010, 0xFF1A1A1E, true);
                if (f == Face.BACK && y <= 4) return 0xFF1A1A1E;
                return flesh(sk, f, x, y);
            };
            s.hat = (f, x, y, w, h) -> {                                                       // red scouter lens + earpiece
                if (f == Face.FRONT && y >= 3 && y <= 4 && x >= 6 && x <= 7) return y == 3 && x == 6 ? 0xFFFFA0A0 : 0xFFD02020;
                if (f == Face.LEFT && y >= 3 && y <= 5 && x >= 2 && x <= 4) return 0xFF5A6070;
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                if (y <= 8 && (f == Face.FRONT || f == Face.BACK)) return armor[f == Face.FRONT ? (x < 4 ? 3 : 2) : 1];
                if (y == 8) return armor[0];
                return cloth(suit, f, x, y, 81);
            };
            s.arm = (f, x, y, w, h) -> {
                if (f == Face.TOP || y <= 2) return pad[y == 2 ? 1 : 2];                        // shoulder pad
                if (y >= 10) return armor[2];                                                  // gloves
                return cloth(suit, f, x, y, 82);
            };
            s.leg = (f, x, y, w, h) -> y >= 7 ? armor[y == 7 ? 1 : 2] : cloth(suit, f, x, y, 83);
            s.save(fighter("ki_soldier"));
        }

        static void androidUnit() throws IOException {
            int[] m = ramp(0xFF9098A8, 5), joint = ramp(0xFF3A3E48, 3);
            FaceFn metal = (f, x, y, w, h) -> {
                int i = switch (f) { case FRONT, TOP -> 3; case BACK, BOTTOM -> 1; default -> 2; };
                if ((x + y * 3) % 7 == 0) i--;                                                // rivets / panel noise
                return m[Math.max(0, i)];
            };
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y == 4 && (x == 1 || x == 2 || x == 5 || x == 6)) return x == 2 || x == 5 ? 0xFFFF3020 : 0xFF901010; // LED eyes
                    if (y == 6 && x >= 2 && x <= 5) return joint[0];                             // grille mouth
                    if (y == 2) return m[2];                                                    // brow seam
                }
                if (y == 7 && f != Face.TOP && f != Face.BOTTOM) return joint[1];               // jaw seam
                return metal.at(f, x, y, w, h);
            };
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    double d = Math.hypot(x - 3.5, y - 3.5);
                    if (d < 1.6) return d < 0.8 ? 0xFFD0F8FF : 0xFF40B8FF;                         // energy core
                    if (d < 2.3) return joint[0];
                    if (x == 0 || x == 7) return m[2];
                }
                if (y == 8 || y == 9) return joint[1];                                          // waist joint
                return metal.at(f, x, y, w, h);
            };
            s.arm = (f, x, y, w, h) -> y == 5 || y == 6 ? joint[1] : y >= 10 ? m[1] : metal.at(f, x, y, w, h);
            s.leg = (f, x, y, w, h) -> y == 5 || y == 6 ? joint[1] : y >= 10 ? joint[0] : metal.at(f, x, y, w, h);
            s.save(fighter("android_unit"));
        }

        static void sproutling() throws IOException {
            int[] g = ramp(0xFF58A83A, 5), leaf = ramp(0xFF3A9A30, 4);
            FaceFn bumpy = (f, x, y, w, h) -> {
                int i = switch (f) { case FRONT, TOP -> 3; case BACK, BOTTOM -> 1; default -> 2; };
                return noise(x, y, f.ordinal() + 90) > 0.8 ? g[Math.max(0, i - 2)] : g[i];
            };
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y >= 3 && y <= 4 && (x == 1 || x == 2 || x == 5 || x == 6)) return (x == 2 || x == 5) && y == 4 ? 0xFF201000 : 0xFFF0D020; // big yellow eyes
                    if (y == 6 && x >= 1 && x <= 6) return x == 1 || x == 6 ? g[1] : 0xFF301818;     // wide grin
                }
                return bumpy.at(f, x, y, w, h);
            };
            s.hat = (f, x, y, w, h) -> {                                                       // sprout: two leaves on top
                if (f == Face.TOP && ((x == 3 && y >= 2 && y <= 4) || (x == 4 && y >= 3 && y <= 5))) return leaf[3];
                if ((f == Face.FRONT || f == Face.BACK) && y == 0 && (x == 3 || x == 4)) return leaf[2];
                return 0;
            };
            s.body = (f, x, y, w, h) -> f == Face.FRONT && x >= 2 && x <= 5 && y >= 2 && y <= 9 ? mix(g[3], 0xFFE8F0B0, 0.35) : bumpy.at(f, x, y, w, h);
            s.arm = (f, x, y, w, h) -> f == Face.FRONT && y == 11 && x % 2 == 0 ? 0xFFE8E0C0 : bumpy.at(f, x, y, w, h); // claws
            s.leg = (f, x, y, w, h) -> y >= 10 ? leaf[1] : bumpy.at(f, x, y, w, h);              // root feet
            s.save(fighter("sproutling"));
        }

        static void tyrantLord() throws IOException {
            int[] ob = ramp(0xFF3A3448, 5), gold = ramp(0xFFD8A840, 4);
            FaceFn shell = (f, x, y, w, h) -> {
                int i = switch (f) { case FRONT, TOP -> 3; case BACK, BOTTOM -> 1; default -> 2; };
                return ob[i];
            };
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (y == 4 && (x == 1 || x == 2 || x == 5 || x == 6)) return x == 2 || x == 5 ? 0xFFFF2030 : 0xFF600818; // red eyes
                    if (y == 6 && x >= 2 && x <= 5) return 0xFF100818;
                    if (y == 3 && (x == 1 || x == 6)) return gold[2];                            // brow ridges
                }
                return shell.at(f, x, y, w, h);
            };
            s.hat = (f, x, y, w, h) -> {                                                       // gold crown-horns
                if (f == Face.FRONT && y <= 1 && (x <= 1 || x >= 6 || y == 1)) return gold[y == 0 ? 3 : 2];
                if ((f == Face.RIGHT || f == Face.LEFT) && y <= 1) return gold[1];
                if (f == Face.TOP && (x <= 1 || x >= 6)) return gold[3];
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT) {
                    if (Math.hypot(x - 3.5, y - 2.5) < 1.3) return 0xFFFF3040;                    // chest gem
                    if (y == 0 || y == 5 || x == 0 || x == 7) return gold[x == 0 ? 3 : 2];       // gold trim
                }
                if (y == 9) return gold[1];
                return shell.at(f, x, y, w, h);
            };
            s.arm = (f, x, y, w, h) -> f == Face.TOP || y <= 1 ? gold[2] : y >= 10 ? gold[1] : shell.at(f, x, y, w, h);
            s.leg = (f, x, y, w, h) -> y >= 10 ? gold[1] : shell.at(f, x, y, w, h);
            s.save(fighter("tyrant_lord"));
        }

        static void rampageBrute() throws IOException {
            int[] sk = ramp(0xFFC04A34, 5), hair = ramp(0xFF1A1418, 3), pants = ramp(0xFF5A4030, 4), gold = ramp(0xFFE0B040, 4);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.TOP || y <= 1) return hair[noise(x, y, 101) > 0.5 ? 1 : 2];      // wild hair
                if (f == Face.FRONT) {
                    if (y == 3 && x >= 1 && x <= 6 && x != 3 && x != 4) return hair[0];          // heavy brow
                    if (y == 4 && (x == 2 || x == 5)) return 0xFFFFE040;                          // small yellow eyes
                    if (y == 6 && (x == 2 || x == 5)) return 0xFFF0E8D0;                          // tusks
                    if (y == 6 && (x == 3 || x == 4)) return 0xFF401010;
                }
                if (f == Face.BACK && y <= 4) return hair[1];
                return flesh(sk, f, x, y);
            };
            s.hat = (f, x, y, w, h) -> (f == Face.TOP && (x == 0 || x == 7) && y <= 2) || ((f == Face.RIGHT || f == Face.LEFT) && y == 0 && x >= 3 && x <= 4)
                    ? 0xFFE8E0C8 : 0;                                                          // horns
            s.body = (f, x, y, w, h) -> {
                if (y == 9) return gold[f == Face.FRONT && (x == 3 || x == 4) ? 3 : 1];          // gold belt
                if (y >= 10) return pants[2];
                if (f == Face.FRONT && ((x == 3 || x == 4) && y >= 4 || y == 4 || y == 7)) return sk[2]; // abs
                return flesh(sk, f, x, y);
            };
            s.arm = (f, x, y, w, h) -> y >= 9 && y <= 10 ? gold[2] : flesh(sk, f, x, y);       // arm bands
            s.leg = (f, x, y, w, h) -> {
                if (y >= 9) return y >= 11 && f == Face.FRONT ? sk[2] : (y == 9 && x % 2 == 0 ? 0 : (y >= 10 ? sk[2] : pants[1])); // torn hems, bare feet
                return cloth(pants, f, x, y, 103);
            };
            s.save(fighter("rampage_brute"));
        }

        static void namekianWarrior() throws IOException {
            int[] sk = ramp(0xFF5DB040, 5), pink = ramp(0xFFE09A9A, 3), gi = ramp(0xFF6A3A9A, 5), sash = ramp(0xFF40B0E0, 3), cape = ramp(0xFFF0F0F0, 3);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> {
                if (f == Face.FRONT) return face(sk, x, y, 0xFF101010, sk[1], true);
                if ((f == Face.RIGHT || f == Face.LEFT) && y >= 2 && y <= 4 && x >= 2 && x <= 3) return sk[1]; // pointed ears
                return flesh(sk, f, x, y);
            };
            s.hat = (f, x, y, w, h) -> {
                if (f == Face.TOP && ((x == 2 && y <= 2) || (x == 5 && y <= 2))) return sk[3];   // antennae
                if (y <= 2 && f != Face.BOTTOM) return f == Face.TOP ? cape[2] : cape[y == 2 ? 0 : 1]; // turban
                if (f == Face.TOP) return cape[2];
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                if (y >= 9 && y <= 10) return sash[y == 9 ? 2 : 1];
                return cloth(gi, f, x, y, 111);
            };
            s.jacket = (f, x, y, w, h) -> f == Face.BACK ? cape[x == 0 || x == 7 ? 0 : 1]        // cape on the back
                    : f == Face.TOP && (y == 0 || y == 3) ? cape[2] : 0;
            s.arm = (f, x, y, w, h) -> {
                if (y < 4) return cloth(gi, f, x, y, 112);
                if ((f == Face.FRONT || f == Face.RIGHT) && y >= 5 && y <= 7) return pink[1];    // pink forearm patches
                return flesh(sk, f, x, y);
            };
            s.leg = (f, x, y, w, h) -> y >= 10 ? 0xFF6A4020 : cloth(gi, f, x, y, 113);
            s.save(fighter("namekian_warrior"));
        }
    }

    // ================================================================== creatures with their own models

    static final class Creatures {
        static void all() throws IOException {
            greatApe();
            dragon();
            formHair();
            spacePod();
        }

        /** Texture for client.render.SpacePodRenderer (128x128): white panelled shell, red window, dark legs and thruster. */
        static void spacePod() throws IOException {
            int[] w = ramp(0xFFE8ECF2, 5), dark = ramp(0xFF3A3E48, 3), red = ramp(0xFFD03040, 4);
            Canvas c = new Canvas(128, 128);
            FaceFn shell = (f, x, y, wd, h) -> {
                int i = switch (f) { case TOP -> 4; case FRONT -> 3; case BOTTOM -> 1; default -> 2; };
                if (x % 6 == 5 || y % 8 == 7) i = Math.max(0, i - 1);                                 // panel lines
                if (f != Face.TOP && f != Face.BOTTOM && y == h / 2) return 0xFFD03040;              // red band
                return w[i];
            };
            box(c, 0, 0, 24, 16, 18, shell);
            box(c, 0, 34, 18, 24, 18, shell);
            box(c, 0, 76, 18, 16, 24, shell);
            box(c, 86, 0, 12, 8, 1, (f, x, y, wd, h) -> f == Face.FRONT
                    ? (x + y < 4 ? 0xFFFFC0C8 : red[y < 2 ? 3 : y > 5 ? 1 : 2]) : dark[1]);         // window
            box(c, 86, 10, 2, 8, 2, (f, x, y, wd, h) -> y >= 7 ? dark[0] : dark[f == Face.FRONT ? 2 : 1]); // legs
            box(c, 86, 20, 8, 2, 8, (f, x, y, wd, h) -> f == Face.BOTTOM
                    ? (Math.hypot(x - 3.5, y - 3.5) < 2.5 ? 0xFFFF8020 : dark[0]) : dark[1]);       // thruster glow
            c.save("entity/space_pod.png");
        }

        /**
         * Hair for client.render.FormHairModel (64x64, greyscale: the hair colour tints it). Regions: cap (0,0),
         * long hair (0,16), eyes (40,0) white, spike tiers (0,48) roots / (16,48) middles / (32,48) tips.
         */
        static void formHair() throws IOException {
            Canvas c = new Canvas(64, 64);
            for (int y = 0; y < 12; y++) for (int x = 0; x < 32; x++) {                              // cap: dense strands
                double v = 0.80 + (noise(x, 0, 301) - 0.5) * 0.16 - (y > 7 ? 0.08 : 0);
                c.set(x, y, grey(v));
            }
            for (int y = 16; y < 44; y++) for (int x = 0; x < 24; x++) {                              // long hair: falling strands
                double v = 0.92 - (y - 16) * 0.006 + (noise(x, 1, 302) - 0.5) * 0.18;
                c.set(x, y, grey(v));
            }
            c.rect(40, 0, 42, 1, 0xFFFFFFFF);                                                         // eyes
            double[] tier = {0.70, 0.86, 1.0};
            for (int t = 0; t < 3; t++)
                for (int y = 48; y < 60; y++) for (int x = t * 16; x < t * 16 + 16; x++) {
                    double v = tier[t] + (noise(x, 2, 303 + t) - 0.5) * 0.12;
                    if (t == 2 && (x + y) % 5 == 0) v = 1.0;                                         // glinting tips
                    c.set(x, y, grey(v));
                }
            c.save("entity/form_hair.png");
        }

        static int grey(double v) {
            int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
            return 0xFF000000 | g << 16 | g << 8 | g;
        }

        /** Scales: staggered arcs, darker at each scale's lower edge. */
        static int scales(int[] r, Face f, int x, int y) {
            int i = switch (f) { case TOP -> 3; case FRONT, BACK -> 2; case BOTTOM -> 1; default -> 2; };
            int row = y / 2;
            boolean edge = y % 2 == 1 && (x + (row % 2) * 2) % 4 != 0;
            if (edge) i = Math.max(0, i - 1);
            if ((x + (row % 2) * 2) % 4 == 1 && y % 2 == 0) i = Math.min(r.length - 1, i + 1);    // glint on each scale
            return r[i];
        }

        /** Texture for client.render.DragonModel (64x64). */
        static void dragon() throws IOException {
            int[] g = ramp(0xFF2E9A48, 5), belly = ramp(0xFFE8D890, 4), horn = ramp(0xFFE8DCC0, 4), gold = ramp(0xFFE0B040, 3);
            Canvas c = new Canvas(64, 64);
            box(c, 0, 0, 8, 8, 12, (f, x, y, w, h) -> f == Face.BOTTOM ? belly[(y % 3 == 0) ? 1 : 2] : scales(g, f, x, y)); // body segment
            box(c, 40, 0, 2, 4, 2, (f, x, y, w, h) -> y == 0 ? gold[2] : gold[1]);                      // dorsal spike
            box(c, 48, 0, 1, 1, 1, (f, x, y, w, h) -> 0xFFFF2020);                                    // eyes
            box(c, 0, 20, 8, 6, 10, (f, x, y, w, h) -> {                                             // skull
                if (f == Face.FRONT && y == 1 && (x == 1 || x == 6)) return g[0];                       // brow
                return f == Face.BOTTOM ? belly[1] : scales(g, f, x, y);
            });
            box(c, 36, 20, 6, 4, 8, (f, x, y, w, h) -> {                                            // snout
                if (f == Face.FRONT && y == 0 && (x == 1 || x == 4)) return g[0];                       // nostrils
                if ((f == Face.RIGHT || f == Face.LEFT) && y == 3 && x % 2 == 0) return 0xFFF8F4E8;    // teeth
                return f == Face.BOTTOM ? belly[2] : f == Face.TOP ? g[3] : g[2];
            });
            box(c, 0, 36, 6, 2, 8, (f, x, y, w, h) -> {                                             // jaw
                if (f == Face.TOP) return 0xFF801828;                                                // mouth
                if ((f == Face.RIGHT || f == Face.LEFT) && y == 0 && x % 2 == 1) return 0xFFF8F4E8;    // lower teeth
                return belly[f == Face.BOTTOM ? 1 : 2];
            });
            box(c, 28, 36, 2, 2, 8, (f, x, y, w, h) -> horn[f == Face.TOP ? 3 : (f == Face.BOTTOM ? 0 : 1 + (x + y) % 2)]); // horns
            box(c, 0, 46, 1, 1, 12, (f, x, y, w, h) -> gold[f == Face.TOP ? 2 : 1]);                 // whiskers
            c.save("entity/eternal_dragon.png");
        }

        /** Fur: vertical strands, lighter on top-facing and front faces. */
        static int fur(int[] r, Face f, int x, int y, int seed) {
            int i = switch (f) { case TOP -> 3; case FRONT -> 2; case BOTTOM -> 0; default -> 1; };
            double strand = noise(x, y / 2, seed);
            if (strand > 0.78) i = Math.min(r.length - 1, i + 1);
            else if (strand < 0.2) i = Math.max(0, i - 1);
            return r[i];
        }

        /** Texture for client.render.GreatApeModel (128x64, same box layout as the model). */
        static void greatApe() throws IOException {
            int[] fur = ramp(0xFF6A4224, 5), skin = ramp(0xFFC89A70, 4), dark = ramp(0xFF2A1A12, 3);
            Canvas c = new Canvas(128, 64);
            box(c, 0, 0, 9, 8, 8, (f, x, y, w, h) -> {                                          // skull
                if (f == Face.FRONT) {
                    if (y == 4 && (x == 2 || x == 6)) return 0xFFFF2018;                          // red eyes
                    if (y == 4 && (x == 1 || x == 3 || x == 5 || x == 7)) return 0xFF801010;
                    if (y >= 3 && x >= 1 && x <= 7) return skin[y == 3 ? 1 : 2];                   // face mask
                }
                return fur(fur, f, x, y, 201);
            });
            box(c, 36, 0, 6, 4, 3, (f, x, y, w, h) -> {                                         // muzzle
                if (f == Face.FRONT) {
                    if (y == 0 && (x == 1 || x == 4)) return dark[0];                            // nostrils
                    if (y == 2) return dark[1];                                                 // mouth
                    if (y == 3 && (x == 0 || x == 5)) return 0xFFF0E8D0;                         // fangs
                }
                return f == Face.TOP ? skin[2] : f == Face.BOTTOM ? skin[0] : skin[1];
            });
            box(c, 36, 8, 9, 1, 1, (f, x, y, w, h) -> f == Face.TOP ? fur[2] : fur[0]);         // brow ridge
            box(c, 58, 0, 1, 3, 2, (f, x, y, w, h) -> f == Face.RIGHT || f == Face.LEFT ? skin[1] : fur[1]); // ears
            box(c, 0, 16, 14, 13, 9, (f, x, y, w, h) -> {                                       // body
                if (f == Face.FRONT && x >= 3 && x <= 10 && y >= 1 && y <= 11) return skin[(x + y) % 5 == 0 ? 1 : 2]; // chest
                return fur(fur, f, x, y, 202);
            });
            box(c, 48, 16, 5, 18, 5, (f, x, y, w, h) -> {                                       // arms
                if (y >= 15) return f == Face.BOTTOM ? dark[0] : dark[f == Face.FRONT ? 2 : 1];  // leathery hands
                return fur(fur, f, x, y, 203);
            });
            box(c, 70, 16, 5, 10, 5, (f, x, y, w, h) -> y >= 8 ? dark[f == Face.FRONT ? 2 : 1] : fur(fur, f, x, y, 204)); // legs + feet
            box(c, 92, 16, 2, 2, 8, (f, x, y, w, h) -> fur(fur, f, x, y, 205));                 // tail
            c.save("entity/great_ape.png");
        }
    }

    // ================================================================== GUI

    static final class Gui {
        static void all() throws IOException {
            hud();
            ui();
        }

        /**
         * UI v2 sheet (256x256) for client.ui.DbzTheme and the HUD. Portrait ring (0,0) 56x56 (white: tinted per form) and
         * backplate (56,0) 56x56; panel nine-slice (112,0) 32x32 slice 8; buttons 32x16 slice 4 at (144,0) normal,
         * (144,16) hover, (144,32) disabled, (144,48) selected; hotbar slot (176,0) 22x22, selected slot (176,24) 24x24;
         * cloud end caps (200,0) 28x22 left, (200,24) right; radial icons 16x16 from (0,64): transform up, power down,
         * fly, overdrive, overdrive off, stats, forms, techniques, life, close, ki orb (white).
         */
        static void ui() throws IOException {
            Canvas c = new Canvas(256, 256);
            int[] gold = ramp(0xFFD8A040, 5);
            // portrait ring: bevelled band, light from the top left, eight notches
            for (int y = 0; y < 56; y++) for (int x = 0; x < 56; x++) {
                double dx = x + 0.5 - 28, dy = y + 0.5 - 28, r = Math.sqrt(dx * dx + dy * dy);
                if (r < 21.5 || r > 27.5) continue;
                double light = 0.72 + 0.28 * (-(dx + dy) / (r * 1.414));
                double ang = Math.atan2(dy, dx);
                boolean notch = Math.abs(Math.sin(ang * 4)) < 0.06 && r > 23 && r < 26.5;
                boolean rim = r < 22.5 || r > 26.5;
                int l = (int) (255 * Math.max(0, Math.min(1, rim ? light * 0.45 : notch ? light * 0.6 : light)));
                c.set(x, y, 0xFF000000 | l << 16 | l << 8 | l);
            }
            for (int y = 0; y < 56; y++) for (int x = 0; x < 56; x++) {           // backplate
                double dx = x + 0.5 - 28, dy = y + 0.5 - 28, r = Math.sqrt(dx * dx + dy * dy);
                if (r > 22.5) continue;
                c.set(56 + x, y, mix(0xFF22305A, 0xFF070A14, Math.min(1, r / 22.5)));
            }
            // panel: dark glass, double gold trim, a diamond in each corner
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int e = Math.min(Math.min(x, y), Math.min(31 - x, 31 - y));
                int col = e == 0 ? 0xFF07070C : e == 1 ? gold[3] : e == 2 ? gold[0] : e == 3 ? 0xFF0C1020 : 0xE6111A2E;
                int cx = x < 16 ? 4 : 27, cy = y < 16 ? 4 : 27;
                if (Math.abs(x - cx) + Math.abs(y - cy) <= 2 && e >= 2) col = Math.abs(x - cx) + Math.abs(y - cy) == 0 ? gold[4] : gold[2];
                c.set(112 + x, y, col);
            }
            button(c, 144, 0, 0xF0182238, gold[1], 0xFF34507A);
            button(c, 144, 16, 0xF0284068, gold[4], 0xFF6C9CD8);
            button(c, 144, 32, 0xC0101018, 0xFF4A4A55, 0xFF22222A);
            button(c, 144, 48, 0xF07A4A10, gold[4], 0xFFFFD27A);
            // hotbar slots
            for (int y = 0; y < 22; y++) for (int x = 0; x < 22; x++) {
                int e = Math.min(Math.min(x, y), Math.min(21 - x, 21 - y));
                boolean corner = (x == 0 || x == 21) && (y == 0 || y == 21);
                if (corner) continue;
                c.set(176 + x, y, e == 0 ? 0xFF07070C : e == 1 ? gold[1] : e == 2 ? 0xFF0C1020 : mix(0xC0223252, 0xC0101828, y / 21.0));
            }
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                int e = Math.min(Math.min(x, y), Math.min(23 - x, 23 - y));
                boolean corner = (x <= 1 || x >= 22) && (y <= 1 || y >= 22) && e == 0;
                if (corner || e > 3) continue;
                c.set(176 + x, 24 + y, e == 0 ? 0xFF2A1404 : e == 1 ? 0xFFFFB040 : e == 2 ? 0xFFFFE6A0 : 0x80FF9A2A);
            }
            // cloud caps: a puffy golden cloud, outlined
            Canvas cloud = new Canvas(28, 22);
            int[] cl = ramp(0xFFF2CE5A, 5);
            double[][] puffs = {{9, 13, 7}, {16, 10, 7.5}, {22, 13, 5.5}, {5, 15, 4.5}, {13, 15, 6}};
            for (double[] p : puffs) cloud.sphere(p[0], p[1], p[2], p[2] * 0.85, cl, false);
            cloud.outline();
            blitInto(c, cloud, 200, 0);
            for (int y = 0; y < 22; y++) for (int x = 0; x < 28; x++) c.set(200 + x, 24 + y, cloud.get(27 - x, y));
            icons(c, gold);
            c.save("gui/ui.png");
        }

        /** A 32x16 button skin: dark edge, a trim colour, a bright top line, the fill. */
        static void button(Canvas c, int x0, int y0, int fill, int trim, int shine) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) {
                int e = Math.min(Math.min(x, y), Math.min(31 - x, 15 - y));
                boolean corner = (x == 0 || x == 31) && (y == 0 || y == 15);
                if (corner) continue;
                int col = e == 0 ? 0xFF07070C : e == 1 ? trim : y == 2 ? shine : mix(fill, 0xFF000000, y > 9 ? 0.18 : 0);
                c.set(x0 + x, y0 + y, col);
            }
        }

        /** Radial menu icons, 16x16, outlined. */
        static void icons(Canvas c, int[] gold) {
            int ink = 0xFFF4F0E6;
            int[] red = ramp(0xFFE8402A, 5), blue = ramp(0xFF5AB8FF, 5), cl = ramp(0xFFF2CE5A, 5);
            Canvas[] ic = new Canvas[12];
            for (int i = 0; i < ic.length; i++) ic[i] = new Canvas(16, 16);
            // 0 transform up: spiky crown of hair over an up arrow
            for (int i = 0; i < 3; i++) ic[0].line(2 + i * 4, 7, 4 + i * 4, 1, gold[4]).line(4 + i * 4, 1, 6 + i * 4, 7, gold[3]);
            ic[0].rect(2, 7, 14, 8, gold[2]).rect(7, 10, 8, 15, ink).line(4, 13, 7, 10, ink).line(11, 13, 8, 10, ink);
            // 1 power down: down arrow
            ic[1].rect(7, 1, 8, 9, blue[3]).line(3, 8, 7, 13, blue[4]).line(12, 8, 8, 13, blue[4]).line(4, 8, 7, 12, blue[2]).line(11, 8, 8, 12, blue[2]);
            // 2 fly: a cloud
            for (double[] p : new double[][]{{5, 10, 3.5}, {9, 8, 4.5}, {12.5, 10, 3}}) ic[2].sphere(p[0], p[1], p[2], p[2] * 0.9, cl, false);
            // 3 overdrive: red burst
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                ic[3].line(8, 8, (int) Math.round(8 + Math.cos(ang) * 7), (int) Math.round(8 + Math.sin(ang) * 7), red[a % 2 == 0 ? 4 : 2]);
            }
            ic[3].sphere(8, 8, 3, 3, red, true);
            // 4 overdrive off: the burst greyed and crossed out
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) ic[4].set(x, y, ic[3].get(x, y) == 0 ? 0 : mix(ic[3].get(x, y), 0xFF505058, 0.6));
            ic[4].line(2, 2, 13, 13, ink).line(3, 2, 14, 13, ink);
            // 5 stats: bar chart
            ic[5].rect(2, 9, 4, 13, blue[3]).rect(6, 5, 8, 13, gold[3]).rect(10, 2, 12, 13, red[3]).rect(1, 14, 14, 14, ink);
            // 6 forms: lightning bolt
            ic[6].paint(0, 0, java.util.Map.of('#', gold[4], 'd', gold[1]),
                    "........##......", ".......##d......", "......##d.......", ".....##d........", "....######d.....", "......##d.......",
                    ".....##d........", "....##d.........", "...######d......", ".....##d........", "....##d.........", "...##d..........",
                    "..##d...........", "..#d............", "................", "................");
            // 7 techniques: a rolled scroll
            ic[7].rect(3, 3, 12, 12, 0xFFE8DCB8).rect(2, 2, 13, 3, 0xFFB08A50).rect(2, 12, 13, 13, 0xFFB08A50);
            for (int y = 5; y <= 10; y += 2) ic[7].hline(5, 10, y, 0xFF806A48);
            // 8 life: head and shoulders
            ic[8].sphere(8, 5, 3.5, 3.5, ramp(0xFFE8B888, 5), false).sphere(8, 14, 6, 4, blue, false);
            // 9 close: X
            ic[9].line(3, 3, 12, 12, red[3]).line(4, 3, 13, 12, red[3]).line(12, 3, 3, 12, red[3]).line(13, 3, 4, 12, red[3]);
            // 10 ki orb (white: tinted per technique)
            ic[10].sphere(8, 8, 5.5, 5.5, ramp(0xFFE0E0E0, 5), true);
            // 11 settings: a gear
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double dx = x + 0.5 - 8, dy = y + 0.5 - 8, r = Math.sqrt(dx * dx + dy * dy), a = Math.atan2(dy, dx);
                boolean tooth = Math.cos(a * 8) > 0.3 && r < 7.2;
                if ((r < 5.2 || tooth) && r > 2.2) ic[11].set(x, y, mix(0xFFB8C0D0, 0xFF6A7488, (dx + dy + 10) / 20.0));
            }
            for (int i = 0; i < ic.length; i++) {
                ic[i].outline();
                blitInto(c, ic[i], i * 16, 64);
            }
        }

        /**
         * HUD sprite sheet (256x256: GuiGraphics nine-slicing assumes that size) for client.DbzHud. Panel nine-slice (0,0) 16x16 slice 4; chip nine-slice (16,0)
         * 16x16 slice 3; icons 8x8 at (32,0) body, (40,0) ki, (48,0) stamina, (56,0) release; bars 112x7: track
         * (0,16), body (0,24), ki (0,32), stamina (0,40), release (0,48).
         */
        static void hud() throws IOException {
            Canvas c = new Canvas(256, 256);
            int[] gold = ramp(0xFFD8A040, 4);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {                       // panel
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                boolean inner = x == 1 || y == 1 || x == 14 || y == 14;
                boolean corner = (x < 3 || x > 12) && (y < 3 || y > 12);
                int col = edge ? 0xF0181014 : inner ? (corner ? gold[3] : gold[x < 8 && y < 8 ? 2 : 1]) : alpha(0xFF101018, 0xC8);
                if (corner && edge) col = gold[0];
                c.set(x, y, col);
            }
            for (int y = 0; y < 16; y++) for (int x = 16; x < 32; x++) {                      // chip
                int lx = x - 16;
                boolean edge = lx == 0 || y == 0 || lx == 15 || y == 15;
                c.set(x, y, edge ? 0xE0303040 : 0xB0000000);
            }
            Canvas icons = new Canvas(8, 8);                                                 // body: red heart-cross
            icons.paint(0, 0, java.util.Map.of('#', 0xFFE0453A, 'h', 0xFFFF9080, 'd', 0xFF901818),
                    ".##.##..",
                    "#hh###d.",
                    "#h####d.",
                    ".#####d.",
                    "..###d..",
                    "...#d...",
                    "........",
                    "........");
            blitInto(c, icons, 32, 0);
            Canvas ki = new Canvas(8, 8);                                                    // ki: blue flame
            ki.paint(0, 0, java.util.Map.of('#', 0xFF3CC8FF, 'h', 0xFFD0F4FF, 'd', 0xFF1868B0),
                    "...#....",
                    "..##....",
                    "..#h#...",
                    ".##h#d..",
                    ".#hh##d.",
                    ".#hh##d.",
                    "..####..",
                    "........");
            blitInto(c, ki, 40, 0);
            Canvas st = new Canvas(8, 8);                                                    // stamina: bolt
            st.paint(0, 0, java.util.Map.of('#', 0xFFF2C43A, 'd', 0xFFA07010),
                    "....##..",
                    "...##...",
                    "..##d...",
                    ".######.",
                    "...d##..",
                    "...##...",
                    "..##....",
                    "........");
            blitInto(c, st, 48, 0);
            Canvas rel = new Canvas(8, 8);                                                   // release: burst
            rel.paint(0, 0, java.util.Map.of('#', 0xFFFF8A2A, 'h', 0xFFFFE0A0),
                    "...#....",
                    ".#.#.#..",
                    "..###...",
                    "###h###.",
                    "..###...",
                    ".#.#.#..",
                    "...#....",
                    "........");
            blitInto(c, rel, 56, 0);
            bar(c, 16, 0xFF2A2A33, true);
            bar(c, 24, 0xFFE0453A, false);
            bar(c, 32, 0xFF3CC8FF, false);
            bar(c, 40, 0xFFF2C43A, false);
            bar(c, 48, 0xFFFF8A2A, false);
            c.save("gui/hud.png");
        }

        /** A 112x7 bar at row {@code v}: bevelled track, or a glossy gradient fill with a soft notch every 10%. */
        static void bar(Canvas c, int v, int base, boolean track) {
            int[] r = ramp(base, 5);
            for (int y = 0; y < 7; y++) for (int x = 0; x < 112; x++) {
                int col;
                if (track) col = y == 0 ? 0xFF15151C : y == 6 ? 0xFF3A3A48 : base;
                else {
                    col = r[y == 0 ? 4 : y == 1 ? 3 : y >= 5 ? 1 : 2];
                    if (x % 11 == 10 && y > 0) col = mix(col, 0xFF000000, 0.25);
                }
                c.set(x, v + y, col);
            }
        }

        static void blitInto(Canvas dst, Canvas src, int x0, int y0) {
            for (int y = 0; y < src.h; y++) for (int x = 0; x < src.w; x++) if (src.opaque(x, y)) dst.set(x0 + x, y0 + y, src.get(x, y));
        }
    }

    // ================================================================== effects (tinted at render time: white/grey + alpha)

    static final class Fx {
        static void all() throws IOException {
            auraFlame();
            impactStar();
            shockRing();
            crater();
            streak();
            auraEdge();
            beamFlow();
        }

        static double smooth(double e0, double e1, double x) {
            double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
            return t * t * (3 - 2 * t);
        }

        static int white(double alpha, double lum) {
            int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
            int l = (int) Math.round(Math.max(0, Math.min(1, lum)) * 255);
            return a << 24 | l << 16 | l << 8 | l;
        }

        /**
         * Eight looping frames of one flame tongue, 32x64 each, side by side. Every moving term uses whole multiples
         * of the frame phase, so frame 7 flows back into frame 0.
         */
        static void auraFlame() throws IOException {
            int fw = 32, fh = 64, frames = 8;
            Canvas c = new Canvas(fw * frames, fh);
            for (int f = 0; f < frames; f++) {
                double ph = f * Math.PI * 2 / frames;
                for (int y = 0; y < fh; y++) {
                    double v = 1 - y / (double) (fh - 1);                       // 0 base .. 1 tip
                    double half = 0.46 * Math.pow(1 - v, 0.55) * (0.9 + 0.1 * Math.sin(v * Math.PI));
                    double cx = 0.5 + 0.13 * Math.pow(v, 1.4) * Math.sin(ph + v * 5) + 0.04 * Math.sin(2 * ph + v * 11);
                    for (int x = 0; x < fw; x++) {
                        double u = (x + 0.5) / fw;
                        double edge = 0.16 * Math.sin(v * 23 - 3 * ph + u * 9) + 0.08 * Math.sin(v * 41 + 2 * ph - u * 17);
                        double d = Math.abs(u - cx) / Math.max(1e-3, half) + edge * v;
                        double mask = 1 - smooth(0.55, 1.0, d);
                        double streak = 0.5 + 0.5 * Math.sin(v * 15 - 2 * ph + Math.sin(u * 8 + ph) * 1.4);
                        double tipBreak = v > 0.7 ? 0.55 + 0.45 * Math.sin(v * 30 - 4 * ph + u * 6) : 1;
                        double base = smooth(0.0, 0.12, v);
                        double a = mask * (0.6 + 0.4 * streak) * Math.max(0, tipBreak) * base;
                        double core = 1 - smooth(0.0, 0.7, d);
                        c.set(f * fw + x, y, a <= 0.01 ? 0 : white(a, 0.78 + 0.22 * core));
                    }
                }
            }
            c.save("entity/aura_flame.png");
        }

        /** A hit flash: eight tapering spikes over a round glow. */
        static void impactStar() throws IOException {
            int n = 64;
            Canvas c = new Canvas(n, n);
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                    double r = Math.sqrt(dx * dx + dy * dy), ang = Math.atan2(dy, dx);
                    double spikes = Math.pow(Math.abs(Math.cos(ang * 2)), 18) + 0.55 * Math.pow(Math.abs(Math.cos(ang * 2 + Math.PI / 4)), 26);
                    double spike = spikes * (1 - smooth(0.1, 1.0, r));
                    double glow = Math.exp(-r * r * 9);
                    double a = Math.min(1, glow + spike);
                    c.set(x, y, a < 0.01 ? 0 : white(a, 1));
                }
            c.save("entity/impact_star.png");
        }

        /** A shockwave: a bright thin ring with a soft trailing inner haze. */
        static void shockRing() throws IOException {
            int n = 64;
            Canvas c = new Canvas(n, n);
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                    double r = Math.sqrt(dx * dx + dy * dy);
                    double ring = Math.exp(-Math.pow((r - 0.86) / 0.05, 2));
                    double haze = r < 0.86 ? 0.22 * Math.pow(r / 0.86, 3) : 0;
                    double a = Math.min(1, ring + haze);
                    c.set(x, y, a < 0.01 ? 0 : white(a, 1));
                }
            c.save("entity/shock_ring.png");
        }

        /** A landing crater decal: scorched pit, a pale rim, cracks running out, fading at the edge. */
        static void crater() throws IOException {
            int n = 64;
            Canvas c = new Canvas(n, n);
            int cracks = 9;
            double[] crackAng = new double[cracks];
            for (int i = 0; i < cracks; i++) crackAng[i] = (i + noise(i, 3, 77) * 0.7) * Math.PI * 2 / cracks;
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                    double r = Math.sqrt(dx * dx + dy * dy), ang = Math.atan2(dy, dx);
                    double grain = noise(x, y, 5) * 0.25;
                    double pit = 1 - smooth(0.15, 0.5, r);                     // dark scorched middle
                    double rim = Math.exp(-Math.pow((r - 0.55) / 0.08, 2));     // raised, lighter ring
                    double crack = 0;
                    for (double ca : crackAng) {
                        double diff = Math.abs(Math.atan2(Math.sin(ang - ca), Math.cos(ang - ca)));
                        double wobble = 0.05 * Math.sin(r * 23 + ca * 3);
                        double width = 0.05 * (1.1 - r);
                        if (r > 0.2 && r < 0.98 && Math.abs(diff - wobble) * r < width) crack = Math.max(crack, 1 - r * 0.8);
                    }
                    double fade = 1 - smooth(0.7, 1.0, r);
                    double a = Math.max(Math.max(pit * 0.9, rim * 0.55), crack * 0.85) * fade;
                    double lum = rim > pit && rim > crack ? 0.42 + grain : 0.08 + grain * 0.4;
                    if (a < 0.02) { c.set(x, y, 0); continue; }
                    int l = (int) Math.round(Math.min(1, lum) * 255);
                    int col = (int) Math.round(a * 255) << 24 | Math.min(255, (int) (l * 1.1)) << 16 | l << 8 | (int) (l * 0.85);
                    c.set(x, y, col);
                }
            c.save("entity/crater.png");
        }

        /** First-person aura: flames licking in from the screen edges, strongest along the bottom. Stretched over the screen. */
        static void auraEdge() throws IOException {
            int n = 128;
            Canvas c = new Canvas(n, n);
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double u = (x + 0.5) / n, v = (y + 0.5) / n;
                    double dx = Math.abs(u * 2 - 1), dy = Math.abs(v * 2 - 1);
                    double d = Math.pow(Math.pow(dx, 5) + Math.pow(dy, 5), 0.2);
                    double flick = 0.07 * Math.sin(u * 25 + v * 7) + 0.05 * Math.sin(u * 41 - v * 13) + 0.04 * Math.sin(v * 33 + u * 5);
                    double a = smooth(0.6, 1.02, d + flick) * (0.65 + 0.35 * v);
                    c.set(x, y, a < 0.01 ? 0 : white(a, 1));
                }
            c.save("gui/aura_edge.png");
        }

        /** Beam core: bright middle, soft edges, pulses of energy that tile seamlessly along v (it scrolls forward). */
        static void beamFlow() throws IOException {
            int w = 32, h = 64;
            Canvas c = new Canvas(w, h);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double u = (x + 0.5) / w, v = (double) y / h, d = Math.abs(u * 2 - 1);
                    double profile = Math.exp(-d * d * 3.2);
                    double pulse = 0.62 + 0.38 * Math.sin(Math.PI * 2 * (v * 2) + Math.sin(u * 9) * 0.9)
                            * (0.6 + 0.4 * Math.sin(Math.PI * 2 * (v * 3) + u * 5));
                    double a = profile * pulse;
                    c.set(x, y, a < 0.01 ? 0 : white(a, d < 0.35 ? 1 : 0.8));
                }
            c.save("entity/beam_flow.png");
        }

        /** A soft streak, bright along its middle: lightning bolts and speed lines. */
        static void streak() throws IOException {
            Canvas c = new Canvas(16, 16);
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    double d = Math.abs((x + 0.5) / 16 * 2 - 1);
                    double a = Math.exp(-d * d * 6);
                    c.set(x, y, white(a, d < 0.25 ? 1 : 0.85));
                }
            c.save("entity/fx_streak.png");
        }
    }
}
