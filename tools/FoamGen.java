import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * textures/entity/foam.png (CX-48): a strip of churned white water, 64 x 64. Across (u) it is densest in the middle and
 * frays out to nothing at both edges; along (v) it repeats. Soft clumps of froth with darker gaps between.
 * Usage: java tools/FoamGen.java
 */
public final class FoamGen {
    public static void main(String[] args) throws Exception {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            double u = (x + 0.5) / n * 2 - 1;
            double across = Math.pow(Math.max(0, 1 - u * u), 1.4);
            double froth = 0.55 * noise(x, y, 8) + 0.3 * noise(x, y, 4) + 0.15 * noise(x, y, 2);
            double a = Math.max(0, Math.min(1, (froth - 0.35) * 2.4)) * across;
            int g = (int) (225 + 30 * froth);
            img.setRGB(x, y, (int) (a * 255) << 24 | Math.min(255, g) << 16 | Math.min(255, g + 2) << 8 | Math.min(255, g + 6));
        }
        ImageIO.write(img, "png", new File("src/main/resources/assets/dbzenith/textures/entity/foam.png"));
        System.out.println("wrote foam.png");
    }

    /** Smooth value noise, tiling every 64 pixels. */
    static double noise(int x, int y, int cell) {
        int cx = x / cell, cy = y / cell, cells = 64 / cell;
        double fx = (x % cell + 0.5) / cell, fy = (y % cell + 0.5) / cell;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(cx % cells, cy % cells, cell), b = hash((cx + 1) % cells, cy % cells, cell);
        double c = hash(cx % cells, (cy + 1) % cells, cell), d = hash((cx + 1) % cells, (cy + 1) % cells, cell);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    static double hash(int x, int y, int s) {
        long h = x * 374761393L + y * 668265263L + s * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
