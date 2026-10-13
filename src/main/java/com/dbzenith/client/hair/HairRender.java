package com.dbzenith.client.hair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a head of hair in head space (the pose stack at the head) the way Minecraft draws everything: in blocks. Each
 * lock is laid down along its path as a chain of small cubes on a half-pixel grid, big near the root and shrinking to
 * a single half-pixel at the tip, so a slanting lock steps like a staircase and the whole head reads as pixel art next
 * to the skin. The cubes take Minecraft's own face lighting (tops bright, sides darker) and vary a little cube to
 * cube; the roots are a little darker than the tips. No scalp is drawn: the locks cover the head themselves.
 * Lock positions come from {@link HairSim} when the hair moves, else from the rest shape. Render thread only.
 */
public final class HairRender {
    /** A plain white texture: the hair is coloured per vertex. */
    public static final ResourceLocation MATERIAL = new ResourceLocation("dbzenith", "textures/entity/aura_white.png");
    /** The grid the cubes sit on, in head pixels. */
    static final float UNIT = .5f;
    /** How far apart along a lock the cubes are laid (smaller than a cube, so they always touch). */
    private static final float STEP = .35f;
    private static final int N = HairLock.KNOTS;
    private static final float[] P = new float[N * 3], AT = new float[N];

    private HairRender() {}

    /**
     * @param live  the simulated knot positions (3 per knot, locks in order) or null for the rest shape
     * @param rgb   hair colour; {@code tips} the dyed tips (-1 for none)
     */
    public static void render(PoseStack pose, VertexConsumer vc, HairShape shape, float[] live, int light, int overlay, int rgb, int tips) {
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float lift = .24f * (1 - Math.max(r, Math.max(g, b)));                       // black hair reads as dark grey blocks, not a silhouette
        r += (1 - r) * lift; g += (1 - g) * lift; b += (1 - b) * lift;
        float tr = tips < 0 ? r : ((tips >> 16) & 255) / 255f, tg = tips < 0 ? g : ((tips >> 8) & 255) / 255f, tb = tips < 0 ? b : (tips & 255) / 255f;

        int li = 0;
        for (HairLock lock : shape.locks()) {
            for (int i = 0; i < N; i++) {
                int o = (li * N + i) * 3;
                boolean sim = live != null && o + 2 < live.length;
                P[i * 3] = sim ? live[o] : (float) lock.x(i);
                P[i * 3 + 1] = sim ? live[o + 1] : (float) lock.y(i);
                P[i * 3 + 2] = sim ? live[o + 2] : (float) lock.z(i);
                AT[i] = i == 0 ? 0 : AT[i - 1] + dist(i - 1, i);
            }
            float total = AT[N - 1];
            float pcx = Float.NaN, pcy = 0, pcz = 0, psz = 0;
            int seg = 0;
            for (float d = 0; d <= total + 1e-4f; d += STEP) {
                while (seg < N - 2 && AT[seg + 1] < d) seg++;
                float span = AT[seg + 1] - AT[seg], f = span < 1e-6f ? 0 : (d - AT[seg]) / span;
                float x = lerp(P[seg * 3], P[seg * 3 + 3], f), y = lerp(P[seg * 3 + 1], P[seg * 3 + 4], f), z = lerp(P[seg * 3 + 2], P[seg * 3 + 5], f);
                float t = total < 1e-6f ? 0 : d / total;
                float w = (float) (lock.w(seg) + (lock.w(seg + 1) - lock.w(seg)) * f);
                float size = Math.max(UNIT, Math.round(w * .62f / UNIT) * UNIT);   // the cube, in whole grid steps
                float cx = snap(x, size), cy = snap(y, size), cz = snap(z, size);
                if (cx == pcx && cy == pcy && cz == pcz && size == psz) continue;    // the same cube again
                pcx = cx; pcy = cy; pcz = cz; psz = size;
                float shade = (.74f + .26f * t) * (1 + .09f * (hash(cx, cy, cz) - .5f));
                float dye = Math.max(0, Math.min(1, (t - .55f) / .45f));
                cube(vc, m, n, cx, cy, cz, size * .5f, (r + (tr - r) * dye) * shade, (g + (tg - g) * dye) * shade, (b + (tb - b) * dye) * shade, light, overlay);
            }
            li++;
        }
        pose.popPose();
    }

    /** A cube's centre on the grid: whole steps for an even number of steps across, half steps for an odd one. */
    private static float snap(float v, float size) {
        int steps = Math.round(size / UNIT);
        float off = (steps & 1) == 1 ? UNIT * .5f : 0;
        return Math.round((v - off) / UNIT) * UNIT + off;
    }

    private static void cube(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float h, float r, float g, float b, int light, int overlay) {
        float x0 = x - h, x1 = x + h, y0 = y - h, y1 = y + h, z0 = z - h, z1 = z + h;
        r = Math.min(1, r); g = Math.min(1, g); b = Math.min(1, b);
        face(vc, m, n, 0, -1, 0, r, g, b, light, overlay, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);   // top (y is down)
        face(vc, m, n, 0, 1, 0, r, g, b, light, overlay, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);    // bottom
        face(vc, m, n, 0, 0, -1, r, g, b, light, overlay, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);   // front
        face(vc, m, n, 0, 0, 1, r, g, b, light, overlay, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);    // back
        face(vc, m, n, -1, 0, 0, r, g, b, light, overlay, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);   // right
        face(vc, m, n, 1, 0, 0, r, g, b, light, overlay, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);    // left
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, float r, float g, float b,
                             int light, int overlay, float... p) {
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(r, g, b, 1f).uv(.5f, .5f).overlayCoords(overlay).uv2(light)
                    .normal(n, nx, ny, nz).endVertex();
        }
    }

    private static float dist(int a, int b) {
        float dx = P[b * 3] - P[a * 3], dy = P[b * 3 + 1] - P[a * 3 + 1], dz = P[b * 3 + 2] - P[a * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 0..1 from a cube's place, the same every frame. */
    private static float hash(float x, float y, float z) {
        int h = Float.floatToIntBits(x * 7.1f + y * 13.3f + z * 3.7f);
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12; h *= 0x297a2d39; h ^= h >>> 15;
        return (h & 1023) / 1023f;
    }
}
