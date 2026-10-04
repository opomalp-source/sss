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
        // Items, blocks, skins, armor and creatures are drawn by tools/ArtGen.java; this file keeps effect textures and test data.
        // --- ki glow: soft radial white, tinted per technique at render time ---
        radialGlow("assets/dbzenith/textures/entity/ki_glow.png", 32);
        beamGlow("assets/dbzenith/textures/entity/ki_beam.png", 32);

        // --- gametest structure: empty 3x3x3 template ---
        // --- status effect icons (18x18) ---
        pixelArt("assets/dbzenith/textures/mob_effect/stun.png", Map.of(
                '#', 0xFF7A6A10, 'a', 0xFFF2E94E, 'b', 0xFFFFFFC0),
                "..................",
                "..........###.....",
                ".........#aa#.....",
                "........#aab#.....",
                ".......#aab#......",
                "......#aab#.......",
                ".....#aabb####....",
                "....#aabbbbaa#....",
                "....####bbaa#.....",
                ".......#baa#......",
                "......#baa#.......",
                ".....#baa#........",
                ".....#aa#.........",
                "....#aa#..........",
                "....#a#...........",
                "....##............",
                "..................",
                "..................");
        pixelArt("assets/dbzenith/textures/mob_effect/ki_seal.png", Map.of(
                '#', 0xFF2E1A4A, 'a', 0xFF6A3FA0, 'b', 0xFFB48CF0, 'c', 0xFFE8DAFF),
                "..................",
                "......######......",
                "....##aaaaaa##....",
                "...#aabbbbbbaa#...",
                "..#abb######bba#..",
                "..#ab#cccccc#ba#..",
                ".#ab#cc####cc#ba#.",
                ".#ab#c#....#c#ba#.",
                ".#ab#c#....#c#ba#.",
                ".#ab#c#....#c#ba#.",
                ".#ab#c#....#c#ba#.",
                ".#ab#cc####cc#ba#.",
                "..#ab#cccccc#ba#..",
                "..#abb######bba#..",
                "...#aabbbbbbaa#...",
                "....##aaaaaa##....",
                "......######......",
                "..................");

        // --- scars and tattoos: 64x64 skin-layout overlays, transparent except the mark ---
        int scarColor = 0xFFE07888, ink = 0xFF14143A;
        overlay("scar_eye", (x, y) -> (x == 13 || x == 14) && y >= 9 && y <= 14 && !(y == 12 && x == 13) ? scarColor : 0);
        overlay("scar_cheek", (x, y) -> x >= 9 && x <= 14 && y >= 13 && y <= 14 && (x + y == 26 || x + y == 27) ? scarColor : 0);
        overlay("scar_chest", (x, y) -> x >= 20 && x <= 27 && y >= 20 && y <= 29 && (y - x == 0 || y - x == 1) ? scarColor : 0);
        overlay("tattoo_arm", (x, y) -> x >= 40 && x <= 55 && (y == 22 || y == 23 || y == 25) ? ink : 0);
        overlay("tattoo_back", (x, y) -> {
            int dx = x - 35, dy = y - 24;                                   // a ring with a bar through it
            double r = Math.sqrt((dx + 0.5) * (dx + 0.5) + (dy + 0.5) * (dy + 0.5));
            return x >= 32 && x <= 39 && y >= 20 && y <= 28 && (Math.abs(r - 2.6) < 0.7 || (x == 35 || x == 36) && y >= 21 && y <= 27) ? ink : 0;
        });
        overlay("tattoo_chest", (x, y) -> x >= 20 && x <= 27 && y >= 20 && y <= 27 && Math.abs(x - 23.5) + Math.abs(y - 23.5) <= 3.5 && Math.abs(x - 23.5) + Math.abs(y - 23.5) >= 2 ? ink : 0);

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

    static void overlay(String name, Pixel pixel) throws IOException {
        texture("assets/dbzenith/textures/entity/cosmetics/" + name + ".png", 64, 64, pixel);
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
