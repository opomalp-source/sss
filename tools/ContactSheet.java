import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Art review helper: tiles every PNG under a folder into one image, scaled up with nearest-neighbour, each with its
 * file name, on a checkerboard so transparency shows. Usage: java tools/ContactSheet.java <folder> <out.png> [cell]
 */
public class ContactSheet {
    public static void main(String[] args) throws IOException {
        Path root = Path.of(args[0]);
        int cell = args.length > 2 ? Integer.parseInt(args[2]) : 128;
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".png")).sorted().toList();
        }
        int cols = 8, label = 14, pad = 6;
        int rows = (files.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * (cell + pad) + pad, rows * (cell + label + pad) + pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(40, 40, 48));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < files.size(); i++) {
            int x = pad + (i % cols) * (cell + pad), y = pad + (i / cols) * (cell + label + pad);
            for (int cy = 0; cy < cell; cy += 8) for (int cx = 0; cx < cell; cx += 8) {
                g.setColor(((cx + cy) / 8) % 2 == 0 ? new Color(90, 90, 100) : new Color(120, 120, 130));
                g.fillRect(x + cx, y + cy, 8, 8);
            }
            BufferedImage img = ImageIO.read(files.get(i).toFile());
            double scale = Math.min((double) cell / img.getWidth(), (double) cell / img.getHeight());
            int w = (int) (img.getWidth() * scale), h = (int) (img.getHeight() * scale);
            g.drawImage(img, x, y, w, h, null);
            g.setColor(Color.WHITE);
            String name = root.relativize(files.get(i)).toString().replace(File.separatorChar, '/');
            g.drawString(name.length() > 24 ? "…" + name.substring(name.length() - 23) : name, x, y + cell + 11);
        }
        g.dispose();
        ImageIO.write(out, "png", new File(args[1]));
        System.out.println("wrote " + args[1] + " (" + files.size() + " images)");
    }
}
