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

        // --- ki glow: soft radial white, tinted per technique at render time ---
        radialGlow("assets/dbzenith/textures/entity/ki_glow.png", 32);
        beamGlow("assets/dbzenith/textures/entity/ki_beam.png", 32);

        // --- gametest structure: empty 3x3x3 template ---
        emptyStructure("data/dbzenith/structures/empty.nbt", 3);
        System.out.println("AssetGen done");
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
