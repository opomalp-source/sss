package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode.Preset;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Offline sheets of every stock hairstyle in every look, and transformations step by step (no Minecraft window).
 * Args: output folder, then optionally "only=PRESET,PRESET" and "views=N" (1 three-quarter, 4 front/side/back/three-quarter).
 */
public final class HairLookSheet {
    private record Surface(float[][] corners, int rgb) {}
    private static final int[][] FACES = {{1,3,7,5}, {0,4,6,2}, {2,6,7,3}, {0,1,5,4}, {4,5,7,6}, {0,2,3,1}};
    private static int W = 230, H = 300;
    private static double SCALE = 6.2, TOP = 150;
    private static HairLook[] looksArg;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        List<Preset> presets = new ArrayList<>(Arrays.asList(Preset.values()));
        presets.remove(Preset.BALD);
        int views = 1;
        for (String a : args) {
            if (a.startsWith("only=")) { presets.clear(); for (String p : a.substring(5).split(",")) presets.add(Preset.valueOf(p)); }
            if (a.startsWith("views=")) views = Integer.parseInt(a.substring(6));
            if (a.equals("big")) { W = 380; H = 470; SCALE = 10; TOP = 250; }
            if (a.startsWith("looks=")) { String[] ls = a.substring(6).split(","); looksArg = new HairLook[ls.length]; for (int i = 0; i < ls.length; i++) looksArg[i] = HairLook.valueOf(ls[i]); }
        }
        HairLook[] looks = looksArg != null ? looksArg : HairLook.values();
        double[] yaws = views == 1 ? new double[]{-Math.PI / 5} : new double[]{0, -Math.PI / 2, Math.PI, -Math.PI / 5};
        // every look of every preset
        for (int part = 0; part * 5 < presets.size(); part++) {
            List<Preset> chunk = presets.subList(part * 5, Math.min(presets.size(), part * 5 + 5));
            BufferedImage sheet = new BufferedImage(W * looks.length, H * chunk.size() * yaws.length, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            int row = 0;
            for (Preset p : chunk) for (double yaw : yaws) {
                for (int c = 0; c < looks.length; c++)
                    g.drawImage(render(CharacterHair.mesh(p, looks[c]), color(looks[c]), yaw, .1, p + " / " + looks[c]), c * W, row * H, null);
                row++;
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(out, "looks-" + part + ".png"));
        }
        // transformations, step by step
        HairLook[] targets = {HairLook.SSJ, HairLook.SSJ2, HairLook.SSJ3};
        float[] steps = {0, .2f, .4f, .6f, .8f, 1};
        for (HairLook t : targets) {
            List<Preset> some = presets.size() > 6 ? presets.subList(0, 7) : presets;
            BufferedImage sheet = new BufferedImage(W * steps.length, H * some.size(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            for (int r = 0; r < some.size(); r++) for (int c = 0; c < steps.length; c++)
                g.drawImage(render(CharacterHair.transition(some.get(r), HairLook.BASE, t, steps[c]), steps[c] < .5f ? 0x17181D : color(t),
                        -Math.PI / 4, .1, some.get(r) + " > " + t + " " + Math.round(steps[c] * 100) + "%"), c * W, r * H, null);
            g.dispose();
            ImageIO.write(sheet, "png", new File(out, "grow-" + t + ".png"));
        }
        System.out.println("hair look sheets written to " + out);
    }

    private static int color(HairLook look) {
        return switch (look) {
            case BASE, SSJ4 -> 0x17181D;
            case GOD -> 0xC8283C;
            case BEAST -> 0xE8E8EC;
            default -> 0xEAC74E;
        };
    }

    private static void cube(List<Surface> out, float x, float y, float z, float w, float h, float d, int color) {
        float[][] c = new float[8][];
        for (int i = 0; i < 8; i++) c[i] = new float[]{x + ((i & 1) != 0 ? w : 0), y + ((i & 2) != 0 ? h : 0), z + ((i & 4) != 0 ? d : 0)};
        out.add(new Surface(c, color));
    }

    static BufferedImage render(List<HairMesh.Segment> mesh, int color, double yaw, double pitch, String label) {
        List<Surface> geometry = new ArrayList<>();
        cube(geometry, -4, -8, -4, 8, 8, 8, 0xCDA67E);
        cube(geometry, -2.7f, -4.1f, -4.08f, 1.8f, 1.2f, 0.1f, 0xEEEAE1);
        cube(geometry, 0.9f, -4.1f, -4.08f, 1.8f, 1.2f, 0.1f, 0xEEEAE1);
        cube(geometry, -1.7f, -4.05f, -4.2f, 0.7f, 0.9f, 0.1f, 0x28272B);
        cube(geometry, 1.0f, -4.05f, -4.2f, 0.7f, 0.9f, 0.1f, 0x28272B);
        cube(geometry, -4, 0, -2, 8, 12, 4, 0xDF9126);
        for (var seg : mesh) geometry.add(new Surface(seg.corners(), color));
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[W * H];
        Arrays.fill(pixels, 0xADB5C1);
        double[] depth = new double[W * H];
        Arrays.fill(depth, Double.POSITIVE_INFINITY);
        double scale = SCALE;
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        for (Surface surface : geometry) {
            double[][] projected = new double[surface.corners.length][];
            for (int i = 0; i < surface.corners.length; i++) {
                float[] v = surface.corners[i];
                double x = v[0] * cy - v[2] * sy, z = v[0] * sy + v[2] * cy;
                double y = v[1] * cp - z * sp;
                z = v[1] * sp + z * cp;
                projected[i] = new double[]{W / 2.0 + x * scale, TOP + y * scale, z};
            }
            for (int[] face : surface.corners.length == 4 ? new int[][]{{0, 1, 2, 3}} : FACES) {
                double[] a = projected[face[0]], b = projected[face[1]], c = projected[face[2]];
                double ax = b[0] - a[0], ay = b[1] - a[1], az = (b[2] - a[2]) * scale;
                double bx = c[0] - a[0], by = c[1] - a[1], bz = (c[2] - a[2]) * scale;
                double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double lighting = 0.55 + 0.45 * Math.abs((-0.3 * nx - 0.65 * ny - 0.70 * nz) / Math.max(1e-9, len));
                int rgb = shade(surface.rgb, lighting);
                triangle(pixels, depth, a, b, c, rgb);
                triangle(pixels, depth, a, c, projected[face[3]], rgb);
            }
        }
        img.setRGB(0, 0, W, H, pixels, 0, W);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x172131));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(label, 8, 16);
        g.dispose();
        return img;
    }

    private static int shade(int c, double f) {
        return ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f);
    }

    private static void triangle(int[] pixels, double[] depth, double[] a, double[] b, double[] c, int color) {
        double det = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(det) < 1e-8) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0])))), x1 = Math.min(W - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1])))), y1 = Math.min(H - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            double u = ((b[1] - c[1]) * (x + .5 - c[0]) + (c[0] - b[0]) * (y + .5 - c[1])) / det;
            double v = ((c[1] - a[1]) * (x + .5 - c[0]) + (a[0] - c[0]) * (y + .5 - c[1])) / det;
            double w = 1 - u - v;
            if (u < 0 || v < 0 || w < 0) continue;
            double z = u * a[2] + v * b[2] + w * c[2];
            int at = y * W + x;
            if (z < depth[at]) {
                depth[at] = z;
                pixels[at] = color;
            }
        }
    }
}
