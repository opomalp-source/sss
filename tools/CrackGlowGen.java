import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Makes textures/entity/ground_cracks_glow.png from ground_cracks.png (CX-43): white, with the cracks' alpha spread a
 * little (a soft blur), so the cracks can glow in any aura colour as they spread under a transformation.
 * Usage: java tools/CrackGlowGen.java
 */
public final class CrackGlowGen {
    public static void main(String[] args) throws Exception {
        String dir = "src/main/resources/assets/dbzenith/textures/entity/";
        BufferedImage in = ImageIO.read(new File(dir + "ground_cracks.png"));
        int w = in.getWidth(), h = in.getHeight(), r = 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double sum = 0, weight = 0;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int sx = x + dx, sy = y + dy;
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                double k = Math.exp(-(dx * dx + dy * dy) / 2.5);
                sum += (in.getRGB(sx, sy) >>> 24) * k;
                weight += k;
            }
            int core = in.getRGB(x, y) >>> 24;
            int a = (int) Math.min(255, Math.max(core, sum / weight * 1.6));
            out.setRGB(x, y, a << 24 | 0xFFFFFF);
        }
        ImageIO.write(out, "png", new File(dir + "ground_cracks_glow.png"));
        System.out.println("wrote ground_cracks_glow.png");
    }
}
