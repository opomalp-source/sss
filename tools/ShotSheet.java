import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/** java ShotSheet.java <folder> <prefix> <out.png> <x> <y> <w> <h> [cols] : crops matching screenshots into a labelled grid. */
public class ShotSheet {
    public static void main(String[] a) throws Exception {
        File[] files = new File(a[0]).listFiles((d, n) -> n.startsWith(a[1]) && n.endsWith(".png"));
        Arrays.sort(files, (p, q) -> Long.compare(p.lastModified(), q.lastModified()));
        int x = Integer.parseInt(a[3]), y = Integer.parseInt(a[4]), w = Integer.parseInt(a[5]), h = Integer.parseInt(a[6]);
        int cols = a.length > 7 ? Integer.parseInt(a[7]) : 6;
        int rows = (files.length + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * w, rows * (h + 16), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        for (int i = 0; i < files.length; i++) {
            BufferedImage img = ImageIO.read(files[i]);
            int cx = (i % cols) * w, cy = (i / cols) * (h + 16);
            g.drawImage(img.getSubimage(x, y, Math.min(w, img.getWidth() - x), Math.min(h, img.getHeight() - y)), cx, cy + 16, null);
            g.setColor(Color.WHITE);
            g.drawString(files[i].getName().replace(a[1], "").replace(".png", ""), cx + 4, cy + 13);
        }
        ImageIO.write(out, "png", new File(a[2]));
    }
}
