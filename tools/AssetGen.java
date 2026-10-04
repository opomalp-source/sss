import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Generates the mod's ORIGINAL placeholder assets and test fixtures, so they are reproducible and reviewable.
 * Run from the repo root with JDK 17+:  java tools/AssetGen.java
 *
 * Every texture here is a placeholder listed in ASSETS_TODO.md.
 */
public class AssetGen {
    static final String RES = "src/main/resources/";

    public static void main(String[] args) throws IOException {
        // --- textures (16x16 pixel art, drawn from char maps) ---
        pixelArt("assets/dbzenith/textures/item/senzu_bean.png", Map.of(
                '#', 0xFF1E4D12, 'a', 0xFF5DAA2E, 'b', 0xFF9BE05A, 'c', 0xFF3C7A1E),
                "................",
                "................",
                "................",
                "......####......",
                ".....#aaaa##....",
                "....#abbaaaa#...",
                "...#abbbaaaaa#..",
                "...#abbaaaaaa#..",
                "..#aaaaaaaaac#..",
                "..#aaaaaaaaac#..",
                "..#caaaaaacc#...",
                "...#ccaaacc#....",
                "....##cccc#.....",
                "......####......",
                "................",
                "................");

        pixelArt("assets/dbzenith/textures/item/moon_orb.png", Map.of(
                '#', 0xFF8A93B8, 'a', 0xFFDDE4FF, 'b', 0xFFFFFFFF, 'c', 0xFFB7C1E8),
                "................",
                "................",
                ".....######.....",
                "....#aaaaaa#....",
                "...#abbaaaaa#...",
                "..#abbbaaaaac#..",
                "..#abbaaaaaac#..",
                "..#aaaaaaaaac#..",
                "..#aaaaaaaaac#..",
                "..#aaaaaaaacc#..",
                "..#caaaaaacc#...",
                "...#ccaaacc#....",
                "....#cccccc#....",
                ".....######.....",
                "................",
                "................");

        pixelArt("assets/dbzenith/textures/item/technique_scroll.png", Map.of(
                '#', 0xFF5A3A1C, 'p', 0xFFEAD9B0, 's', 0xFFC8B080, 'r', 0xFFB02020, 'k', 0xFF3A2A1A),
                "................",
                "..##########....",
                ".#pppppppppp#...",
                ".#psssssssp#....",
                "..#pkkkkkkp#....",
                "..#psssssp#.....",
                "..#pkkkkkp#.....",
                "..#psssssp#.....",
                "..#pkkkp.p#.....",
                "..#pppppp#......",
                "..#pprrpp#......",
                "..#pprrpp#......",
                ".#pppppppp#.....",
                ".##########.....",
                "................",
                "................");

        // --- training blocks (16x16 procedural) ---
        texture("assets/dbzenith/textures/block/gravity_chamber.png", 16, 16, (x, y) -> {
            boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
            boolean grid = x % 5 == 0 || y % 5 == 0;
            double d = Math.hypot(x - 7.5, y - 7.5);
            if (d < 2.6) return d < 1.4 ? 0xFFFF8080 : 0xFFE02020;      // red core lamp
            if (edge) return 0xFF2A2A30;
            return grid ? 0xFF4A4C55 : 0xFF6B6E78;
        });
        texture("assets/dbzenith/textures/block/punching_bag.png", 16, 16, (x, y) -> {
            if (y == 2 || y == 13) return 0xFF3A1A10;                    // straps
            if (x == 7 || x == 8) return 0xFF8A2A20;                     // seam
            int shade = (x + y) % 4 == 0 ? 0xFFA82E24 : 0xFFC0392B;
            return x < 2 || x > 13 ? 0xFF8A2A20 : shade;
        });
        texture("assets/dbzenith/textures/block/time_chamber_door.png", 16, 16, (x, y) -> {
            if (x == 0 || x == 15 || y == 0 || y == 15) return 0xFFD4AF37; // gold frame
            if (x >= 4 && x <= 11 && y >= 3) return (x == 4 || x == 11 || y == 3) ? 0xFFD4AF37 : 0xFF1A1A22; // doorway
            return 0xFFF4F4F4;
        });
        // --- training weights (items + armor layer) ---
        texture("assets/dbzenith/textures/item/training_weights.png", 16, 16, (x, y) -> vest(x, y, 0xFF55575F, 0xFF8A6A3A));
        texture("assets/dbzenith/textures/item/heavy_training_weights.png", 16, 16, (x, y) -> vest(x, y, 0xFF2E3036, 0xFFB03020));
        texture("assets/dbzenith/textures/models/armor/weights_layer_1.png", 64, 32, (x, y) -> {
            boolean body = x >= 16 && x < 40 && y >= 16 && y < 32;
            if (!body) return 0;
            return (y - 16) % 4 == 0 ? 0xFF8A6A3A : 0xFF55575F;           // vest with straps
        });
        texture("assets/dbzenith/textures/models/armor/weights_layer_2.png", 64, 32, (x, y) -> 0);

        // --- dragon balls: orange glassy sphere with N red stars ---
        int[][][] starLayouts = {
                {{8, 8}},
                {{6, 8}, {10, 8}},
                {{8, 5}, {5, 10}, {11, 10}},
                {{5, 5}, {11, 5}, {5, 11}, {11, 11}},
                {{8, 4}, {4, 8}, {12, 8}, {6, 12}, {10, 12}},
                {{5, 4}, {11, 4}, {4, 8}, {12, 8}, {5, 12}, {11, 12}},
                {{8, 3}, {4, 6}, {12, 6}, {8, 8}, {4, 11}, {12, 11}, {8, 13}}};
        for (int n = 1; n <= 7; n++) {
            int[][] stars = starLayouts[n - 1];
            texture("assets/dbzenith/textures/block/dragon_ball_" + n + ".png", 16, 16, (x, y) -> {
                for (int[] s : stars) {
                    int dx = Math.abs(x - s[0]);
                    int dy = Math.abs(y - s[1]);
                    if (dx + dy <= 1) return 0xFFD01818;                         // small red star
                }
                double d = Math.hypot(x - 6.0, y - 5.5) / 11.0;                  // highlight top-left
                int r = 255;
                int g = (int) Math.max(90, 200 - d * 120);
                int b = (int) Math.max(0, 70 - d * 90);
                if (Math.hypot(x - 5, y - 4) < 1.6) return 0xFFFFF4D0;            // glint
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            });
        }
        texture("assets/dbzenith/textures/item/dragon_radar.png", 16, 16, (x, y) -> {
            double d = Math.hypot(x - 7.5, y - 8.5);
            if (d > 7) return 0;
            if (d > 6) return 0xFFB0B0B8;                                        // casing
            if (y == 2 && x >= 6 && x <= 9) return 0xFF808088;                   // button
            if (Math.abs(x - 7.5) < 0.6 || Math.abs(y - 8.5) < 0.6) return 0xFF40FF60;
            if ((x == 11 && y == 6) || (x == 4 && y == 11)) return 0xFFFFC020;   // blips
            return 0xFF104A20;
        });

        // --- gear: scouter, capsule, gi sets (items + armor layers) ---
        texture("assets/dbzenith/textures/item/scouter.png", 16, 16, (x, y) -> {
            if (x >= 2 && x <= 13 && y >= 9 && y <= 10) return 0xFF505058;            // band
            if (x >= 9 && x <= 14 && y >= 3 && y <= 8) return (x == 9 || x == 14 || y == 3 || y == 8) ? 0xFF202024 : 0xC040FF70; // lens
            if (x >= 2 && x <= 4 && y >= 7 && y <= 12) return 0xFFE0E0E0;            // ear piece
            return 0;
        });
        texture("assets/dbzenith/textures/item/capsule.png", 16, 16, (x, y) -> {
            double d = Math.hypot((x - 7.5) / 3.2, (y - 7.5) / 6.0);
            if (d > 1) return 0;
            if (y == 7 || y == 8) return 0xFF303038;                                   // seam
            if (y < 3 && x >= 6 && x <= 9) return 0xFFE02020;                          // button
            return x < 7 ? 0xFFF8F8F8 : 0xFFD8D8E0;
        });
        int[][] giColors = {{0xFFF07820, 0xFF2040B0}, {0xFF7030A0, 0xFF402010}, {0xFFF4F4F4, 0xFF202028}};
        String[] giSets = {"turtle", "demon", "battle_armor"};
        for (int s = 0; s < 3; s++) {
            int main = giColors[s][0];
            int accent = giColors[s][1];
            String set = giSets[s];
            texture("assets/dbzenith/textures/item/" + set + "_top.png", 16, 16, (x, y) -> {
                boolean body = x >= 4 && x <= 11 && y >= 3 && y <= 13;
                boolean sleeves = y >= 3 && y <= 7 && (x == 2 || x == 3 || x == 12 || x == 13);
                if (y == 10 && x >= 4 && x <= 11) return accent;                       // belt / sash
                if (body) return (y < 5 && x >= 6 && x <= 9) ? accent : main;
                return sleeves ? accent : 0;
            });
            texture("assets/dbzenith/textures/item/" + set + "_pants.png", 16, 16, (x, y) -> {
                if (y >= 2 && y <= 4 && x >= 4 && x <= 11) return accent;              // waist
                boolean legs = y >= 5 && y <= 14 && ((x >= 4 && x <= 7) || (x >= 8 && x <= 11));
                return legs ? main : 0;
            });
            texture("assets/dbzenith/textures/item/" + set + "_boots.png", 16, 16, (x, y) -> {
                boolean boot = y >= 7 && y <= 13 && ((x >= 2 && x <= 6) || (x >= 9 && x <= 13));
                if (!boot) return 0;
                return y == 7 ? main : accent;
            });
            texture("assets/dbzenith/textures/models/armor/" + set + "_layer_1.png", 64, 32, (x, y) -> {
                boolean body = x >= 16 && x < 40 && y >= 16 && y < 32;
                boolean arms = x >= 40 && x < 56 && y >= 16 && y < 32;
                boolean feet = x >= 0 && x < 16 && y >= 26 && y < 32;
                if (body) return (y >= 26 && y <= 27) ? accent : main;
                if (arms) return y >= 26 ? accent : main;
                if (feet) return accent;
                return 0;
            });
            texture("assets/dbzenith/textures/models/armor/" + set + "_layer_2.png", 64, 32, (x, y) -> {
                boolean legs = x >= 0 && x < 16 && y >= 16 && y < 30;
                boolean waist = x >= 16 && x < 40 && y >= 26 && y < 32;
                if (legs) return main;
                if (waist) return accent;
                return 0;
            });
        }
        texture("assets/dbzenith/textures/models/armor/scouter_layer_1.png", 64, 32, (x, y) -> {
            // head front face is x 8..16, y 8..16: a green lens over the left eye and a band
            if (y >= 9 && y <= 12 && x >= 9 && x <= 12) return 0xB040FF70;
            if (y == 11 && ((x >= 0 && x < 9) || (x >= 16 && x < 24))) return 0xFF505058;
            return 0;
        });
        texture("assets/dbzenith/textures/models/armor/scouter_layer_2.png", 64, 32, (x, y) -> 0);

        // --- enemy fighters: 64x64 humanoid skins (head, eyes, body, arms, hands, legs, feet, accent) ---
        skin("sproutling", 0xFF4CA03A, 0xFFD02020, 0xFF3E8A30, 0xFF4CA03A, 0xFF3E8A30, 0xFF3E8A30, 0xFF2E6A24, 0xFF2A5A20);
        skin("ki_soldier", 0xFFE8C8A8, 0xFF202020, 0xFFEEEEEE, 0xFF2A2A38, 0xFFE8C8A8, 0xFF2A2A38, 0xFFEEEEEE, 0xFFE0C040);
        skin("android_unit", 0xFF9098A0, 0xFFFF2020, 0xFF4A4E58, 0xFF8890A0, 0xFF9098A0, 0xFF3A3E48, 0xFF2A2E38, 0xFFC02020);
        skin("tyrant_lord", 0xFFF4F0F8, 0xFFD01030, 0xFFF4F0F8, 0xFFF4F0F8, 0xFFF4F0F8, 0xFFF4F0F8, 0xFF7030A0, 0xFF7030A0);
        skin("rampage_brute", 0xFFB07040, 0xFFFF4020, 0xFF802020, 0xFFB07040, 0xFFB07040, 0xFF402820, 0xFF2A1A10, 0xFFFFC020);
        skin("martial_arts_master", 0xFFE8C0A0, 0xFF202020, 0xFFF07820, 0xFFE8C0A0, 0xFFE8C0A0, 0xFFF07820, 0xFF6A4020, 0xFF2040B0);
        skin("patrol_officer", 0xFFD8B090, 0xFF203060, 0xFF2A4AA0, 0xFF2A4AA0, 0xFFD8B090, 0xFF1A2A60, 0xFF101010, 0xFFE0E0E0);
        texture("assets/dbzenith/textures/item/space_pod.png", 16, 16, (x, y) -> {
            double d = Math.hypot(x - 7.5, y - 6.5);
            if (y >= 12 && (x == 4 || x == 11) && y <= 14) return 0xFF505058;       // legs
            if (d > 6) return 0;
            if (Math.hypot(x - 8.5, y - 5) < 2.4) return 0xFF7FD4FF;                // window
            return d > 5 ? 0xFF909098 : 0xFFE8E8EC;                                  // hull
        });
        texture("assets/dbzenith/textures/item/tyrant_sigil.png", 16, 16, (x, y) -> {
            double d = Math.hypot(x - 7.5, y - 7.5);
            if (d > 6.5) return 0;
            if (d > 5.5) return 0xFFD4AF37;
            if (Math.abs(x - 7.5) < 1.2 && y > 3 && y < 12) return 0xFFC890FF;
            return 0xFF3A1A50;
        });
        texture("assets/dbzenith/textures/item/rage_totem.png", 16, 16, (x, y) -> {
            if (x < 5 || x > 10 || y < 1 || y > 14) return 0;
            if (y == 4 && (x == 6 || x == 9)) return 0xFFFF4020;                    // eyes
            if (y == 7 && x >= 6 && x <= 9) return 0xFFFFFFFF;                       // teeth
            return (y % 4 == 0) ? 0xFF5A2A10 : 0xFF8A4A20;
        });

        // --- ki glow: soft radial white, tinted per technique at render time ---
        radialGlow("assets/dbzenith/textures/entity/ki_glow.png", 32);
        beamGlow("assets/dbzenith/textures/entity/ki_beam.png", 32);
        hairTexture("assets/dbzenith/textures/entity/form_hair.png");

        // --- gametest structure: empty 3x3x3 template ---
        emptyStructure("data/dbzenith/structures/empty.nbt", 3);
        System.out.println("AssetGen done");
    }

    interface Pixel {
        int argb(int x, int y);
    }

    static void texture(String path, int w, int h, Pixel pixel) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, pixel.argb(x, y));
        File out = new File(RES + path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /** Paints a 64x64 humanoid skin (zombie/player layout; left limbs mirror the right ones). */
    static void skin(String name, int head, int eyes, int body, int arms, int hands, int legs, int feet, int accent) throws IOException {
        texture("assets/dbzenith/textures/entity/fighter/" + name + ".png", 64, 64, (x, y) -> {
            if (y < 16 && x < 32) {                                                   // head
                if (y >= 8 && x >= 8 && x < 16) {                                     // face
                    if (y == 12 && (x == 9 || x == 10 || x == 13 || x == 14)) return (x == 10 || x == 13) ? eyes : 0xFFFFFFFF;
                    if (y == 14 && x >= 10 && x <= 13) return 0xFF301818;            // mouth
                }
                if (y < 8 && name.equals("tyrant_lord")) return accent;               // dome on top
                if (name.equals("martial_arts_master") && y >= 13 && y < 16 && x >= 8 && x < 16) return 0xFFF4F4F4; // white beard
                if (name.equals("patrol_officer") && y < 8) return 0xFF1A2A60;        // cap
                return head;
            }
            if (y >= 16 && y < 32) {
                if (x >= 16 && x < 40) {                                              // body
                    if (y >= 20 && y < 23 && x >= 20 && x < 28 && !name.equals("sproutling")) return accent; // collar/chest detail
                    if (y == 30 || y == 31) return accent;                            // belt
                    return body;
                }
                if (x >= 40 && x < 56) return y >= 29 ? hands : arms;                // arm + hand
                if (x < 16) return y >= 29 ? feet : legs;                            // leg + foot
            }
            return 0;
        });
    }

    /** A small vest icon: body color with strap color bands. */
    static int vest(int x, int y, int body, int strap) {
        boolean shape = (y >= 3 && y <= 13 && x >= 3 && x <= 12) && !(y < 6 && x >= 6 && x <= 9);
        if (!shape) return 0;
        if (x == 3 || x == 12 || y == 13) return 0xFF1A1A1E;
        return (y - 3) % 3 == 0 ? strap : body;
    }

    static void pixelArt(String path, Map<Character, Integer> palette, String... rows) throws IOException {
        BufferedImage img = new BufferedImage(rows[0].length(), rows.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                img.setRGB(x, y, palette.getOrDefault(rows[y].charAt(x), 0x00000000));
            }
        }
        File out = new File(RES + path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    static void radialGlow(String path, int size) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double c = (size - 1) / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.hypot(x - c, y - c) / (size / 2.0);
                double a = Math.max(0, 1 - d);
                a = Math.pow(a, 1.6);
                int alpha = (int) Math.round(255 * Math.min(1, a * 1.4));
                img.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        File out = new File(RES + path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /** Soft across the width (u), uniform along the length (v). */
    static void beamGlow(String path, int size) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double c = (size - 1) / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.abs(x - c) / (size / 2.0);
                double a = Math.pow(Math.max(0, 1 - d), 1.3);
                int alpha = (int) Math.round(255 * Math.min(1, a * 1.5));
                img.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        File out = new File(RES + path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /** Light strands (tinted per form at render time) plus a pure white patch at (40,0) for pupils. */
    static void hairTexture(String path) throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        java.util.Random rnd = new java.util.Random(7);
        int[] column = new int[64];
        for (int x = 0; x < 64; x++) column[x] = 200 + rnd.nextInt(56);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int v = Math.min(255, column[x] - (y % 8 == 7 ? 25 : 0) + rnd.nextInt(10));
                img.setRGB(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        for (int y = 0; y < 8; y++) for (int x = 40; x < 48; x++) img.setRGB(x, y, 0xFFFFFFFF);
        File out = new File(RES + path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    // Minimal NBT writer: just enough for a structure template.
    static void emptyStructure(String path, int size) throws IOException {
        File out = new File(RES + path);
        out.getParentFile().mkdirs();
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(out)))) {
            o.writeByte(10); o.writeUTF("");                 // root compound
            o.writeByte(3); o.writeUTF("DataVersion"); o.writeInt(3465); // 1.20.1
            o.writeByte(9); o.writeUTF("size"); o.writeByte(3); o.writeInt(3);
            o.writeInt(size); o.writeInt(size); o.writeInt(size);
            o.writeByte(9); o.writeUTF("palette"); o.writeByte(10); o.writeInt(1);
            o.writeByte(8); o.writeUTF("Name"); o.writeUTF("minecraft:air"); o.writeByte(0);
            o.writeByte(9); o.writeUTF("blocks"); o.writeByte(0); o.writeInt(0);
            o.writeByte(9); o.writeUTF("entities"); o.writeByte(0); o.writeInt(0);
            o.writeByte(0);                                   // end root
        }
        System.out.println("wrote " + out);
    }
}
