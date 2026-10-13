package com.dbzenith.client.hair;

import com.dbzenith.appearance.HairCode.Preset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Offline sheets of the hair, drawn from the vertices {@link HairRender} actually emits, lit the way Minecraft lights
 * entities (no game window). Args: output folder, then optionally "only=PRESET,..", "looks=SSJ,..", "views=4", "big",
 * "grow" (transformations step by step).
 */
public final class HairSheet {
    private record Quad(float[] p, int rgb, float nx, float ny, float nz) {}
    private static int W = 230, H = 300;
    private static double SCALE = 6.2, TOP = 150;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        List<Preset> presets = new ArrayList<>(Arrays.asList(Preset.values()));
        presets.remove(Preset.BALD);
        HairLook[] looks = HairLook.values();
        int views = 1;
        boolean grow = false;
        for (String a : args) {
            if (a.startsWith("only=")) { presets.clear(); for (String p : a.substring(5).split(",")) presets.add(Preset.valueOf(p)); }
            if (a.startsWith("looks=")) { String[] ls = a.substring(6).split(","); looks = new HairLook[ls.length]; for (int i = 0; i < ls.length; i++) looks[i] = HairLook.valueOf(ls[i]); }
            if (a.startsWith("views=")) views = Integer.parseInt(a.substring(6));
            if (a.equals("big")) { W = 380; H = 470; SCALE = 10; TOP = 250; }
            if (a.equals("grow")) grow = true;
        }
        double[] yaws = views == 1 ? new double[]{-Math.PI / 5} : new double[]{0, -Math.PI / 2, Math.PI, -Math.PI / 5};
        if (grow) {
            float[] steps = {0, .2f, .4f, .6f, .8f, 1};
            for (HairLook t : looks) {
                if (t == HairLook.BASE) continue;
                BufferedImage sheet = new BufferedImage(W * steps.length, H * presets.size(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = sheet.createGraphics();
                for (int r = 0; r < presets.size(); r++) for (int c = 0; c < steps.length; c++)
                    g.drawImage(render(HairStyles.between(presets.get(r), HairLook.BASE, t, steps[c]), steps[c] < .5f ? 0x1A1B22 : color(t),
                            -Math.PI / 4, .1, presets.get(r) + " > " + t + " " + Math.round(steps[c] * 100) + "%"), c * W, r * H, null);
                g.dispose();
                ImageIO.write(sheet, "png", new File(out, "grow-" + t + ".png"));
            }
            return;
        }
        for (int part = 0; part * 5 < presets.size(); part++) {
            List<Preset> chunk = presets.subList(part * 5, Math.min(presets.size(), part * 5 + 5));
            boolean across = yaws.length > 1;                                         // angles across, looks down
            int cols = across ? yaws.length : looks.length, rows = across ? chunk.size() * looks.length : chunk.size();
            BufferedImage sheet = new BufferedImage(W * cols, H * rows, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            int row = 0;
            for (Preset p : chunk) {
                if (across) for (HairLook look : looks) {
                    for (int c = 0; c < yaws.length; c++) g.drawImage(render(HairStyles.of(p, look), color(look), yaws[c], .1, p + " / " + look), c * W, row * H, null);
                    row++;
                }
                else {
                    for (int c = 0; c < looks.length; c++) g.drawImage(render(HairStyles.of(p, looks[c]), color(looks[c]), yaws[0], .1, p + " / " + looks[c]), c * W, row * H, null);
                    row++;
                }
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(out, "looks-" + part + ".png"));
        }
    }

    static int color(HairLook look) {
        return switch (look) {
            case BASE, SSJ4 -> 0x1A1B22;
            case GOD -> 0xC8283C;
            case BEAST -> 0xE8E8EC;
            default -> 0xF2C94C;
        };
    }

    /** Collects what the renderer emits. */
    private static final class Catch implements VertexConsumer {
        final List<Quad> quads = new ArrayList<>();
        float[] p = new float[12];
        int n, rgb;
        float nx, ny, nz;

        @Override public VertexConsumer vertex(double x, double y, double z) { p[n * 3] = (float) x * 16; p[n * 3 + 1] = (float) y * 16; p[n * 3 + 2] = (float) z * 16; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { rgb = r << 16 | g << 8 | b; return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { nx = x; ny = y; nz = z; return this; }
        @Override public void endVertex() { if (++n == 4) { quads.add(new Quad(p, rgb, nx, ny, nz)); p = new float[12]; n = 0; } }
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }

    static BufferedImage render(HairShape shape, int color, double yaw, double pitch, String label) {
        Catch c = new Catch();
        HairRender.render(new PoseStack(), c, shape, null, 15728880, 0, color, -1);
        List<Quad> quads = new ArrayList<>(c.quads);
        box(quads, -4, -8, -4, 4, 0, 4, 0xCDA67E);                                   // the head
        box(quads, -2.7f, -4.1f, -4.08f, -.9f, -2.9f, -4.0f, 0xEEEAE1);
        box(quads, .9f, -4.1f, -4.08f, 2.7f, -2.9f, -4.0f, 0xEEEAE1);
        box(quads, -1.7f, -4.05f, -4.2f, -1f, -3.15f, -4.1f, 0x28272B);
        box(quads, 1f, -4.05f, -4.2f, 1.7f, -3.15f, -4.1f, 0x28272B);
        box(quads, -4, 0, -2, 4, 12, 2, 0xDF9126);                                   // the body
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[W * H];
        Arrays.fill(px, 0xADB5C1);
        double[] depth = new double[W * H];
        Arrays.fill(depth, Double.POSITIVE_INFINITY);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        for (Quad q : quads) {
            double[][] pr = new double[4][];
            for (int i = 0; i < 4; i++) {
                double x = q.p[i * 3] * cy - q.p[i * 3 + 2] * sy, z = q.p[i * 3] * sy + q.p[i * 3 + 2] * cy;
                double y = q.p[i * 3 + 1] * cp - z * sp;
                z = q.p[i * 3 + 1] * sp + z * cp;
                pr[i] = new double[]{W / 2.0 + x * SCALE, TOP + y * SCALE, z};
            }
            // Minecraft's entity lights, in head space (y down): two directions, 0.6 each, 0.4 ambient
            double l0 = Math.max(0, (.2 * q.nx - 1 * q.ny - .7 * q.nz) / 1.237), l1 = Math.max(0, (-.2 * q.nx - 1 * q.ny + .7 * q.nz) / 1.237);
            double light = Math.min(1, (l0 + l1) * .6 + .4);
            int rgb = shade(q.rgb, light);
            tri(px, depth, pr[0], pr[1], pr[2], rgb);
            tri(px, depth, pr[0], pr[2], pr[3], rgb);
        }
        img.setRGB(0, 0, W, H, px, 0, W);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x172131));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(label, 8, 16);
        g.dispose();
        return img;
    }

    private static void box(List<Quad> q, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        q.add(new Quad(new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, rgb, 0, -1, 0));
        q.add(new Quad(new float[]{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, rgb, 0, 1, 0));
        q.add(new Quad(new float[]{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, rgb, 0, 0, -1));
        q.add(new Quad(new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, rgb, 0, 0, 1));
        q.add(new Quad(new float[]{x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1}, rgb, -1, 0, 0));
        q.add(new Quad(new float[]{x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}, rgb, 1, 0, 0));
    }

    private static int shade(int c, double f) {
        return ((int) Math.min(255, ((c >> 16) & 255) * f) << 16) | ((int) Math.min(255, ((c >> 8) & 255) * f) << 8) | (int) Math.min(255, (c & 255) * f);
    }

    private static void tri(int[] px, double[] depth, double[] a, double[] b, double[] c, int color) {
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
                px[at] = color;
            }
        }
    }
}
