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
        Faces.all();
        Hd.all();
        HdRaces.all();
        FormLooks.all();
        FormFx.all();
        GuiHd.all();
        Painted.all();
        RaceParts.all();
        Gear.all();
        AuraV3.all();
        NpcArt.all();
        HudHd.all();
        Metals.all();
        System.out.println("ArtGen done");
    }



    // ================================================================== transformation looks

    /**
     * How transformations change the body (transform.FormLooks): recoloured race skins (an orange Namekian, a golden
     * Frost Demon, a grey evil Majin...) made from the race skins by shifting chosen colours, and the SSJ4 fur overlays.
     * Also writes face_defaults.json: each race skin's eye, brow and sclera colours, for FaceLayer.
     */
    static final class FormLooks {
        interface Pick { boolean test(float h, float s, float b); }
        interface Shift { float[] apply(float h, float s, float b); }

        static void all() throws IOException {
            Pick green = (h, s, b) -> s > 0.2f && h > 0.2f && h < 0.45f;
            Pick pale = (h, s, b) -> s < 0.28f && b > 0.45f;
            Pick pink = (h, s, b) -> s > 0.15f && (h > 0.85f || h < 0.03f);
            Pick saturated = (h, s, b) -> s > 0.25f;
            Pick flesh = (h, s, b) -> s > 0.12f && h > 0.0f && h < 0.13f;
            recolor("namekian", "namekian_orange", green, (h, s, b) -> new float[]{0.075f, Math.min(1, s * 1.15f), Math.min(1, b * 1.05f)});
            recolor("demon_namekian", "demon_namekian_king", saturated, (h, s, b) -> new float[]{0.97f, Math.min(1, s * 1.1f), b * 0.75f});
            recolor("frost_demon", "frost_demon_golden", pale, (h, s, b) -> new float[]{0.125f, 0.72f, Math.min(1, b * 1.02f)});
            recolor("metal_frost_demon", "metal_frost_demon_core", (h, s, b) -> s < 0.3f, (h, s, b) -> new float[]{0.12f, 0.55f, Math.min(1, b * 1.05f)});
            recolor("mutant_frost_demon", "mutant_frost_demon_god", saturated, (h, s, b) -> new float[]{(h + 0.05f) % 1, s, b * 0.85f});
            recolor("majin", "majin_pure", pink, (h, s, b) -> new float[]{h, s * 0.75f, Math.min(1, b * 1.08f)});
            recolor("majin", "majin_evil", pink, (h, s, b) -> new float[]{h, s * 0.12f, b * 0.82f});
            recolor("corrupted_majin", "corrupted_majin_pure", saturated, (h, s, b) -> new float[]{0.78f, Math.min(1, s * 1.1f), b * 0.7f});
            recolor("vampire", "vampire_crimson", pale, (h, s, b) -> new float[]{0.985f, 0.32f, b * 0.95f});
            recolor("bio_android", "bio_android_perfect", green, (h, s, b) -> new float[]{0.42f, Math.min(1, s * 1.1f), b});
            recolor("bio_android", "bio_android_zenith", green, (h, s, b) -> new float[]{0.13f, Math.min(1, s * 1.2f), Math.min(1, b * 1.08f)});
            recolor("tuffle", "tuffle_golden", pale, (h, s, b) -> new float[]{0.12f, 0.62f, Math.min(1, b * 1.04f)});
            recolor("gen_alien", "gen_alien_apex", saturated, (h, s, b) -> new float[]{(h + 0.33f) % 1, s, b});
            recolor("core_demon", "core_demon_god", saturated, (h, s, b) -> new float[]{0.99f, Math.min(1, s * 1.15f), b * 0.72f});
            recolor("kai", "kai_supreme", (h, s, b) -> b > 0.3f, (h, s, b) -> new float[]{h, s * 0.85f, Math.min(1, b * 1.1f)});
            fur("ssj4_fur", 0xFFC0283A);
            fur("ssj4_fur_silver", 0xFFD8DCE6);
            faceDefaults();
            masks();
        }

        /**
         * CX-16b: each race skin split into tintable layers for the race colour choices: name_skin.png (the skin) and
         * name_mark.png (bands, shell, spots), greyscale (the brightest tone of the ramp white) and empty elsewhere. Each
         * pixel goes to whichever ramp it is nearest: the skin's, a marking's, or the rest (the shorts, hair, gems).
         */
        static void masks() throws IOException {
            mask("namekian", 0xFF62B444, new int[]{0xFFE09A9A, 0xFF8A1E24}, new int[0]);
            mask("demon_namekian", 0xFF3A8A6A, new int[]{0xFFB070A0, 0xFF8A1E24}, new int[0]);
            mask("frost_demon", 0xFFF0EEF4, new int[]{0xFF8A4AC8}, new int[0]);
            mask("metal_frost_demon", 0xFFD8E2EC, new int[]{0xFF6A7A90}, new int[0]);
            mask("mutant_frost_demon", 0xFF2A2230, new int[]{0xFFE84AB0}, new int[0]);
            mask("majin", 0xFFF59AC0, new int[0], new int[0]);
            mask("corrupted_majin", 0xFF9A90A8, new int[0], new int[]{0xFF3A1A4A});
            mask("vampire", 0xFFE6E0EA, new int[0], new int[]{0xFF1C1418});
            mask("bio_android", 0xFF5AB04A, new int[]{0xFF1E3A1A}, new int[]{0xFF20242A, 0xFFE8E4D8, 0xFF7A3A9A});
            mask("tuffle", 0xFFEDE0D6, new int[0], new int[]{0xFFC8CCD8, 0xFFD01020, 0xFFFF9090, 0xFFE8C040});
            mask("gen_alien", 0xFF7A9AC0, new int[0], new int[0]);
            mask("kai", 0xFFD8B8EC, new int[0], new int[]{0xFFF8F8FF});
            mask("core_demon", 0xFFC83030, new int[0], new int[]{0xFF141010, 0xFF101010, 0xFFFFE070, 0xFFE03010});
        }

        static void mask(String name, int skinC, int[] marks, int[] rest) throws IOException {
            java.util.List<int[]> cand = new java.util.ArrayList<>();                    // {colour, class}: 0 skin, 1 mark, 2 rest
            for (int c : ramp(skinC, 6)) cand.add(new int[]{c, 0});
            for (int m : marks) for (int c : ramp(m, 6)) cand.add(new int[]{c, 1});
            for (int c : ramp(0xFF26346E, 6)) cand.add(new int[]{c, 2});                 // the shorts, their band and stripe
            for (int c : ramp(0xFF181C2C, 5)) cand.add(new int[]{c, 2});
            cand.add(new int[]{0xFFE0DCD4, 2});
            for (int r : rest) for (int c : ramp(r, 4)) cand.add(new int[]{c, 2});
            double[] top = {0, 0};
            for (int[] k : cand) if (k[1] < 2) top[k[1]] = Math.max(top[k[1]], lum(k[0]));
            for (String folder : new String[]{"race_hd", "race_painted"}) {
                File in = new File(RES + "entity/" + folder + "/" + name + ".png");
                if (!in.exists()) continue;
                java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
                Canvas skin = new Canvas(src.getWidth(), src.getHeight()), mark = new Canvas(src.getWidth(), src.getHeight());
                boolean anyMark = false;
                for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
                    int argb = src.getRGB(x, y);
                    if ((argb >>> 24) == 0) continue;
                    int best = 2;
                    double bd = Double.MAX_VALUE;
                    for (int[] k : cand) {
                        double d = dist(argb, k[0]);
                        if (d < bd) {
                            bd = d;
                            best = k[1];
                        }
                    }
                    if (best == 2 || bd > 70) continue;
                    int g = (int) Math.round(Math.min(1, lum(argb) / Math.max(1, top[best])) * 255);
                    int grey = 0xFF000000 | g << 16 | g << 8 | g;
                    if (best == 0) skin.set(x, y, grey);
                    else {
                        mark.set(x, y, grey);
                        anyMark = true;
                    }
                }
                skin.save("entity/" + folder + "/" + name + "_skin.png");
                if (anyMark) mark.save("entity/" + folder + "/" + name + "_mark.png");
            }
        }

        static double lum(int c) {
            return 0.299 * (c >> 16 & 255) + 0.587 * (c >> 8 & 255) + 0.114 * (c & 255);
        }

        static double dist(int a, int b) {
            int dr = (a >> 16 & 255) - (b >> 16 & 255), dg = (a >> 8 & 255) - (b >> 8 & 255), db = (a & 255) - (b & 255);
            return Math.sqrt(dr * dr * 0.9 + dg * dg * 1.2 + db * db * 0.8);
        }

        static void recolor(String from, String to, Pick pick, Shift shift) throws IOException {
            for (String folder : new String[]{"race", "race_hd", "race_painted"}) recolor(folder, from, to, pick, shift);
        }

        static void recolor(String folder, String from, String to, Pick pick, Shift shift) throws IOException {
            File in = new File(RES + "entity/" + folder + "/" + from + ".png");
            if (!in.exists()) return;
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
            Canvas c = new Canvas(src.getWidth(), src.getHeight());
            float[] hsb = new float[3];
            for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                java.awt.Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, hsb);
                if (pick.test(hsb[0], hsb[1], hsb[2])) {
                    float[] n = shift.apply(hsb[0], hsb[1], hsb[2]);
                    argb = argb & 0xFF000000 | java.awt.Color.HSBtoRGB(n[0], Math.max(0, Math.min(1, n[1])), Math.max(0, Math.min(1, n[2]))) & 0xFFFFFF;
                }
                c.set(x, y, argb);
            }
            c.save("entity/" + folder + "/" + to + ".png");
        }

        /** Fur over the torso, upper arms and shoulders (skin layout, base layer: outer layers may be hidden), leaving the chest and the hands bare. */
        static void fur(String name, int base) throws IOException {
            int[] fur = ramp(base, 5);
            Random r = new Random(name.hashCode());
            Skin s = new Skin();
            s.body = (f, x, y, w, h) -> {
                if (f == Face.FRONT && x >= 2 && x <= 5 && y <= 8) return 0;                          // bare chest and stomach
                if (f == Face.BOTTOM) return 0;
                int i = 1 + r.nextInt(3) - (f == Face.BACK ? 1 : 0);
                return fur[Math.max(0, Math.min(fur.length - 1, i + (y < 3 ? 1 : 0)))];
            };
            s.arm = (f, x, y, w, h) -> {
                if (y > 6 || f == Face.BOTTOM) return 0;                                                 // forearms bare
                if (y == 6 && r.nextBoolean()) return 0;                                        // a ragged edge
                return fur[Math.max(0, Math.min(fur.length - 1, 1 + r.nextInt(3)))];
            };
            s.save("entity/form/" + name + ".png");
        }

        static void faceDefaults() throws IOException {
            StringBuilder b = new StringBuilder("{\n");
            int i = 0;
            for (var e : FACE_DEFAULTS.entrySet()) {
                b.append("  \"").append(e.getKey()).append("\": ").append(e.getValue()).append(++i < FACE_DEFAULTS.size() ? "," : "").append("\n");
            }
            b.append("}\n");
            java.nio.file.Files.writeString(java.nio.file.Path.of("src/main/resources/assets/dbzenith/face_defaults.json"), b.toString());
            System.out.println("face defaults for " + FACE_DEFAULTS.size() + " race skins");
        }
    }

    // ================================================================== HD art (CX-13a): 128x128 skins, same model UVs

    /**
     * High-detail versions of the generated bodies, their outfit and the face parts, painted at twice the resolution
     * (Minecraft maps skins by fractions, so a 128x128 skin sits on the same model). Painters work in continuous
     * face coordinates (u, v in 0..1) with soft shapes, so anatomy reads as forms rather than pixels.
     */
    static final class Hd {
        static final int S = 2;

        static final class HdSkin {
            FaceFn head, body, arm, leg;

            void save(String path) throws IOException {
                Canvas c = new Canvas(64 * S, 64 * S);
                paint(c);
                c.save(path);
            }

            /** Paints this skin onto a 128x128 canvas (transparent pixels left as they are). */
            void paint(Canvas c) {
                if (head != null) box(c, 0, 0, 8 * S, 8 * S, 8 * S, head);
                if (body != null) box(c, 16 * S, 16 * S, 8 * S, 12 * S, 4 * S, body);
                if (arm != null) { box(c, 40 * S, 16 * S, 4 * S, 12 * S, 4 * S, arm); box(c, 32 * S, 48 * S, 4 * S, 12 * S, 4 * S, Skin.mirror(arm)); }
                if (leg != null) { box(c, 0, 16 * S, 4 * S, 12 * S, 4 * S, leg); box(c, 16 * S, 48 * S, 4 * S, 12 * S, 4 * S, Skin.mirror(leg)); }
            }
        }

        static void all() throws IOException {
            body("lean", 0.6);
            body("athletic", 1.0);
            body("bulky", 1.45);
            outfit();
            HdFaces.all();
        }

        // ---------------------------------------------------------- helpers

        static double g(double u, double v, double cu, double cv, double su, double sv) {
            double a = (u - cu) / su, b = (v - cv) / sv;
            return Math.exp(-0.5 * (a * a + b * b));
        }

        /** A soft line from (u0,v0) to (u1,v1) of half-width w. */
        static double line(double u, double v, double u0, double v0, double u1, double v1, double w) {
            double dx = u1 - u0, dy = v1 - v0, t = Math.max(0, Math.min(1, ((u - u0) * dx + (v - v0) * dy) / (dx * dx + dy * dy)));
            double px = u0 + t * dx - u, py = v0 + t * dy - v;
            return Math.exp(-0.5 * (px * px + py * py) / (w * w));
        }

        /** Smooth value noise in 0..1 with feature size {@code size} pixels. */
        static double smooth(double x, double y, double size, int seed) {
            double fx = x / size, fy = y / size;
            int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
            double tx = fx - ix, ty = fy - iy;
            tx = tx * tx * (3 - 2 * tx);
            ty = ty * ty * (3 - 2 * ty);
            double a = noise(ix, iy, seed), b = noise(ix + 1, iy, seed), c = noise(ix, iy + 1, seed), d = noise(ix + 1, iy + 1, seed);
            return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
        }

        static double uOf(int x, int w) { return (x + 0.5) / w; }
        static double vOf(int y, int h) { return (y + 0.5) / h; }

        /** Ambient occlusion towards the edges of a face (the creases where faces of the model meet). */
        static double edgeAo(double u, double v, double strength) {
            double e = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v));
            return -strength * Math.exp(-e / 0.06);
        }

        static int lum(double v) {
            int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
            return 0xFF000000 | g << 16 | g << 8 | g;
        }

        // ---------------------------------------------------------- bodies (greyscale, tinted by skin tone at render)

        static void body(String name, double k) throws IOException {
            anatomy(k, name.hashCode()).save("entity/body_hd/" + name + ".png");
        }

        /** Greyscale anatomy (luminance about 0.6-1.1) for a definition {@code k}; race skins colour it through a ramp. */
        static HdSkin anatomy(double k, int seed) {
            HdSkin s = new HdSkin();
            s.head = (f, x, y, w, h) -> {
                double u = uOf(x, w), v = vOf(y, h), l = Skins.faceLight(f);
                if (f == Face.FRONT) {
                    l += 0.03 * g(u, v, 0.22, 0.62, 0.12, 0.08) + 0.03 * g(u, v, 0.78, 0.62, 0.12, 0.08);   // cheekbones
                    l -= 0.04 * g(u, v, 0.28, 0.55, 0.12, 0.05) + 0.04 * g(u, v, 0.72, 0.55, 0.12, 0.05);   // eye sockets
                    l -= 0.05 * g(u, v, 0.5, 0.97, 0.3, 0.05);                                              // under the chin
                    l -= 0.04 * (g(u, v, 0.03, 0.8, 0.04, 0.2) + g(u, v, 0.97, 0.8, 0.04, 0.2));            // jaw
                    l += 0.02 * g(u, v, 0.5, 0.18, 0.3, 0.12);                                              // forehead
                }
                if (f == Face.RIGHT || f == Face.LEFT) {                                                     // ears
                    double ear = g(u, v, 0.5, 0.55, 0.12, 0.16);
                    l += 0.05 * ear - 0.08 * g(u, v, 0.5, 0.58, 0.05, 0.08) * (ear > 0.3 ? 1 : 0);
                }
                if (f == Face.BOTTOM) l -= 0.04;
                l += edgeAo(u, v, 0.05) + 0.02 * (smooth(x, y, 3, seed) - 0.5);
                return lum(l);
            };
            s.body = (f, x, y, w, h) -> {
                double u = uOf(x, w), v = vOf(y, h), l = Skins.faceLight(f);
                if (f == Face.FRONT) {
                    l += 0.08 * k * (g(u, v, 0.3, 0.15, 0.17, 0.09) + g(u, v, 0.7, 0.15, 0.17, 0.09));     // pecs
                    l -= 0.13 * k * Math.exp(-0.5 * Math.pow((v - 0.26 - 0.03 * Math.cos((u - 0.5) * 6)) / 0.018, 2)) * (u > 0.1 && u < 0.9 ? 1 : 0);
                    l -= 0.06 * k * Math.exp(-0.5 * Math.pow((u - 0.5) / 0.014, 2)) * (v < 0.82 ? 1 : 0); // sternum, linea alba
                    for (int r = 0; r < 3; r++) {                                                            // a six pack
                        double cv = 0.38 + r * 0.135;
                        l += 0.06 * k * (g(u, v, 0.41, cv, 0.065, 0.045) + g(u, v, 0.59, cv, 0.065, 0.045));
                    }
                    l -= 0.07 * k * (g(u, v, 0.13, 0.58, 0.05, 0.2) + g(u, v, 0.87, 0.58, 0.05, 0.2));     // obliques
                    l -= 0.05 * k * (line(u, v, 0.12, 0.04, 0.42, 0.07, 0.015) + line(u, v, 0.58, 0.07, 0.88, 0.04, 0.015)); // collarbones
                    l += 0.03 * k * g(u, v, 0.5, 0.88, 0.12, 0.05);                                         // lower abs
                }
                if (f == Face.BACK) {
                    l += 0.07 * k * (g(u, v, 0.27, 0.2, 0.13, 0.13) + g(u, v, 0.73, 0.2, 0.13, 0.13));      // shoulder blades
                    l -= 0.07 * k * Math.exp(-0.5 * Math.pow((u - 0.5) / 0.03, 2));                          // spine
                    l += 0.05 * k * (g(u, v, 0.12, 0.52, 0.08, 0.2) + g(u, v, 0.88, 0.52, 0.08, 0.2));      // lats
                    l -= 0.04 * k * g(u, v, 0.5, 0.92, 0.25, 0.05);
                }
                if (f == Face.RIGHT || f == Face.LEFT) l += 0.04 * k * g(u, v, 0.5, 0.45, 0.25, 0.25);
                l += edgeAo(u, v, 0.06) + 0.02 * (smooth(x, y, 3, seed + 1) - 0.5);
                return lum(l);
            };
            s.arm = (f, x, y, w, h) -> {
                double u = uOf(x, w), v = vOf(y, h), l = Skins.faceLight(f);
                if (f != Face.TOP && f != Face.BOTTOM) {
                    l += 0.07 * k * g(u, v, 0.5, 0.1, 0.35, 0.1);                                             // deltoid cap
                    l -= 0.07 * k * Math.exp(-0.5 * Math.pow((v - 0.24) / 0.02, 2));                       // its edge
                    if (f == Face.FRONT) l += 0.08 * k * g(u, v, 0.5, 0.38, 0.28, 0.1);                       // biceps
                    if (f == Face.BACK) l += 0.06 * k * g(u, v, 0.5, 0.36, 0.3, 0.11);                        // triceps
                    l -= 0.07 * k * Math.exp(-0.5 * Math.pow((v - 0.56) / 0.025, 2));                      // elbow crease
                    l += 0.05 * k * g(u, v, 0.4, 0.7, 0.25, 0.1);                                             // forearm
                    if (v > 0.88) l -= 0.03 + 0.03 * (smooth(x, y, 1.2, seed + 7) > 0.6 ? 1 : 0);              // hands, knuckles
                }
                l += edgeAo(u, v, 0.06) + 0.02 * (smooth(x, y, 3, seed + 2) - 0.5);
                return lum(l);
            };
            s.leg = (f, x, y, w, h) -> {
                double u = uOf(x, w), v = vOf(y, h), l = Skins.faceLight(f);
                if (f == Face.FRONT) {
                    l += 0.07 * k * g(u, v, 0.5, 0.25, 0.3, 0.16);                                            // quads
                    l += 0.04 * k * g(u, v, 0.3, 0.42, 0.12, 0.06);                                           // teardrop
                    l -= 0.06 * k * Math.exp(-0.5 * Math.pow((v - 0.52) / 0.025, 2));                      // knee
                    l += 0.03 * g(u, v, 0.5, 0.56, 0.2, 0.04);
                    l += 0.03 * k * g(u, v, 0.45, 0.75, 0.15, 0.12);                                          // shin
                }
                if (f == Face.BACK) l += 0.07 * k * g(u, v, 0.5, 0.68, 0.3, 0.12);                            // calves
                l += edgeAo(u, v, 0.06) + 0.02 * (smooth(x, y, 3, seed + 3) - 0.5);
                return lum(l);
            };
            return s;
        }

        // ---------------------------------------------------------- the outfit (untinted)

        static void outfit() throws IOException {
            int[] shorts = ramp(0xFF26346E, 6), band = ramp(0xFF181C2C, 4);
            HdSkin s = new HdSkin();
            s.head = (f, x, y, w, h) -> 0;
            s.body = (f, x, y, w, h) -> {                                    // CX-16a: training shorts, nothing else
                double v = vOf(y, h);
                if (v < 0.86 || f == Face.TOP) return 0;
                if (v < 0.925) return band[f == Face.FRONT ? 3 : 2];
                return pantsAt(shorts, f, x, y, w, h, 77);
            };
            s.arm = (f, x, y, w, h) -> 0;
            s.leg = (f, x, y, w, h) -> {
                double v = vOf(y, h), u = uOf(x, w);
                if (v > 0.42 || f == Face.BOTTOM) return 0;
                if ((f == Face.LEFT || f == Face.RIGHT) && Math.abs(u - 0.5) < 0.1) return 0xFFE0DCD4;
                return pantsAt(shorts, f, x, y, w, h, 61);
            };
            s.save("entity/body_hd/outfit.png");
        }

        /** Gi trousers: soft vertical folds, creases bunching at the knee and over the boots. */
        static int pantsAt(int[] p, Face f, int x, int y, int w, int h, int seed) {
            if (f == Face.TOP) return 0;
            double u = uOf(x, w), v = vOf(y, h);
            double l = switch (f) { case FRONT -> 3.0; case BACK -> 1.8; default -> 2.4; };
            l += 0.7 * Math.sin(u * 9 + smooth(x, y, 4, seed) * 3) * 0.5;                          // long folds
            l -= 1.0 * g(u, v, 0.5, 0.52, 0.4, 0.03);                                               // the knee crease
            l -= 0.8 * Math.max(0, Math.sin(v * 40 + u * 3)) * (v > 0.66 ? 1 : 0);                   // bunching over the boot
            l += 0.6 * (smooth(x, y, 2, seed + 9) - 0.5);
            return p[(int) Math.max(0, Math.min(p.length - 1, Math.round(l)))];
        }
    }

    /**
     * HD face parts: 16x16 faces at u 16, v 16 of a 128x128 skin. Eyes have lashes, whites, a tinted iris, a dark pupil
     * and a highlight (the pupil layer, drawn over the iris); brows are tinted by hair colour.
     */
    static final class HdFaces {
        static final int WHITE = 0xFFF6F4F0, LASH = 0xFF161012, TINT = 0xFFFFFFFF, PUPIL = 0xFF0C0A10, SHINE = 0xFFFFFFFF;

        static void all() throws IOException {
            String[][] eyes = {
                    // anime style (CX-14b): a thick top lid with a flick at the outer corner, open whites, a small iris
                    // toward the nose with a highlight, a soft lower lid. Column 0 is the outer corner.
                    // normal                          wide                                      narrow
                    {"LLLLLL.", ".WWIHL.", ".WWPIL.", "..SSS.."}, {".LLLLL.", "LWWIHL.", ".WWIIL.", ".WWPIW.", "..SSS.."}, {"LLLLLL.", ".LWPHL.", "..SSS.."},
                    // sharp (the lid slants down to the nose)  gentle                           tired
                    {"LLL....", ".WWLLL.", ".WWIHL.", "..WPIL.", "...SS.."}, {"..LLL..", ".LWIHL.", ".WWPIW.", "..SSS.."}, {".LLLLL.", "LLLLLL.", ".WIPIW.", ".SSSSS."},
                    // closed                          cat
                    {".......", "L.....L", ".LLLLL.", "......."}, {".LLLLL.", "LIIHII.", ".IIPII.", ".IIPII.", "..SSS.."}};
            for (int i = 0; i < eyes.length; i++) {
                Canvas whites = new Canvas(128, 128), iris = new Canvas(128, 128), pupils = new Canvas(128, 128);
                String[] grid = eyes[i];
                for (int row = 0; row < grid.length; row++) {
                    for (int col = 0; col < grid[row].length(); col++) {
                        char ch = grid[row].charAt(col);
                        for (int side = 0; side < 2; side++) {
                            int x = side == 0 ? 1 + col : 14 - col, y = 7 + row;
                            switch (ch) {
                                case 'W' -> px(whites, x, y, WHITE);
                                case 'L' -> px(whites, x, y, LASH);
                                case 'S' -> px(whites, x, y, 0x28301820);
                                case 'T' -> px(whites, x, y, 0x40FFFFFF);
                                case 'I' -> px(iris, x, y, TINT);
                                case 'P' -> { px(iris, x, y, TINT); px(pupils, x, y, PUPIL); }
                                case 'H' -> { px(iris, x, y, TINT); px(pupils, x, y, SHINE); }
                                default -> { }
                            }
                        }
                    }
                }
                whites.save("entity/face_hd/eyes_" + i + ".png");
                iris.save("entity/face_hd/iris_" + i + ".png");
                pupils.save("entity/face_hd/pupil_" + i + ".png");
            }
            String[][] brows = {
                    {".......", ".TTTTT."},                                   // normal: a clean bar one row above the eye
                    {".TTTTTT", "TTTTTTT", ".TTTT.."},                       // thick
                    {"TT.....", ".TTT...", "...TTT."},                        // fierce: low at the inner end
                    {"...TTTT", ".TTTT..", "TT....."},                       // worried
                    {".TTTTTTT", ".TTTTTTT"},                                 // joined (runs to the middle)
                    {}};
            for (int i = 0; i < brows.length; i++) {
                Canvas c = new Canvas(128, 128);
                for (int row = 0; row < brows[i].length; row++) for (int col = 0; col < brows[i][row].length(); col++) {
                    if (brows[i][row].charAt(col) != 'T') continue;
                    px(c, 1 + col, 4 + row, TINT);
                    px(c, 14 - col, 4 + row, TINT);
                }
                c.save("entity/face_hd/brows_" + i + ".png");
            }
            int line = 0xFF3E201C, teeth = 0xFFF4F0E6, tongue = 0xFFC05058, deep = 0xFF3A1014;
            String[][] mouths = {
                    {"..LLLL.."},                                             // neutral: one short line
                    {".L....L.", "..LLLL.."},                                 // smile
                    {"LLLLLLLL", "LTTTTTTL", ".DDDDDD.", "..pppp.."},         // grin
                    {"..LLLL..", ".L....L."},                                 // frown
                    {"......L.", "..LLLL.."},                                 // smirk
                    {"..LLLL..", ".LDDDDL.", ".LDggDL.", "..LLLL.."}};        // shout
            for (int i = 0; i < mouths.length; i++) {
                Canvas c = new Canvas(128, 128);
                for (int row = 0; row < mouths[i].length; row++) for (int col = 0; col < 8; col++) {
                    char ch = col < mouths[i][row].length() ? mouths[i][row].charAt(col) : '.';
                    int color = switch (ch) {
                        case 'L' -> line;
                        case 'p' -> 0x50A04A40;                                    // the lower lip, soft
                        case 'T' -> teeth;
                        case 'D' -> deep;
                        case 'g' -> tongue;
                        default -> 0;
                    };
                    if (color != 0) px(c, 4 + col, 12 + row, color);
                }
                c.save("entity/face_hd/mouth_" + i + ".png");
            }
            for (int i = 0; i < 4; i++) {
                Canvas c = new Canvas(128, 128);
                switch (i) {
                    case 0 -> { px(c, 8, 10, 0x70301818); px(c, 7, 11, 0x90301818); }   // a small hook
                    case 1 -> { }
                    case 2 -> { px(c, 7, 11, 0x50000000); px(c, 8, 11, 0x50000000); }
                    default -> { for (int y = 7; y <= 10; y++) px(c, 7, y, 0x30FFFFFF); px(c, 8, 10, 0x30000000); px(c, 6, 11, 0x50000000); px(c, 9, 11, 0x50000000); }
                }
                c.save("entity/face_hd/nose_" + i + ".png");
            }
            for (int i = 0; i < 8; i++) {
                Canvas c = new Canvas(128, 128);
                switch (i) {
                    case 1 -> { for (int x = 1; x <= 4; x++) for (int y = 11; y <= 12; y++) { px(c, x, y, 0x60FF6080); px(c, 15 - x, y, 0x60FF6080); } }
                    case 2 -> { int[][] dots = {{2, 11}, {4, 10}, {3, 12}, {5, 11}, {1, 10}}; for (int[] d : dots) { px(c, d[0], d[1], 0x90804A28); px(c, 15 - d[0], d[1], 0x90804A28); } }
                    case 3 -> { for (int k = 0; k < 3; k++) for (int x = 0; x <= 3; x++) { px(c, x, 10 + k * 2 - (x == 0 ? 1 : 0) * (k - 1), 0xB0201818); px(c, 15 - x, 10 + k * 2 - (x == 0 ? 1 : 0) * (k - 1), 0xB0201818); } }
                    case 4 -> { for (int y = 10; y <= 13; y++) { px(c, 2, y, 0xFFC02020); px(c, 3, y, 0xFFC02020); px(c, 13, y, 0xFFC02020); px(c, 12, y, 0xFFC02020); } }
                    case 5 -> { px(c, 7, 1, LASH); px(c, 8, 1, LASH); px(c, 6, 2, LASH); px(c, 9, 2, LASH); px(c, 7, 2, WHITE); px(c, 8, 2, 0xFFC01830); px(c, 7, 3, LASH); px(c, 8, 3, LASH); }
                    case 6 -> { for (int x = 2; x <= 13; x++) for (int y = 12; y <= 15; y++) if (noise(x, y, 66) > 0.45 && !(y <= 13 && x >= 5 && x <= 10)) px(c, x, y, 0x40201010); }
                    case 7 -> { px(c, 7, 1, 0xFFFF80A0); px(c, 8, 1, 0xFFE03050); px(c, 6, 2, 0xFFE03050); px(c, 7, 2, 0xFFFF90B0); px(c, 8, 2, 0xFFC02040); px(c, 9, 2, 0xFFA01030); px(c, 7, 3, 0xFFA01030); px(c, 8, 3, 0xFF801028); }
                    default -> { }
                }
                c.save("entity/face_hd/extra_" + i + ".png");
            }
        }

        static void px(Canvas c, int x, int y, int color) {
            if (x >= 0 && x < 16 && y >= 0 && y < 16) c.set(16 + x, 16 + y, color);
        }
    }






    // ================================================================== NPC art (CX-12)

    /**
     * NPC skins in the painted style, composed from the same parts players wear: a painted race skin or painted anatomy
     * in a skin tone, painted clothes (the gear textures, or outfits painted here), touches of their own (beards,
     * glasses, robes, ties, loincloths) and the anime face parts baked in. Hair, horns, antennae, tails, shells, hats
     * and halos are 3D, drawn by the client (client.render.NpcLooks). All designs original.
     */
    static final class NpcArt {
        static void all() throws IOException {
            master();
            patrolOfficer();
            kiSoldier();
            androidUnit();
            sproutling();
            tyrantLord();
            rampageBrute();
            namekianWarrior();
            enma();
            ogre("ogre_clerk_red", 0xFFD84A3A, false);
            ogre("ogre_clerk_blue", 0xFF4A78D8, false);
            ogre("ogre_guard", 0xFFC8402E, true);
            northKai();
            grandKai();
            trainingMonkey();
            damnedWarrior();
            trainingCricket();
        }


        /** The Kai's cricket (32x32 for its own model): green-brown body, darker wing cases, pale legs. */
        static void trainingCricket() throws IOException {
            int[] body = ramp(0xFF6A7A2A, 5), wing = ramp(0xFF4A3A1A, 4), leg = ramp(0xFF9AA060, 4);
            Canvas c = new Canvas(32, 32);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int col;
                if (y < 9 && x < 16) col = body[2 + (int) Math.round(Math.sin(x * 0.9) * 0.6) + (y < 3 ? 1 : 0)];
                else if (y < 9) col = wing[(x + y) % 3 == 0 ? 1 : 2];
                else if (y < 15) col = x < 12 ? body[y < 11 ? 3 : 2] : leg[1];
                else col = leg[(x + y) % 2 == 0 ? 2 : 1];
                c.set(x, y, col);
            }
            c.save("entity/training_cricket.png");
        }
        /** A skin under construction: layers stacked bottom to top, then saved as a fighter texture. */
        static final class Npc {
            final Canvas c = new Canvas(128, 128);

            static java.awt.image.BufferedImage read(String path) throws IOException {
                return ImageIO.read(new File(RES + path));
            }

            /** A painted race skin as the base. */
            Npc race(String skin) throws IOException {
                return layer("entity/race_painted/" + skin + ".png");
            }

            /** Painted anatomy in a skin tone. */
            Npc body(String build, int tone) throws IOException {
                java.awt.image.BufferedImage b = read("entity/body_painted/" + build + ".png");
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    int p = b.getRGB(x, y);
                    if ((p >>> 24) == 0) continue;
                    double l = (p & 255) / 255.0;
                    int r = (int) (((tone >> 16) & 255) * l), g = (int) (((tone >> 8) & 255) * l), bl = (int) ((tone & 255) * l);
                    c.set(x, y, 0xFF000000 | r << 16 | g << 8 | bl);
                }
                return this;
            }

            /** Lays an image over what is there (by its alpha), optionally tinted (multiplied). */
            Npc layer(String path, int tint) throws IOException {
                java.awt.image.BufferedImage b = read(path);
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    int p = b.getRGB(x * b.getWidth() / 128, y * b.getHeight() / 128);
                    if (tint != 0xFFFFFF) p = (p & 0xFF000000) | (((p >> 16) & 255) * ((tint >> 16) & 255) / 255) << 16
                            | (((p >> 8) & 255) * ((tint >> 8) & 255) / 255) << 8 | ((p & 255) * (tint & 255) / 255);
                    over(x, y, p);
                }
                return this;
            }

            Npc layer(String path) throws IOException {
                return layer(path, 0xFFFFFF);
            }

            /** Paints a skin of touches (transparent where it returns 0) over what is there. */
            Npc paint(Hd.HdSkin s) {
                Canvas t = new Canvas(128, 128);
                s.paint(t);
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) over(x, y, t.get(x, y));
                return this;
            }

            void over(int x, int y, int p) {
                int a = p >>> 24;
                if (a == 0) return;
                if (a == 255) {
                    c.set(x, y, p);
                    return;
                }
                int d = c.get(x, y);
                double t = a / 255.0;
                c.set(x, y, mix(d | 0xFF000000, p | 0xFF000000, t) | (Math.max(a, d >>> 24) << 24));
            }

            /** The anime face parts: eyes (whites or not), iris, pupils, brows, nose, mouth. */
            Npc face(int eyes, int iris, boolean whites, int brows, int browColor, int mouth, int nose) throws IOException {
                layer("entity/face_hd/eyes_" + eyes + ".png", whites ? 0xFFFFFF : iris & 0xFFFFFF);
                layer("entity/face_hd/iris_" + eyes + ".png", iris & 0xFFFFFF);
                layer("entity/face_hd/pupil_" + eyes + ".png");
                if (brows < 5) layer("entity/face_hd/brows_" + brows + ".png", browColor & 0xFFFFFF);
                layer("entity/face_hd/nose_" + nose + ".png");
                return layer("entity/face_hd/mouth_" + mouth + ".png");
            }

            void save(String name) throws IOException {
                c.save("entity/fighter/" + name + ".png");
            }
        }

        // ---------------------------------------------------------- shared painting helpers

        static double u(int x, int w) { return (x + 0.5) / w; }
        static double v(int y, int h) { return (y + 0.5) / h; }

        static int cloth(int[] r, Face f, int x, int y, int w, int h, int seed) {
            return HdRaces.paintedCloth(r, f, x, y, w, h, seed, 1.0);
        }

        static int flat(int[] r, Face f, double extra) {
            return HdRaces.tone(r, Painted.BASE + Painted.face(f) + extra);
        }

        static int ink(int[] r) {
            return HdRaces.tone(r, Painted.INK);
        }

        static boolean row(double vv, double at, int h) {
            return Math.abs(vv - at) * h < 0.6;
        }

        /** Dark round glasses over both eyes (the head's front face), with a bridge. */
        static int glasses(Face f, int x, int y, int w, int h, int lens, int frame) {
            if (f != Face.FRONT) return f == Face.LEFT || f == Face.RIGHT ? (v(y, h) > 0.47 && v(y, h) < 0.53 && u(x, w) < 0.55 ? frame : 0) : 0;
            double uu = u(x, w), vv = v(y, h);
            for (double cx : new double[]{0.28, 0.72}) {
                double d = Math.hypot((uu - cx) / 0.2, (vv - 0.52) / 0.12);
                if (d < 0.8) return lens;
                if (d < 1.05) return frame;
            }
            if (vv > 0.47 && vv < 0.53 && uu > 0.45 && uu < 0.55) return frame;
            return 0;
        }

        // ---------------------------------------------------------- the people of the world

        /** The old master of the turtle school: bald, a long white beard and moustache, dark glasses, the school's gi. */
        static void master() throws IOException {
            int[] beard = ramp(0xFFF2F0EA, 5);
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                int g = glasses(f, x, y, w, h, 0xFF141418, 0xFF2A2A30);
                if (g != 0) return g;
                if (f == Face.FRONT) {
                    if (vv > 0.66 && vv < 0.78 && Math.abs(uu - 0.5) < 0.34 - (vv - 0.66)) return flat(beard, f, vv < 0.69 ? 0.04 : 0);   // moustache
                    if (vv > 0.76) return flat(beard, f, (x + y) % 3 == 0 ? -0.06 : 0);                                   // beard
                }
                if ((f == Face.LEFT || f == Face.RIGHT) && vv > 0.62 && uu < 0.4) return flat(beard, f, -0.04);
                return 0;
            };
            s.body = (f, x, y, w, h) -> {                                                                                  // the beard falls to the chest
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT && vv < 0.42 - Math.abs(uu - 0.5) * 0.9) return flat(beard, f, (x + y) % 3 == 0 ? -0.06 : 0);
                return 0;
            };
            new Npc().body("lean", 0xFFE6C29C).layer("entity/gear/turtle_top.png").layer("entity/gear/turtle_pants.png")
                    .layer("entity/gear/turtle_boots.png").face(0, 0xFF241A12, true, 1, 0xFFF2F0EA, 0, 0).paint(s).save("martial_arts_master");
        }

        /** An Earth patrol officer: navy uniform with a gold badge, a duty belt, boots (the cap is 3D). */
        static void patrolOfficer() throws IOException {
            int[] navy = ramp(0xFF24346A, 6), gold = ramp(0xFFE0B040, 4), black = ramp(0xFF1E1E24, 5);
            Hd.HdSkin s = new Hd.HdSkin();
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM) return 0;
                if (vv > 0.78 && vv < 0.88) return row(vv, 0.785, h) || row(vv, 0.875, h) ? ink(black) : (f == Face.FRONT && Math.abs(uu - 0.5) < 0.08 ? flat(gold, f, 0) : flat(black, f, 0));
                if (vv >= 0.88) return cloth(navy, f, x, y, w, h, 611);
                if (f == Face.FRONT) {
                    if (Math.abs(uu - 0.5) < 0.025) return ink(navy);                                                     // the button line
                    if (Math.hypot(uu - 0.3, (vv - 0.24) * 0.7) < 0.07) return flat(gold, f, 0.05);                        // the badge
                    if (vv < 0.12 && Math.abs(uu - 0.5) < 0.2 - vv) return flat(ramp(0xFFE8ECF4, 4), f, 0);               // the collar
                }
                return cloth(navy, f, x, y, w, h, 612);
            };
            s.arm = (f, x, y, w, h) -> v(y, h) > 0.86 ? 0 : v(y, h) > 0.82 ? ink(navy) : cloth(navy, f, x, y, w, h, 613);
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.8 || f == Face.BOTTOM ? (row(v(y, h), 0.805, h) ? ink(black) : flat(black, f, 0)) : cloth(navy, f, x, y, w, h, 614);
            new Npc().body("athletic", 0xFFEEC6A0).paint(s).face(3, 0xFF3A2A1A, true, 0, 0xFF3A2414, 0, 0).save("patrol_officer");
        }

        /** A soldier of the tyrant's army: a green-skinned alien in white armour with purple pads (3D) and a scouter. */
        static void kiSoldier() throws IOException {
            new Npc().body("athletic", 0xFF7AB89A).layer("entity/gear/frost_armor_top.png").layer("entity/gear/frost_armor_pants.png")
                    .layer("entity/gear/frost_armor_boots.png").face(3, 0xFFB01828, true, 2, 0xFF2A5A3A, 4, 0).save("ki_soldier");
        }

        /** A combat android: pale, black hair, a grey jumpsuit with red shoulder stripes and a red-ringed bolt on the chest. */
        static void androidUnit() throws IOException {
            int[] grey = ramp(0xFF6A7080, 6), red = ramp(0xFFC82828, 4), black = ramp(0xFF1C1C22, 5);
            Hd.HdSkin s = new Hd.HdSkin();
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM) return 0;
                if (f == Face.FRONT) {
                    double d = Math.hypot((uu - 0.5) / 0.7, vv - 0.3);
                    if (d < 0.16 && d > 0.12) return flat(red, f, 0);
                    if (d <= 0.12 && Math.abs((uu - 0.5) * 1.6 + (vv - 0.3)) < 0.03) return flat(ramp(0xFFF0F0F0, 3), f, 0);   // the bolt
                }
                if (f == Face.TOP) return flat(red, f, 0);
                return cloth(grey, f, x, y, w, h, 621);
            };
            s.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv < 0.1) return flat(red, f, 0);                                                                     // shoulder stripes
                if (vv > 0.82) return row(vv, 0.825, h) ? ink(black) : flat(black, f, 0);                                 // gloves
                return cloth(grey, f, x, y, w, h, 622);
            };
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.78 || f == Face.BOTTOM ? flat(black, f, 0) : cloth(grey, f, x, y, w, h, 623);
            new Npc().body("athletic", 0xFFF2DCCA).paint(s).face(2, 0xFF5AA8E0, true, 0, 0xFF141418, 0, 2).save("android_unit");
        }

        /** A sproutling: a small green creature grown from a seed, its head ridged with dark veins, red eyes, a toothy grin. */
        static void sproutling() throws IOException {
            int[] green = ramp(0xFF5AA83A, 6);
            Hd.HdSkin s = new Hd.HdSkin();
            FaceFn veins = (f, x, y, w, h) -> {
                double n = Hd.smooth(x * 1.4, y * 1.4, 4, 631 + f.ordinal());
                return Math.abs(n - 0.5) < 0.03 ? ink(green) : 0;
            };
            s.head = (f, x, y, w, h) -> f == Face.FRONT && v(y, h) > 0.3 ? 0 : veins.at(f, x, y, w, h);
            s.body = veins;
            s.arm = veins;
            s.leg = veins;
            new Npc().body("lean", 0xFF6AB848).paint(s).face(7, 0xFFE01818, false, 5, 0, 2, 1).save("sproutling");
        }

        /** The tyrant lord: a Frost Demon in his final shape, cold crimson eyes and a smirk (his tail is 3D). */
        static void tyrantLord() throws IOException {
            new Npc().race("frost_demon").face(3, 0xFFC01830, true, 5, 0, 4, 0).save("tyrant_lord");
        }

        /** The rampaging brute: a towering, bald warrior with a heavy moustache in battle armour (his tail is 3D). */
        static void rampageBrute() throws IOException {
            int[] hair = ramp(0xFF1C1414, 4);
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT && vv > 0.68 && vv < 0.76 && Math.abs(uu - 0.5) < 0.32) return flat(hair, f, 0);   // moustache
                if (f == Face.FRONT && vv >= 0.76 && vv < 0.88 && (Math.abs(uu - 0.5) > 0.24 && Math.abs(uu - 0.5) < 0.32)) return flat(hair, f, -0.04);
                return 0;
            };
            new Npc().body("bulky", 0xFFD8A878).layer("entity/gear/battle_armor_top.png").layer("entity/gear/battle_armor_pants.png")
                    .layer("entity/gear/battle_armor_boots.png").face(3, 0xFF241A12, true, 2, 0xFF1C1414, 2, 0).paint(s).save("rampage_brute");
        }

        /** A Namekian warrior in his gi (the cape, pads, antennae and ears are 3D). */
        static void namekianWarrior() throws IOException {
            new Npc().race("namekian").layer("entity/gear/namekian_top.png").layer("entity/gear/namekian_pants.png")   // the race is bare now (CX-16a): dressed in the gi
                    .layer("entity/gear/namekian_boots.png").face(3, 0xFF201418, true, 5, 0, 0, 0).save("namekian_warrior");
        }

        // ---------------------------------------------------------- the other world

        /** Enma, judge of the dead: a giant red ogre, a great black beard, a deep purple robe with gold, his hat is 3D. */
        static void enma() throws IOException {
            int[] beard = ramp(0xFF181216, 5), robe = ramp(0xFF4A2468, 6), gold = ramp(0xFFE0B040, 5), sash = ramp(0xFFB82A2A, 5);
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT && vv > 0.64) {
                    if (vv < 0.72 && Math.abs(uu - 0.5) < 0.12) return 0;                                                  // the mouth shows
                    return flat(beard, f, (x * 3 + y) % 5 == 0 ? 0.06 : 0);
                }
                if ((f == Face.LEFT || f == Face.RIGHT) && vv > 0.4) return flat(beard, f, (x + y) % 4 == 0 ? 0.05 : 0);    // whiskers into the beard
                if (f == Face.BACK && vv > 0.5) return flat(beard, f, 0);
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT) {
                    if (vv < 0.36 - Math.abs(uu - 0.5) * 0.6) return flat(beard, f, (x + y) % 4 == 0 ? 0.05 : 0);          // the beard spills on the chest
                    if (Math.abs(uu - 0.5) < 0.05) return flat(gold, f, 0);                                                 // the robe's gold edge
                }
                if (vv > 0.62 && vv < 0.74) return row(vv, 0.625, h) || row(vv, 0.735, h) ? ink(sash) : flat(sash, f, 0); // a red sash
                return cloth(robe, f, x, y, w, h, 641);
            };
            s.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv > 0.86) return 0;
                if (vv > 0.78) return row(vv, 0.785, h) ? ink(gold) : flat(gold, f, 0);                                   // wide gold cuffs
                return cloth(robe, f, x, y, w, h, 642);
            };
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.86 || f == Face.BOTTOM ? flat(ramp(0xFF1A1A1E, 4), f, 0) : cloth(robe, f, x, y, w, h, 643);
            new Npc().body("bulky", 0xFFC8382C).face(3, 0xFF141010, true, 1, 0xFF181216, 3, 0).paint(s).save("enma");
        }

        /** An ogre of the check-in station: red or blue skin, a white shirt and black tie; the guard wears a tiger-striped wrap. */
        static void ogre(String name, int tone, boolean guard) throws IOException {
            int[] shirt = ramp(0xFFF0EEEA, 5), tie = ramp(0xFF1E1E26, 5), slacks = ramp(0xFF2A2A34, 6), tiger = ramp(0xFFE8A82A, 5), iron = ramp(0xFF8A8E98, 4);
            Hd.HdSkin s = new Hd.HdSkin();
            if (guard) {
                s.body = (f, x, y, w, h) -> v(y, h) > 0.82 && f != Face.TOP ? (Math.sin(u(x, w) * 18 + v(y, h) * 6) > 0.55 ? ink(tiger) : flat(tiger, f, 0)) : 0;
                s.arm = (f, x, y, w, h) -> v(y, h) > 0.7 && v(y, h) < 0.84 && f != Face.BOTTOM ? (row(v(y, h), 0.705, h) || row(v(y, h), 0.835, h) ? ink(iron) : flat(iron, f, 0)) : 0;
                s.leg = (f, x, y, w, h) -> {
                    double vv = v(y, h);
                    if (f == Face.BOTTOM || vv > 0.42) return 0;
                    if (row(vv, 0.415, h)) return ink(tiger);                                                               // the wrap's ragged hem
                    return Math.sin(u(x, w) * 14 + vv * 9) > 0.55 ? ink(tiger) : flat(tiger, f, 0);
                };
            } else {
                s.body = (f, x, y, w, h) -> {
                    double uu = u(x, w), vv = v(y, h);
                    if (f == Face.BOTTOM) return 0;
                    if (vv > 0.88) return cloth(slacks, f, x, y, w, h, 651);
                    if (f == Face.FRONT && Math.abs(uu - 0.5) < 0.07 + (vv > 0.12 ? 0.02 : 0) && vv < 0.68) return Math.abs(uu - 0.5) > 0.06 ? ink(tie) : flat(tie, f, 0.04);
                    if (f == Face.FRONT && vv < 0.1 && Math.abs(uu - 0.5) < 0.22) return flat(shirt, f, 0.05);           // the collar
                    return cloth(shirt, f, x, y, w, h, 652);
                };
                s.arm = (f, x, y, w, h) -> v(y, h) > 0.86 ? 0 : v(y, h) > 0.83 ? ink(shirt) : cloth(shirt, f, x, y, w, h, 653);
                s.leg = (f, x, y, w, h) -> v(y, h) > 0.86 || f == Face.BOTTOM ? flat(tie, f, 0) : cloth(slacks, f, x, y, w, h, 654);
            }
            new Npc().body(guard ? "bulky" : "athletic", tone).paint(s)
                    .face(guard ? 3 : 0, 0xFF141010, true, guard ? 2 : 0, 0xFF141010, guard ? 2 : 0, 0).save(name);
        }

        /** The Kai of the north: short and round, pale blue, dark round glasses, whiskers, a navy robe with a gold sigil. */
        static void northKai() throws IOException {
            int[] robe = ramp(0xFF1E2A5A, 6), gold = ramp(0xFFE0B040, 5), white = ramp(0xFFF0F0F2, 4), skin = ramp(0xFF7AA0E0, 5);
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                int g = glasses(f, x, y, w, h, 0xFF101014, 0xFF101014);
                if (g != 0) return g;
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT && vv > 0.68 && vv < 0.74 && (uu < 0.22 || uu > 0.78) && x % 2 == 0) return ink(skin);  // whiskers
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM) return 0;
                if (f == Face.FRONT) {
                    if (vv < 0.1 && Math.abs(uu - 0.5) < 0.24) return flat(white, f, 0);                                  // the collar
                    double d = Math.hypot((uu - 0.5) / 0.8, vv - 0.36);
                    if (d < 0.17) {                                                                                           // the sigil: a ring round three waves
                        if (d > 0.13) return flat(gold, f, 0);
                        if (Math.abs(Math.sin((uu - 0.5) * 40) * 0.025 - ((vv - 0.36) % 0.06)) < 0.008) return flat(gold, f, 0.05);
                        return flat(white, f, 0);
                    }
                }
                if (vv > 0.72 && vv < 0.8) return row(vv, 0.725, h) ? ink(gold) : flat(gold, f, 0);                       // the belt
                return cloth(robe, f, x, y, w, h, 661);
            };
            s.arm = (f, x, y, w, h) -> v(y, h) > 0.8 ? 0 : v(y, h) > 0.76 ? ink(robe) : cloth(robe, f, x, y, w, h, 662);
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.86 || f == Face.BOTTOM ? flat(ramp(0xFF6A4424, 4), f, 0) : cloth(robe, f, x, y, w, h, 663);
            new Npc().body("bulky", 0xFF7AA0E0).face(0, 0xFF101014, true, 5, 0, 1, 2).paint(s).save("north_kai");
        }

        /** The Grand Kai: tall and old, lavender skin, a white moustache, round glasses, a purple robe embroidered in gold. */
        static void grandKai() throws IOException {
            int[] robe = ramp(0xFF6A2A8A, 6), gold = ramp(0xFFE8C050, 5), white = ramp(0xFFF4F2F0, 5);
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                int g = glasses(f, x, y, w, h, 0xC8D8F0FF, 0xFF2A1A10);
                if (g != 0) return g;
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT && vv > 0.68 && vv < 0.76 && Math.abs(uu - 0.5) < 0.3 - (vv - 0.68) * 1.5) return flat(white, f, 0);
                return 0;
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM) return 0;
                if (f == Face.FRONT && Math.abs(uu - 0.5) < 0.08) return ((int) (vv * h)) % 3 == 0 ? flat(gold, f, 0.06) : flat(gold, f, 0);
                if (vv < 0.08) return flat(gold, f, 0);
                return cloth(robe, f, x, y, w, h, 671);
            };
            s.arm = (f, x, y, w, h) -> v(y, h) > 0.84 ? 0 : v(y, h) > 0.78 ? flat(gold, f, 0) : cloth(robe, f, x, y, w, h, 672);
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.88 || f == Face.BOTTOM ? flat(gold, f, -0.1) : cloth(white, f, x, y, w, h, 673);
            new Npc().body("lean", 0xFFC8A6E0).face(4, 0xFF2A1A30, true, 0, 0xFFF4F2F0, 1, 2).paint(s).save("grand_kai");
        }

        /** The Kai's training monkey: brown fur, a tan face and belly, quick bright eyes (the tail is 3D). */
        static void trainingMonkey() throws IOException {
            int[] tan = ramp(0xFFE8C090, 5);
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT && Math.hypot((uu - 0.5) / 0.42, (vv - 0.62) / 0.36) < 1) return flat(tan, f, 0);
                return 0;
            };
            s.body = (f, x, y, w, h) -> f == Face.FRONT && Math.hypot((u(x, w) - 0.5) / 0.32, (v(y, h) - 0.55) / 0.35) < 1 ? flat(tan, f, 0) : 0;
            new Npc().body("lean", 0xFF8A5A30).paint(s).face(1, 0xFF1A1208, true, 5, 0, 1, 1).save("training_monkey");
        }

        /** A damned warrior of Limbo: ash-pale skin, a torn dark gi, burning red eyes, teeth bared. */
        static void damnedWarrior() throws IOException {
            int[] gi = ramp(0xFF2A2230, 6), sash = ramp(0xFF8A1A22, 4);
            Hd.HdSkin s = new Hd.HdSkin();
            s.body = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.BOTTOM) return 0;
                if (Hd.smooth(x * 1.3, y, 3, 681 + f.ordinal()) > 0.7) return 0;                                         // torn
                if (vv > 0.76 && vv < 0.88) return flat(sash, f, 0);
                return cloth(gi, f, x, y, w, h, 682);
            };
            s.arm = (f, x, y, w, h) -> v(y, h) < 0.3 + 0.08 * Math.sin(u(x, w) * 20) ? cloth(gi, f, x, y, w, h, 683) : 0;
            s.leg = (f, x, y, w, h) -> v(y, h) < 0.7 + 0.06 * Math.sin(u(x, w) * 25) && f != Face.BOTTOM ? cloth(gi, f, x, y, w, h, 684) : 0;
            new Npc().body("athletic", 0xFFB8B2C2).paint(s).face(2, 0xFFFF3020, true, 2, 0xFF2A2430, 2, 0).save("damned_warrior");
        }
    }
    // ================================================================== aura v3 (CX-11a)

    /**
     * Aura textures, white with alpha (tinted at render time). aura_tongue.png: 16 frames (48x96 each) of one flame
     * tongue, a jagged flickering silhouette with streaks of light running up through it; everything is periodic in
     * time, so the frames loop seamlessly. aura_spike.png: a sharp lick. aura_wisp.png: a soft curling wisp.
     */
    static final class AuraV3 {
        static void all() throws IOException {
            tongue();
            spike();
            wisp();
            cracks();
        }

        static int px(double grey, double alpha) {
            int g = (int) Math.round(Math.max(0, Math.min(1, grey)) * 255), a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
            return a == 0 ? 0 : a << 24 | g << 16 | g << 8 | g;
        }

        static void tongue() throws IOException {
            int fw = 48, fh = 96, frames = 16;
            Canvas c = new Canvas(fw * frames, fh);
            double TAU = Math.PI * 2;
            for (int k = 0; k < frames; k++) {
                double t = k / (double) frames;
                for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) {
                    double u = (x + 0.5) / fw - 0.5, v = (y + 0.5) / fh;                 // v: 0 the tip .. 1 the base
                    double jag = 0.13 * Math.sin(v * 13 + TAU * t * 2 + 1.1) + 0.08 * Math.sin(v * 29 - TAU * t * 3)
                            + 0.05 * Math.sin(v * 51 + TAU * t * 5 + 2.3);
                    double hw = 0.46 * Math.pow(v, 0.62) * (1 + jag);
                    double cx = 0.07 * (1 - v) * Math.sin(TAU * t + v * 5);              // the tip sways
                    double d = Math.abs(u - cx) / Math.max(0.01, hw);
                    if (d >= 1) continue;
                    double flow = 0.5 + 0.5 * Math.sin(v * 9 + TAU * t * 2 + 2.5 * Math.sin(u * 8 + TAU * t));   // light running up
                    double b = (1 - d * d) * (0.5 + 0.5 * Math.pow(v, 0.5)) + 0.4 * Math.exp(-Math.pow(d / 0.32, 2));
                    b *= 0.82 + 0.18 * flow;
                    double a = Math.min(1, (1 - d) * 3.2) * (0.45 + 0.55 * Math.pow(v, 0.35)) * (0.85 + 0.15 * flow);
                    c.set(k * fw + x, y, px(0.55 + 0.45 * b, a));
                }
            }
            c.save("entity/aura_tongue.png");
        }

        static void spike() throws IOException {
            Canvas c = new Canvas(16, 64);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 16; x++) {
                double u = (x + 0.5) / 16 - 0.5, v = (y + 0.5) / 64;
                double hw = 0.46 * Math.pow(v, 1.5);
                double d = Math.abs(u) / Math.max(0.005, hw);
                if (d >= 1) continue;
                c.set(x, y, px(0.75 + 0.25 * (1 - d), Math.min(1, (1 - d) * 2.5) * (0.4 + 0.6 * v)));
            }
            c.save("entity/aura_spike.png");
        }


        /** Ground cracks: jagged dark fissures branching out from a broken centre, fading toward their ends (128x128). */
        static void cracks() throws IOException {
            int n = 128;
            double[] a = new double[n * n];
            Random rnd = new Random(1102);
            for (int k = 0; k < 11; k++) {
                double ang = k * Math.PI * 2 / 11 + rnd.nextDouble() * 0.4;
                walk(a, n, n / 2.0, n / 2.0, ang, 18 + rnd.nextInt(26), 1.6, rnd, 2);
            }
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {                   // the shattered middle
                double d = Math.hypot(x + 0.5 - n / 2.0, y + 0.5 - n / 2.0);
                if (d < 9) a[y * n + x] = Math.max(a[y * n + x], 0.55 * (1 - d / 9) + (noise(x, y, 1103) > 0.7 ? 0.3 : 0));
            }
            Canvas c = new Canvas(n, n);
            for (int i = 0; i < n * n; i++) if (a[i] > 0.02) c.set(i % n, i / n, (int) Math.round(Math.min(1, a[i]) * 235) << 24 | 0x1A1410);
            c.save("entity/ground_cracks.png");
        }

        /** One crack: a jittering walk outward, thinning, sometimes forking. */
        static void walk(double[] a, int n, double x, double y, double ang, int steps, double width, Random rnd, int forks) {
            for (int s = 0; s < steps; s++) {
                ang += (rnd.nextDouble() - 0.5) * 0.42;
                x += Math.cos(ang) * 1.4;
                y += Math.sin(ang) * 1.4;
                double w = width * (1 - s / (double) steps) + 0.4, strength = 1 - 0.6 * s / steps;
                for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
                    int px = (int) x + dx, py = (int) y + dy;
                    if (px < 0 || py < 0 || px >= n || py >= n) continue;
                    double d = Math.hypot(px + 0.5 - x, py + 0.5 - y);
                    if (d < w) a[py * n + px] = Math.max(a[py * n + px], strength);
                }
                if (forks > 0 && rnd.nextDouble() < 0.06) walk(a, n, x, y, ang + (rnd.nextBoolean() ? 0.7 : -0.7), steps - s, width * 0.6, rnd, forks - 1);
            }
        }
        static void wisp() throws IOException {
            Canvas c = new Canvas(32, 64);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 32; x++) {
                double u = (x + 0.5) / 32 - 0.5, v = (y + 0.5) / 64;
                double cx = 0.26 * Math.sin(v * Math.PI * 1.6);
                double hw = 0.13 * Math.sin(Math.PI * v) + 0.01;
                double d = Math.abs(u - cx) / hw;
                if (d >= 1) continue;
                c.set(x, y, px(1, (1 - d * d) * Math.sin(Math.PI * v)));
            }
            c.save("entity/aura_wisp.png");
        }
    }
    // ================================================================== fighting clothes, painted (CX-14e)

    /**
     * Gi sets as painted skin-layout textures (128x128, drawn on the player model: client.render.GearLayer), one per
     * piece so pieces mix: tops (the gi with its V of undershirt, wrap line, sash and knot, sleeves and wristbands, and
     * an original school emblem on the back; battle armour's outlined chest plate over a bodysuit, with gloves; the
     * Majin vest left open over the belly), trousers with clean fold lines, boots with wraps, caps and soles. Plus the
     * greyscale material sheet for the 3D pieces (cape, turban, pads, sash tails, baggy trousers).
     */
    static final class Gear {
        static void all() throws IOException {
            gi("turtle", 0xFFF07820, 0xFF2852C8, 0xFF2852C8, 0xFF2852C8, true);
            gi("demon", 0xFF6A3A9A, 0xFFC02838, 0xFF2A1A2E, 0xFF2A1A2E, true);
            gi("namekian", 0xFF5A3A8A, 0xFF40B0E0, 0xFF5A3418, 0xFF46306E, false);
            battleArmor();
            majin();
            hoodie();
            fusion();
            parts();
        }

        static double u(int x, int w) { return (x + 0.5) / w; }
        static double v(int y, int h) { return (y + 0.5) / h; }

        static int cloth(int[] r, Face f, int x, int y, int w, int h, int seed) {
            return HdRaces.paintedCloth(r, f, x, y, w, h, seed, 1.0);
        }

        static int flat(int[] r, Face f, double extra) {
            return HdRaces.tone(r, Painted.BASE + Painted.face(f) + extra);
        }

        static int ink(int[] r) {
            return HdRaces.tone(r, Painted.INK);
        }

        /** Within half a pixel of a row (v). */
        static boolean row(double vv, double at, int h) {
            return Math.abs(vv - at) * h < 0.6;
        }

        /** An original school emblem: a ring around a rising flame. */
        static boolean emblem(double uu, double vv, double cu, double cv, double r) {
            double dx = (uu - cu) / r, dy = (vv - cv) / r * 1.5, d = Math.hypot(dx, dy);
            boolean ring = d > 0.78 && d < 1.0;
            boolean flame = Math.abs(dx) < 0.32 * (1 - (dy + 0.7) / 1.4) + 0.06 && dy > -0.7 && dy < 0.55;
            return ring || flame;
        }

        static void gi(String set, int clothC, int sashC, int bootC, int underC, boolean emblem) throws IOException {
            int[] cl = ramp(clothC, 6), sa = ramp(sashC, 5), bt = ramp(bootC, 5), un = ramp(underC, 5), white = ramp(0xFFF4F0E8, 4);
            Hd.HdSkin top = new Hd.HdSkin();
            top.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.9) return 0;
                if (vv >= 0.765) {                                                                  // the sash and its knot
                    if (row(vv, 0.77, h) || row(vv, 0.895, h)) return ink(sa);
                    if (f == Face.FRONT && Math.abs(uu - 0.36) < 0.09) return Math.abs(uu - 0.36) > 0.065 ? ink(sa) : flat(sa, f, 0.06);
                    return flat(sa, f, 0);
                }
                if (f == Face.FRONT) {
                    double d = Math.abs(uu - 0.5), edge = (0.4 - vv) * 1.0;
                    if (vv < 0.4 && d < edge - 0.04) return flat(un, f, 0);                          // undershirt in the V
                    if (vv < 0.4 && d < edge + 0.02) return ink(cl);                                 // the collar's edge
                    if (Painted.one(x, y, w, h, 0.5, 0.4, 0.66, 0.765) < Painted.LINE) return ink(cl);   // where the gi wraps over
                }
                if (f == Face.BACK && emblem && emblem(uu, vv, 0.5, 0.3, 0.22)) {
                    double dx = (uu - 0.5) / 0.22, dy = (vv - 0.3) / 0.22 * 1.5, dd = Math.hypot(dx, dy);
                    return dd > 0.74 && dd < 0.8 || dd > 0.98 ? ink(cl) : white[2];
                }
                return cloth(cl, f, x, y, w, h, set.hashCode());
            };
            top.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.TOP) return flat(cl, f, 0);
                if (vv < 0.36) return row(vv, 0.35, h) ? ink(cl) : cloth(cl, f, x, y, w, h, set.hashCode() + 1);   // short sleeves
                if (vv >= 0.72 && vv < 0.87 && f != Face.BOTTOM) {                                  // wristbands
                    if (row(vv, 0.725, h) || row(vv, 0.865, h)) return ink(sa);
                    return flat(sa, f, row(vv, 0.795, h) ? 0.08 : 0);
                }
                return 0;
            };
            top.save("entity/gear/" + set + "_top.png");

            Hd.HdSkin pants = new Hd.HdSkin();
            pants.body = (f, x, y, w, h) -> v(y, h) > 0.88 && f != Face.TOP ? cloth(cl, f, x, y, w, h, set.hashCode() + 2) : 0;
            pants.leg = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.78) return 0;
                if (row(vv, 0.775, h)) return ink(cl);                                              // tucked into the boots
                double fold = Math.min(Painted.one(x, y, w, h, 0.3, 0.04, 0.37, 0.4), Painted.one(x, y, w, h, 0.72, 0.12, 0.64, 0.38));
                fold = Math.min(fold, Painted.one(x, y, w, h, 0.2, 0.5, 0.5, 0.55, 0.8, 0.5));
                fold = Math.min(fold, Painted.one(x, y, w, h, 0.15, 0.68, 0.5, 0.72, 0.85, 0.67));
                if (fold < Painted.LINE) return HdRaces.tone(cl, Painted.SHADE);
                return flat(cl, f, (u(x, w) < 0.1 || u(x, w) > 0.9) ? -0.1 : 0);
            };
            pants.save("entity/gear/" + set + "_pants.png");

            Hd.HdSkin boots = new Hd.HdSkin();
            boots.leg = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.BOTTOM) return ink(bt);
                if (vv < 0.72) return 0;
                if (row(vv, 0.725, h) || vv > 0.955) return ink(bt);                               // the cuff's edge, the sole
                if (vv < 0.78) return flat(sa, f, 0.02);                                           // a coloured cuff
                if (row(vv, 0.82, h) || row(vv, 0.87, h)) return HdRaces.tone(bt, Painted.SHADE);   // wraps
                return flat(bt, f, (u(x, w) < 0.12 || u(x, w) > 0.88) ? -0.1 : 0);
            };
            boots.save("entity/gear/" + set + "_boots.png");
        }

        static void battleArmor() throws IOException {
            battleArmor("battle_armor", 0xFFECEEF2, 0xFFD8B040, 0xFF2A2E48);
            battleArmor("frost_armor", 0xFFF2F0F6, 0xFF8A4AC8, 0xFF1C1A24);                    // Frost Demon armour: white plate, purple trim
        }

        static void battleArmor(String name, int plateC, int trimC, int suitC) throws IOException {
            int[] plate = ramp(plateC, 6), gold = ramp(trimC, 5), suit = ramp(suitC, 6);
            Hd.HdSkin top = new Hd.HdSkin();
            top.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM) return 0;
                if (f == Face.TOP) return flat(gold, f, 0);                                         // the shoulder straps
                if (vv < 0.72) {
                    if (f == Face.FRONT || f == Face.BACK) {
                        if (vv < 0.08 && (uu < 0.3 || uu > 0.7)) return row(vv, 0.075, h) ? ink(gold) : flat(gold, f, 0);
                        if (f == Face.FRONT && vv > 0.42 && (row(vv, 0.5, h) || row(vv, 0.61, h)) && uu > 0.12 && uu < 0.88) return ink(plate);
                    }
                    return HdRaces.paintedPlate(plate, f, uu, vv, 0, 0, 1, 0.72);
                }
                if (row(vv, 0.735, h)) return ink(suit);
                return cloth(suit, f, x, y, w, h, 301);                                            // the bodysuit below
            };
            top.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv >= 0.82) return row(vv, 0.825, h) ? ink(plate) : flat(plate, f, 0);         // gloves
                return cloth(suit, f, x, y, w, h, 302);
            };
            top.save("entity/gear/" + name + "_top.png");

            Hd.HdSkin pants = new Hd.HdSkin();
            pants.body = (f, x, y, w, h) -> v(y, h) > 0.88 && f != Face.TOP ? cloth(suit, f, x, y, w, h, 303) : 0;
            pants.leg = (f, x, y, w, h) -> v(y, h) > 0.72 || f == Face.BOTTOM ? 0 : cloth(suit, f, x, y, w, h, 304);
            pants.save("entity/gear/" + name + "_pants.png");

            Hd.HdSkin boots = new Hd.HdSkin();
            boots.leg = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.BOTTOM) return ink(plate);
                if (vv < 0.7) return 0;
                if (row(vv, 0.705, h) || vv > 0.955) return ink(plate);
                if (vv > 0.86 && f == Face.FRONT) return row(vv, 0.865, h) ? ink(gold) : flat(gold, f, 0);   // toe caps
                return flat(plate, f, (u(x, w) < 0.12 || u(x, w) > 0.88) ? -0.1 : 0);
            };
            boots.save("entity/gear/" + name + "_boots.png");
        }

        static void majin() throws IOException {
            int[] vest = ramp(0xFF26222E, 6), gold = ramp(0xFFE0B040, 5), white = ramp(0xFFF2F0F4, 6), shoe = ramp(0xFFB8862A, 5);
            Hd.HdSkin top = new Hd.HdSkin();
            top.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.7) return 0;
                if (row(vv, 0.69, h)) return ink(gold);
                if (vv > 0.655) return flat(gold, f, 0);                                            // the hem's trim
                if (f == Face.FRONT) {
                    double d = Math.abs(uu - 0.5);
                    if (d < 0.3) return 0;                                                           // open over the belly
                    if (d < 0.345) return d < 0.31 ? ink(gold) : flat(gold, f, 0);
                }
                return cloth(vest, f, x, y, w, h, 401);
            };
            top.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv < 0.8) return 0;
                return row(vv, 0.805, h) ? ink(white) : flat(white, f, 0);                         // gloves
            };
            top.save("entity/gear/majin_top.png");

            Hd.HdSkin pants = new Hd.HdSkin();
            pants.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.TOP || vv < 0.72) return 0;
                if (vv < 0.86) {                                                                     // the belt, an original emblem on the buckle
                    if (row(vv, 0.725, h) || row(vv, 0.855, h)) return ink(vest);
                    if (f == Face.FRONT && Math.abs(uu - 0.5) < 0.16) {
                        double dx = (uu - 0.5) / 0.16, dy = (vv - 0.79) / 0.06;
                        if (Math.abs(dx) > 0.86 || Math.abs(dy) > 0.82) return ink(gold);
                        boolean star = Math.abs(dx) + Math.abs(dy) * 0.9 < 0.62 && !(Math.abs(dx) < 0.18 && Math.abs(dy) < 0.22);
                        return star ? ink(vest) : flat(gold, f, 0.05);
                    }
                    return flat(vest, f, 0);
                }
                return cloth(white, f, x, y, w, h, 402);
            };
            pants.leg = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.8) return 0;
                if (vv > 0.74) return row(vv, 0.745, h) ? ink(vest) : flat(vest, f, 0);            // ankle cuffs
                return cloth(white, f, x, y, w, h, 403);
            };
            pants.save("entity/gear/majin_pants.png");

            Hd.HdSkin boots = new Hd.HdSkin();
            boots.leg = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.BOTTOM) return ink(shoe);
                if (vv < 0.8) return 0;
                if (vv > 0.955) return ink(shoe);
                if (f == Face.FRONT && vv > 0.88) return flat(gold, f, 0.04);                       // curled gold toes
                return flat(shoe, f, (u(x, w) < 0.12 || u(x, w) > 0.88) ? -0.1 : 0);
            };
            boots.save("entity/gear/majin_boots.png");
        }



        /**
         * The fusion outfit (12c, worn by a fused warrior from the dance): an open teal vest with lighter edges over the
         * bare chest, a wide blue sash, black wristbands, white baggy trousers gathered at black ankle cuffs, black boots.
         * The quilted gold shoulder pads are 3D (GearModel).
         */
        static void fusion() throws IOException {
            int[] vest = ramp(0xFF1F6F80, 6), edge = ramp(0xFF58B8C4, 5), sash = ramp(0xFF3A62C8, 5), white = ramp(0xFFF2F0F4, 6),
                    black = ramp(0xFF24242C, 5);
            Hd.HdSkin top = new Hd.HdSkin();
            top.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.9) return 0;
                if (vv >= 0.72) {                                                                   // the wide sash
                    if (row(vv, 0.725, h) || row(vv, 0.895, h)) return ink(sash);
                    if (row(vv, 0.81, h)) return HdRaces.tone(sash, Painted.SHADE);
                    return flat(sash, f, 0);
                }
                if (f == Face.FRONT) {
                    double d = Math.abs(uu - 0.5), open = 0.2 + vv * 0.08;
                    if (d < open) return 0;                                                          // open over the chest
                    if (d < open + 0.05) return d < open + 0.012 ? ink(edge) : flat(edge, f, 0.02);   // the lighter edge
                }
                if (f == Face.TOP) return flat(vest, f, 0);
                return cloth(vest, f, x, y, w, h, 601);
            };
            top.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv >= 0.74 && vv < 0.9 && f != Face.BOTTOM) {                                  // black wristbands
                    if (row(vv, 0.745, h) || row(vv, 0.895, h)) return ink(black);
                    return flat(black, f, row(vv, 0.82, h) ? 0.1 : 0);
                }
                return 0;
            };
            top.save("entity/gear/fusion_top.png");

            Hd.HdSkin pants = new Hd.HdSkin();
            pants.body = (f, x, y, w, h) -> v(y, h) > 0.88 && f != Face.TOP ? cloth(white, f, x, y, w, h, 602) : 0;
            pants.leg = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.8) return 0;
                if (vv > 0.72) return row(vv, 0.725, h) ? ink(black) : flat(black, f, x % 2 == 0 ? -0.05 : 0.02);   // ankle cuffs
                return cloth(white, f, x, y, w, h, 603);
            };
            pants.save("entity/gear/fusion_pants.png");

            Hd.HdSkin boots = new Hd.HdSkin();
            boots.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM) return ink(black);
                if (vv < 0.8) return 0;
                if (vv > 0.955) return ink(black);
                if (f == Face.FRONT && vv > 0.87 && Math.abs(uu - 0.5) < 0.3) return flat(edge, f, -0.05);   // a teal toe cap
                return flat(black, f, (uu < 0.12 || uu > 0.88) ? -0.1 : 0);
            };
            boots.save("entity/gear/fusion_boots.png");
        }

        /** A zip hoodie (drawstrings, a kangaroo pocket, ribbed hem and cuffs), joggers with a side stripe, white sneakers. */
        static void hoodie() throws IOException {
            int[] red = ramp(0xFFC8283A, 6), white = ramp(0xFFF2F0F4, 5), black = ramp(0xFF24242C, 6), grey = ramp(0xFF9A9AA4, 4);
            Hd.HdSkin top = new Hd.HdSkin();
            top.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.92) return 0;
                if (vv > 0.86) return row(vv, 0.865, h) || x % 2 == 0 ? HdRaces.tone(red, Painted.MID) : flat(red, f, -0.04);   // ribbed hem
                if (f == Face.FRONT) {
                    if (Math.abs(uu - 0.5) < 0.035) return ink(red);                                 // the zip
                    if ((Math.abs(uu - 0.4) < 0.03 || Math.abs(uu - 0.6) < 0.03) && vv > 0.04 && vv < 0.34) return flat(white, f, 0);   // drawstrings
                    if (vv > 0.55 && vv < 0.8 && Math.abs(uu - 0.5) < 0.3) {                          // the pocket
                        if (row(vv, 0.555, h) || Math.abs(Math.abs(uu - 0.5) - 0.3) * w < 0.7) return ink(red);
                    }
                }
                return cloth(red, f, x, y, w, h, 501);
            };
            top.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv > 0.86) return 0;
                if (vv > 0.79) return row(vv, 0.795, h) ? ink(red) : flat(red, f, x % 2 == 0 ? -0.08 : 0);   // cuffs
                return cloth(red, f, x, y, w, h, 502);
            };
            top.save("entity/gear/hoodie_top.png");

            Hd.HdSkin pants = new Hd.HdSkin();
            pants.body = (f, x, y, w, h) -> v(y, h) > 0.88 && f != Face.TOP ? cloth(black, f, x, y, w, h, 503) : 0;
            pants.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM || vv > 0.8) return 0;
                if (vv > 0.72) return row(vv, 0.725, h) ? ink(black) : flat(black, f, x % 2 == 0 ? -0.06 : 0.02);   // ankle cuffs
                if ((f == Face.LEFT || f == Face.RIGHT) && Math.abs(uu - 0.5) < 0.12) return flat(white, f, 0);   // the side stripe
                return cloth(black, f, x, y, w, h, 504);
            };
            pants.save("entity/gear/hoodie_pants.png");

            Hd.HdSkin boots = new Hd.HdSkin();
            boots.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.BOTTOM) return flat(grey, f, 0);
                if (vv < 0.8) return 0;
                if (vv > 0.93) return row(vv, 0.935, h) ? ink(grey) : flat(grey, f, 0.04);         // the sole
                if (f == Face.BACK && vv < 0.88) return flat(red, f, 0);                             // a red heel tab
                if (f == Face.FRONT && vv > 0.84 && vv < 0.91 && Math.abs(uu - 0.5) < 0.22 && y % 2 == 0) return ink(white);   // laces
                return flat(white, f, (uu < 0.12 || uu > 0.88) ? -0.08 : 0);
            };
            boots.save("entity/gear/hoodie_boots.png");
        }
        /** Greyscale materials for the 3D pieces: cloth with folds (0,0), plate (64,0), band (64,32); 128x128. */
        static void parts() throws IOException {
            Canvas c = new Canvas(128, 128);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                double l = 0.94 - 0.1 * y / 64.0;
                double n = Hd.smooth(x * 1.4, y * 0.3, 5, 501);
                if (Math.abs(n - 0.5) < 0.025) l = 0.74;                                          // fold lines
                else if (n > 0.5 && n < 0.56) l -= 0.06;
                c.set(x, y, Hd.lum(l));
            }
            for (int y = 0; y < 32; y++) for (int x = 64; x < 128; x++) {
                double l = y % 18 < 2 ? 1.0 : y % 18 > 13 ? 0.78 : 0.92;                          // plate: lit rim, body, shaded edge
                c.set(x, y, Hd.lum(l));
            }
            for (int y = 32; y < 64; y++) for (int x = 64; x < 128; x++) {
                c.set(x, y, Hd.lum((y - 32) % 6 == 5 ? 0.72 : 0.92));                            // band: stitched rows
            }
            c.save("entity/gear_parts.png");
        }
    }
    // ================================================================== race parts (CX-14d)

    /**
     * Greyscale materials for the 3D race parts (client.render.RaceFeatureModel), tinted at render time, painted in
     * flat cel bands: skin (antennae, ears, the Majin tentacle), ridged bone (horns), veined carapace (wings, Frost
     * Demon ear plates) and streaked fur (the Saiyan tail). 128x128 for a 64x64-unit model sheet.
     */
    static final class RaceParts {
        static void all() throws IOException {
            Canvas c = new Canvas(128, 128);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                double l = y < 9 ? 1.0 : y < 19 ? 0.9 : 0.8;                                    // skin: light from above, in bands
                c.set(x, y, Hd.lum(l));
                double b = (y % 5 == 4) ? 0.7 : y % 5 == 0 ? 1.0 : 0.9;                          // bone: ridged rings
                c.set(32 + x, y, Hd.lum(b - (y > 22 ? 0.06 : 0)));
                double s = Math.abs(Math.sin(x * 0.55 + y * 0.28)) < 0.13 || Math.abs(Math.sin(x * 0.2 - y * 0.6)) < 0.08 ? 0.66 : 0.92;
                c.set(64 + x, y, Hd.lum(s - (y > 24 ? 0.08 : 0)));                               // carapace: veins
            }
            for (int y = 32; y < 64; y++) for (int x = 0; x < 72; x++) {                            // fur: strands along the tail
                double strand = noise(x / 2, 0, 911);
                double l = 0.8 + 0.16 * strand - 0.12 * ((y - 32) / 32.0);
                if ((x + (y - 32) / 7) % 4 == 0) l = 0.64;                                        // the gaps between tufts
                if (noise(x, y / 3, 913) > 0.92) l = 1.0;                                         // stray light hairs
                c.set(x, y, Hd.lum(l));
            }
            for (int y = 64; y < 96; y++) for (int x = 0; x < 128; x++) {                          // a wide carapace row for big shells
                double s = Math.abs(Math.sin(x * 0.4 + y * 0.2)) < 0.1 ? 0.7 : (y - 64) % 16 < 2 ? 1.0 : 0.9;
                c.set(x, y, Hd.lum(s));
            }
            c.save("entity/race_parts.png");
        }
    }
    // ================================================================== painted art (CX-14a)

    /**
     * The painted style (the user's chosen direction): anime-styled skins at 128x128 with clean line art for the
     * anatomy in a dark shade of the skin, over three or four flat cel tones (light from the top, shade at the
     * edges and under each muscle). Lines are polylines in face coordinates, drawn one pixel wide; symmetric ones are
     * given for the left half and mirrored. Luminance uses the same range as the HD anatomy (ink 0.5 ... light
     * 1.0), so bodies are tinted by skin tone and race skins colour it through their ramps.
     */
    static final class Painted {
        static final double HI = 1.0, BASE = 0.94, MID = 0.82, SHADE = 0.72, INK = 0.5;
        static final double LINE = 0.62;

        static void all() throws IOException {
            anatomy(0.6).save("entity/body_painted/lean.png");
            anatomy(1.0).save("entity/body_painted/athletic.png");
            anatomy(1.45).save("entity/body_painted/bulky.png");
            outfit();
        }

        static double face(Face f) {
            return switch (f) { case FRONT -> 0; case TOP -> 0.03; case BACK -> -0.05; case BOTTOM -> -0.12; default -> -0.03; };
        }

        /** Pixel distance from a point to a polyline given in face coordinates (u0, v0, u1, v1, ...). */
        static double dist(double px, double py, int w, int h, double[] p) {
            double best = 1e9;
            for (int i = 0; i + 3 < p.length; i += 2) {
                double ax = p[i] * w, ay = p[i + 1] * h, bx = p[i + 2] * w, by = p[i + 3] * h;
                double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
                best = Math.min(best, Math.hypot(ax + t * dx - px, ay + t * dy - py));
            }
            return best;
        }

        private static final java.util.Map<String, boolean[][]> STROKES = new java.util.HashMap<>();

        /**
         * A clean one-pixel line along a polyline in face coordinates: sampled finely, then thinned "pixel-perfect"
         * (a pixel that only turns a corner between two diagonal neighbours is dropped), like a hand-drawn stroke.
         */
        static boolean[][] raster(int w, int h, double[] p) {
            return STROKES.computeIfAbsent(w + "x" + h + java.util.Arrays.toString(p), k -> {
                java.util.List<int[]> out = new java.util.ArrayList<>();
                for (int i = 0; i + 3 < p.length; i += 2) {
                    double ax = p[i] * w, ay = p[i + 1] * h, bx = p[i + 2] * w, by = p[i + 3] * h;
                    int steps = (int) Math.ceil(Math.hypot(bx - ax, by - ay) * 4) + 1;
                    for (int s = 0; s <= steps; s++) {
                        double t = (double) s / steps;
                        int x = Math.max(0, Math.min(w - 1, (int) Math.floor(ax + (bx - ax) * t)));
                        int y = Math.max(0, Math.min(h - 1, (int) Math.floor(ay + (by - ay) * t)));
                        int[] last = out.isEmpty() ? null : out.get(out.size() - 1);
                        if (last != null && last[0] == x && last[1] == y) continue;
                        if (out.size() >= 2) {
                            int[] a = out.get(out.size() - 2);
                            if (Math.abs(a[0] - x) == 1 && Math.abs(a[1] - y) == 1) out.remove(out.size() - 1);
                        }
                        out.add(new int[]{x, y});
                    }
                }
                boolean[][] m = new boolean[h][w];
                for (int[] q : out) m[q[1]][q[0]] = true;
                return m;
            });
        }

        /** 0 on the stroke, 9 off it (compared against LINE). */
        static double one(int x, int y, int w, int h, double... p) {
            return raster(w, h, p)[y][x] ? 0 : 9;
        }

        /** A stroke and its mirror image across the middle of the face. */
        static double sym(int x, int y, int w, int h, double... p) {
            boolean[][] m = raster(w, h, p);
            return m[y][x] || m[y][w - 1 - x] ? 0 : 9;
        }

        static boolean ell(double u, double v, double cu, double cv, double ru, double rv) {
            double a = (u - cu) / ru, b = (v - cv) / rv;
            return a * a + b * b < 1;
        }

        /** A piecewise-linear profile through (m0, v0, m1, v1, ...), m ascending. */
        static double curve(double m, double... p) {
            if (m <= p[0]) return p[1];
            for (int i = 0; i + 3 < p.length; i += 2) if (m <= p[i + 2]) return p[i + 1] + (p[i + 3] - p[i + 1]) * (m - p[i]) / (p[i + 2] - p[i]);
            return p[p.length - 1];
        }

        static double u(int x, int w) { return (x + 0.5) / w; }
        static double v(int y, int h) { return (y + 0.5) / h; }

        /** Painted anatomy for a muscle definition k (lean 0.6, athletic 1.0, bulky 1.45): luminance per pixel. */
        static Hd.HdSkin anatomy(double k) {
            double ink = k >= 0.8 ? INK : (INK + MID) / 2, minor = k >= 1.2 ? INK + 0.08 : MID - 0.05;
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h), l = BASE + face(f);
                if (f == Face.BOTTOM) return Hd.lum(SHADE);
                if (f == Face.FRONT) {
                    if (uu < 0.06 || uu > 0.94 || vv > 0.94) l = MID;                                   // the jaw turning away
                }
                if (f == Face.LEFT || f == Face.RIGHT) {
                    if (uu < 0.07 || uu > 0.93) l = MID;
                    if (ell(uu, vv, 0.53, 0.57, 0.08, 0.09)) l = MID;                                    // inside the ear
                    if (one(x, y, w, h, 0.42, 0.44, 0.58, 0.42, 0.67, 0.54, 0.61, 0.69, 0.46, 0.71) < LINE) l = ink;
                }
                return Hd.lum(l);
            };
            s.body = (f, x, y, w, h) -> switch (f) {
                case FRONT -> mapped(CHEST, f, x, y, w, h, k);
                case BACK -> mapped(BACK, f, x, y, w, h, k);
                case TOP -> Hd.lum(BASE + face(f));
                case BOTTOM -> Hd.lum(MID);
                default -> mapped(FLANK, f, x, y, w, h, k);
            };
            s.arm = (f, x, y, w, h) -> switch (f) {
                case FRONT -> mapped(ARM_FRONT, f, x, y, w, h, k);
                case BACK -> mapped(ARM_BACK, f, x, y, w, h, k);
                case TOP -> Hd.lum(BASE + face(f));
                case BOTTOM -> Hd.lum(MID);
                default -> mapped(ARM_SIDE, f, x, y, w, h, k);
            };
            s.leg = (f, x, y, w, h) -> switch (f) {
                case FRONT -> mapped(LEG_FRONT, f, x, y, w, h, k);
                case BACK -> mapped(LEG_BACK, f, x, y, w, h, k);
                case TOP -> Hd.lum(BASE + face(f));
                case BOTTOM -> Hd.lum(SHADE);
                default -> mapped(LEG_SIDE, f, x, y, w, h, k);
            };
            return s;
        }

        // ---------------------------------------------------------- hand-drawn muscle maps (left half of each face, mirrored)
        // . base  h light  m mid tone  s shade  l soft line  k ink line

        static final String[] CHEST = {
                "m.......",
                "m..llll.",
                "m.hhh..m",
                "m.hhhh.m",
                "m..hhh.m",
                "k......m",
                "mk.....k",
                "mmk...ks",
                "m.skkks.",
                "m..sss.m",
                "ml.lhh.m",
                "m.ll...m",
                "m..llllm",
                "ml.lhh.m",
                "m.ll...m",
                "m..llllm",
                "m..lhh.m",
                "m..l...m",
                "m..llllm",
                "m..l...k",
                "m.l....m",
                "m..l....",
                "m...l...",
                "m....l.."};
        static final String[] BACK = {
                "m.......",
                "m...lll.",
                "m.ll...m",
                "ml.hhh.m",
                "m.hhhhkm",
                "m.hhh.km",
                "m.k..k.m",
                "m..kk..m",
                "m......m",
                "ml.....m",
                "m.l....m",
                "m..l...m",
                "m..l...m",
                "m...l..m",
                "m...l..m",
                "m....l.m",
                "m......m",
                "m......m",
                "m......m",
                "m....l.m",
                "m......m",
                "m......m",
                "m......m",
                "m......m"};
        static final String[] FLANK = {
                "m...", "m...", "m...", "m.h.", "m.hh", "m.hh", "m.h.", "m...", "m...", "m...", "ml..", "m.l.",
                "ml..", "m.l.", "m...", "m...", "m...", "m...", "m...", "m...", "m...", "m...", "m...", "m..."};
        static final String[] ARM_FRONT = {
                "m...", "m.hh", "m.hh", "m...", "k...", "mkk.", "m..k", "m...", "m.hh", "m.hh", "m.h.", "m...",
                "l...", "mlll", "m...", "m.h.", "m.h.", "m.h.", "m...", "m...", "m...", "mmmm", "m.m.", "m.m."};
        static final String[] ARM_BACK = {
                "m...", "m.hh", "m.hh", "m...", "k...", "mkk.", "m..k", "m...", "ml..", "ml.h", "ml.h", "ml..",
                "m.l.", "m..l", "m...", "m.mm", "m...", "m.h.", "m.h.", "m...", "m...", "mmmm", "m...", "m..."};
        static final String[] ARM_SIDE = {
                "m...", "m.hh", "m.hh", "m...", "k...", "mkk.", "m..k", "m...", "m.h.", "m.h.", "m...", "m...",
                "m...", "m...", "m.l.", "m...", "m.h.", "m.h.", "m...", "m...", "m...", "mmmm", "m...", "m..."};
        static final String[] LEG_FRONT = {
                "m...", "m.hh", "mhhh", "mhh.", "m.h.", "m...", "ml..", "m.l.", "m..l", "m...", "m...", "m.ll",
                "ml..", "m.ll", "m...", "m.h.", "m.h.", "m.h.", "m.h.", "m...", "m...", "mmmm", "m.m.", "m.m."};
        static final String[] LEG_BACK = {
                "m...", "m..m", "m..m", "m..m", "m..m", "m..m", "m..m", "m..m", "m..m", "m...", "m...", "mlll",
                "m...", "m.hh", "m.hh", "m.hh", "m.h.", "ml..", "m.l.", "m..l", "m...", "mmmm", "m...", "m..."};
        static final String[] LEG_SIDE = {
                "m...", "m.h.", "m.h.", "m.h.", "m...", "m...", "m...", "m...", "m...", "m...", "m...", "m.l.",
                "m...", "m...", "m.h.", "m.h.", "m...", "m...", "m...", "m...", "m...", "mmmm", "m...", "m..."};

        /** The map's symbol at a pixel: the left half as drawn, the right half mirrored, scaled to the face. */
        static char at(String[] map, int x, int y, int w, int h) {
            int half = map[0].length(), col = x < w / 2 ? x : w - 1 - x;
            col = Math.min(half - 1, col * half * 2 / w);
            int row = Math.min(map.length - 1, y * map.length / h);
            return map[row].charAt(col);
        }

        /** A symbol's luminance for a build: lean builds soften every line, bulky ones darken them. */
        static double lumFor(char c, double k) {
            double ink = k >= 0.8 ? INK : MID - 0.06, line = k >= 1.2 ? INK + 0.08 : k >= 0.8 ? MID - 0.08 : MID;
            return switch (c) {
                case 'k' -> ink;
                case 'l' -> line;
                case 'm' -> MID;
                case 's' -> k >= 0.8 ? SHADE : MID;
                case 'h' -> HI;
                default -> BASE;
            };
        }

        static int mapped(String[] map, Face f, int x, int y, int w, int h, double k) {
            return Hd.lum(Math.min(lumFor(at(map, x, y, w, h), k), lumFor(at(map, x, y, w, h), k) + face(f)));
        }
        // ---------------------------------------------------------- the outfit (untinted): gi trousers, sash, wristbands, boots

        static int at(int[] r, int i) {
            return r[Math.max(0, Math.min(r.length - 1, i))];
        }

        static void outfit() throws IOException {
            int[] shorts = ramp(0xFF26346E, 6), band = ramp(0xFF181C2C, 5), stripe = ramp(0xFFE8E4DC, 4);
            Hd.HdSkin s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> 0;
            s.body = (f, x, y, w, h) -> {                                                              // CX-16a: training shorts, nothing else
                double uu = u(x, w), vv = v(y, h);
                if (vv < 0.86 || f == Face.TOP) return 0;
                if (vv < 0.925) {                                                                         // the waistband and its drawstring
                    if (Math.abs(vv - 0.865) * h < 0.7) return band[0];
                    if (f == Face.FRONT && vv > 0.88 && (Math.abs(uu - 0.46) < 0.02 || Math.abs(uu - 0.54) < 0.02)) return stripe[3];
                    return at(band, f == Face.FRONT ? 3 : 2);
                }
                return at(shorts, f == Face.FRONT ? 4 : f == Face.BACK ? 2 : 3);
            };
            s.arm = (f, x, y, w, h) -> 0;
            s.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.42 || f == Face.BOTTOM) return 0;                                              // to mid-thigh, then bare legs
                if (f == Face.TOP) return at(shorts, 2);
                if (Math.abs(vv - 0.41) * h < 0.8) return shorts[0];                                      // the hem
                if ((f == Face.LEFT || f == Face.RIGHT) && Math.abs(uu - 0.5) < 0.1) return at(stripe, 2); // a side stripe
                int i = f == Face.FRONT ? 4 : f == Face.BACK ? 2 : 3;
                if (uu < 0.1 || uu > 0.9) i--;
                if (Math.abs(vv - 0.08 - uu * 0.12) * h < 0.5 && f == Face.FRONT) i -= 2;                 // one fold across the front
                return at(shorts, i);
            };
            s.save("entity/body_painted/outfit.png");
        }

        /** Gi trousers: flat tones with a few clean fold lines, the knee crease and the bunching over the boots. */
        static int trousers(int[] p, Face f, int x, int y, int w, int h, boolean waist) {
            if (f == Face.TOP) return 0;
            double uu = u(x, w);
            int i = f == Face.FRONT ? 4 : f == Face.BACK ? 2 : 3;
            if (uu < 0.1 || uu > 0.9) i--;
            if (waist) return at(p, i);
            double d = Math.min(one(x, y, w, h, 0.3, 0.04, 0.37, 0.4), one(x, y, w, h, 0.72, 0.12, 0.64, 0.38));
            d = Math.min(d, one(x, y, w, h, 0.2, 0.5, 0.5, 0.55, 0.8, 0.5));
            d = Math.min(d, Math.min(one(x, y, w, h, 0.12, 0.64, 0.5, 0.68, 0.88, 0.63), one(x, y, w, h, 0.2, 0.71, 0.55, 0.69, 0.8, 0.72)));
            if (d < LINE) return at(p, i - 2);
            return at(p, i);
        }
    }
    // ================================================================== HD race skins (CX-13b)

    /**
     * Every race skin at 128x128, built from a small material kit on top of the HD anatomy: skin shaded through the
     * race's colour ramp, cloth with folds and seams, glossy bevelled shell plates, spots, stripes, cracks and ribbing.
     * Faces are left plain (FaceLayer draws them); the designs follow the classic skins with far more detail.
     */
    static final class HdRaces {
        /** Painted mode (CX-14a): the same designs in clean line art and flat cel tones, written to race_painted/. */
        static boolean painted;

        static String dir() {
            return painted ? "race_painted" : "race_hd";
        }

        static Hd.HdSkin anat(double k, int seed) {
            return painted ? Painted.anatomy(k) : Hd.anatomy(k, seed);
        }

        static void all() throws IOException {
            painted = false;
            designs();
            painted = true;
            designs();
            painted = false;
        }

        static void designs() throws IOException {
            namekian("namekian", 0xFF62B444, 0xFFE09A9A, 0xFF6A3A9A, 0xFF40B0E0, 0xFFE8E4F0, 0xFF5A3418, 1.0);
            namekian("demon_namekian", 0xFF3A8A6A, 0xFFB070A0, 0xFF1A1420, 0xFFC01830, 0xFF3A2A44, 0xFF2A1A10, 1.1);
            frostDemon("frost_demon", 0xFFF0EEF4, 0xFF8A4AC8, false);
            frostDemon("metal_frost_demon", 0xFFD8E2EC, 0xFF6A7A90, true);
            frostDemon("mutant_frost_demon", 0xFF2A2230, 0xFFE84AB0, false);
            majin("majin", 0xFFF59AC0, 0xFF2A2234, 0xFFF2F0F4, 0xFFE8C040, 0.35, false);
            majin("corrupted_majin", 0xFF9A90A8, 0xFF3A1A4A, 0xFF4A4458, 0xFFB070FF, 0.8, true);
            vampire();
            bioAndroid();
            tuffle();
            genAlien();
            kai();
            coreDemon();
        }

        // ---------------------------------------------------------- the material kit

        static double u(int x, int w) { return (x + 0.5) / w; }
        static double v(int y, int h) { return (y + 0.5) / h; }

        /** A ramp colour for a luminance (about 0.6 dark to 1.08 bright), smoothly interpolated. */
        static int tone(int[] r, double l) {
            double t = Math.max(0, Math.min(1, (l - 0.6) / 0.48)) * (r.length - 1);
            int i = (int) Math.floor(t);
            return i >= r.length - 1 ? r[r.length - 1] : mix(r[i], r[i + 1], t - i);
        }

        static double lumOf(FaceFn fn, Face f, int x, int y, int w, int h) {
            return (fn.at(f, x, y, w, h) & 255) / 255.0;
        }

        /** Cloth: face light, long soft folds, fine weave noise, darker at the creases. */
        static int cloth(int[] r, Face f, int x, int y, int w, int h, int seed, double folds) {
            if (painted) return paintedCloth(r, f, x, y, w, h, seed, folds);
            double uu = u(x, w), vv = v(y, h);
            double l = Skins.faceLight(f) + folds * 0.035 * Math.sin(uu * 7 + Hd.smooth(x, y, 6, seed) * 5)
                    + 0.05 * (Hd.smooth(x, y, 2.2, seed + 1) - 0.5) + Hd.edgeAo(uu, vv, 0.07);
            return tone(r, l);
        }

        /** A shell plate between (u0,v0) and (u1,v1): bevelled (bright top edge, dark lower edge) with a gloss spot. */
        static int plate(int[] r, Face f, double uu, double vv, double u0, double v0, double u1, double v1) {
            if (painted) return paintedPlate(r, f, uu, vv, u0, v0, u1, v1);
            double top = (vv - v0) / (v1 - v0), side = Math.min(uu - u0, u1 - uu) / (u1 - u0);
            double l = Skins.faceLight(f) + 0.04;
            if (top < 0.08) l += 0.12;
            else if (top > 0.9) l -= 0.14;
            if (side < 0.05) l -= 0.06;
            l += 0.18 * Hd.g(uu, vv, u0 + 0.3 * (u1 - u0), v0 + 0.25 * (v1 - v0), 0.1 * (u1 - u0) + 0.02, 0.08 * (v1 - v0) + 0.02);
            return tone(r, l);
        }

        static boolean in(double uu, double vv, double u0, double v0, double u1, double v1) {
            return uu >= u0 && uu <= u1 && vv >= v0 && vv <= v1;
        }

        /** Painted cloth: flat tones by face, a few clean fold lines along long contours, shade at the edges. */
        static int paintedCloth(int[] r, Face f, int x, int y, int w, int h, int seed, double folds) {
            double uu = u(x, w);
            double l = Painted.BASE + Painted.face(f);
            if (f == Face.BOTTOM) l = Painted.MID;
            if (uu < 0.08 || uu > 0.92) l -= 0.1;
            double n = Hd.smooth(x * 1.6, y * 0.45, 5, seed);                                         // long, mostly vertical folds
            if (folds > 0 && Math.abs(n - 0.5) < 0.022 * folds) l -= 0.15;
            else if (folds > 0 && n > 0.5 && n < 0.5 + 0.06 * folds) l -= 0.06;                       // soft shade beside each fold
            return tone(r, l);
        }

        /** A painted shell plate: outlined, a shaded lower part and a hard gloss highlight. */
        static int paintedPlate(int[] r, Face f, double uu, double vv, double u0, double v0, double u1, double v1) {
            double top = (vv - v0) / (v1 - v0), side = Math.min(uu - u0, u1 - uu) / (u1 - u0);
            double l = Painted.BASE + Painted.face(f) + 0.02;
            if (top < 0.05 || top > 0.95 || side < 0.035) l = Painted.INK;
            else if (top > 0.75) l = Painted.MID;
            else if (Painted.ell(uu, vv, u0 + 0.3 * (u1 - u0), v0 + 0.25 * (v1 - v0), 0.12 * (u1 - u0) + 0.02, 0.1 * (v1 - v0) + 0.02)) l = 1.08;
            return tone(r, l);
        }

        static int skin(int[] r, FaceFn anat, Face f, int x, int y, int w, int h) {
            return tone(r, lumOf(anat, f, x, y, w, h));
        }


        // ---------------------------------------------------------- bare to the waist (CX-16a)

        static final int[] SHORTS = ramp(0xFF26346E, 6), SHORTS_BAND = ramp(0xFF181C2C, 5);

        /** The training shorts everyone starts in, on the body (the waistband and seat). 0 above them. */
        static int shortsBody(Face f, int x, int y, int w, int h) {
            double vv = v(y, h);
            if (vv < 0.86 || f == Face.TOP) return 0;
            if (vv < 0.925) return tone(SHORTS_BAND, (painted ? Painted.BASE + Painted.face(f) : Skins.faceLight(f)) + 0.02);
            return cloth(SHORTS, f, x, y, w, h, 301, 0.4);
        }

        /** The shorts on a leg, to mid-thigh; 0 below. */
        static int shortsLeg(Face f, int x, int y, int w, int h) {
            double uu = u(x, w), vv = v(y, h);
            if (vv > 0.42 || f == Face.BOTTOM || f == Face.TOP) return 0;
            if (Math.abs(vv - 0.41) * h < 0.8) return SHORTS[0];
            if ((f == Face.LEFT || f == Face.RIGHT) && Math.abs(uu - 0.5) < 0.1) return 0xFFE0DCD4;
            return cloth(SHORTS, f, x, y, w, h, 302, 0.4);
        }

        /**
         * A race as every new character starts out: bare skin (with the race's own marks, given as overlays that return
         * 0 where there is none), barefoot, in training shorts. Replaces the body, arms and legs; the head is kept.
         */
        static void bare(Hd.HdSkin s, Hd.HdSkin a, int[] sk, FaceFn bodyMark, FaceFn armMark, FaceFn legMark) {
            s.body = (f, x, y, w, h) -> {
                int c = shortsBody(f, x, y, w, h);
                if (c != 0) return c;
                int m = bodyMark == null ? 0 : bodyMark.at(f, x, y, w, h);
                return m != 0 ? m : skin(sk, a.body, f, x, y, w, h);
            };
            s.arm = (f, x, y, w, h) -> {
                int m = armMark == null ? 0 : armMark.at(f, x, y, w, h);
                return m != 0 ? m : skin(sk, a.arm, f, x, y, w, h);
            };
            s.leg = (f, x, y, w, h) -> {
                int c = shortsLeg(f, x, y, w, h);
                if (c != 0) return c;
                int m = legMark == null ? 0 : legMark.at(f, x, y, w, h);
                return m != 0 ? m : skin(sk, a.leg, f, x, y, w, h);
            };
        }

        // ---------------------------------------------------------- races

        static void namekian(String name, int skinC, int pinkC, int giC, int sashC, int pantsC, int shoeC, double k) throws IOException {
            int[] sk = ramp(skinC, 6), pink = ramp(pinkC, 4), gi = ramp(giC, 6), sash = ramp(sashC, 4), pants = ramp(pantsC, 5), shoe = ramp(shoeC, 4);
            Hd.HdSkin a = anat(k, name.hashCode()), s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double l = lumOf(a.head, f, x, y, w, h), uu = u(x, w), vv = v(y, h);
                if (f == Face.TOP) l -= 0.1 * Math.exp(-Math.pow(((vv * 4) % 1) - 0.5, 2) / 0.012);     // the ridged crown
                if ((f == Face.RIGHT || f == Face.LEFT) && in(uu, vv, 0.25, 0.25, 0.55, 0.6)) l -= 0.06;   // ear hollows
                return tone(sk, l);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.86) return cloth(pants, f, x, y, w, h, 31, 1.2);
                if (vv > 0.73) {                                                                          // the sash and its knot
                    int c = cloth(sash, f, x, y, w, h, 32, 0.6);
                    return f == Face.FRONT && Math.abs(uu - 0.5) < 0.09 ? tone(sash, Skins.faceLight(f) + 0.1 - 0.15 * Math.abs(uu - 0.5) / 0.09) : c;
                }
                if (f == Face.FRONT) {
                    double neck = Math.abs(uu - 0.5) - (0.3 - vv) * 0.85;                                  // the V of the gi
                    if (neck < 0) return skin(sk, a.body, f, x, y, w, h);
                    if (neck < 0.03) return gi[1];
                }
                return cloth(gi, f, x, y, w, h, 33, 1.0);
            };
            s.arm = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv < 0.3) return vv > 0.27 ? gi[1] : cloth(gi, f, x, y, w, h, 34, 0.8);
                if ((f == Face.FRONT || f == Face.RIGHT) && vv > 0.42 && vv < 0.64) {                   // pink muscle bands, striated
                    if (painted) {                                                                       // outlined in deep red
                        if (Math.abs(vv - 0.425) * h < 0.7 || Math.abs(vv - 0.635) * h < 0.7) return 0xFF8A1E24;
                        return tone(pink, lumOf(a.arm, f, x, y, w, h));
                    }
                    double l = lumOf(a.arm, f, x, y, w, h) + 0.07 * Math.sin(vv * 90 + uu * 3);
                    return tone(pink, l);
                }
                return skin(sk, a.arm, f, x, y, w, h);
            };
            s.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.84 || f == Face.BOTTOM) return tone(shoe, Skins.faceLight(f) + (f == Face.FRONT ? 0.15 * Hd.g(uu, vv, 0.5, 0.95, 0.3, 0.05) : 0));
                return cloth(pants, f, x, y, w, h, 35, 1.3);
            };
            bare(s, a, sk, null, (f, x, y, w, h) -> {                                                 // CX-16a: bare but for the pink bands
                double uu = u(x, w), vv = v(y, h);
                if ((f == Face.FRONT || f == Face.RIGHT) && vv > 0.42 && vv < 0.64) {
                    if (painted) {
                        if (Math.abs(vv - 0.425) * h < 0.7 || Math.abs(vv - 0.635) * h < 0.7) return 0xFF8A1E24;
                        return tone(pink, lumOf(a.arm, f, x, y, w, h));
                    }
                    return tone(pink, lumOf(a.arm, f, x, y, w, h) + 0.07 * Math.sin(vv * 90 + uu * 3));
                }
                return 0;
            }, null);
            s.save("entity/" + dir() + "/" + name + ".png");
        }

        static void frostDemon(String name, int skinC, int shellC, boolean metal) throws IOException {
            int[] sk = ramp(skinC, 6), shell = ramp(shellC, 6);
            Hd.HdSkin a = anat(0.8, name.hashCode()), s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.TOP) return plate(shell, f, uu, vv, 0, 0, 1, 1);
                if (f == Face.FRONT) {
                    if (vv < 0.18) return plate(shell, f, uu, vv, 0, 0, 1, 0.18);                      // the dome
                    if ((uu < 0.1 || uu > 0.9) && vv > 0.3 && vv < 0.52) return plate(shell, f, uu, vv, uu < 0.1 ? 0 : 0.9, 0.3, uu < 0.1 ? 0.1 : 1, 0.52);
                } else if (f != Face.BOTTOM) {
                    double edge = f == Face.BACK ? 0.48 : 0.22;
                    if (vv < edge) return plate(shell, f, uu, vv, 0, 0, 1, edge);
                }
                return skin(sk, a.head, f, x, y, w, h);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (painted) {                                                                           // bare and lined, with one chest gem
                    if (f == Face.FRONT && Painted.ell(uu, vv, 0.5, 0.3, 0.2, 0.11)) {
                        if (!Painted.ell(uu, vv, 0.5, 0.3, 0.17, 0.085)) return tone(shell, Painted.INK);
                        return tone(shell, Painted.ell(uu, vv, 0.45, 0.26, 0.06, 0.035) ? 1.08 : vv > 0.33 ? Painted.MID : Painted.BASE);
                    }
                    return skin(sk, a.body, f, x, y, w, h);
                }
                if (f == Face.FRONT && in(uu, vv, 0.08, 0.05, 0.92, 0.58)) {
                    if (Math.abs(uu - 0.5) < 0.012) return shell[0];                                  // the seam down the chest
                    return plate(shell, f, uu, vv, uu < 0.5 ? 0.08 : 0.5, 0.05, uu < 0.5 ? 0.5 : 0.92, 0.58);
                }
                if (f == Face.BACK && in(uu, vv, 0.06, 0.05, 0.94, 0.4)) return plate(shell, f, uu, vv, 0.06, 0.05, 0.94, 0.4);
                return skin(sk, a.body, f, x, y, w, h);
            };
            s.arm = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.TOP || vv < 0.26) return plate(shell, f, uu, vv, 0, 0, 1, 0.26);
                if (vv > 0.52 && vv < 0.8 && f != Face.BOTTOM && !painted) return plate(shell, f, uu, vv, 0, 0.52, 1, 0.8);
                return skin(sk, a.arm, f, x, y, w, h);
            };
            s.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.42 && vv < 0.8 && f != Face.BOTTOM && f != Face.TOP && !painted) return plate(shell, f, uu, vv, 0, 0.42, 1, 0.8);
                int c = skin(sk, a.leg, f, x, y, w, h);
                return metal && ((int) (vv * h)) % 6 == 0 ? darker(c, 0.92) : c;                     // a metal body shows its panel lines
            };
            s.save("entity/" + dir() + "/" + name + ".png");
        }

        static void majin(String name, int skinC, int vestC, int pantsC, int beltC, double k, boolean corrupted) throws IOException {
            int[] sk = ramp(skinC, 6), vest = ramp(vestC, 5), pants = ramp(pantsC, 5), belt = ramp(beltC, 4);
            Hd.HdSkin a = anat(k, name.hashCode()), s = new Hd.HdSkin();
            FaceFn cracks = (f, x, y, w, h) -> corrupted && Math.abs(Hd.smooth(x, y, 6, name.hashCode() + f.ordinal()) - 0.5) < 0.018 ? 1 : 0;
            s.head = (f, x, y, w, h) -> {
                double l = lumOf(a.head, f, x, y, w, h);
                if (f == Face.TOP) l += 0.05 * Hd.g(u(x, w), v(y, h), 0.5, 0.5, 0.3, 0.3);
                if (cracks.at(f, x, y, w, h) == 1 && f != Face.FRONT) l -= 0.25;
                return tone(sk, l);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.78) return cloth(pants, f, x, y, w, h, 41, 1.6);
                if (vv > 0.7) return f == Face.FRONT && Math.abs(uu - 0.5) < 0.09 ? tone(belt, 1.05) : tone(belt, Skins.faceLight(f) + (vv < 0.73 ? 0.08 : 0));
                if (f == Face.FRONT && Math.abs(uu - 0.5) < 0.28) {                                    // the open vest shows the belly
                    double l = lumOf(a.body, f, x, y, w, h) + 0.07 * Hd.g(uu, vv, 0.5, 0.5, 0.25, 0.2);
                    if (cracks.at(f, x, y, w, h) == 1) l -= 0.25;
                    return tone(sk, l);
                }
                if (f == Face.FRONT && Math.abs(uu - 0.5) < 0.31) return corrupted ? vest[0] : belt[2];  // the vest's edge
                if (corrupted && vv > 0.6 && Hd.smooth(x, y, 2, 77) > 0.6) return 0;                    // torn
                return cloth(vest, f, x, y, w, h, 42, 0.8);
            };
            s.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv > 0.72) return vv < 0.76 ? tone(corrupted ? vest : pants, 0.75) : cloth(corrupted ? vest : pants, f, x, y, w, h, 43, 0.5);
                int c = skin(sk, a.arm, f, x, y, w, h);
                return cracks.at(f, x, y, w, h) == 1 ? darker(c, 0.7) : c;
            };
            s.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.74 || f == Face.BOTTOM) return tone(belt, Skins.faceLight(f) + (vv < 0.78 ? 0.1 : 0) + 0.12 * Hd.g(uu, vv, 0.4, 0.86, 0.15, 0.05));
                return cloth(pants, f, x, y, w, h, 44, 1.8);
            };
            bare(s, a, sk, (f, x, y, w, h) -> cracks.at(f, x, y, w, h) == 1 ? darker(skin(sk, a.body, f, x, y, w, h), 0.7) : 0,   // CX-16a: bare
                    (f, x, y, w, h) -> cracks.at(f, x, y, w, h) == 1 ? darker(skin(sk, a.arm, f, x, y, w, h), 0.7) : 0, null);
            if (painted) {                                                                               // the rows of little holes
                FaceFn body = s.body, arm = s.arm;
                s.body = (f, x, y, w, h) -> f == Face.FRONT && y == 2 && x % 3 == 1 && Math.abs(u(x, w) - 0.5) < 0.26 ? sk[0] : body.at(f, x, y, w, h);
                s.arm = (f, x, y, w, h) -> (f == Face.LEFT || f == Face.RIGHT) && x == w / 2 && y % 4 == 1 && v(y, h) > 0.08 && v(y, h) < 0.68 ? sk[0] : arm.at(f, x, y, w, h);
            }
            s.save("entity/" + dir() + "/" + name + ".png");
        }

        static void vampire() throws IOException {
            int[] sk = ramp(0xFFE6E0EA, 6), coat = ramp(0xFF1A1420, 6), lining = ramp(0xFFA01028, 5), vest = ramp(0xFF5A1020, 5),
                    hair = ramp(0xFF1C1418, 5), boot = ramp(0xFF2A1A16, 4), trousers = ramp(0xFF24202A, 5), gold = ramp(0xFFE8C040, 3);
            Hd.HdSkin a = anat(0.6, 61), s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                boolean hairHere = f == Face.TOP || (f != Face.FRONT && f != Face.BOTTOM && vv < (f == Face.BACK ? 0.45 : 0.22))
                        || (f == Face.FRONT && vv < 0.1 + Math.max(0, 0.12 - Math.abs(uu - 0.5)) * 1.2);   // a widow's peak
                if (hairHere) return tone(hair, Skins.faceLight(f) + 0.12 * Math.sin(uu * 30 + vv * 6) * 0.5);   // slicked, with a sheen
                return skin(sk, a.head, f, x, y, w, h);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT) {
                    double d = Math.abs(uu - 0.5);
                    if (d < 0.12 && vv < 0.18) return tone(ramp(0xFFF2F0F4, 4), 0.95 + 0.08 * Math.sin(vv * 40));   // the cravat
                    if (d < 0.2 && vv < 0.62) {                                                                 // the waistcoat and its buttons
                        if (d < 0.02 && (Math.abs(vv - 0.3) < 0.02 || Math.abs(vv - 0.42) < 0.02 || Math.abs(vv - 0.54) < 0.02)) return gold[2];
                        return cloth(vest, f, x, y, w, h, 51, 0.5);
                    }
                    if (d < 0.26 && vv < 0.95) return cloth(lining, f, x, y, w, h, 52, 0.4);
                }
                if (f == Face.BACK && vv < 0.1) return lining[2];
                return cloth(coat, f, x, y, w, h, 53, 1.0);
            };
            s.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv > 0.88) return skin(sk, a.arm, f, x, y, w, h);
                if (vv > 0.82) return lining[2];
                return cloth(coat, f, x, y, w, h, 54, 0.9);
            };
            s.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.76 || f == Face.BOTTOM) return tone(boot, Skins.faceLight(f) + 0.14 * Hd.g(uu, vv, 0.35, 0.82, 0.12, 0.05));
                return cloth(trousers, f, x, y, w, h, 55, 1.1);
            };
            bare(s, a, sk, null, null, null);                                                          // CX-16a: bare
            s.save("entity/" + dir() + "/vampire.png");
        }

        static void bioAndroid() throws IOException {
            int[] shell = ramp(0xFF5AB04A, 6), joint = ramp(0xFF20242A, 5), mask = ramp(0xFFE8E4D8, 5), spot = ramp(0xFF1E3A1A, 4);
            Hd.HdSkin a = anat(1.1, 71), s = new Hd.HdSkin();
            FaceFn spotted = (f, x, y, w, h) -> {
                double sp = Hd.smooth(x * 1.0, y * 1.0, 3.2, 71 + f.ordinal() * 13);
                double l = 0;
                if (painted && sp > 0.66) return sp > 0.685 ? tone(spot, 0.86) : tone(spot, Painted.INK);          // flat spots, inked rims
                if (sp > 0.7) return tone(spot, 0.8 + (sp - 0.7) * 0.6);
                if (sp > 0.66) l -= 0.08;                                                              // a soft rim around each spot
                return tone(shell, l + Skins.faceLight(f) + 0.04);
            };
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT && vv > 0.22) {
                    if ((uu < 0.12 || uu > 0.88) && vv > 0.55) return tone(ramp(0xFF7A3A9A, 4), 0.8 + 0.1 * Math.sin(vv * 40));   // cheek grooves
                    return skin(mask, a.head, f, x, y, w, h);
                }
                if (f == Face.TOP && Math.abs(uu - 0.5) < 0.1) return tone(shell, 0.75 + 0.1 * Math.sin(vv * 30));          // the crest
                return spotted.at(f, x, y, w, h);
            };
            FaceFn ribbed = (f, x, y, w, h) -> tone(joint, Skins.faceLight(f) - 0.05 + 0.08 * Math.sin(v(y, h) * 70));
            s.body = (f, x, y, w, h) -> f == Face.FRONT && Math.abs(u(x, w) - 0.5) < 0.25 && v(y, h) > 0.5 ? ribbed.at(f, x, y, w, h) : spotted.at(f, x, y, w, h);
            s.arm = (f, x, y, w, h) -> v(y, h) > 0.33 && v(y, h) < 0.6 ? ribbed.at(f, x, y, w, h) : spotted.at(f, x, y, w, h);
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.33 && v(y, h) < 0.7 ? ribbed.at(f, x, y, w, h) : spotted.at(f, x, y, w, h);
            s.save("entity/" + dir() + "/bio_android.png");
        }

        static void tuffle() throws IOException {
            int[] sk = ramp(0xFFEDE0D6, 6), suit = ramp(0xFFE8ECF2, 6), trim = ramp(0xFF3A6AB0, 5), hair = ramp(0xFFC8CCD8, 5);
            Hd.HdSkin a = anat(0.5, 81), s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                boolean hairHere = f == Face.TOP || (f != Face.FRONT && f != Face.BOTTOM && vv < 0.32)
                        || (f == Face.FRONT && vv < 0.12 + 0.05 * Math.abs(Math.sin(uu * 18)));            // a jagged fringe
                if (hairHere) return tone(hair, Skins.faceLight(f) + 0.08 * Math.sin(uu * 40 + vv * 10));
                return skin(sk, a.head, f, x, y, w, h);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.FRONT) {
                    double gem = Math.hypot((uu - 0.5) / 0.75, vv - 0.3);
                    if (gem < 0.09) return mix(0xFFD01020, 0xFFFF9090, Math.max(0, 1 - Math.hypot(uu - 0.46, vv - 0.26) / 0.06));   // the core gem
                    if (gem < 0.11) return 0xFFE8C040;
                }
                if (vv < 0.06 || (vv > 0.64 && vv < 0.7)) return tone(trim, Skins.faceLight(f) + 0.05);
                if (Math.abs(uu - 0.3) < 0.01 || Math.abs(uu - 0.7) < 0.01) return suit[2];               // panel seams
                return cloth(suit, f, x, y, w, h, 61, 0.4);
            };
            s.arm = (f, x, y, w, h) -> v(y, h) > 0.76 ? cloth(trim, f, x, y, w, h, 62, 0.3) : cloth(suit, f, x, y, w, h, 63, 0.5);
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.76 || f == Face.BOTTOM ? cloth(trim, f, x, y, w, h, 64, 0.3) : cloth(suit, f, x, y, w, h, 65, 0.6);
            bare(s, a, sk, (f, x, y, w, h) -> {                                                       // CX-16a: bare but for the core gem
                if (f != Face.FRONT) return 0;
                double uu = u(x, w), vv = v(y, h), gem = Math.hypot((uu - 0.5) / 0.75, vv - 0.3);
                if (gem < 0.09) return mix(0xFFD01020, 0xFFFF9090, Math.max(0, 1 - Math.hypot(uu - 0.46, vv - 0.26) / 0.06));
                return gem < 0.11 ? 0xFFE8C040 : 0;
            }, null, null);
            s.save("entity/" + dir() + "/tuffle.png");
        }

        static void genAlien() throws IOException {
            int[] sk = ramp(0xFF7A9AC0, 6), harness = ramp(0xFF4A3424, 5), metal = ramp(0xFFC8C8D0, 4), pants = ramp(0xFF3A3A4A, 5);
            Hd.HdSkin a = anat(0.9, 91), s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double l = lumOf(a.head, f, x, y, w, h), vv = v(y, h);
                if (f == Face.BACK) l -= 0.12 * Math.exp(-Math.pow(((vv * 5) % 1) - 0.5, 2) / 0.01);    // ridges down the skull
                return tone(sk, l);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if ((f == Face.FRONT || f == Face.BACK) && (Math.abs(uu - 0.25) < 0.05 || Math.abs(uu - 0.75) < 0.05)) {
                    if (Math.abs(vv - 0.3) < 0.03) return metal[2];                                       // buckles
                    return tone(harness, Skins.faceLight(f) + 0.05 * Math.sin(vv * 50));
                }
                if (vv > 0.66 && vv < 0.72) return tone(metal, Skins.faceLight(f) + 0.06);
                double l = lumOf(a.body, f, x, y, w, h);
                if (f == Face.BACK && Math.sin(vv * 20) > 0.75) l -= 0.1;                                // stripes
                return tone(sk, l);
            };
            s.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv > 0.66 && vv < 0.76) return tone(harness, Skins.faceLight(f) + 0.04);
                double l = lumOf(a.arm, f, x, y, w, h);
                if (f != Face.FRONT && Math.sin(vv * 18) > 0.75) l -= 0.1;
                return tone(sk, l);
            };
            s.leg = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv < 0.5) return skin(sk, a.leg, f, x, y, w, h);
                if (f == Face.FRONT && in(uu, vv, 0.2, 0.55, 0.8, 0.68)) return plate(metal, f, uu, vv, 0.2, 0.55, 0.8, 0.68);   // knee pads
                return cloth(pants, f, x, y, w, h, 71, 1.0);
            };
            bare(s, a, sk, (f, x, y, w, h) -> f == Face.BACK && Math.sin(v(y, h) * 20) > 0.75 ? tone(sk, lumOf(a.body, f, x, y, w, h) - 0.1) : 0,
                    (f, x, y, w, h) -> f != Face.FRONT && Math.sin(v(y, h) * 18) > 0.75 ? tone(sk, lumOf(a.arm, f, x, y, w, h) - 0.1) : 0, null);   // CX-16a: bare, striped
            s.save("entity/" + dir() + "/gen_alien.png");
        }

        static void kai() throws IOException {
            int[] sk = ramp(0xFFD8B8EC, 6), robe = ramp(0xFF2C3A8A, 6), under = ramp(0xFFF4F2F8, 5), gold = ramp(0xFFE8C040, 4), sash = ramp(0xFF5AB0E8, 4);
            Hd.HdSkin a = anat(0.5, 101), s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.TOP && Math.abs(uu - 0.5) < 0.16) return tone(ramp(0xFFF8F8FF, 4), 0.95 + 0.1 * Math.sin(uu * 60 + vv * 20));   // the white tuft
                return skin(sk, a.head, f, x, y, w, h);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv < 0.07) return tone(gold, Skins.faceLight(f) + 0.08 * Math.sin(uu * 60));          // the engraved collar
                if (vv > 0.66 && vv < 0.76) return cloth(sash, f, x, y, w, h, 81, 0.5);
                if (f == Face.FRONT) {
                    double d = Math.abs(uu - 0.5);
                    if (d < 0.12 && vv < 0.66) return cloth(under, f, x, y, w, h, 82, 0.3);
                    if (d < 0.15 && vv < 0.66) return ((int) (vv * h)) % 4 < 2 ? gold[2] : gold[1];        // embroidered edge
                }
                return cloth(robe, f, x, y, w, h, 83, 1.0);
            };
            s.arm = (f, x, y, w, h) -> {
                double vv = v(y, h);
                if (vv < 0.52) return cloth(robe, f, x, y, w, h, 84, 0.8);
                if (vv < 0.55) return gold[2];
                if (vv < 0.84) return cloth(under, f, x, y, w, h, 85, 0.6);
                return skin(sk, a.arm, f, x, y, w, h);
            };
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.86 || f == Face.BOTTOM ? tone(gold, Skins.faceLight(f) + 0.05) : cloth(under, f, x, y, w, h, 86, 1.1);
            bare(s, a, sk, null, null, null);                                                          // CX-16a: bare
            s.save("entity/" + dir() + "/kai.png");
        }

        static void coreDemon() throws IOException {
            int[] sk = ramp(0xFFC83030, 6), garb = ramp(0xFF181418, 6), gold = ramp(0xFFE8C040, 4), hair = ramp(0xFF141010, 4);
            Hd.HdSkin a = anat(1.25, 111), s = new Hd.HdSkin();
            s.head = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (f == Face.TOP || (f != Face.BOTTOM && vv < (f == Face.FRONT ? 0.1 : 0.2) + 0.04 * Math.abs(Math.sin(uu * 20)))) {
                    return tone(hair, Skins.faceLight(f) + 0.08 * Math.sin(uu * 50));                  // short spiky black hair
                }
                return skin(sk, a.head, f, x, y, w, h);
            };
            s.body = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv > 0.74 && vv < 0.8) return tone(gold, Skins.faceLight(f) + 0.05);
                if (f == Face.FRONT) {
                    double em = Math.abs(uu - 0.5) * 1.4 + Math.abs(vv - 0.27);                          // the burning emblem
                    if (em < 0.12) return mix(0xFFFFE070, 0xFFE03010, Math.min(1, em / 0.12));
                }
                return cloth(garb, f, x, y, w, h, 91, 0.9);
            };
            s.arm = (f, x, y, w, h) -> {
                double uu = u(x, w), vv = v(y, h);
                if (vv < 0.4 + 0.04 * Math.abs(Math.sin(uu * 25))) return cloth(garb, f, x, y, w, h, 92, 0.7);   // a clawed hem
                if (vv > 0.93) return tone(ramp(0xFF101010, 3), 0.8);                                     // claws
                return skin(sk, a.arm, f, x, y, w, h);
            };
            s.leg = (f, x, y, w, h) -> v(y, h) > 0.85 || f == Face.BOTTOM ? tone(garb, Skins.faceLight(f) - 0.1) : cloth(garb, f, x, y, w, h, 93, 1.1);
            bare(s, a, sk, (f, x, y, w, h) -> {                                                       // CX-16a: bare, the emblem burnt into the chest
                if (f != Face.FRONT) return 0;
                double em = Math.abs(u(x, w) - 0.5) * 1.4 + Math.abs(v(y, h) - 0.27);
                return em < 0.12 ? mix(0xFFFFE070, 0xFFE03010, Math.min(1, em / 0.12)) : 0;
            }, (f, x, y, w, h) -> v(y, h) > 0.93 ? tone(ramp(0xFF101010, 3), 0.8) : 0, null);
            s.save("entity/" + dir() + "/core_demon.png");
        }
    }

    // ================================================================== form detail (CX-13c)

    /**
     * Form detail textures: HD hair (strands along each spike, cylinder shading, an anime shine band), a body glow
     * rim for god ki (drawn additively in the aura colour), a flat body mask (the Kaioken flush) and four frames of
     * lightning crawling over the body.
     */
    static final class FormFx {
        static void all() throws IOException {
            hairHd();
            bodyGlow();
            bodyMask();
            for (int i = 0; i < 4; i++) sparks(i);
        }

        /** 256x256, the same regions as form_hair.png at four times the resolution. */
        static void hairHd() throws IOException {
            int S = 4;
            Canvas c = new Canvas(64 * S, 64 * S);
            for (int y = 0; y < 12 * S; y++) for (int x = 0; x < 32 * S; x++) {                        // cap: dense strands
                double v = 0.8 + (Hd.smooth(x, 0, 1.5, 301) - 0.5) * 0.25 - (y > 7 * S ? 0.08 : 0) + 0.05 * Math.sin(x * 0.9);
                c.set(x, y, Creatures.grey(v));
            }
            for (int y = 16 * S; y < 44 * S; y++) for (int x = 0; x < 24 * S; x++) {                    // long hair: falling strands
                double v = 0.92 - (y - 16 * S) * 0.0015 + (Hd.smooth(x, 0, 1.4, 302) - 0.5) * 0.28;
                if (Math.abs(((y - 16 * S) % 40) - 12) < 2 && Hd.smooth(x, 3, 3, 309) > 0.45) v += 0.12;  // shine
                c.set(x, y, Creatures.grey(v));
            }
            c.rect(40 * S, 0, 42 * S, S, 0xFFFFFFFF);                                                     // eyes
            double[] tier = {0.66, 0.84, 0.98};
            for (int t = 0; t < 3; t++) {
                int x0 = t * 16 * S;
                for (int y = 48 * S; y < 60 * S; y++) for (int x = x0; x < x0 + 16 * S; x++) {
                    // the mesh samples u 3..9 and v 50..56 of each tier (x0+12..x0+36, 200..224): v runs root -> tip
                    double uu = (x - (x0 + 12)) / 24.0, vv = (y - 200) / 24.0;
                    double v = tier[t];
                    v += (Hd.smooth(x, 0, 1.6, 303 + t) - 0.5) * 0.3;                                    // strands along the spike
                    v -= 0.16 * Math.pow(Math.abs(uu - 0.5) * 2, 2);                                       // round: darker at the sides
                    if (t > 0) {                                                                            // the shine band, jagged
                        double band = 0.35 + 0.08 * Math.sin(uu * 20);
                        if (Math.abs(vv - band) < 0.07) v = Math.max(v, 1.0);
                    }
                    if (t == 0) v -= 0.12 * (1 - vv);                                                      // roots darken into the scalp
                    if (t == 2) v += 0.06 * vv;                                                            // tips catch the light
                    c.set(x, y, Creatures.grey(v));
                }
            }
            c.save("entity/form_hair_hd.png");
        }

        /** The god-ki rim: bright along every edge of the body, a faint glow across it. */
        static void bodyGlow() throws IOException {
            Hd.HdSkin s = new Hd.HdSkin();
            FaceFn rim = (f, x, y, w, h) -> {
                double u = (x + 0.5) / w, v = (y + 0.5) / h;
                double e = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v));
                double val = 0.75 * Math.exp(-e / 0.07) + 0.1 + 0.08 * Hd.smooth(x, y, 3, f.ordinal() + 501);
                if (f == Face.TOP || f == Face.BOTTOM) val *= 0.5;
                return Creatures.grey(val);
            };
            s.head = rim;
            s.body = rim;
            s.arm = rim;
            s.leg = rim;
            s.save("entity/form/body_glow.png");
        }

        static void bodyMask() throws IOException {
            Hd.HdSkin s = new Hd.HdSkin();
            FaceFn white = (f, x, y, w, h) -> 0xFFFFFFFF;
            s.head = white;
            s.body = white;
            s.arm = white;
            s.leg = white;
            s.save("entity/form/body_mask.png");
        }

        /** Lightning: on some faces, a jagged bolt running down the face (a bright core, a softer halo). */
        static void sparks(int frame) throws IOException {
            Hd.HdSkin s = new Hd.HdSkin();
            int[] part = {0};
            FaceFn bolt = (f, x, y, w, h) -> {
                int seed = 900 + frame * 97 + f.ordinal() * 13 + w * 7 + h;
                if (noise(seed, 1, 5) > 0.55 || f == Face.TOP || f == Face.BOTTOM) return 0;
                double u = (x + 0.5) / w, v = (y + 0.5) / h;
                double v0 = noise(seed, 2, 5) * 0.4, v1 = v0 + 0.35 + noise(seed, 3, 5) * 0.25;
                if (v < v0 || v > v1) return 0;
                int steps = 6;
                double t = (v - v0) / (v1 - v0) * steps;
                int i = (int) t;
                double a = 0.2 + 0.6 * noise(seed, 10 + i, 5), b = 0.2 + 0.6 * noise(seed, 11 + i, 5);
                double cu = a + (b - a) * (t - i);
                double d = Math.abs(u - cu) * w;
                if (d < 0.7) return 0xFFFFFFFF;
                if (d < 2.0) return Creatures.grey(0.45);
                return 0;
            };
            s.head = bolt;
            s.body = bolt;
            s.arm = bolt;
            s.leg = bolt;
            s.save("entity/form/sparks_" + frame + ".png");
        }
    }

    // ================================================================== HUD v2 (CX-13e)

    /**
     * The Zenith HUD (drawn at a quarter scale): six portrait frames, one per family of forms (standard, flame,
     * divine, savage, regal, tech), each a coloured metal layer plus a white glow layer the HUD tints with the aura;
     * an ornate bar housing with an icon socket, the three bar icons and a chamfered plate; and a tileable energy
     * strip for the animated bar fills.
     * hud_frames.png (1536x512): metal of style s at (s*256, 0), glow at (s*256, 256); frame centre (128,128),
     * head window r<76, inner rim 76-86, gauge track 86-100, outer rim 100-112, ornaments beyond, gem at the bottom.
     * hud_bars.png (512x256): housing (0,0,512,48); icons (k*32, 64, 32, 32): body, ki, stamina; plate (0,128,192,40).
     */
    static final class HudHd {
        static final int[] METALS = {0xFFD8A040, 0xFFE0A838, 0xFFC8D4E8, 0xFF7A5A4A, 0xFFE8B850, 0xFF8EA0B4};
        static final double CX = 127.5, CY = 127.5;

        static void all() throws IOException {
            Canvas c = new Canvas(1536, 512);
            for (int s = 0; s < 6; s++) {
                metal(c, s);
                glow(c, s);
            }
            c.save("gui/hud_frames.png");
            bars();
            energy();
        }

        /** Degrees, 0 at the top, clockwise. */
        static double ang(double dx, double dy) {
            return Math.toDegrees(Math.atan2(dx, -dy));
        }

        static double diff(double a, double b) {
            double d = ((a - b) % 360 + 540) % 360 - 180;
            return Math.abs(d);
        }

        /** Whether a style's metal ornaments cover this point past the outer rim. */
        static boolean ornament(int style, double a, double r) {
            if (r < 111 || r >= 127) return false;
            double d = r - 112;
            switch (style) {
                case 0 -> {                                                                         // four diamond points
                    for (int k = 0; k < 4; k++) if (diff(a, k * 90) < 11 * (1 - d / 15)) return true;
                }
                case 1 -> {                                                                         // short fins between the flames
                    for (int k = 0; k < 8; k++) if (diff(a, 22.5 + k * 45) < 7 * (1 - d / 8)) return true;
                }
                case 2 -> {                                                                         // a halo ring on four struts
                    if (r >= 117 && r < 122) return true;
                    for (int k = 0; k < 4; k++) if (diff(a, 45 + k * 90) < 2.4 && r < 118) return true;
                }
                case 3 -> {                                                                         // hooked thorns
                    for (int k = 0; k < 9; k++) {
                        double ak = k * 40 + (noise(k, 1, 77) - 0.5) * 16, len = 8 + noise(k, 2, 77) * 6;
                        if (d < len && diff(a - d * 0.9, ak) < 8 * (1 - d / len)) return true;
                    }
                }
                case 4 -> {                                                                         // a crown on top, swept wings at the sides
                    double[] at = {-36, -18, 0, 18, 36}, len = {11, 8, 14, 8, 11};
                    for (int k = 0; k < 5; k++) if (d < len[k] && diff(a, at[k]) < 6 * (1 - d / len[k])) return true;
                    double side = Math.abs(a);
                    if (side > 65 && side < 115) {
                        double l = 9 * Math.sin(Math.PI * (side - 65) / 50) * (0.7 + 0.3 * Math.cos((side - 65) * 0.45));
                        if (d < l) return true;
                    }
                }
                case 5 -> {                                                                         // notched brackets
                    for (int k = 0; k < 6; k++) {
                        double dd = diff(a, 30 + k * 60);
                        if (dd < 7 && r < 121 && !(dd < 2.5 && r > 116)) return true;
                    }
                }
                default -> { }
            }
            return false;
        }

        static boolean gemSetting(double x, double y) {
            return Math.hypot(x - CX, y - (CY + 106)) < 12;
        }

        static void metal(Canvas c, int style) {
            int[] ramp = ramp(METALS[style], 9);
            boolean[][] m = new boolean[256][256];
            boolean[][] track = new boolean[256][256];
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                double dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy);
                track[y][x] = r >= 86 && r < 100;
                m[y][x] = (r >= 76 && r < 86) || (r >= 100 && r < 112) || ornament(style, ang(dx, dy), r) || gemSetting(x + 0.5, y + 0.5);
                if (gemSetting(x + 0.5, y + 0.5)) track[y][x] = false;
            }
            int x0 = style * 256;
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                double dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy);
                int col = 0;
                if (track[y][x]) {
                    col = 0xFF0B0D16;
                    double edge = Math.min(r - 86, 100 - r);
                    if (edge < 2) col = 0xFF05060A;
                    if (diff(ang(dx, dy), Math.round(ang(dx, dy) / 30) * 30) < 0.6) col = 0xFF1C2232;     // ticks
                } else if (m[y][x]) {
                    double gx = x + 0.5 - CX, gy = y + 0.5 - (CY + 106), gr = Math.hypot(gx, gy);
                    if (gr < 9) col = 0xFF140C06;                                                   // under the gem
                    else {
                        double v = 0.52 + 0.14 * (-dx - dy) / (r * 1.414);
                        for (int k = 1; k <= 3; k++) {                                              // bevel from the shape's own edges
                            if (!has(m, x - k, y - k)) v += 0.3 / k;
                            if (!has(m, x + k, y + k)) v -= 0.3 / k;
                        }
                        if (r >= 100 && r < 112 && Math.abs(r - 106) < 0.8 && gr >= 12) v -= 0.35;  // groove on the outer rim
                        if (gr < 12) v = 0.75 - (gr - 9) * 0.12;                                    // the gem's bezel
                        if (style == 0) for (int k = 0; k < 4; k++) {                               // domed studs
                            double sa = Math.toRadians(45 + k * 90), sx = CX + Math.sin(sa) * 106, sy = CY - Math.cos(sa) * 106;
                            double sd = Math.hypot(x + 0.5 - sx, y + 0.5 - sy);
                            if (sd < 4.5) v = 0.3 + 0.7 * Math.max(0, 1 - Math.hypot(x + 0.5 - sx + 1.3, y + 0.5 - sy + 1.3) / 4.5);
                        }
                        if (style == 5 && r >= 100 && r < 112 && (int) ((ang(dx, dy) + 360) / 6) % 2 == 0 && Math.abs(r - 106) < 2) v -= 0.12;   // knurling
                        v += (noise(x, y, 811 + style) - 0.5) * 0.08;
                        col = ramp[(int) Math.max(0, Math.min(ramp.length - 1, Math.round(v * (ramp.length - 1))))];
                    }
                } else if (near(m, x, y, 2) || (near(track, x, y, 2) && r < 86)) col = 0xFF05060A;      // outline
                c.set(x0 + x, y, col);
            }
        }

        static boolean has(boolean[][] m, int x, int y) {
            return x >= 0 && y >= 0 && x < m.length && y < m.length && m[y][x];
        }

        static boolean near(boolean[][] m, int x, int y, int d) {
            for (int j = -d; j <= d; j++) for (int i = -d; i <= d; i++) if (i * i + j * j <= d * d && has(m, x + i, y + j)) return true;
            return false;
        }

        static void glow(Canvas c, int style) {
            int x0 = style * 256, y0 = 256;
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                double dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy), a = ang(dx, dy);
                double v = 0, al = 0;
                if (r >= 86 && r < 87.6) { v = 1; al = 0.45; }                                     // the track's inner light
                double d = r - 112;
                switch (style) {
                    case 0 -> {
                        if (r >= 112 && r < 114.5) { v = 1; al = Math.max(al, 0.3); }
                    }
                    case 1 -> {                                                                     // flames, tallest at the top
                        if (d >= -6) {
                            double hgt = 9 + 12 * Math.pow((1 + Math.cos(Math.toRadians(a))) / 2, 1.3);
                            double phase = Math.toRadians(a) * 16 + d * 0.11 * (a < 0 ? -1 : 1);
                            double tongue = Math.pow(0.5 + 0.5 * Math.cos(phase), 2.2);
                            double lim = hgt * (0.35 + 0.65 * tongue) - 6;
                            if (d < lim) {
                                double in = 1 - (d + 6) / (lim + 6);
                                v = 0.55 + 0.45 * in;
                                al = Math.min(d < 0 ? 0.55 : 1, Math.pow(in, 0.7) * 1.4);
                            }
                        }
                    }
                    case 2 -> {                                                                     // rays and a soft halo
                        if (d >= 1) {
                            int k = (int) Math.round(a / 15);
                            double len = k % 2 == 0 ? 15 : 9, w = 2.6 * (1 - (d - 1) / len);
                            if (d - 1 < len && diff(a, k * 15) < w) { v = 1; al = 0.95 * (1 - (d - 1) / len) + 0.05; }
                            double halo = 0.5 * Math.exp(-Math.pow((r - 119.5) / 4.5, 2));
                            if (halo > al * 0.5) { v = 1; al = Math.max(al, halo); }
                        }
                    }
                    case 3 -> {                                                                     // three claw slashes across the top right
                        double px = 203, py = 53, ux = -0.5, uy = 0.866, nx = 0.866, ny = 0.5;
                        for (int k = -1; k <= 1; k++) {
                            double qx = x + 0.5 - (px + nx * k * 8), qy = y + 0.5 - (py + ny * k * 8);
                            double t = qx * ux + qy * uy, n = Math.abs(qx * nx + qy * ny), half = 32 - Math.abs(k) * 6;
                            double w = 3 * (1 - Math.abs(t) / half);
                            if (Math.abs(t) < half && n < w) { v = 1; al = Math.max(al, 0.9); }
                            else if (Math.abs(t) < half && n < w + 2) { v = 0.8; al = Math.max(al, 0.3); }
                        }
                    }
                    case 4 -> {                                                                     // jewels on the crown tips, lit wing edges
                        double[] at = {-36, -18, 0, 18, 36}, len = {11, 8, 14, 8, 11};
                        for (int k = 0; k < 5; k++) {
                            double ta = Math.toRadians(at[k]), tr = 112 + len[k];
                            double jx = CX + Math.sin(ta) * tr, jy = CY - Math.cos(ta) * tr, jd = Math.hypot(x + 0.5 - jx, y + 0.5 - jy);
                            if (jd < 4.8) { v = 0.5 + 0.5 * Math.max(0, 1 - Math.hypot(x + 0.5 - jx + 1.2, y + 0.5 - jy + 1.2) / 4.8); al = 1; }
                        }
                        double side = Math.abs(a);
                        if (side > 65 && side < 115 && d > 0) {
                            double l = 9 * Math.sin(Math.PI * (side - 65) / 50) * (0.7 + 0.3 * Math.cos((side - 65) * 0.45));
                            if (Math.abs(d - l - 1.5) < 1.3) { v = 1; al = Math.max(al, 0.55); }
                        }
                    }
                    case 5 -> {                                                                     // circuit traces and a segmented light ring
                        if (d >= 1 && d < 3 && ((a + 360) % 10) < 7) { v = 1; al = Math.max(al, 0.5); }
                        for (int k = 0; k < 6; k++) for (int s = -1; s <= 1; s += 2) {
                            double base = k * 60 + s * 12;
                            if (d >= 3 && d < 9 && diff(a, base) < 0.9) { v = 1; al = 0.85; }
                            double end = base + s * 6;
                            if (Math.abs(r - 121) < 1 && diff(a, (base + end) / 2) < 3) { v = 1; al = 0.85; }
                            double ea = Math.toRadians(end), ex = CX + Math.sin(ea) * 121, ey = CY - Math.cos(ea) * 121;
                            if (Math.hypot(x + 0.5 - ex, y + 0.5 - ey) < 2.4) { v = 1; al = 1; }
                        }
                    }
                    default -> { }
                }
                double gx = x + 0.5 - CX, gy = y + 0.5 - (CY + 106), gr = Math.hypot(gx, gy);
                if (gr < 9) {                                                                       // the gem, cut and shining
                    double l = 1 - Math.hypot(gx + 2.5, gy + 2.5) / 11;
                    v = 0.3 + 0.7 * Math.max(0, l);
                    if (Math.abs(gx) + Math.abs(gy) > 9) v *= 0.75;
                    if (Math.hypot(gx + 3, gy + 3) < 1.8) v = 1.3;
                    al = 1;
                }
                if (al <= 0) continue;
                int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
                c.set(x0 + x, y0 + y, (int) Math.round(Math.min(1, al) * 255) << 24 | g << 16 | g << 8 | g);
            }
        }

        static void bars() throws IOException {
            Canvas c = new Canvas(512, 256);
            int[] gold = ramp(0xFFD8A040, 9);
            boolean[][] m = new boolean[48][512];
            boolean[][] hole = new boolean[48][512];
            for (int y = 0; y < 48; y++) for (int x = 0; x < 512; x++) {
                double px = x + 0.5, py = y + 0.5, sr = Math.hypot(px - 24, py - 24);
                double top = px < 476 ? 4 : 4 + (px - 476) * 0.55, bottom = px < 476 ? 44 : 44 - (px - 476) * 0.55;
                boolean rail = px >= 24 && py >= top && py < bottom;
                boolean window = py >= 12 && py < 36 && px >= 46 && px < 470 - (py - 12) * 0.35;
                m[y][x] = (sr < 22 || rail) && !window;
                hole[y][x] = sr < 15;
            }
            for (int y = 0; y < 48; y++) for (int x = 0; x < 512; x++) {
                int col = 0;
                if (hole[y][x]) {
                    double l = Math.hypot(x + 0.5 - 24, y + 0.5 - 24) / 15;
                    col = mix(0xFF141A2A, 0xFF05060A, l);
                } else if (m[y][x]) {
                    double v = 0.5 + 0.18 * (24 - y) / 24.0;
                    for (int k = 1; k <= 3; k++) {
                        if (!has(m, x - k, y - k) || has(hole, x - k, y - k)) v += 0.3 / k;
                        if (!has(m, x + k, y + k) || has(hole, x + k, y + k)) v -= 0.3 / k;
                    }
                    if (x > 50 && (y == 7 || y == 40)) v -= 0.25;                                   // engraved lines along the rail
                    v += (noise(x, y, 821) - 0.5) * 0.08;
                    col = gold[(int) Math.max(0, Math.min(8, Math.round(v * 8)))];
                } else if (near(m, x, y, 2) && !(y >= 13 && y < 35 && x >= 48 && x < 466 - (y - 12) * 0.35)) col = 0xFF05060A;
                c.set(x, y, col);
            }
            for (int k = 0; k < 3; k++) icon(c, k);
            plate(c);
            c.save("gui/hud_bars.png");
        }

        static void icon(Canvas c, int kind) {
            java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
            switch (kind) {
                case 0 -> { }
                case 1 -> {                                                                         // a ki flame
                    p.moveTo(16, 3);
                    p.curveTo(19, 10, 26, 14, 25, 21);
                    p.curveTo(24, 28, 8, 28, 7, 21);
                    p.curveTo(6, 15, 12, 13, 16, 3);
                    p.closePath();
                }
                default -> {                                                                        // a lightning bolt
                    p.moveTo(19, 3);
                    p.lineTo(8, 18);
                    p.lineTo(15, 18);
                    p.lineTo(12, 29);
                    p.lineTo(24, 13);
                    p.lineTo(17, 13);
                    p.lineTo(21, 3);
                    p.closePath();
                }
            }
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                double px = x + 0.5, py = y + 0.5;
                boolean in;
                if (kind == 0) {                                                                    // a heart
                    double hx = (px - 16) / 11, hy = -(py - 15) / 11;
                    double q = hx * hx + hy * hy - 1;
                    in = q * q * q - hx * hx * hy * hy * hy < 0;
                } else in = p.contains(px, py);
                if (!in) continue;
                double v = 0.65 + 0.35 * (1 - Math.hypot(px - 11, py - 9) / 20);
                c.set(kind * 32 + x, 64 + y, Creatures.grey(v));
            }
        }

        /** A chamfered glass plate with a gold trim, for nine-slicing (border 12). */
        static void plate(Canvas c) {
            int[] gold = ramp(0xFFD8A040, 9);
            for (int y = 0; y < 40; y++) for (int x = 0; x < 192; x++) {
                int ex = Math.min(x, 191 - x), ey = Math.min(y, 39 - y);
                int e = Math.min(ex, ey), cut = ex + ey;                                            // distance in, and along the chamfer
                if (cut < 8) continue;
                int col;
                if (e < 2 || cut < 10) col = 0xFF05060A;
                else if (e < 4 || cut < 13) col = gold[y < 20 ? 7 : 4];
                else if (e < 5 || cut < 14) col = 0xFF05060A;
                else col = mix(0xE81C2640, 0xE80A0E18, y / 39.0);
                c.set(x, 128 + y, col);
            }
        }

        /** Flowing light for the bar fills, seamless left to right. */
        static void energy() throws IOException {
            Canvas c = new Canvas(128, 24);
            double TAU = Math.PI * 2;
            for (int y = 0; y < 24; y++) for (int x = 0; x < 128; x++) {
                double u = x / 128.0;
                double wave = 1.2 * Math.sin(TAU * u * 2 + 0.7) + 0.6 * Math.sin(TAU * u * 3);                 // streaks that drift up and down
                double v = 0.74 + 0.09 * Math.sin(y * 0.55 + wave) + 0.05 * Math.sin(TAU * u * 5 + y * 0.08);
                if (y < 6) v += 0.3 * (1 - y / 6.0);
                if (y > 17) v -= 0.22 * (y - 17) / 6.0;
                c.set(x, y, Creatures.grey(v));
            }
            c.save("gui/hud_energy.png");
        }
    }

    // ================================================================== UI v2 (CX-13d)

    /**
     * The Zenith UI sheet (512x512, drawn at a quarter scale so every GUI pixel holds four): an ornate window frame
     * (bevelled gold with an engraved groove, an inner glow line and amber gem corners; the middle left clear), a hex
     * tile for window bodies, four button skins (normal, hover, disabled, selected) and a radial glow.
     * Regions: frame (0,0,128,128) border 32; hex tile (0,128,64,64); buttons (128, state*48, 96, 48) border 12;
     * glow (256,0,256,256).
     */
    static final class GuiHd {
        static void all() throws IOException {
            Canvas c = new Canvas(512, 512);
            frame(c);
            hexTile(c);
            for (int s = 0; s < 4; s++) button(c, s);
            glow(c);
            c.save("gui/ui_hd.png");
        }

        static void frame(Canvas c) {
            int[] gold = ramp(0xFFD8A040, 7);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int e = Math.min(Math.min(x, y), Math.min(127 - x, 127 - y));
                boolean lit = (x < 64 && x == e) || (y < 64 && y == e);                       // top and left faces catch the light
                int col;
                if (e < 2) col = 0xFF05060A;
                else if (e < 12) {
                    double t = (e - 2) / 9.0;
                    double l = 3.2 + (lit ? 1.4 : -0.6) * Math.cos(t * Math.PI) - 2.4 * Math.exp(-Math.pow((t - 0.5) / 0.09, 2));   // bevel, groove
                    l += (noise(x, y, 701) - 0.5) * 0.6;
                    col = gold[(int) Math.max(0, Math.min(gold.length - 1, Math.round(l)))];
                } else if (e < 14) col = 0xFF0A0C14;
                else if (e < 16) col = e == 14 ? 0xC04AA8E0 : 0x504AA8E0;                         // an inner glow line
                else col = 0;
                c.set(x, y, col);
            }
            for (int corner = 0; corner < 4; corner++) {                                          // amber gems at the corners
                int cx = corner % 2 == 0 ? 15 : 112, cy = corner < 2 ? 15 : 112;
                for (int y = -14; y <= 14; y++) for (int x = -14; x <= 14; x++) {
                    double r = Math.hypot(x, y);
                    if (r > 13.6) continue;
                    int col;
                    if (r > 11.8) col = 0xFF2A1404;
                    else if (r > 9.8) col = gold[r > 10.8 ? 3 : 5];
                    else {
                        double l = 1 - Math.hypot(x + 3.2, y + 3.2) / 12.5;
                        col = mix(0xFF8A2A04, 0xFFFFD27A, Math.max(0, Math.min(1, l)));
                        if (Math.hypot(x + 3.8, y + 3.8) < 2.4) col = 0xFFFFF6E0;                  // a glint
                    }
                    c.set(cx + x, cy + y, col);
                }
            }
        }

        /** A faint hex lattice, tiled behind window bodies at low alpha. */
        static void hexTile(Canvas c) {
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                double qx = x / 16.0, qy = y / 13.86;
                int row = (int) Math.floor(qy);
                double ox = qx - (row % 2 == 0 ? 0 : 0.5);
                double fx = ox - Math.floor(ox) - 0.5, fy = qy - row - 0.5;
                double d = Math.max(Math.abs(fx) * 1.15 + Math.abs(fy) * 0.6, Math.abs(fy) * 1.2);
                int a = d > 0.53 && d < 0.58 ? 22 : 0;
                c.set(x, 128 + y, a << 24 | 0x6A8AC0);
            }
        }

        static void button(Canvas c, int state) {
            int top, bottom, trim, glowEdge;
            switch (state) {
                case 1 -> { top = 0xFF2E4A86; bottom = 0xFF18264A; trim = 0xFFFFD27A; glowEdge = 0x9060B8FF; }
                case 2 -> { top = 0xFF2A2A32; bottom = 0xFF18181E; trim = 0xFF4A4A55; glowEdge = 0; }
                case 3 -> { top = 0xFFFFB848; bottom = 0xFFB0400E; trim = 0xFFFFE6A0; glowEdge = 0x80FFD27A; }
                default -> { top = 0xFF22305A; bottom = 0xFF0E1628; trim = 0xFFB08030; glowEdge = 0; }
            }
            int x0 = 128, y0 = state * 48;
            for (int y = 0; y < 48; y++) for (int x = 0; x < 96; x++) {
                int e = Math.min(Math.min(x, y), Math.min(95 - x, 47 - y));
                boolean corner = (x < 3 || x > 92) && (y < 3 || y > 44) && e < 2;
                if (corner) continue;
                int col;
                if (e < 2) col = glowEdge != 0 ? glowEdge : 0xFF05060A;
                else if (e < 4) col = e == 2 ? 0xFF05060A : trim;
                else if (e < 6) col = e == 4 ? mix(trim, 0xFF000000, 0.45) : 0xFF0A0C14;
                else {
                    double t = (y - 6) / 36.0;
                    col = mix(top, bottom, Math.max(0, Math.min(1, t)));
                    if (y == 6 || y == 7) col = mix(col, 0xFFFFFFFF, 0.28);                            // the top gloss
                    if (y >= 40) col = mix(col, 0xFF000000, 0.25);                                    // a lower shadow
                    col = mix(col, noise(x, y, 711 + state) > 0.5 ? 0xFFFFFFFF : 0xFF000000, 0.03);
                }
                c.set(x0 + x, y0 + y, col);
            }
        }

        static void glow(Canvas c) {
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                double r = Math.hypot(x - 127.5, y - 127.5) / 128;
                double a = Math.max(0, 1 - r);
                a = a * a * (3 - 2 * a);
                c.set(256 + x, y, (int) (a * 255) << 24 | 0xFFFFFF);
            }
        }
    }
    // ================================================================== faces

    /**
     * Face parts for the generated bodies (appearance.FaceParts): 64x64 skin-layout overlays touching only the head's
     * front (8x8 at u 8, v 8). Face rows: brows 2-3, eyes 4-5, nose 5, mouth 6-7. Irises and brows are white here and
     * tinted when drawn (eye colour, hair colour); everything else carries its own colour, the soft parts translucent so
     * they sit on any skin tone.
     */
    static final class Faces {
        static final int WHITE = 0xFFF4F4F4, LASH = 0xFF1A1414, TINT = 0xFFFFFFFF;

        static void all() throws IOException {
            eyes();
            irises();
            brows();
            mouths();
            noses();
            extras();
        }

        static Canvas face() {
            return new Canvas(64, 64);
        }

        static void px(Canvas c, int x, int y, int color) {
            c.set(8 + x, 8 + y, color);
        }

        static void eyes() throws IOException {
            for (int i = 0; i < 8; i++) {
                Canvas c = face();
                switch (i) {
                    case 0 -> { px(c, 1, 4, WHITE); px(c, 6, 4, WHITE); }                                   // normal
                    case 1 -> { px(c, 1, 4, WHITE); px(c, 6, 4, WHITE); px(c, 1, 5, WHITE); px(c, 6, 5, WHITE); }   // wide
                    case 2 -> { px(c, 1, 4, LASH); px(c, 6, 4, LASH); }                                     // narrow
                    case 3 -> { px(c, 1, 4, WHITE); px(c, 6, 4, WHITE); px(c, 0, 4, LASH); px(c, 7, 4, LASH); } // sharp
                    case 4 -> { px(c, 1, 4, WHITE); px(c, 6, 4, WHITE);                                    // gentle
                        for (int x : new int[]{1, 2, 5, 6}) px(c, x, 5, 0x40FFFFFF); }
                    case 5 -> { px(c, 1, 4, WHITE); px(c, 6, 4, WHITE);                                    // tired
                        for (int x : new int[]{1, 2, 5, 6}) px(c, x, 5, 0x58402030); }
                    case 6 -> { for (int x : new int[]{1, 2, 5, 6}) px(c, x, 4, LASH); }                  // closed
                    default -> { px(c, 2, 4, LASH); px(c, 5, 4, LASH); }                                    // cat: slit pupils
                }
                c.save("entity/face/eyes_" + i + ".png");
            }
        }

        /** Where each eye shape keeps its irises (tinted with the eye colour; forms make them glow). */
        static void irises() throws IOException {
            for (int i = 0; i < 8; i++) {
                Canvas c = face();
                switch (i) {
                    case 1 -> { px(c, 2, 4, TINT); px(c, 5, 4, TINT); px(c, 2, 5, TINT); px(c, 5, 5, TINT); }
                    case 6 -> { }
                    case 7 -> { px(c, 1, 4, TINT); px(c, 6, 4, TINT); }
                    default -> { px(c, 2, 4, TINT); px(c, 5, 4, TINT); }
                }
                c.save("entity/face/iris_" + i + ".png");
            }
        }

        static void brows() throws IOException {
            int[][][] shapes = {
                    {{1, 3}, {2, 3}, {5, 3}, {6, 3}},                                                      // normal
                    {{1, 3}, {2, 3}, {5, 3}, {6, 3}, {1, 2}, {2, 2}, {5, 2}, {6, 2}, {0, 3}, {7, 3}},      // thick
                    {{1, 2}, {2, 3}, {5, 3}, {6, 2}},                                                      // angry
                    {{1, 3}, {2, 2}, {5, 2}, {6, 3}},                                                      // worried
                    {{1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3}, {6, 3}},                                      // joined
                    {}};                                                                                    // none
            for (int i = 0; i < shapes.length; i++) {
                Canvas c = face();
                for (int[] p : shapes[i]) px(c, p[0], p[1], TINT);
                c.save("entity/face/brows_" + i + ".png");
            }
        }

        static void mouths() throws IOException {
            int lip = 0xFF7A3E30, deep = 0xFF4A1414, teeth = 0xFFF2F0E8;
            for (int i = 0; i < 6; i++) {
                Canvas c = face();
                switch (i) {
                    case 0 -> { px(c, 3, 6, lip); px(c, 4, 6, lip); }                                      // neutral
                    case 1 -> { px(c, 2, 6, lip); px(c, 3, 7, lip); px(c, 4, 7, lip); px(c, 5, 6, lip); }    // smile
                    case 2 -> { px(c, 2, 6, deep); px(c, 3, 6, teeth); px(c, 4, 6, teeth); px(c, 5, 6, deep);  // grin
                        px(c, 3, 7, deep); px(c, 4, 7, deep); }
                    case 3 -> { px(c, 2, 7, lip); px(c, 3, 6, lip); px(c, 4, 6, lip); px(c, 5, 7, lip); }    // frown
                    case 4 -> { px(c, 2, 6, lip); px(c, 3, 6, lip); px(c, 4, 6, lip); px(c, 5, 5, lip); }    // smirk
                    default -> { px(c, 3, 6, deep); px(c, 4, 6, deep); px(c, 3, 7, deep); px(c, 4, 7, deep); } // shout
                }
                c.save("entity/face/mouth_" + i + ".png");
            }
        }

        static void noses() throws IOException {
            for (int i = 0; i < 4; i++) {
                Canvas c = face();
                switch (i) {
                    case 0 -> { px(c, 3, 5, 0x38000000); px(c, 4, 5, 0x38000000); }                       // shadow
                    case 1 -> { }                                                                            // none
                    case 2 -> px(c, 4, 5, 0x60000000);                                                       // dot
                    default -> { px(c, 3, 4, 0x38FFFFFF); px(c, 3, 5, 0x48000000); px(c, 4, 5, 0x28000000); } // bridge
                }
                c.save("entity/face/nose_" + i + ".png");
            }
        }

        static void extras() throws IOException {
            for (int i = 0; i < 8; i++) {
                Canvas c = face();
                switch (i) {
                    case 1 -> { px(c, 1, 5, 0x70FF6080); px(c, 6, 5, 0x70FF6080); px(c, 0, 5, 0x40FF6080); px(c, 7, 5, 0x40FF6080); }  // blush
                    case 2 -> { px(c, 1, 5, 0x90804A28); px(c, 2, 6, 0x90804A28); px(c, 5, 6, 0x90804A28); px(c, 6, 5, 0x90804A28); }  // freckles
                    case 3 -> { for (int y : new int[]{5, 6}) { px(c, 0, y, 0xC0201818); px(c, 7, y, 0xC0201818); }                    // whiskers
                        px(c, 1, 6, 0x80201818); px(c, 6, 6, 0x80201818); }
                    case 4 -> { for (int y : new int[]{5, 6}) { px(c, 1, y, 0xFFC02020); px(c, 6, y, 0xFFC02020); } }                  // war paint
                    case 5 -> { px(c, 3, 1, WHITE); px(c, 4, 1, 0xFFC01830); px(c, 3, 0, LASH); px(c, 4, 0, LASH); }                  // third eye
                    case 6 -> { for (int x = 1; x <= 6; x++) { if (x != 3 && x != 4) px(c, x, 6, 0x40201010); px(c, x, 7, 0x40201010); } } // stubble
                    case 7 -> { px(c, 3, 1, 0xFFE03050); px(c, 4, 1, 0xFFE03050); px(c, 3, 0, 0xFFFF80A0); px(c, 4, 2, 0xFFA01030); } // forehead gem
                    default -> { }
                }
                c.save("entity/face/extra_" + i + ".png");
            }
        }
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
            gi("namekian", 0xFF5A3A8A, 0xFF40B0E0, 0xFF5A3418);
            gi("majin", 0xFF26222E, 0xFFE0B040, 0xFFB8862A);
            gi("hoodie", 0xFFC8283A, 0xFFF2F0F4, 0xFFF2F0F4);
            gi("fusion", 0xFF1F6F80, 0xFF3A62C8, 0xFF24242C);
            potara();
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

        /** Potara earrings: a pair, each a gold ring through the lobe with a round green bead hanging under it. */
        static void potara() throws IOException {
            Canvas c = new Canvas(16, 16);
            int[] gold = ramp(0xFFE8B838, 5), bead = ramp(0xFF3CC860, 5);
            for (int[] at : new int[][]{{4, 3}, {11, 5}}) {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    double d = Math.hypot(x + 0.5 - at[0], (y + 0.5 - at[1]) / 1.1);
                    if (d >= 1.4 && d <= 2.5) c.set(x, y, gold[(int) clamp((float) (2.5 - (y + 0.5 - at[1]) * 0.8), 0, 4)]);
                }
                c.sphere(at[0], at[1] + 6.2, 2.9, 2.9, bead, true);
            }
            c.outline().save("item/potara_earrings.png");
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
            battleArmor("battle_armor", 0xFFE8ECF0, 0xFFD8B040, 0xFF2A2E48);
            battleArmor("frost_armor", 0xFFF2F0F6, 0xFF8A4AC8, 0xFF1C1A24);
        }

        static void battleArmor(String name, int plateC, int trimC, int suitC) throws IOException {
            int[] w = ramp(plateC, 5), y = ramp(trimC, 4), d = ramp(suitC, 4);
            Canvas top = new Canvas(16, 16);
            top.rect(4, 4, 11, 13, d[2]);                                                    // undersuit
            top.sphere(8, 8, 4.6, 5, w, false);                                              // chest plate
            top.sphere(3, 4.5, 2.6, 2.2, y, false).sphere(13, 4.5, 2.6, 2.2, y, false);      // shoulder pads
            top.rect(6, 3, 9, 4, d[1]);                                                      // collar
            top.hline(4, 11, 12, w[1]).hline(5, 10, 13, d[1]);
            top.outline().save("item/" + name + "_top.png");

            Canvas pants = new Canvas(16, 16);
            pants.rect(3, 2, 12, 5, d[2]).rect(3, 5, 7, 14, d[2]).rect(8, 5, 12, 14, d[2]);
            pants.rect(3, 2, 12, 3, w[3]).rect(3, 4, 5, 6, w[2]).rect(10, 4, 12, 6, w[2]);  // belt + hip guards
            pants.vline(7, 6, 14, d[1]).vline(8, 6, 14, d[3]);
            pants.edgeShade(0.2).outline().save("item/" + name + "_pants.png");

            Canvas b = new Canvas(16, 16);
            for (int s = 0; s < 2; s++) {
                int x0 = s == 0 ? 2 : 9;
                b.rect(x0, 3, x0 + 4, 12, w[2]).rect(x0, 12, x0 + 5, 14, w[1]);
                b.rect(x0, 3, x0 + 4, 5, y[2]).hline(x0, x0 + 4, 5, y[1]);                 // gold tops
                b.vline(x0, 6, 12, w[3]);
            }
            b.edgeShade(0.2).outline().save("item/" + name + "_boots.png");
        }
    }

    // ================================================================== blocks

    static final class Blocks {
        static void all() throws IOException {
            for (int star = 1; star <= 7; star++) dragonBall(star);
            otherBalls();
            punchingBag();
            gravityChamber();
            timeChamberDoor();
            namekTree();
            otherworld();
        }


        /** The other world: a golden cloud sea, the scales of Snake Way, and the shimmering springs of paradise (animated). */
        static void otherworld() throws IOException {
            Canvas cloud = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double puff = 0;
                for (int k = 0; k < 5; k++) {
                    double cx = (k * 7 + 3) % 16, cy = (k * 5 + 4) % 16, rr = 3.5 + (k % 3);
                    double dx = Math.min(Math.abs(x + 0.5 - cx), 16 - Math.abs(x + 0.5 - cx)), dy = Math.min(Math.abs(y + 0.5 - cy), 16 - Math.abs(y + 0.5 - cy));
                    puff = Math.max(puff, 1 - Math.hypot(dx, dy) / rr);
                }
                int c = mix(0xFFF2DCA0, 0xFFFFFBEC, Math.min(1, puff * 1.3));
                cloud.set(x, y, (int) Math.round(170 + 50 * puff) << 24 | (c & 0xFFFFFF));
            }
            cloud.save("block/otherworld_cloud.png");

            int[] sc = ramp(0xFFE8A030, 6);
            Canvas scale = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int row = y / 4, ox = (row % 2) * 2;
                int cx = ((x + ox) / 4) * 4 + 2 - ox;
                double d = Math.hypot(x + 0.5 - cx, (y % 4) + 0.5 - 0.5);                     // overlapping half-round scales
                int i = d > 2.4 ? 1 : 3 + (y % 4 == 0 ? 1 : 0) - (d > 1.8 ? 1 : 0);
                if (noise(x, y, 907) > 0.9) i--;
                scale.set(x, y, sc[Math.max(0, Math.min(5, i))]);
            }
            scale.save("block/snake_scale.png");

            int frames = 8;
            Canvas spring = new Canvas(16, 16 * frames);
            for (int f = 0; f < frames; f++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double t = f / (double) frames * Math.PI * 2;
                double w = 0.5 + 0.25 * Math.sin(x * 0.8 + t) + 0.25 * Math.sin(y * 0.7 - t + x * 0.3);
                int c = mix(0xFF2AC8C0, 0xFF9FF4E8, w);
                if (Math.sin(x * 1.7 + y * 1.3 + t * 2) > 0.93) c = 0xFFFFF0A0;                  // glints of gold
                spring.set(x, f * 16 + y, 0xD0000000 | (c & 0xFFFFFF));
            }
            spring.save("block/sacred_spring.png");
            java.nio.file.Files.writeString(java.nio.file.Path.of(RES + "block/sacred_spring.png.mcmeta"), "{\"animation\": {\"frametime\": 3}}\n");
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
         * The Black Star Dragon Balls (12d): deep red glass with black stars, on the same small model as Earth's; and the
         * Super Dragon Balls: big gold-orange spheres filling their block, with large red stars (2x2 pixels each).
         */
        static void otherBalls() throws IOException {
            int[] red = ramp(0xFFD0301A, 6), gold = ramp(0xFFF09418, 6);
            Canvas shell = new Canvas(16, 16), bigShell = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                shell.set(x, y, red[(int) clamp((float) ((0.66 - (y - 4) * 0.03) * 6), 0, 5)]);
                bigShell.set(x, y, gold[(int) clamp((float) ((0.5 - (y - 8) * 0.02) * 6), 0, 5)]);
            }
            shell.set(6, 6, 0xFFFFD8C8).set(7, 6, 0xFFFF9A80).set(6, 7, 0xFFFF9A80);
            bigShell.set(4, 3, 0xFFFFFFFF).set(5, 3, 0xFFFFF0C0).set(4, 4, 0xFFFFF0C0).set(3, 4, 0xFFFFE8A0);
            shell.save("block/black_star_ball_shell.png");
            bigShell.save("block/super_dragon_ball_shell.png");
            for (int star = 1; star <= 7; star++) {
                Canvas c = new Canvas(16, 16);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) c.set(x, y, shell.get(x, y));
                for (int[] s : STARS[star - 1]) c.set(5 + s[0], 5 + s[1], 0xFF140606);
                c.set(5, 5, mix(c.get(5, 5), 0xFFFFFFFF, 0.45));
                c.save("block/black_star_ball_" + star + ".png");
                Canvas b = new Canvas(16, 16);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) b.set(x, y, bigShell.get(x, y));
                for (int[] s : STARS[star - 1]) {
                    int x = 2 + s[0] * 2, y = 2 + s[1] * 2;
                    b.set(x, y, 0xFFD81010).set(x + 1, y, 0xFFD81010).set(x, y + 1, 0xFFB80C0C).set(x + 1, y + 1, 0xFFB80C0C);
                }
                b.set(3, 3, mix(b.get(3, 3), 0xFFFFFFFF, 0.6));
                b.save("block/super_dragon_ball_" + star + ".png");
            }
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

    // ================================================================== metals and alloys (12e)

    /**
     * Metals and alloys: an ingot for each (a bevelled bar, lit from the top left), raw chunks for the ores (lumpy,
     * speckled), blend piles for the alloys (a mound of mixed grains in the colours that go into it), ore blocks (the
     * stone, or Limbo's blackstone, with veins of the metal), the riveted Katchin plates and the denser weights.
     */
    static final class Metals {
        static final String[][] METALS = {
                {"tin", "C8CCD4"}, {"zinc", "98A6B2"}, {"silver", "E6EAF2"}, {"titanium", "8C9CB4"}, {"tungsten", "5C6068"},
                {"mithril", "9AE0F0"}, {"adamantium", "7A5AA0"}, {"katchin", "4A8A7C"},
                {"bronze", "C88A3A"}, {"brass", "D8B850"}, {"steel", "7E848E"}, {"electrum", "E8D890"}, {"durasteel", "5A6C80"},
                {"tungsten_carbide", "3C4048"}, {"orichalcum", "E8803A"}, {"celestial_bronze", "F0CC70"}, {"kachi_katchin", "3A4C8A"}};
        static final java.util.Set<String> ORES = java.util.Set.of("tin", "zinc", "silver", "titanium", "tungsten", "mithril", "adamantium", "katchin");
        /** What each alloy is made of, for the grains in its blend. */
        static final java.util.Map<String, int[]> GRAINS = java.util.Map.of(
                "bronze", new int[]{0xFFE07850, 0xFFC8CCD4}, "brass", new int[]{0xFFE07850, 0xFF98A6B2},
                "steel", new int[]{0xFFD8D8D8, 0xFF2A2A2E}, "electrum", new int[]{0xFFF0C840, 0xFFE6EAF2},
                "durasteel", new int[]{0xFF7E848E, 0xFF8C9CB4}, "tungsten_carbide", new int[]{0xFF5C6068, 0xFF8C9CB4},
                "orichalcum", new int[]{0xFF9AE0F0, 0xFF7A5AA0}, "celestial_bronze", new int[]{0xFFE8D890, 0xFFE8803A, 0xFF4A8A7C},
                "kachi_katchin", new int[]{0xFF4A8A7C, 0xFFF0CC70, 0xFFB070FF});

        static void all() throws IOException {
            for (String[] m : METALS) {
                int c = 0xFF000000 | Integer.parseInt(m[1], 16);
                ingot(m[0], c);
                if (ORES.contains(m[0])) {
                    raw(m[0], c);
                    ore(m[0], c, m[0].equals("adamantium"));
                } else {
                    blend(m[0], c, GRAINS.get(m[0]));
                }
            }
            plates("katchin_block", 0xFF4A8A7C, false);
            plates("kachi_katchin_block", 0xFF3A4C8A, true);
            Items.weights("tungsten_training_weights", 0xFF4A4E58, 0xFF2A1A12, true);
            Items.weights("katchin_training_weights", 0xFF3E7A6C, 0xFF1A1A22, true);
        }

        /** A bar seen from above the front: a light top, the mid front, a dark end, a hard rim and a glint. */
        static void ingot(String name, int color) throws IOException {
            int[] r = ramp(color, 6);
            Canvas c = new Canvas(16, 16);
            c.paint(0, 0, java.util.Map.of('#', r[0], 't', r[4], 'T', r[5], 'f', r[3], 'F', r[2], 's', r[1]),
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "......########..",
                    "....##TTtttttt#.",
                    "..##TTtttttttt#.",
                    ".#tttttttttt##s#",
                    ".#ffffffffff#ss#",
                    ".#fFffffffff#ss#",
                    ".#FFFFFFFFFF#s#.",
                    ".#FFFFFFFFFF##..",
                    "..##########....",
                    "................",
                    "................");
            c.set(5, 6, mix(r[5], 0xFFFFFFFF, 0.7)).set(6, 6, mix(r[5], 0xFFFFFFFF, 0.4));
            c.save("item/" + name + "_ingot.png");
        }

        /** A raw lump: three rounded knobs, lit from the top, speckled, rimmed. */
        static void raw(String name, int color) throws IOException {
            int[] r = ramp(color, 6), rock = ramp(0xFF6A6A70, 4);
            Canvas c = new Canvas(16, 16);
            double[][] knobs = {{6.5, 9, 4.2}, {10, 8, 3.6}, {8, 5.5, 3.2}};
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double best = 9;
                for (double[] k : knobs) best = Math.min(best, Math.hypot(x + 0.5 - k[0], y + 0.5 - k[1]) / k[2]);
                if (best > 1) continue;
                int i = (int) clamp((float) (4.2 - (y - 4) * 0.35 - best * 1.5), 0, 5);
                boolean rocky = noise(x, y, name.hashCode()) > 0.72;
                c.set(x, y, rocky ? rock[Math.min(3, i / 2 + 1)] : r[i]);
            }
            c.outline().save("item/raw_" + name + ".png");
        }

        /** A mound of grains: the alloy's own colour mixed with the colours of what goes into it. */
        static void blend(String name, int color, int[] grains) throws IOException {
            int[] r = ramp(color, 6);
            Canvas c = new Canvas(16, 16);
            for (int y = 5; y < 14; y++) for (int x = 1; x < 15; x++) {
                double half = (y - 4) * 0.85;                                          // a cone, flattened at the foot
                if (Math.abs(x + 0.5 - 8) > Math.min(6.6, half)) continue;
                int i = (int) clamp((float) (4.5 - (y - 5) * 0.35 - (x - 4) * 0.12), 0, 5);
                double n = noise(x, y, name.hashCode());
                int px = r[i];
                if (n > 0.66) px = mix(grains[(x + y) % grains.length], r[i], 0.25);   // grains of the ingredients
                else if (n < 0.12) px = r[Math.max(0, i - 2)];
                c.set(x, y, px);
            }
            c.set(8, 5, mix(r[5], 0xFFFFFFFF, 0.5));
            c.outline().save("item/" + name + "_blend.png");
        }

        /** Stone (or blackstone) with veins of the metal: clusters with a lit edge and a dark rim. */
        static void ore(String name, int color, boolean blackstone) throws IOException {
            int[] r = ramp(color, 6), st = ramp(blackstone ? 0xFF2E282E : 0xFF7E7E80, 5);
            Canvas c = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double n = noise(x, y, 17), m = noise(x / 2, y / 2, 29);
                int i = n > 0.8 ? 3 : n < 0.2 ? 1 : 2;
                if (m > 0.85) i = Math.max(0, i - 1);
                c.set(x, y, st[i]);
            }
            boolean dark = FormLooks.lum(color) < 110;                                 // dark metals (tungsten) need a brighter glint
            int[][] veins = {{3, 3}, {11, 4}, {7, 9}, {12, 12}, {2, 11}};
            java.util.Random rnd = new java.util.Random(name.hashCode());
            for (int[] v : veins) {
                for (int k = 0; k < 7; k++) {                                          // bold enough to spot against the stone
                    int x = v[0] + rnd.nextInt(3) - 1, y = v[1] + rnd.nextInt(3) - 1;
                    if (x < 0 || y < 0 || x > 15 || y > 15) continue;
                    c.set(x, y, mix(r[3 + rnd.nextInt(3)], 0xFFFFFFFF, dark ? 0.42 : 0.15));
                }
                c.set(v[0], v[1], r[5]);
                if (v[1] + 2 < 16) c.set(v[0], v[1] + 2, r[0]);
            }
            c.save("block/" + name + "_ore.png");
        }

        /** Riveted plates of the hardest metals: four bevelled panels, a rivet at each corner, a faint sheen. */
        static void plates(String name, int color, boolean gilded) throws IOException {
            int[] r = ramp(color, 6), gold = ramp(0xFFE8C050, 4);
            Canvas c = new Canvas(16, 16);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int px = x % 8, py = y % 8;
                int i = 3;
                if (px == 0 || py == 0) i = 5;                                         // the lit edge of each panel
                else if (px == 7 || py == 7) i = 0;                                    // its shadowed edge
                else if (noise(x, y, name.hashCode()) > 0.8) i = 4;
                c.set(x, y, r[i]);
                if ((px == 1 || px == 6) && (py == 1 || py == 6)) c.set(x, y, gilded ? gold[3] : r[5]);   // rivets
            }
            if (gilded) for (int i = 2; i < 14; i += 4) c.set(i, i, mix(gold[2], r[4], 0.4));                 // a gold sheen
            c.save("block/" + name + ".png");
        }
    }

    // ================================================================== armor layers (64x32, vanilla layout)

    static final class Armor {
        static void all() throws IOException {
            gi("turtle", 0xFFF07820, 0xFF2852C8, 0xFF2852C8, 0xFF2852C8);
            gi("demon", 0xFF6A3A9A, 0xFFC02838, 0xFF2A1A2E, 0xFF2A1A2E);
            gi("namekian", 0xFF5A3A8A, 0xFF40B0E0, 0xFF5A3418, 0xFF46306E);
            gi("majin", 0xFF26222E, 0xFFE0B040, 0xFFB8862A, 0xFFF2F0F4);
            gi("hoodie", 0xFFC8283A, 0xFF24242C, 0xFFF2F0F4, 0xFF24242C);
            gi("fusion", 0xFF1F6F80, 0xFF3A62C8, 0xFF24242C, 0xFFF2F0F4);
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
            battleArmor("battle_armor", 0xFFE8ECF0, 0xFFD8B040, 0xFF2A2E48);
            battleArmor("frost_armor", 0xFFF2F0F6, 0xFF8A4AC8, 0xFF1C1A24);
        }

        static void battleArmor(String name, int plateC, int trimC, int suitC) throws IOException {
            int[] w = ramp(plateC, 5), gold = ramp(trimC, 4), suit = ramp(suitC, 4);
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
            l1.save("models/armor/" + name + "_layer_1.png");

            Canvas l2 = new Canvas(64, 32);
            box(l2, 0, 16, 4, 12, 4, (f, x, y, wd, h) -> f == Face.FRONT ? suit[2] : suit[1]);
            box(l2, 16, 16, 8, 12, 4, (f, x, y, wd, h) -> {                                   // hip guards
                if (f == Face.TOP || f == Face.BOTTOM) return 0;
                if (y >= 9) return y == 9 ? w[3] : w[2];
                return 0;
            });
            l2.save("models/armor/" + name + "_layer_2.png");
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
            facelessPaint = path.startsWith("entity/race/");
            faceSeen = false;
            if (head != null) box(c, 0, 0, 8, 8, 8, head);
            if (body != null) box(c, 16, 16, 8, 12, 4, body);
            if (arm != null) { box(c, 40, 16, 4, 12, 4, arm); box(c, 32, 48, 4, 12, 4, mirror(arm)); }
            if (leg != null) { box(c, 0, 16, 4, 12, 4, leg); box(c, 16, 48, 4, 12, 4, mirror(leg)); }
            if (hat != null) box(c, 32, 0, 8, 8, 8, hat);
            if (jacket != null) box(c, 16, 32, 8, 12, 4, jacket);
            if (sleeve != null) { box(c, 40, 32, 4, 12, 4, sleeve); box(c, 48, 48, 4, 12, 4, mirror(sleeve)); }
            if (pants != null) { box(c, 0, 32, 4, 12, 4, pants); box(c, 0, 48, 4, 12, 4, mirror(pants)); }
            c.save(path);
            if (facelessPaint && faceSeen) {
                String name = path.substring("entity/race/".length(), path.length() - 4);
                FACE_DEFAULTS.put(name, String.format("{\"iris\": %d, \"brow\": %d, \"whites\": %b}", faceEye & 0xFFFFFF, faceBrow & 0xFFFFFF, faceWhites));
            }
            facelessPaint = false;
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
    // race skins leave the face to FaceLayer; what their face looked like goes to face_defaults.json
    static boolean facelessPaint, faceSeen, faceWhites;
    static int faceEye, faceBrow;
    static final java.util.Map<String, String> FACE_DEFAULTS = new java.util.LinkedHashMap<>();

    static int face(int[] sk, int x, int y, int eye, int brow, boolean whites) {
        faceSeen = true;
        faceEye = eye;
        faceBrow = brow;
        faceWhites = whites;
        if (facelessPaint) return sk[3];
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
            int[] shorts = ramp(0xFF26346E, 5);
            Skin s = new Skin();
            s.head = (f, x, y, w, h) -> 0;                                                  // the face is drawn by parts (Faces)
            s.body = (f, x, y, w, h) -> y == 10 ? 0xFF121A36 : y == 11 ? shorts[f == Face.FRONT ? 3 : 2] : 0;   // CX-16a: shorts only
            s.arm = (f, x, y, w, h) -> 0;
            s.leg = (f, x, y, w, h) -> {
                if (y > 4 || f == Face.BOTTOM || f == Face.TOP) return 0;
                if ((f == Face.LEFT || f == Face.RIGHT) && x == 1) return 0xFFE0DCD4;
                return shorts[(f == Face.FRONT ? 3 : f == Face.BACK ? 1 : 2) - (y == 4 ? 1 : 0)];
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
            dragon("eternal_dragon", 0xFF2E9A48, 0xFFE8D890, 0xFFE0B040, 0xFFFF2020);
            dragon("black_star_dragon", 0xFFB0281C, 0xFFE8C890, 0xFF4A1410, 0xFFFFD030);   // 12d: red, dark spines, gold eyes
            dragon("super_dragon", 0xFFE8A828, 0xFFFFF0B8, 0xFFFF7020, 0xFFFF2020);        // 12d: gold, orange spines
        }

        static void dragon(String name, int bodyC, int bellyC, int spineC, int eyeC) throws IOException {
            int[] g = ramp(bodyC, 5), belly = ramp(bellyC, 4), horn = ramp(0xFFE8DCC0, 4), gold = ramp(spineC, 3);
            Canvas c = new Canvas(64, 64);
            box(c, 0, 0, 8, 8, 12, (f, x, y, w, h) -> f == Face.BOTTOM ? belly[(y % 3 == 0) ? 1 : 2] : scales(g, f, x, y)); // body segment
            box(c, 40, 0, 2, 4, 2, (f, x, y, w, h) -> y == 0 ? gold[2] : gold[1]);                      // dorsal spike
            box(c, 48, 0, 1, 1, 1, (f, x, y, w, h) -> eyeC);                                          // eyes
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
            c.save("entity/" + name + ".png");
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
            greatApe("great_ape", 0xFF6A4224, 0xFFFF2018);
            greatApe("great_ape_golden", 0xFFE0A830, 0xFFFF2018);                              // the golden ape
            greatApe("great_ape_legendary", 0xFFC8B83A, 0xFF7CFF4A);                           // the legendary ape, green-eyed
        }

        static void greatApe(String name, int furColor, int eyeColor) throws IOException {
            int[] fur = ramp(furColor, 5), skin = ramp(0xFFC89A70, 4), dark = ramp(0xFF2A1A12, 3);
            Canvas c = new Canvas(128, 64);
            box(c, 0, 0, 9, 8, 8, (f, x, y, w, h) -> {                                          // skull
                if (f == Face.FRONT) {
                    if (y == 4 && (x == 2 || x == 6)) return eyeColor;                            // the eyes
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
            c.save("entity/" + name + ".png");
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
            Canvas[] ic = new Canvas[13];
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
            // 12 racial skills: a double helix, gold and blue, with rungs
            for (int y = 1; y < 15; y++) {
                double ph = (y - 1) / 13.0 * Math.PI * 2;
                int xa = (int) Math.round(7.5 + Math.sin(ph) * 4.5), xb = (int) Math.round(7.5 - Math.sin(ph) * 4.5);
                if (y % 3 == 0) ic[12].hline(Math.min(xa, xb), Math.max(xa, xb), y, 0xFF8A8A98);
                ic[12].set(xa, y, Math.cos(ph) > 0 ? gold[4] : gold[1]).set(xb, y, Math.cos(ph) < 0 ? blue[4] : blue[1]);
                ic[12].set(xa + 1, y, Math.cos(ph) > 0 ? gold[3] : gold[0]).set(xb + 1, y, Math.cos(ph) < 0 ? blue[3] : blue[0]);
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
