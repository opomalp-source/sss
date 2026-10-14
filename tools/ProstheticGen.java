import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Prosthetic limbs (CX-54): textures/entity/prosthetic.png, a player-skin-layout sheet (128 x 128) painted as brushed
 * steel plates with seams, rivets, a dark joint band at the elbows and knees, a darker shoulder and hip socket and a
 * blue accent stripe down the outside; and the item icons textures/item/prosthetic_arm.png and prosthetic_leg.png.
 * Usage: java tools/ProstheticGen.java
 */
public final class ProstheticGen {
    static final String OUT = "src/main/resources/assets/dbzenith/textures/";

    public static void main(String[] args) throws Exception {
        sheet();
        icon("prosthetic_arm", true);
        icon("prosthetic_leg", false);
        System.out.println("ProstheticGen done");
    }

    /** The skin-layout sheet: the four limbs' regions (64-pixel units, doubled), each 4 wide faces x 12 tall. */
    static void sheet() throws Exception {
        int s = 2;
        BufferedImage img = new BufferedImage(64 * s, 64 * s, BufferedImage.TYPE_INT_ARGB);
        int[][] limbs = {{40, 16}, {32, 48}, {0, 16}, {16, 48}};                  // right arm, left arm, right leg, left leg
        for (int[] l : limbs) {
            for (int y = 0; y < 16 * s; y++) for (int x = 0; x < 16 * s; x++) {
                int px = l[0] * s + x, py = l[1] * s + y;
                boolean cap = y < 4 * s;                                         // the top and bottom faces
                int rowInSide = y - 4 * s;                                       // 0..24 down the side faces
                double t = cap ? 0.5 : rowInSide / (12.0 * s);
                int face = x / (4 * s);                                          // which side face (0 outer .. 3)
                double shade = 0.62 + 0.18 * Math.sin(t * Math.PI) + 0.06 * (face == 1 ? 1 : face == 3 ? -1 : 0);
                shade += ((x * 7 + y * 13) % 5 - 2) * 0.008;                       // brushed grain
                int c = grey(shade);
                if (!cap) {
                    if (rowInSide % (4 * s) == 0) c = grey(shade - 0.18);         // plate seams
                    if (Math.abs(t - 0.5) < 0.06) c = grey(0.28 + 0.05 * Math.sin(x));   // the joint band
                    if (t < 0.12) c = grey(0.4);                                 // the socket at the top
                    if ((x % (4 * s) == 1 || x % (4 * s) == 4 * s - 2) && rowInSide % (4 * s) == 2) c = grey(0.9);   // rivets
                    if (face == 0 && x % (4 * s) >= s && x % (4 * s) < 2 * s && t > 0.15 && t < 0.42) c = 0x3060C8;   // accent
                } else c = grey(0.45 + ((x + y) % 3) * 0.04);
                img.setRGB(px, py, 0xFF000000 | c);
            }
        }
        ImageIO.write(img, "png", new File(OUT + "entity/prosthetic.png"));
    }

    static void icon(String name, boolean arm) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // a limb lying diagonally: upper part, joint, lower part, and a hand or a foot
        for (int i = 0; i < 12; i++) {
            int x = 2 + i, y = 13 - i;
            boolean joint = i == 6;
            for (int w = -1; w <= 1; w++) {
                int c = joint ? grey(0.3) : grey(0.55 + 0.15 * (w == -1 ? 1 : w == 1 ? -1 : 0));
                if (!joint && i % 3 == 0 && w == 0) c = grey(0.4);
                set(img, x + w, y, c);
                set(img, x, y + w, c);
            }
            if (i == 3) set(img, x, y, 0x3060C8);
        }
        if (arm) {                                                               // fingers
            set(img, 14, 1, grey(0.75)); set(img, 15, 2, grey(0.75)); set(img, 13, 0, grey(0.6)); set(img, 15, 0, grey(0.6));
        } else {                                                                 // a foot
            for (int x = 12; x < 16; x++) set(img, x, 2, grey(0.5));
            set(img, 15, 1, grey(0.5));
        }
        for (int i = 0; i < 2; i++) set(img, 1 + i, 14 - i, grey(0.3));         // the socket
        ImageIO.write(img, "png", new File(OUT + "item/" + name + ".png"));
    }

    static void set(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, 0xFF000000 | rgb);
    }

    static int grey(double v) {
        int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
        int b = Math.min(255, g + 12);
        return g << 16 | g << 8 | b;
    }
}
