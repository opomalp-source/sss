import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws textures/entity/nimbus_puff.png: one soft cloud puff, 64 x 64, greys the cloud colour tints. A round body with
 * a cauliflower edge (a few bumps), lit from the top left (lighter there, a little darker toward the bottom right),
 * soft at the very rim. Usage: java tools/NimbusTextureGen.java <out.png>
 */
public final class NimbusTextureGen {
    public static void main(String[] args) throws Exception {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            double u = (x + .5) / n * 2 - 1, v = (y + .5) / n * 2 - 1;
            double r = Math.sqrt(u * u + v * v), a = Math.atan2(v, u);
            double edge = .86 + .05 * Math.cos(a * 5 + .7) + .03 * Math.cos(a * 9 - 1.3) + .02 * Math.sin(a * 13);
            double alpha = 1 - smooth(edge - .14, edge, r);                              // soft at the rim
            if (alpha <= 0) { img.setRGB(x, y, 0); continue; }
            double light = .95 + .05 * Math.max(-1, Math.min(1, (-u * .5 - v * .85) / .9));     // lit from the top left
            double sheen = Math.exp(-(Math.pow(u + .28, 2) + Math.pow(v + .34, 2)) / .07) * .12;   // a soft highlight
            double rim = smooth(edge - .3, edge - .05, r) * .03;                            // a touch darker at the rim
            int g = (int) Math.round(255 * Math.max(0, Math.min(1, light + sheen - rim)));
            int al = (int) Math.round(255 * alpha);
            img.setRGB(x, y, al << 24 | g << 16 | g << 8 | g);
        }
        ImageIO.write(img, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }

    private static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
