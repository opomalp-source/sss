import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws textures/entity/hair_block.png: a 16 x 16 tile of hair at Minecraft's own pixel size, greys that the hair
 * colour tints. Strands run down the tile (v follows a lock's length, u goes across it): columns of slightly different
 * tone, a few darker strand gaps that break and restart, a scatter of lighter pixels. It tiles in both directions.
 * Usage: java tools/HairTextureGen.java <out.png>
 */
public final class HairTextureGen {
    public static void main(String[] args) throws Exception {
        int n = 16;
        int[] column = {214, 198, 224, 206, 190, 218, 202, 228, 196, 212, 186, 220, 204, 226, 194, 210};
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        java.util.Random rnd = new java.util.Random(35);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int g = column[x];
                boolean gap = (x == 4 && (y < 6 || y > 10)) || (x == 10 && y > 3 && y < 13) || (x == 14 && (y < 3 || y > 8));
                if (gap) g -= 30;                                                     // the dark lines between strands
                if (rnd.nextInt(9) == 0) g += 14;                                     // a lighter hair here and there
                g = Math.max(0, Math.min(255, g));
                img.setRGB(x, y, 0xFF000000 | g << 16 | g << 8 | g);
            }
        }
        ImageIO.write(img, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }
}
