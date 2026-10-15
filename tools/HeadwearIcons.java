import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Item icons for head clothing (the clothing rework), 16x16 in the game's flat pixel style: Piccolo's turban (a
 * white dome on a purple band), a bandana (orange, folded, knotted), a headband (a red loop with two tails).
 * Usage: java tools/HeadwearIcons.java
 */
public final class HeadwearIcons {
    static final String DIR = "src/main/resources/assets/dbzenith/textures/item/";

    public static void main(String[] args) throws Exception {
        turban();
        bandana();
        headband();
        System.out.println("HeadwearIcons: 3 icons");
    }

    static void turban() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double dx = (x + 0.5 - 8) / 6.2, dy = (y + 0.5 - 9.5) / 7.0;
            if (y >= 11 && y <= 13 && x >= 1 && x <= 14) {                       // the band
                img.setRGB(x, y, y == 13 || x == 1 || x == 14 ? 0xFF3A1E5A : y == 11 ? 0xFF8A5AC0 : 0xFF6A3A9A);
                continue;
            }
            if (y < 11 && dx * dx + dy * dy < 1) {                               // the dome, lit from the upper left
                boolean edge = dx * dx + dy * dy > 0.78;
                double light = 0.5 - 0.35 * dx - 0.4 * dy;
                int c = edge ? 0xFF8A8478 : light > 0.75 ? 0xFFFFFFFF : light > 0.35 ? 0xFFEDE8DC : 0xFFCFC8B8;
                if (x == 8 && y > 3) c = 0xFFB8B0A0;                                 // the fold up the front
                img.setRGB(x, y, c);
            }
        }
        ImageIO.write(img, "png", new File(DIR + "turban.png"));
    }

    static void bandana() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double half = (13.5 - y) * 0.95;                                     // the triangle hanging down from the band
            boolean cloth = y >= 6 && y <= 13 && Math.abs(x + 0.5 - 7) <= half;
            boolean band = y >= 3 && y <= 5 && x >= 0 && x <= 13;
            boolean knot = (x >= 13 && x <= 15 && y >= 2 && y <= 6) || (x == 14 && y >= 7 && y <= 9) || (x == 15 && y >= 7 && y <= 10);
            if (knot) img.setRGB(x, y, x == 15 || y == 6 ? 0xFF8A3A0C : 0xFFD0661A);
            else if (band) img.setRGB(x, y, y == 5 ? 0xFF8A3A0C : y == 3 ? 0xFFFFA050 : 0xFFE06A18);
            else if (cloth) {
                boolean edge = Math.abs(x + 0.5 - 7) > half - 1 || y == 13;
                img.setRGB(x, y, edge ? 0xFF9A4410 : (x + y) % 5 == 0 ? 0xFFFFA050 : 0xFFF07820);
            }
        }
        ImageIO.write(img, "png", new File(DIR + "bandana.png"));
    }

    static void headband() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double dx = (x + 0.5 - 7) / 5.6, dy = (y + 0.5 - 6.5) / 3.2;
            double r = dx * dx + dy * dy;
            boolean loop = r < 1 && r > 0.45;
            boolean tail = (x >= 9 && x <= 10 && y >= 8 && y <= 14 && (x + y) % 7 != 0) || (x >= 11 && x <= 12 && y >= 9 && y <= 13);
            if (loop) img.setRGB(x, y, r > 0.85 ? 0xFF6A1018 : dy < 0 ? 0xFFE04050 : 0xFFC8283A);
            else if (tail) img.setRGB(x, y, x % 2 == 0 ? 0xFFA01E2C : 0xFFC8283A);
        }
        ImageIO.write(img, "png", new File(DIR + "headband.png"));
    }
}
