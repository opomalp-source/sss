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
 * Offline sheets of the hair, drawn from what {@link HairRender} actually emits (positions, per-vertex colours, the
 * hair texture through its UVs) and lit the way Minecraft lights entities. No game window. Args: output folder, then
 * optionally "only=PRESET,..", "looks=SSJ,..", "views=4", "big", "grow".
 */
public final class HairSheet {
    /** A quad as emitted: corners (x, y, z), colours, texture coordinates, the normal. */
    private record Quad(float[] p, int[] rgb, float[] uv, float nx, float ny, float nz, boolean textured) {}
    private static int W = 230, H = 300;
    private static double SCALE = 6.2, TOP = 150, PITCH = .1;
    private static BufferedImage tex;

    public static void main(String[] args) throws Exception {
        tex = ImageIO.read(HairSheet.class.getResourceAsStream("/assets/dbzenith/textures/entity/aura_white.png"));
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
            if (a.startsWith("pitch=")) PITCH = Double.parseDouble(a.substring(6));
        }
        double[] yaws = views == 1 ? new double[]{-Math.PI / 5} : new double[]{0, -Math.PI / 2, Math.PI, -Math.PI / 5};
        if (grow) {
            float[] steps = {0, .2f, .4f, .6f, .8f, 1};
            for (HairLook t : looks) {
                if (t == HairLook.BASE) continue;
                BufferedImage sheet = new BufferedImage(W * steps.length, H * presets.size(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = sheet.createGraphics();
                for (int r = 0; r < presets.size(); r++) for (int c = 0; c < steps.length; c++)
                    g.drawImage(render(HairStyles.between(presets.get(r), HairLook.BASE, t, steps[c]), steps[c] < .5f ? color(HairLook.BASE) : color(t),
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
                    for (int c = 0; c < yaws.length; c++) g.drawImage(render(HairStyles.of(p, look), color(look), yaws[c], PITCH, p + " / " + look), c * W, row * H, null);
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
            default -> 0xFFE15A;
        };
    }

    /** Collects what the renderer emits. */
    private static final class Catch implements VertexConsumer {
        final List<Quad> quads = new ArrayList<>();
        float[] p = new float[12], uv = new float[8];
        int[] rgb = new int[4];
        int n;
        float nx, ny, nz;

        @Override public VertexConsumer vertex(double x, double y, double z) { p[n * 3] = (float) x * 16; p[n * 3 + 1] = (float) y * 16; p[n * 3 + 2] = (float) z * 16; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { rgb[n] = r << 16 | g << 8 | b; return this; }
        @Override public VertexConsumer uv(float u, float v) { uv[n * 2] = u; uv[n * 2 + 1] = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { nx = x; ny = y; nz = z; return this; }
        @Override public void endVertex() {
            if (++n == 4) {
                quads.add(new Quad(p, rgb, uv, nx, ny, nz, true));
                p = new float[12]; uv = new float[8]; rgb = new int[4]; n = 0;
            }
        }
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
            double l0 = Math.max(0, (.2 * q.nx - 1 * q.ny - .7 * q.nz) / 1.237), l1 = Math.max(0, (-.2 * q.nx - 1 * q.ny + .7 * q.nz) / 1.237);
            double light = Math.min(1, (l0 + l1) * .6 + .4);                         // Minecraft's entity lights
            tri(px, depth, pr, q, 0, 1, 2, light);
            tri(px, depth, pr, q, 0, 2, 3, light);
        }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
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
        int[] c = {rgb, rgb, rgb, rgb};
        float[] uv = new float[8];
        q.add(new Quad(new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, c, uv, 0, -1, 0, false));
        q.add(new Quad(new float[]{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, c, uv, 0, 1, 0, false));
        q.add(new Quad(new float[]{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, c, uv, 0, 0, -1, false));
        q.add(new Quad(new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, c, uv, 0, 0, 1, false));
        q.add(new Quad(new float[]{x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1}, c, uv, -1, 0, 0, false));
        q.add(new Quad(new float[]{x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}, c, uv, 1, 0, 0, false));
    }

    private static void tri(int[] px, double[] depth, double[][] pr, Quad q, int ia, int ib, int ic, double light) {
        double[] a = pr[ia], b = pr[ib], c = pr[ic];
        double det = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(det) < 1e-8) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0])))), x1 = Math.min(W - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1])))), y1 = Math.min(H - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            double wa = ((b[1] - c[1]) * (x + .5 - c[0]) + (c[0] - b[0]) * (y + .5 - c[1])) / det;
            double wb = ((c[1] - a[1]) * (x + .5 - c[0]) + (a[0] - c[0]) * (y + .5 - c[1])) / det;
            double wc = 1 - wa - wb;
            if (wa < 0 || wb < 0 || wc < 0) continue;
            double z = wa * a[2] + wb * b[2] + wc * c[2];
            int at = y * W + x;
            if (z >= depth[at]) continue;
            double r = 0, g = 0, bl = 0;
            int[] cs = {q.rgb[ia], q.rgb[ib], q.rgb[ic]};
            double[] ws = {wa, wb, wc};
            for (int k = 0; k < 3; k++) {
                r += ((cs[k] >> 16) & 255) * ws[k];
                g += ((cs[k] >> 8) & 255) * ws[k];
                bl += (cs[k] & 255) * ws[k];
            }
            double t = 1;
            if (q.textured) {
                double u = wa * q.uv[ia * 2] + wb * q.uv[ib * 2] + wc * q.uv[ic * 2];
                double v = wa * q.uv[ia * 2 + 1] + wb * q.uv[ib * 2 + 1] + wc * q.uv[ic * 2 + 1];
                int tx = Math.max(0, Math.min(tex.getWidth() - 1, (int) (u * tex.getWidth())));
                int ty = Math.max(0, Math.min(tex.getHeight() - 1, (int) (v * tex.getHeight())));
                int texel = tex.getRGB(tx, ty);
                if ((texel >>> 24) < 26) continue;                                    // cut out, like the game
                t = (texel & 255) / 255.0;
            }
            depth[at] = z;
            double f = light * t;
            px[at] = ((int) Math.min(255, r * f) << 16) | ((int) Math.min(255, g * f) << 8) | (int) Math.min(255, bl * f);
        }
    }
}
