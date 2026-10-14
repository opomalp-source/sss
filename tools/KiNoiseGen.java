import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * textures/entity/ki_noise.png (the ki attack rework): 128 x 128 tileable noise for the energy shader to scroll over
 * attacks. Red: billowing fractal noise (four octaves of smooth value noise); green: ridged noise (sharp bright creases,
 * for crackling strands); blue: a second billow, offset. Usage: java tools/KiNoiseGen.java
 */
public final class KiNoiseGen {
    static final int N = 128;

    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            double r = fbm(x, y, 1, false), g = fbm(x, y, 7, true), b = fbm(x + 37, y + 91, 3, false);
            img.setRGB(x, y, c(r) << 16 | c(g) << 8 | c(b));
        }
        ImageIO.write(img, "png", new File("src/main/resources/assets/dbzenith/textures/entity/ki_noise.png"));
        System.out.println("wrote ki_noise.png");
    }

    static int c(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** Fractal noise, tiling every N pixels; ridged: 1 - |2n - 1|, squared, for creases. */
    static double fbm(int x, int y, long seed, boolean ridged) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int o = 0, cell = 32; o < 4; o++, cell /= 2) {
            double n = value(x, y, cell, seed + o * 101);
            if (ridged) n = Math.pow(1 - Math.abs(2 * n - 1), 2);
            sum += n * amp;
            norm += amp;
            amp *= 0.5;
        }
        return sum / norm;
    }

    static double value(int x, int y, int cell, long seed) {
        int cells = N / cell, cx = x / cell, cy = y / cell;
        double fx = (x % cell) / (double) cell, fy = (y % cell) / (double) cell;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = h(cx % cells, cy % cells, seed), b = h((cx + 1) % cells, cy % cells, seed);
        double c = h(cx % cells, (cy + 1) % cells, seed), d = h((cx + 1) % cells, (cy + 1) % cells, seed);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    static double h(int x, int y, long s) {
        long v = x * 374761393L + y * 668265263L + s * 2246822519L;
        v = (v ^ (v >>> 13)) * 1274126177L;
        return ((v ^ (v >>> 16)) & 0xFFFF) / 65535.0;
    }
}
