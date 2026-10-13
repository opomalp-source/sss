import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws textures/entity/hair_lock.png, 32 x 64. Left half: one lock of hair, u across its width, v from the root (top
 * row) to the tip, in greys the hair colour tints: a mid tone, dark edges outlining each lock, faint strand lines, a
 * slightly darker root and lighter tip. Right half: the gloss, a saw-edged anime highlight streak (opaque white, the
 * rest clear) laid over the lit side of each lock. Usage: java tools/HairTextureGen.java <out.png>
 */
public final class HairTextureGen {
    public static void main(String[] args) throws Exception {
        int w = 16, h = 64;
        BufferedImage img = new BufferedImage(w * 2, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            double v = y / (double) (h - 1);
            for (int x = 0; x < w; x++) {
                double g = 228;
                if (v < .12) g -= 30 * (1 - v / .12);                                    // the root, into the scalp
                if (v > .84) g += 18 * (v - .84) / .16;                                  // the tip catches light
                int stripe = (x * 7 + (y / 9) * 3) % 11;                                 // strand lines, broken every few rows
                if (x == 5 || x == 10) g -= stripe < 7 ? 22 : 9;
                if (x == 3 || x == 12) g -= stripe < 4 ? 12 : 0;
                if (x == 0 || x == w - 1) g = 104;                                       // the outline
                else if (x == 1 || x == w - 2) g = Math.min(g, 172);
                int c = (int) Math.max(0, Math.min(255, g));
                img.setRGB(x, y, 0xFF000000 | c << 16 | c << 8 | c);
            }
            for (int x = 0; x < w; x++) {                                                // the gloss
                double cy = 32, center = 7.5 + (y - cy) * .22;
                double half = 5.4 * Math.pow(Math.max(0, 1 - Math.abs(y - cy) / 27), .6);
                double d = Math.abs(x + .5 - center);
                boolean gap = y >= 29 && y <= 31;                                        // broken in two
                boolean saw = (y % 4 == 0) ? d < half * .55 : (y % 4 == 2) ? d < half * .8 : d < half;
                boolean in = !gap && saw && half > .5 && x > 1 && x < w - 2;
                int a = in ? (d > half - 1.2 ? 200 : 255) : 0;
                img.setRGB(w + x, y, a << 24 | 0xFFFFFF);
            }
        }
        ImageIO.write(img, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }
}
