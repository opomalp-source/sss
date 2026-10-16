// Crops (and tiles, and scales up) screenshots to look at closely: java tools/dev/Crop.java out.png "a.png,b.png" x y w h scale
import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;
public class Crop { public static void main(String[] a) throws Exception {
 int x=Integer.parseInt(a[2]), y=Integer.parseInt(a[3]), w=Integer.parseInt(a[4]), h=Integer.parseInt(a[5]), s=Integer.parseInt(a[6]);
 int n = a[1].split(",").length; BufferedImage o = new BufferedImage(w*s*n, h*s, BufferedImage.TYPE_INT_RGB); int i=0;
 for (String f : a[1].split(",")) { BufferedImage im = ImageIO.read(new File(f));
  for (int yy=0;yy<h*s;yy++) for (int xx=0;xx<w*s;xx++) o.setRGB(i*w*s+xx, yy, im.getRGB(x+xx/s, y+yy/s)); i++; }
 ImageIO.write(o,"png",new File(a[0])); } }
