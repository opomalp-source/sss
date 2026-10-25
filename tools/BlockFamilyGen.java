import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The civilizations' building blocks (docs/WORLD_STRUCTURES.md): one table, everything generated from it, in the
 * landmark set's style (tools/gen_architecture.py: 16x16, flat anime colours, a soft grain, one-pixel bevels lit from
 * the top left, darker joints). Writes for every block: its textures, blockstate, block and item models (cubes,
 * top-and-bottom, pillars, glass, lamps, painted windows, a rounded corner piece, doors; stairs, slabs and walls),
 * loot tables, the mineable/stairs/slabs/walls/doors tags, en_us names, and
 * src/main/java/com/dbzenith/world/arch/CivilizationBlockList.java, the table the registry reads.
 * Run from the repository root: java tools/BlockFamilyGen.java
 */
public class BlockFamilyGen {
    static final Path ROOT = Path.of("src", "main");
    static final Path ASSETS = ROOT.resolve("resources/assets/dbzenith"), DATA = ROOT.resolve("resources/data/dbzenith");
    static final Path JAVA = ROOT.resolve("java/com/dbzenith/world/arch/CivilizationBlockList.java");

    // ------------------------------------------------------------------ a 16x16 canvas in float RGB

    static final class T {
        final double[][][] c = new double[16][16][3];
        final double[][] a = new double[16][16];
        final Random rng;

        T(String base, long seed, double grain) {
            rng = new Random(seed * 7919 + 17);
            double[] b = rgb(base);
            double[][] g = new double[16][16];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) g[y][x] = rng.nextGaussian();
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    double n = grain == 0 ? 0 : (g[y][x] + g[(y + 15) % 16][x] * 0.5 + g[y][(x + 15) % 16] * 0.5) / 2 * grain;
                    for (int k = 0; k < 3; k++) c[y][x][k] = b[k] + n;
                    a[y][x] = 255;
                }
        }

        T rect(int x0, int y0, int x1, int y1, String col, double al) {
            double[] v = rgb(col);
            for (int y = Math.max(0, y0); y <= Math.min(15, y1); y++)
                for (int x = Math.max(0, x0); x <= Math.min(15, x1); x++)
                    for (int k = 0; k < 3; k++) c[y][x][k] = c[y][x][k] * (1 - al) + v[k] * al;
            return this;
        }

        T rect(int x0, int y0, int x1, int y1, String col) {
            return rect(x0, y0, x1, y1, col, 1);
        }

        T shade(int x0, int y0, int x1, int y1, double k) {
            for (int y = Math.max(0, y0); y <= Math.min(15, y1); y++)
                for (int x = Math.max(0, x0); x <= Math.min(15, x1); x++)
                    for (int q = 0; q < 3; q++) c[y][x][q] *= 1 + k;
            return this;
        }

        T px(int x, int y, String col, double al) {
            if (x < 0 || y < 0 || x > 15 || y > 15) return this;
            double[] v = rgb(col);
            for (int k = 0; k < 3; k++) c[y][x][k] = c[y][x][k] * (1 - al) + v[k] * al;
            return this;
        }

        T px(int x, int y, String col) {
            return px(x, y, col, 1);
        }

        /** A raised tile: top and left edges lit, bottom and right in shade. */
        T bevel(int x0, int y0, int x1, int y1, double hi, double lo) {
            shade(x0, y0, x1, y0, hi);
            shade(x0, y0 + 1, x0, y1, hi);
            shade(x0, y1, x1, y1, -lo);
            shade(x1, y0, x1, y1 - 1, -lo);
            return this;
        }

        T bevel(int x0, int y0, int x1, int y1) {
            return bevel(x0, y0, x1, y1, 0.12, 0.16);
        }

        T joints(String col, int[] xs, int[] ys, double al) {
            for (int x : xs) rect(x, 0, x, 15, col, al);
            for (int y : ys) rect(0, y, 15, y, col, al);
            return this;
        }

        /** A disc (or ring between r0 and r1) centred at cx, cy (pixel centres at .5). */
        T disc(double cx, double cy, double r0, double r1, String col, double al) {
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                    if (d >= r0 && d < r1) px(x, y, col, al);
                }
            return this;
        }

        T alpha(int x0, int y0, int x1, int y1, double v) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) a[y][x] = v;
            return this;
        }

        void save(String name) throws IOException {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int r = clamp(c[y][x][0]), g = clamp(c[y][x][1]), b = clamp(c[y][x][2]), al = clamp(a[y][x]);
                    img.setRGB(x, y, al << 24 | r << 16 | g << 8 | b);
                }
            File f = ASSETS.resolve("textures/" + name + ".png").toFile();
            f.getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
        }
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static double[] rgb(String h) {
        h = h.replace("#", "");
        return new double[]{Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16), Integer.parseInt(h.substring(4, 6), 16)};
    }

    // ------------------------------------------------------------------ painters

    /** A smooth panel: soft grain, one bevel round the whole face, a faint seam {@code seams} times across. */
    static T panel(String base, long seed, String seam, int rows) {
        T t = new T(base, seed, 2.5).bevel(0, 0, 15, 15, 0.06, 0.1);
        for (int r = 1; r < rows; r++) t.rect(0, r * 16 / rows, 15, r * 16 / rows, seam, 0.5);
        return t;
    }

    static T tiles(String base, String joint, int n, long seed, double grain) {
        T t = new T(base, seed, grain);
        int s = 16 / n;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) t.bevel(i * s, j * s, i * s + s - 1, j * s + s - 1, 0.1, 0.14);
        int[] lines = new int[n];
        for (int i = 0; i < n; i++) lines[i] = i * s;
        return t.joints(joint, lines, lines, 0.55);
    }

    static T plates(String base, String seam, long seed, boolean rivets) {
        T t = new T(base, seed, 4);
        t.bevel(0, 0, 15, 7, 0.1, 0.14).bevel(0, 8, 15, 15, 0.1, 0.14);
        t.rect(0, 7, 15, 7, seam, 0.7).rect(7, 0, 7, 7, seam, 0.6).rect(11, 8, 11, 15, seam, 0.6);
        if (rivets) for (int[] p : new int[][]{{2, 2}, {12, 2}, {2, 12}, {14, 12}, {8, 12}}) t.px(p[0], p[1], seam, 0.8).px(p[0] - 1, p[1] - 1, "#FFFFFF", 0.15);
        return t;
    }

    /** A round window of radius {@code r} in a frame, on a wall colour. */
    static T porthole(String wall, String frame, String glass, String glint, long seed, double r) {
        T t = new T(wall, seed, 2.5).bevel(0, 0, 15, 15, 0.05, 0.08);
        t.disc(8, 8, r, r + 1.4, frame, 1);
        t.disc(8, 8, 0, r, glass, 1);
        t.disc(8, 8, 0, r, "#000000", 0).shade(4, 4, 11, 11, 0);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
            if (d < r) t.shade(x, y, x, y, -0.18 * (y + 0.5 - 8 + r) / (2 * r));            // darker toward the bottom
        }
        t.disc(6.2, 6.2, 0, r * 0.32, glint, 0.75).disc(9.8, 10.2, 0, r * 0.14, glint, 0.35);
        return t;
    }

    static T glow(String base, String core, long seed, double rr) {
        T t = new T(base, seed, 2);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double d = Math.min(1, Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / rr);
                t.px(x, y, core, 1 - d);
            }
        return t;
    }

    static T stripes(String[] cols, int[] widths, long seed) {
        T t = new T(cols[0], seed, 3);
        int y = 0;
        for (int i = 0; i < cols.length; i++) {
            t.rect(0, y, 15, y + widths[i] - 1, cols[i], 0.92);
            y += widths[i];
        }
        return t;
    }

    // ------------------------------------------------------------------ the table

    enum Shape { CUBE, CUBE_TB, PILLAR, GLASS, LAMP, WINDOW, CORNER, DOOR }

    record B(String id, String name, Shape shape, String material, String variants, String color, Supplier<T[]> paint) {}

    static final List<B> BLOCKS = new ArrayList<>();

    static void b(String id, String name, Shape shape, String material, String variants, String color, Supplier<T[]> paint) {
        BLOCKS.add(new B(id, name, shape, material, variants, color, paint));
    }

    static T[] one(T t) {
        return new T[]{t};
    }

    static {
        // ---- Earth / Capsule Corporation: clean white panels, blue and cyan, porthole windows, dome roofs, clean metal
        String W = "#F1F0EB", WS = "#C9CCD2", BL = "#3E7ED0", CY = "#4CC4DC", MT = "#BFC6CF", NAVY = "#1F2A44";
        b("capsule_white", "Capsule White Panel", Shape.CUBE, "stone", "stairs slab wall", "SNOW", () -> one(panel(W, 1, WS, 1)));
        b("capsule_white_seam", "Capsule Seamed Panel", Shape.CUBE, "stone", "stairs slab", "SNOW", () -> one(panel(W, 2, WS, 2)));
        b("capsule_blue", "Capsule Blue Panel", Shape.CUBE, "stone", "stairs slab", "COLOR_BLUE", () -> one(panel(BL, 3, "#2C5FA6", 2)));
        b("capsule_cyan", "Capsule Cyan Panel", Shape.CUBE, "stone", "stairs slab", "COLOR_CYAN", () -> one(panel(CY, 4, "#2F9DB5", 2)));
        b("capsule_metal", "Capsule Metal Trim", Shape.CUBE_TB, "metal", "slab wall", "METAL", () -> {
            T side = new T(MT, 5, 3);
            for (int y = 0; y < 16; y++) side.shade(0, y, 15, y, 0.06 * Math.sin(y * 0.9));                 // brushed
            side.rect(0, 0, 15, 0, "#E8EDF2", 0.8).rect(0, 15, 15, 15, "#8E97A3", 0.8).rect(0, 7, 15, 8, "#9BA4AF", 0.6);
            T top = tiles(MT, "#8E97A3", 1, 6, 3);
            return new T[]{side, top};
        });
        b("capsule_window", "Capsule Porthole Window", Shape.WINDOW, "stone", "", "SNOW", () -> one(porthole(W, MT, "#2E6FA8", "#CFEFFF", 7, 4.6)));
        b("capsule_window_band", "Capsule Window Band", Shape.WINDOW, "stone", "", "COLOR_LIGHT_BLUE", () -> {
            T t = new T(W, 8, 2.5);
            t.rect(0, 4, 15, 11, "#2E6FA8").rect(0, 3, 15, 3, MT).rect(0, 12, 15, 12, MT);
            t.rect(0, 5, 15, 6, "#7FC4EC", 0.5).rect(3, 5, 6, 5, "#CFEFFF", 0.8).rect(7, 4, 7, 11, MT, 0.9);
            return one(t);
        });
        b("capsule_dome_white", "Capsule Dome Shell", Shape.CUBE, "stone", "stairs slab", "SNOW", () -> {
            T t = new T("#EEEDE6", 9, 2.5);
            for (int x = 0; x < 16; x += 8) t.rect(x, 0, x, 15, "#D2D4D8", 0.7);
            t.rect(0, 15, 15, 15, "#D2D4D8", 0.6).bevel(0, 0, 15, 15, 0.05, 0.08);
            return one(t);
        });
        b("capsule_dome_yellow", "Capsule Dome Yellow", Shape.CUBE, "stone", "stairs slab", "COLOR_YELLOW", () -> {
            T t = new T("#F2C94A", 10, 3);
            for (int x = 0; x < 16; x += 8) t.rect(x, 0, x, 15, "#C99B22", 0.6);
            t.rect(0, 15, 15, 15, "#C99B22", 0.5).bevel(0, 0, 15, 15, 0.06, 0.1);
            return one(t);
        });
        b("capsule_dome_orange", "Capsule Dome Orange", Shape.CUBE, "stone", "stairs slab", "COLOR_ORANGE", () -> {
            T t = new T("#EE8A3A", 11, 3);
            for (int x = 0; x < 16; x += 8) t.rect(x, 0, x, 15, "#B9601F", 0.6);
            t.rect(0, 15, 15, 15, "#B9601F", 0.5).bevel(0, 0, 15, 15, 0.06, 0.1);
            return one(t);
        });
        b("capsule_lab_floor", "Laboratory Floor", Shape.CUBE, "hard", "slab", "COLOR_LIGHT_GRAY", () -> {
            T t = tiles("#D5D9DE", "#8A929C", 2, 12, 3);
            for (int[] p : new int[][]{{2, 2}, {13, 2}, {2, 13}, {13, 13}, {5, 10}, {10, 5}}) t.px(p[0], p[1], "#8A929C", 0.7);
            return one(t);
        });
        b("capsule_light", "Capsule Light Panel", Shape.LAMP, "lamp", "", "SNOW", () -> {
            T t = new T(W, 13, 2).bevel(0, 0, 15, 15);
            t.rect(2, 2, 13, 13, "#DFF7FF").rect(2, 2, 13, 2, "#FFFFFF").rect(2, 13, 13, 13, "#A8E6F7").rect(4, 4, 11, 11, "#FFFFFF", 0.7);
            return one(t);
        });
        b("capsule_tech_panel", "Capsule Tech Panel", Shape.CUBE, "metal", "", "COLOR_LIGHT_GRAY", () -> {
            T t = new T("#C3CAD3", 14, 3).bevel(0, 0, 15, 15);
            t.rect(2, 2, 13, 8, NAVY).bevel(2, 2, 13, 8, -0.2, -0.1);
            t.rect(3, 4, 7, 4, "#5BE3F2").rect(3, 6, 10, 6, "#5BE3F2", 0.6).rect(9, 3, 12, 3, "#7CF29A", 0.8);
            t.px(3, 11, "#E2443C").px(6, 11, "#F2C230").px(9, 11, "#4CD964").rect(11, 10, 13, 12, "#8E97A3");
            return one(t);
        });
        b("capsule_logo", "Capsule Corporation Mark", Shape.CUBE, "stone", "", "SNOW", () -> {
            T t = new T(W, 15, 2).bevel(0, 0, 15, 15, 0.05, 0.08);
            t.disc(8, 8, 5.6, 7.2, NAVY, 1);                                                     // the ring
            t.disc(8, 8, 2.8, 4.6, NAVY, 1).rect(9, 6, 12, 10, W);                                 // a big C
            t.disc(8, 8, 1.2, 2.2, NAVY, 1).rect(8, 7, 10, 9, W);                                  // and a small one in it
            return one(t);
        });
        b("capsule_corner", "Capsule Rounded Corner", Shape.CORNER, "stone", "", "SNOW", () -> one(panel(W, 16, WS, 2)));
        b("capsule_door", "Capsule Sliding Door", Shape.DOOR, "metal", "", "SNOW", () -> {
            T bottom = new T(W, 17, 2).bevel(0, 0, 15, 15);
            bottom.rect(3, 0, 12, 13, "#2E6FA8").rect(3, 0, 12, 1, "#7FC4EC", 0.6).rect(7, 0, 8, 13, MT).rect(0, 14, 15, 15, MT);
            T top = new T(W, 18, 2).bevel(0, 0, 15, 15);
            top.rect(3, 3, 12, 15, "#2E6FA8").rect(3, 3, 12, 4, "#CFEFFF", 0.7).rect(7, 3, 8, 15, MT).rect(0, 0, 15, 1, MT);
            return new T[]{bottom, top, doorIcon(W, "#2E6FA8", MT)};
        });

        // ---- Saiyan: dark military metal, white armour plate, gold, royal red, spaceport concrete, padded training walls
        String DK = "#3D3F46", DKS = "#26282D", AW = "#E7E3D6", GD = "#D6A63A", RR = "#9E2E2A", BR = "#5B4636";
        b("saiyan_metal", "Saiyan Military Panel", Shape.CUBE, "hard", "stairs slab wall", "COLOR_GRAY", () -> one(plates(DK, DKS, 20, true)));
        b("saiyan_wall", "Saiyan Plated Wall", Shape.CUBE, "hard", "stairs slab", "COLOR_BROWN", () -> {
            T t = new T(BR, 21, 4);
            for (int x : new int[]{0, 5, 10}) t.bevel(x, 0, Math.min(15, x + 4), 15, 0.1, 0.16);
            t.joints("#3A2C22", new int[]{0, 5, 10, 15}, new int[]{}, 0.8).rect(0, 7, 15, 8, "#3A2C22", 0.5);
            return one(t);
        });
        b("saiyan_support", "Saiyan Girder", Shape.PILLAR, "hard", "", "COLOR_GRAY", () -> {
            T side = new T(DK, 22, 3);
            side.rect(0, 0, 2, 15, DKS).rect(13, 0, 15, 15, DKS).bevel(3, 0, 12, 15, 0.12, 0.2);
            for (int y = 2; y < 16; y += 4) side.px(1, y, GD, 0.7).px(14, y, GD, 0.7);
            T top = tiles(DKS, "#15161A", 1, 23, 3).disc(8, 8, 0, 3, DK, 1);
            return new T[]{side, top};
        });
        b("saiyan_armor_white", "Saiyan Armour Plate", Shape.CUBE, "hard", "stairs slab", "SNOW", () -> one(plates(AW, "#A79F87", 24, false)));
        b("saiyan_royal", "Saiyan Royal Stone", Shape.CUBE, "hard", "stairs slab wall", "COLOR_RED", () -> {
            T t = new T(RR, 25, 4).bevel(0, 0, 15, 15);
            t.rect(0, 2, 15, 2, GD, 0.85).rect(0, 13, 15, 13, GD, 0.85).rect(0, 3, 15, 3, "#6E1E1B", 0.5);
            return one(t);
        });
        b("saiyan_gold_trim", "Saiyan Gold Trim", Shape.CUBE_TB, "metal", "", "GOLD", () -> {
            T side = stripes(new String[]{DKS, GD, "#F2CE6A", GD, DKS}, new int[]{4, 2, 2, 4, 4}, 26);
            T top = new T(GD, 27, 4).bevel(0, 0, 15, 15);
            return new T[]{side, top};
        });
        b("saiyan_spaceport", "Saiyan Spaceport Floor", Shape.CUBE, "hard", "slab", "COLOR_GRAY", () -> {
            T t = tiles("#8D8B84", "#5F5D58", 2, 28, 4);
            for (int i = 0; i < 16; i++) if ((i / 2) % 2 == 0) t.px(i, 15 - i, "#E8B92F", 0.85).px(i, 14 - i, "#E8B92F", 0.6);
            return one(t);
        });
        b("saiyan_training", "Saiyan Training Pad", Shape.CUBE, "cloth", "slab", "COLOR_GREEN", () -> {
            T t = new T("#56603F", 29, 4);
            for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) t.bevel(i * 8 + 1, j * 8 + 1, i * 8 + 6, j * 8 + 6, 0.14, 0.2);
            t.joints("#30361F", new int[]{0, 7, 8, 15}, new int[]{0, 7, 8, 15}, 0.6);
            for (int[] p : new int[][]{{4, 4}, {12, 4}, {4, 12}, {12, 12}}) t.px(p[0], p[1], "#30361F", 0.6);
            return one(t);
        });
        b("saiyan_crest", "Saiyan Royal Crest", Shape.CUBE, "hard", "", "COLOR_RED", () -> {
            T t = new T(RR, 30, 3).bevel(0, 0, 15, 15);
            t.disc(8, 7.5, 5.2, 6.6, GD, 1);                                                      // a gold ring
            t.rect(7, 3, 8, 12, GD).rect(5, 5, 10, 6, GD).rect(4, 9, 11, 9, GD, 0.9);            // a crowned blade, original design
            t.px(5, 4, GD).px(10, 4, GD).px(7, 2, "#F2CE6A").px(8, 2, "#F2CE6A");
            return one(t);
        });
        b("saiyan_damaged", "Battle-Damaged Saiyan Panel", Shape.CUBE, "hard", "stairs slab", "COLOR_GRAY", () -> {
            T t = plates(DK, DKS, 31, true);
            int[][] crack = {{3, 1}, {4, 2}, {4, 3}, {5, 4}, {6, 5}, {6, 6}, {7, 7}, {9, 8}, {10, 9}, {10, 10}, {12, 11}, {6, 7}, {5, 8}, {4, 9}};
            for (int[] p : crack) t.px(p[0], p[1], "#121316", 0.9);
            for (int y = 3; y < 11; y++) for (int x = 3; x < 12; x++) if (t.rng.nextDouble() < 0.25) t.shade(x, y, x, y, -0.25);   // scorch
            return one(t);
        });
        b("saiyan_light", "Saiyan Light Strip", Shape.LAMP, "lamp", "", "COLOR_ORANGE", () -> {
            T t = new T(DK, 32, 2).bevel(0, 0, 15, 15);
            t.rect(1, 5, 14, 10, "#FF7A2E").rect(1, 6, 14, 9, "#FFC27A").rect(1, 7, 14, 8, "#FFF0D2");
            return one(t);
        });
        b("saiyan_window", "Saiyan Porthole Window", Shape.WINDOW, "hard", "", "COLOR_GRAY", () -> one(porthole(AW, DK, "#6E1A16", "#FFB49A", 33, 4.4)));
        b("saiyan_corner", "Saiyan Rounded Corner", Shape.CORNER, "hard", "", "SNOW", () -> one(plates(AW, "#A79F87", 34, false)));
        b("saiyan_door", "Saiyan Military Door", Shape.DOOR, "metal", "", "COLOR_GRAY", () -> {
            T bottom = plates(DK, DKS, 35, true);
            bottom.rect(2, 0, 13, 14, "#4A4D55").rect(7, 0, 8, 14, GD).bevel(2, 0, 13, 14);
            T top = plates(DK, DKS, 36, true);
            top.rect(2, 2, 13, 15, "#4A4D55").rect(7, 2, 8, 15, GD).rect(4, 4, 11, 6, "#FF7A2E", 0.8).bevel(2, 2, 13, 15);
            return new T[]{bottom, top, doorIcon(DK, "#4A4D55", GD)};
        });

        // ---- Namekian: smooth white organic shells, soft blue, green-tinted round windows, carved temple stone
        String NW = "#F4F6F2", NWS = "#D3DCD6", NB = "#7DB6D8", NG = "#3F8F6A";
        b("namek_shell", "Namekian Shell", Shape.CUBE, "stone", "stairs slab wall", "SNOW", () -> one(new T(NW, 40, 2).bevel(0, 0, 15, 15, 0.04, 0.06)));
        b("namek_dome", "Namekian Dome Shell", Shape.CUBE, "stone", "stairs slab", "SNOW", () -> {
            T t = new T(NW, 41, 2);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double v = Math.sin((x + y * 0.35) * 0.55);
                if (Math.abs(v) < 0.12) t.px(x, y, NWS, 0.7);
            }
            return one(t.bevel(0, 0, 15, 15, 0.04, 0.06));
        });
        b("namek_band", "Namekian Banded Shell", Shape.CUBE, "stone", "", "COLOR_LIGHT_BLUE", () -> {
            T t = new T(NW, 42, 2);
            t.rect(0, 6, 15, 9, NB).rect(0, 6, 15, 6, "#A9D4EC").rect(0, 9, 15, 9, "#5B93B8");
            return one(t);
        });
        b("namek_accent", "Namekian Blue Accent", Shape.CUBE, "stone", "stairs slab", "COLOR_LIGHT_BLUE", () -> one(new T(NB, 43, 3).bevel(0, 0, 15, 15, 0.08, 0.12)));
        b("namek_window", "Namekian Round Window", Shape.WINDOW, "stone", "", "SNOW", () -> one(porthole(NW, NWS, "#2F6E5A", "#C8F2DC", 44, 5.0)));
        b("namek_temple", "Namekian Temple Stone", Shape.CUBE, "hard", "stairs slab wall", "TERRACOTTA_WHITE", () -> {
            T t = new T("#DADFD3", 45, 4);
            t.bevel(0, 0, 15, 15);
            for (int x = 0; x < 16; x++) {
                int y = (int) Math.round(8 + 3 * Math.sin(x * 0.7));
                t.px(x, y, "#9FAE9A", 0.8).px(x, y + 1, "#FFFFFF", 0.25);
            }
            return one(t);
        });
        b("namek_pattern", "Namekian Carved Pattern", Shape.CUBE, "stone", "", "SNOW", () -> {
            T t = new T(NW, 46, 2).bevel(0, 0, 15, 15, 0.04, 0.06);
            for (double a = 0; a < Math.PI * 3.2; a += 0.08) {                                 // an organic spiral
                double r = 1 + a * 0.55;
                t.px((int) Math.round(8 + Math.cos(a) * r), (int) Math.round(8 + Math.sin(a) * r), NG, 0.75);
            }
            return one(t);
        });
        b("namek_floor", "Namekian Floor", Shape.CUBE, "stone", "slab", "TERRACOTTA_WHITE", () -> {
            T t = new T("#E8E9DD", 47, 3);
            for (int[] c : new int[][]{{4, 4}, {12, 4}, {4, 12}, {12, 12}}) t.disc(c[0], c[1], 3.2, 4.0, "#C3C8B4", 0.8).disc(c[0], c[1], 0, 3.2, "#F2F3EA", 0.5);
            return one(t);
        });
        b("namek_lamp", "Namekian Glow Lamp", Shape.LAMP, "lamp", "", "COLOR_LIGHT_GREEN", () -> {
            T t = new T(NW, 48, 2).bevel(0, 0, 15, 15, 0.04, 0.06);
            t.disc(8, 8, 0, 5.5, "#9BF0B6", 1).disc(8, 8, 0, 3.5, "#D8FFE4", 1).disc(6.5, 6.5, 0, 1.2, "#FFFFFF", 0.9);
            return one(t);
        });
        b("namek_pillar", "Namekian Pillar", Shape.PILLAR, "stone", "", "SNOW", () -> {
            T side = new T(NW, 49, 2);
            for (int x : new int[]{3, 7, 11}) side.rect(x, 0, x, 15, NWS, 0.7);
            side.rect(0, 0, 15, 1, NB, 0.8).rect(0, 14, 15, 15, NB, 0.8);
            T top = new T(NW, 50, 2).disc(8, 8, 5, 6, NWS, 0.8);
            return new T[]{side, top};
        });
        b("namek_corner", "Namekian Rounded Corner", Shape.CORNER, "stone", "", "SNOW", () -> one(new T(NW, 51, 2).bevel(0, 0, 15, 15, 0.04, 0.06)));
        b("namek_door", "Namekian Door", Shape.DOOR, "stone", "", "SNOW", () -> {
            T bottom = new T(NW, 52, 2);
            bottom.rect(2, 0, 13, 15, "#C2D8C8").rect(2, 0, 2, 15, NWS).rect(13, 0, 13, 15, NWS).rect(7, 0, 8, 15, "#9DBCA8", 0.6);
            T top = new T(NW, 53, 2);
            for (int y = 0; y < 16; y++) for (int x = 2; x <= 13; x++) {
                double d = Math.hypot(x + 0.5 - 8, y + 0.5 - 16);
                if (d < 9.5) top.px(x, y, "#C2D8C8");
            }
            top.disc(8, 9, 0, 2.6, "#2F6E5A", 1).disc(7.4, 8.4, 0, 0.9, "#C8F2DC", 0.8);
            return new T[]{bottom, top, doorIcon(NW, "#C2D8C8", NWS)};
        });

        // ---- Frieza Force: white and purple panels, reinforced lilac-grey, dark station floors, pink energy light
        String FW = "#EDEDF2", FP = "#7A4FB0", FPD = "#563483", FG = "#9A96A8", FD = "#34323C";
        b("frieza_white", "Frieza Force White Panel", Shape.CUBE, "metal", "stairs slab wall", "SNOW", () -> {
            T t = panel(FW, 60, "#BDBBC8", 2);
            t.rect(0, 15, 15, 15, FP, 0.35);
            return one(t);
        });
        b("frieza_purple", "Frieza Force Purple Panel", Shape.CUBE, "metal", "stairs slab", "COLOR_PURPLE", () -> one(panel(FP, 61, FPD, 2)));
        b("frieza_reinforced", "Frieza Force Reinforced Wall", Shape.CUBE, "hard", "stairs slab wall", "COLOR_LIGHT_GRAY", () -> one(plates(FG, "#6C687A", 62, true)));
        b("frieza_floor", "Space Station Floor", Shape.CUBE, "hard", "slab", "COLOR_GRAY", () -> {
            T t = tiles(FD, "#1E1D24", 4, 63, 3);
            t.rect(0, 7, 15, 8, FP, 0.5).rect(7, 0, 8, 15, FP, 0.5);
            return one(t);
        });
        b("frieza_control", "Alien Control Panel", Shape.CUBE, "metal", "", "COLOR_PURPLE", () -> {
            T t = new T(FG, 64, 3).bevel(0, 0, 15, 15);
            t.rect(1, 1, 14, 9, "#151A22").bevel(1, 1, 14, 9, -0.2, -0.1);
            for (int i = 0; i < 6; i++) t.rect(2, 2 + i, 2 + 2 + (i * 5) % 9, 2 + i, "#58F58C", 0.75);   // readouts
            t.disc(11, 5, 1.5, 2.6, "#58F58C", 0.8);
            t.px(3, 12, "#FF4FA8").px(6, 12, "#58F58C").px(9, 12, "#F2D44C").rect(11, 11, 13, 13, FPD);
            return one(t);
        });
        b("frieza_light", "Energy Light", Shape.LAMP, "lamp", "", "COLOR_MAGENTA", () -> {
            T t = new T(FG, 65, 2).bevel(0, 0, 15, 15);
            t.disc(8, 8, 0, 6, "#E04FD0", 1).disc(8, 8, 0, 4, "#FF9CF0", 1).disc(8, 8, 0, 2, "#FFFFFF", 1);
            return one(t);
        });
        b("frieza_lab", "Frieza Force Lab Tiles", Shape.CUBE, "stone", "slab", "SNOW", () -> {
            T t = tiles(FW, "#BDBBC8", 2, 66, 2);
            t.rect(0, 0, 15, 0, "#5BD3E8", 0.4).rect(0, 8, 15, 8, "#5BD3E8", 0.4);
            return one(t);
        });
        b("frieza_insignia", "Frieza Force Mark", Shape.CUBE, "metal", "", "COLOR_PURPLE", () -> {
            T t = new T(FP, 67, 3).bevel(0, 0, 15, 15);
            t.disc(8, 8, 4.8, 6.2, FW, 1);                                                        // original mark: a ring
            t.rect(7, 2, 8, 13, FW).rect(4, 7, 11, 8, FW, 0.9).disc(8, 8, 0, 2, FPD, 1);          // crossed by a white star
            return one(t);
        });
        b("frieza_hangar", "Hangar Plating", Shape.CUBE, "hard", "stairs slab", "COLOR_GRAY", () -> {
            T t = plates("#55525E", "#2F2D36", 68, true);
            for (int i = 0; i < 16; i++) if ((i / 3) % 2 == 0) t.px(i, 14, "#E8B92F", 0.8).px(i, 15, "#E8B92F", 0.8);
            return one(t);
        });
        b("frieza_window", "Frieza Force Porthole", Shape.WINDOW, "metal", "", "SNOW", () -> one(porthole(FW, FG, "#3B1F62", "#E6C7FF", 69, 4.8)));
        b("frieza_corner", "Frieza Force Rounded Corner", Shape.CORNER, "metal", "", "SNOW", () -> one(panel(FW, 70, "#BDBBC8", 2)));
        b("frieza_door", "Security Door", Shape.DOOR, "metal", "", "COLOR_PURPLE", () -> {
            T bottom = panel(FW, 71, "#BDBBC8", 1);
            bottom.rect(2, 0, 13, 15, FP).rect(7, 0, 8, 15, FPD);
            for (int i = 2; i <= 13; i++) if ((i / 2) % 2 == 0) bottom.px(i, 13, "#E8B92F").px(i, 14, "#E8B92F");
            T top = panel(FW, 72, "#BDBBC8", 1);
            top.rect(2, 2, 13, 15, FP).rect(7, 2, 8, 15, FPD).rect(4, 4, 11, 6, "#3B1F62").rect(4, 4, 6, 4, "#E6C7FF", 0.7);
            return new T[]{bottom, top, doorIcon(FW, FP, FPD)};
        });

        // ---- Divine and other world: sacred white stone, gold, temple pillars, pink rounded roofs, celestial blue
        String DS = "#F3EFE4", DG = "#E2B53E", DP = "#E99BB8";
        b("divine_stone", "Sacred Stone", Shape.CUBE, "stone", "stairs slab wall", "QUARTZ", () -> {
            T t = new T(DS, 80, 3).bevel(0, 0, 15, 15, 0.05, 0.08);
            int[][] vein = {{1, 12}, {2, 11}, {3, 11}, {4, 10}, {5, 10}, {6, 9}, {8, 9}, {9, 8}, {10, 8}, {12, 7}, {13, 6}};
            for (int[] p : vein) t.px(p[0], p[1], DG, 0.45);
            return one(t);
        });
        b("divine_tiles", "Divine Floor Tiles", Shape.CUBE, "stone", "slab", "QUARTZ", () -> {
            T t = tiles(DS, "#CBBF9F", 2, 81, 2);
            for (int[] c : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) t.px(c[0] + 4, c[1] + 4, DG, 0.9).px(c[0] + 3, c[1] + 4, DG, 0.5).px(c[0] + 4, c[1] + 3, DG, 0.5);
            return one(t);
        });
        b("divine_pillar", "Temple Pillar", Shape.PILLAR, "stone", "", "QUARTZ", () -> {
            T side = new T(DS, 82, 2);
            for (int x : new int[]{2, 5, 8, 11, 14}) side.rect(x, 0, x, 15, "#D8D1BF", 0.8);
            side.rect(0, 0, 15, 1, DG).rect(0, 14, 15, 15, DG);
            T top = new T(DS, 83, 2).disc(8, 8, 6, 7.5, DG, 0.9);
            return new T[]{side, top};
        });
        b("divine_roof_pink", "Pink Rounded Roof", Shape.CUBE, "clay", "stairs slab", "COLOR_PINK", () -> {
            T t = new T(DP, 84, 3);
            for (int y = 0; y < 16; y += 4) {
                t.rect(0, y + 3, 15, y + 3, "#C26E8E", 0.6);
                for (int x = 0; x < 16; x += 4) t.disc(x + 2, y + 1.5, 0, 1.6, "#F5BDD2", 0.5);
            }
            return one(t);
        });
        b("divine_gold", "Divine Gold", Shape.CUBE, "metal", "stairs slab", "GOLD", () -> {
            T t = new T(DG, 85, 4).bevel(0, 0, 15, 15, 0.16, 0.18);
            t.rect(2, 2, 13, 2, "#F8DE84", 0.7).rect(2, 3, 2, 13, "#F8DE84", 0.5);
            return one(t);
        });
        b("divine_celestial", "Celestial Panel", Shape.CUBE, "stone", "", "COLOR_BLUE", () -> {
            T t = new T("#243A78", 86, 4).bevel(0, 0, 15, 15);
            for (int i = 0; i < 9; i++) t.px(t.rng.nextInt(14) + 1, t.rng.nextInt(14) + 1, "#FFFFFF", 0.5 + t.rng.nextDouble() * 0.5);
            t.disc(8, 8, 5.4, 6.2, DG, 0.9);
            return one(t);
        });
        b("divine_light", "Divine Light", Shape.LAMP, "lamp", "", "GOLD", () -> {
            T t = glow(DS, "#FFF6D0", 87, 7);
            t.disc(8, 8, 6.6, 7.6, DG, 0.9);
            return one(t);
        });
        b("divine_ornament", "Divine Ornament", Shape.CUBE, "metal", "", "GOLD", () -> {
            T t = new T(DS, 88, 2).bevel(0, 0, 15, 15, 0.05, 0.08);
            t.disc(8, 8, 3.5, 5, DG, 1).disc(8, 8, 0, 2, "#5FD0C9", 1);
            for (int[] p : new int[][]{{8, 1}, {8, 14}, {1, 8}, {14, 8}}) t.px(p[0], p[1], DG).px(p[0], p[1] + (p[1] < 8 ? 1 : -1), DG);
            return one(t);
        });
        b("divine_emerald_sand", "Emerald Time Sand", Shape.CUBE, "sand", "", "EMERALD", () -> {
            T t = new T("#3CC77A", 89, 10);
            for (int i = 0; i < 18; i++) t.px(t.rng.nextInt(16), t.rng.nextInt(16), "#B8FFD5", 0.7);
            return one(t);
        });
    }

    /** A door's inventory icon: the door's face, narrowed and with a transparent margin. */
    static T doorIcon(String frame, String panel, String trim) {
        T t = new T(frame, 99, 2);
        t.alpha(0, 0, 15, 15, 0);
        t.alpha(4, 0, 11, 15, 255).rect(5, 1, 10, 14, panel).rect(4, 0, 11, 0, trim).rect(4, 15, 11, 15, trim).rect(10, 7, 10, 8, trim);
        return t;
    }

    // ------------------------------------------------------------------ writing

    static final Map<String, String> LANG = new LinkedHashMap<>();
    static final List<String> PICK = new ArrayList<>(), SHOVEL = new ArrayList<>(), STAIRS = new ArrayList<>(), SLABS = new ArrayList<>(),
            WALLS = new ArrayList<>(), DOORS = new ArrayList<>();

    static void write(Path p, String json) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, json + "\n", StandardCharsets.UTF_8);
    }

    static String q(String s) {
        return "\"" + s + "\"";
    }

    static String tex(String id) {
        return "dbzenith:block/" + id;
    }

    static void model(String name, String parent, String... texPairs) throws IOException {
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < texPairs.length; i += 2) t.append(i == 0 ? "" : ", ").append(q(texPairs[i])).append(": ").append(q(texPairs[i + 1]));
        write(ASSETS.resolve("models/block/" + name + ".json"), "{\n  \"parent\": " + q(parent) + ",\n  \"textures\": {" + t + "}\n}");
    }

    static void itemModel(String name, String parent) throws IOException {
        write(ASSETS.resolve("models/item/" + name + ".json"), "{\n  \"parent\": " + q(parent) + "\n}");
    }

    static void simpleState(String id, String model) throws IOException {
        write(ASSETS.resolve("blockstates/" + id + ".json"), "{\n  \"variants\": {\"\": {\"model\": " + q(model) + "}}\n}");
    }

    static void loot(String id, boolean slab, boolean door) throws IOException {
        String fn = "";
        if (slab) fn = ", \"functions\": [{\"function\": \"minecraft:set_count\", \"count\": 2, \"add\": false, \"conditions\": [{\"condition\": "
                + "\"minecraft:block_state_property\", \"block\": \"dbzenith:" + id + "\", \"properties\": {\"type\": \"double\"}}]}, "
                + "{\"function\": \"minecraft:explosion_decay\"}]";
        String cond = door ? ", \"conditions\": [{\"condition\": \"minecraft:block_state_property\", \"block\": \"dbzenith:" + id
                + "\", \"properties\": {\"half\": \"lower\"}}]" : "";
        write(DATA.resolve("loot_tables/blocks/" + id + ".json"), "{\"type\": \"minecraft:block\", \"pools\": [{\"rolls\": 1, \"entries\": "
                + "[{\"type\": \"minecraft:item\", \"name\": \"dbzenith:" + id + "\"" + fn + "}]" + cond
                + ", \"conditions_\": []}]}".replace(", \"conditions_\": []", ""));
    }

    static void emit(B b) throws IOException {
        T[] art = b.paint().get();
        String id = b.id();
        if (b.shape() != Shape.DOOR) art[0].save("block/" + id);
        String side = tex(id), top = side;
        if (b.shape() == Shape.CUBE_TB || b.shape() == Shape.PILLAR) {
            art[1].save("block/" + id + "_top");
            top = tex(id + "_top");
        }
        LANG.put("block.dbzenith." + id, b.name());
        (b.material().equals("sand") ? SHOVEL : PICK).add("dbzenith:" + id);
        switch (b.shape()) {
            case PILLAR -> {
                model(id, "minecraft:block/cube_column", "end", top, "side", side);
                model(id + "_horizontal", "minecraft:block/cube_column_horizontal", "end", top, "side", side);
                write(ASSETS.resolve("blockstates/" + id + ".json"), "{\n  \"variants\": {\n    \"axis=y\": {\"model\": \"dbzenith:block/" + id
                        + "\"},\n    \"axis=z\": {\"model\": \"dbzenith:block/" + id + "_horizontal\", \"x\": 90},\n    \"axis=x\": {\"model\": \"dbzenith:block/"
                        + id + "_horizontal\", \"x\": 90, \"y\": 90}\n  }\n}");
                itemModel(id, "dbzenith:block/" + id);
            }
            case CUBE_TB -> {
                model(id, "minecraft:block/cube_bottom_top", "top", top, "bottom", top, "side", side);
                simpleState(id, "dbzenith:block/" + id);
                itemModel(id, "dbzenith:block/" + id);
            }
            case GLASS -> {
                write(ASSETS.resolve("models/block/" + id + ".json"), "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"render_type\": \"minecraft:translucent\",\n"
                        + "  \"textures\": {\"all\": " + q(side) + "}\n}");
                simpleState(id, "dbzenith:block/" + id);
                itemModel(id, "dbzenith:block/" + id);
            }
            case CORNER -> {
                model(id, "dbzenith:block/template_round_corner", "side", side, "particle", side);
                StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
                String[][] f = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};
                for (int i = 0; i < 4; i++)
                    s.append("    \"facing=").append(f[i][0]).append("\": {\"model\": \"dbzenith:block/").append(id).append("\"")
                            .append(f[i][1].equals("0") ? "" : ", \"y\": " + f[i][1]).append("}").append(i < 3 ? ",\n" : "\n");
                write(ASSETS.resolve("blockstates/" + id + ".json"), s + "  }\n}");
                itemModel(id, "dbzenith:block/" + id);
            }
            case DOOR -> {
                art[1].save("block/" + id + "_top");
                art[0].save("block/" + id + "_bottom");
                art[2].save("item/" + id);
                String bt = tex(id + "_bottom"), tt = tex(id + "_top");
                for (String half : new String[]{"bottom", "top"})
                    for (String hinge : new String[]{"left", "right"})
                        for (String open : new String[]{"", "_open"}) {
                            String m = id + "_" + half + "_" + hinge + open;
                            write(ASSETS.resolve("models/block/" + m + ".json"), "{\n  \"parent\": \"minecraft:block/door_" + half + "_" + hinge + open
                                    + "\",\n  \"render_type\": \"minecraft:cutout\",\n  \"textures\": {\"bottom\": " + q(bt) + ", \"top\": " + q(tt) + "}\n}");
                        }
                StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
                String[][] f = {{"east", "0"}, {"south", "90"}, {"west", "180"}, {"north", "270"}};
                List<String> lines = new ArrayList<>();
                for (String[] fa : f)
                    for (String half : new String[]{"lower", "upper"})
                        for (String hinge : new String[]{"left", "right"})
                            for (String open : new String[]{"false", "true"}) {
                                int y = Integer.parseInt(fa[1]);
                                boolean o = open.equals("true");
                                if (o) y = (y + (hinge.equals("left") ? 90 : 270)) % 360;
                                String m = id + "_" + (half.equals("lower") ? "bottom" : "top") + "_" + hinge + (o ? "_open" : "");
                                lines.add("    \"facing=" + fa[0] + ",half=" + half + ",hinge=" + hinge + ",open=" + open + "\": {\"model\": \"dbzenith:block/" + m + "\""
                                        + (y == 0 ? "" : ", \"y\": " + y) + "}");
                            }
                s.append(String.join(",\n", lines)).append("\n  }\n}");
                write(ASSETS.resolve("blockstates/" + id + ".json"), s.toString());
                write(ASSETS.resolve("models/item/" + id + ".json"), "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"dbzenith:item/" + id + "\"}\n}");
                DOORS.add("dbzenith:" + id);
                loot(id, false, true);
                return;
            }
            default -> {
                model(id, "minecraft:block/cube_all", "all", side);
                simpleState(id, "dbzenith:block/" + id);
                itemModel(id, "dbzenith:block/" + id);
            }
        }
        loot(id, false, false);
        String v = b.variants();
        if (v.contains("stairs")) {
            String s = id + "_stairs";
            model(s, "minecraft:block/stairs", "bottom", top, "top", top, "side", side);
            model(s + "_inner", "minecraft:block/inner_stairs", "bottom", top, "top", top, "side", side);
            model(s + "_outer", "minecraft:block/outer_stairs", "bottom", top, "top", top, "side", side);
            itemModel(s, "dbzenith:block/" + s);
            write(ASSETS.resolve("blockstates/" + s + ".json"), stairsState(s));
            loot(s, false, false);
            LANG.put("block.dbzenith." + s, b.name() + " Stairs");
            PICK.add("dbzenith:" + s);
            STAIRS.add("dbzenith:" + s);
        }
        if (v.contains("slab")) {
            String s = id + "_slab";
            model(s, "minecraft:block/slab", "bottom", top, "top", top, "side", side);
            model(s + "_top", "minecraft:block/slab_top", "bottom", top, "top", top, "side", side);
            itemModel(s, "dbzenith:block/" + s);
            write(ASSETS.resolve("blockstates/" + s + ".json"), "{\n  \"variants\": {\n    \"type=bottom\": {\"model\": \"dbzenith:block/" + s
                    + "\"},\n    \"type=top\": {\"model\": \"dbzenith:block/" + s + "_top\"},\n    \"type=double\": {\"model\": \"dbzenith:block/" + id + "\"}\n  }\n}");
            loot(s, true, false);
            LANG.put("block.dbzenith." + s, b.name() + " Slab");
            PICK.add("dbzenith:" + s);
            SLABS.add("dbzenith:" + s);
        }
        if (v.contains("wall")) {
            String s = id + "_wall";
            model(s + "_post", "minecraft:block/template_wall_post", "wall", side);
            model(s + "_side", "minecraft:block/template_wall_side", "wall", side);
            model(s + "_side_tall", "minecraft:block/template_wall_side_tall", "wall", side);
            model(s + "_inventory", "minecraft:block/wall_inventory", "wall", side);
            itemModel(s, "dbzenith:block/" + s + "_inventory");
            write(ASSETS.resolve("blockstates/" + s + ".json"), wallState(s));
            loot(s, false, false);
            LANG.put("block.dbzenith." + s, b.name() + " Wall");
            PICK.add("dbzenith:" + s);
            WALLS.add("dbzenith:" + s);
        }
    }

    static String stairsState(String s) {
        List<String> out = new ArrayList<>();
        String[][] rot = {{"east", "0"}, {"south", "90"}, {"west", "180"}, {"north", "270"}};
        for (String[] r : rot)
            for (String half : new String[]{"bottom", "top"})
                for (String shape : new String[]{"straight", "inner_left", "inner_right", "outer_left", "outer_right"}) {
                    String model = "dbzenith:block/" + s + (shape.contains("inner") ? "_inner" : shape.contains("outer") ? "_outer" : "");
                    int y = Integer.parseInt(r[1]);
                    int yy = y;
                    if (shape.equals("inner_left") || shape.equals("outer_left")) yy = (y + 270) % 360;
                    int x = 0;
                    if (half.equals("top")) {
                        x = 180;
                        if (shape.equals("inner_left") || shape.equals("outer_left")) yy = y;
                        else if (shape.equals("inner_right") || shape.equals("outer_right")) yy = (y + 90) % 360;
                    }
                    StringBuilder e = new StringBuilder("{\"model\": " + q(model));
                    if (x != 0) e.append(", \"x\": ").append(x);
                    if (yy != 0) e.append(", \"y\": ").append(yy);
                    if (x != 0 || yy != 0) e.append(", \"uvlock\": true");
                    out.add("    \"facing=" + r[0] + ",half=" + half + ",shape=" + shape + "\": " + e + "}");
                }
        return "{\n  \"variants\": {\n" + String.join(",\n", out) + "\n  }\n}";
    }

    static String wallState(String s) {
        List<String> parts = new ArrayList<>();
        parts.add("    {\"when\": {\"up\": \"true\"}, \"apply\": {\"model\": \"dbzenith:block/" + s + "_post\"}}");
        String[][] d = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};
        for (String[] e : d)
            for (String[] h : new String[][]{{"low", "_side"}, {"tall", "_side_tall"}})
                parts.add("    {\"when\": {\"" + e[0] + "\": \"" + h[0] + "\"}, \"apply\": {\"model\": \"dbzenith:block/" + s + h[1] + "\", \"uvlock\": true"
                        + (e[1].equals("0") ? "" : ", \"y\": " + e[1]) + "}}");
        return "{\n  \"multipart\": [\n" + String.join(",\n", parts) + "\n  ]\n}";
    }

    /** The rounded corner: a quarter-round column of radius 16 about the block's south-east edge, in eight steps. */
    static void cornerTemplate() throws IOException {
        int[] z0 = {10, 7, 4, 3, 2, 1, 0, 0};
        List<String> els = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int x0 = i * 2, x1 = x0 + 2, z = z0[i];
            String uvSide = "[" + x0 + ", 0, " + x1 + ", 16]", uvTop = "[" + x0 + ", " + z + ", " + x1 + ", 16]";
            els.add("    {\"from\": [" + x0 + ", 0, " + z + "], \"to\": [" + x1 + ", 16, 16], \"faces\": {"
                    + "\"north\": {\"uv\": " + uvSide + ", \"texture\": \"#side\"}, "
                    + "\"south\": {\"uv\": [" + (16 - x1) + ", 0, " + (16 - x0) + ", 16], \"texture\": \"#side\", \"cullface\": \"south\"}, "
                    + "\"west\": {\"uv\": [" + z + ", 0, 16, 16], \"texture\": \"#side\"" + (i == 0 ? ", \"cullface\": \"west\"" : "") + "}, "
                    + "\"east\": {\"uv\": [0, 0, " + (16 - z) + ", 16], \"texture\": \"#side\"" + (i == 7 ? ", \"cullface\": \"east\"" : "") + "}, "
                    + "\"up\": {\"uv\": " + uvTop + ", \"texture\": \"#side\", \"cullface\": \"up\"}, "
                    + "\"down\": {\"uv\": " + uvTop + ", \"texture\": \"#side\", \"cullface\": \"down\"}}}");
        }
        write(ASSETS.resolve("models/block/template_round_corner.json"), "{\n  \"parent\": \"minecraft:block/block\",\n  \"ambientocclusion\": false,\n"
                + "  \"textures\": {\"particle\": \"#side\"},\n  \"elements\": [\n" + String.join(",\n", els) + "\n  ]\n}");
    }

    static void mergeTag(Path p, List<String> values) throws IOException {
        LinkedHashSet<String> have = new LinkedHashSet<>();
        if (Files.exists(p)) {
            Matcher m = Pattern.compile("\"([a-z0-9_:/.-]+)\"").matcher(Files.readString(p));
            while (m.find()) if (m.group(1).contains(":")) have.add(m.group(1));
        }
        have.addAll(values);
        List<String> qv = new ArrayList<>();
        for (String v : have) qv.add("    " + q(v));
        write(p, "{\n  \"replace\": false,\n  \"values\": [\n" + String.join(",\n", qv) + "\n  ]\n}");
    }

    /** Adds names to en_us.json (keeps what is there; replaces only our own keys). */
    static void mergeLang() throws IOException {
        Path p = ASSETS.resolve("lang/en_us.json");
        String s = Files.readString(p, StandardCharsets.UTF_8).stripTrailing();
        StringBuilder add = new StringBuilder();
        for (var e : LANG.entrySet()) {
            String key = q(e.getKey());
            if (s.contains(key + ":")) s = s.replaceAll(Pattern.quote(key) + ": \"[^\"]*\"", Matcher.quoteReplacement(key + ": " + q(e.getValue())));
            else add.append(",\n  ").append(key).append(": ").append(q(e.getValue()));
        }
        String nl = s.contains("\r\n") ? "\r\n" : "\n";
        s = s.substring(0, s.lastIndexOf('}')).stripTrailing() + add.toString().replace("\n", nl) + nl + "}" + nl;
        Files.writeString(p, s, StandardCharsets.UTF_8);
    }

    static void javaList() throws IOException {
        List<String> rows = new ArrayList<>();
        for (B b : BLOCKS) {
            String v = b.variants();
            rows.add("            new Spec(\"" + b.id() + "\", Shape." + b.shape() + ", Material." + b.material().toUpperCase() + ", MapColor." + b.color() + ", "
                    + v.contains("stairs") + ", " + v.contains("slab") + ", " + v.contains("wall") + ")");
        }
        write(JAVA, """
                package com.dbzenith.world.arch;

                import net.minecraft.world.level.material.MapColor;

                import java.util.List;

                /**
                 * The civilizations' building blocks (docs/WORLD_STRUCTURES.md). GENERATED by tools/BlockFamilyGen.java from its table
                 * (with the textures, models, blockstates, loot tables, tags and names): edit the table there and re-run
                 * (java tools/BlockFamilyGen.java), do not edit this file.
                 */
                public final class CivilizationBlockList {
                    public enum Shape { CUBE, CUBE_TB, PILLAR, GLASS, LAMP, WINDOW, CORNER, DOOR }

                    public enum Material { STONE, HARD, CLAY, CLOTH, GLASS, LAMP, SAND, METAL }

                    /** One block: its id, shape, material, map colour, and whether it has stairs, slab and wall variants. */
                    public record Spec(String id, Shape shape, Material material, MapColor color, boolean stairs, boolean slab, boolean wall) {}

                    public static final List<Spec> ALL = List.of(
                """ + String.join(",\n", rows) + """
                );

                    private CivilizationBlockList() {}
                }""");
    }

    public static void main(String[] args) throws IOException {
        cornerTemplate();
        for (B b : BLOCKS) emit(b);
        mergeLang();
        Path tags = ROOT.resolve("resources/data/minecraft/tags/blocks"), itags = ROOT.resolve("resources/data/minecraft/tags/items");
        mergeTag(tags.resolve("mineable/pickaxe.json"), PICK);
        mergeTag(tags.resolve("mineable/shovel.json"), SHOVEL);
        mergeTag(tags.resolve("stairs.json"), STAIRS);
        mergeTag(tags.resolve("slabs.json"), SLABS);
        mergeTag(tags.resolve("walls.json"), WALLS);
        mergeTag(tags.resolve("doors.json"), DOORS);
        mergeTag(itags.resolve("stairs.json"), STAIRS);
        mergeTag(itags.resolve("slabs.json"), SLABS);
        mergeTag(itags.resolve("walls.json"), WALLS);
        mergeTag(itags.resolve("doors.json"), DOORS);
        javaList();
        System.out.println("BlockFamilyGen: " + BLOCKS.size() + " blocks");
    }
}
