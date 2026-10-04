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
