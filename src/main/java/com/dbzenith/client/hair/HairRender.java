package com.dbzenith.client.hair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a head of hair in head space (the pose stack at the head). Each lock is a smooth tapering band: wide across
 * the scalp, thin away from it, its frame carried along the lock so it twists only as much as it bends, ending in a
 * point. Roots are a little darker than tips, the scalp darker still, so the volume reads under flat light. Lock
 * positions come from {@link HairSim} when the hair moves, else from the rest shape. Render thread only (it reuses
 * scratch arrays).
 */
public final class HairRender {
    /** A plain white texture: the hair is coloured per vertex. */
    public static final ResourceLocation MATERIAL = new ResourceLocation("dbzenith", "textures/entity/aura_white.png");
    private static final int N = HairLock.KNOTS;
    private static final double CX = 0, CY = -4.2, CZ = .6;                        // the middle of the head, for "outward"
    private static final float[] SIGN = {1, 1, -1, 1, -1, -1, 1, -1};
    private static final float[] P = new float[N * 3], S = new float[N * 3], U = new float[N * 3], C = new float[N * 4 * 3];

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
        float lift = .1f * (1 - Math.max(r, Math.max(g, b)));                      // black hair keeps a little sheen
        r += (1 - r) * lift;
        g += (1 - g) * lift;
        b += (1 - b) * lift;
        float tr = tips < 0 ? r : ((tips >> 16) & 255) / 255f, tg = tips < 0 ? g : ((tips >> 8) & 255) / 255f, tb = tips < 0 ? b : (tips & 255) / 255f;

        float[] q = shape.cap().quads();                                          // the scalp
        for (int f = 0; f + 15 <= q.length; f += 15) {
            for (int c = 0; c < 4; c++) {
                vc.vertex(m, q[f + c * 3], q[f + c * 3 + 1], q[f + c * 3 + 2]).color(r * .74f, g * .74f, b * .74f, 1f).uv(.5f, .5f)
                        .overlayCoords(overlay).uv2(light).normal(n, q[f + 12], q[f + 13], q[f + 14]).endVertex();
            }
        }

        int li = 0;
        for (HairLock lock : shape.locks()) {
            for (int i = 0; i < N; i++) {
                int o = (li * N + i) * 3;
                if (live != null && o + 2 < live.length) {
                    P[i * 3] = live[o];
                    P[i * 3 + 1] = live[o + 1];
                    P[i * 3 + 2] = live[o + 2];
                } else {
                    P[i * 3] = (float) lock.x(i);
                    P[i * 3 + 1] = (float) lock.y(i);
                    P[i * 3 + 2] = (float) lock.z(i);
                }
            }
            frames(lock);
            for (int i = 0; i < N; i++) corners(i, (float) lock.w(i), (float) lock.t(i));
            for (int i = 0; i < N - 1; i++) {
                float t0 = i / (float) (N - 1), t1 = (i + 1) / (float) (N - 1);
                for (int side = 0; side < 4; side++) {
                    int a = side, bb = (side + 1) & 3;
                    quad(vc, m, n, i, a, bb, t0, t1, r, g, b, tr, tg, tb, light, overlay);
                }
            }
            li++;
        }
        pose.popPose();
    }

    /** Along the lock: the direction across the scalp (S) and away from it (U) at each knot, carried smoothly. */
    private static void frames(HairLock lock) {
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i < N; i++) {
            int a = Math.max(0, i - 1), c = Math.min(N - 1, i + 1);
            float tx = P[c * 3] - P[a * 3], ty = P[c * 3 + 1] - P[a * 3 + 1], tz = P[c * 3 + 2] - P[a * 3 + 2];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-5f) { tx = 0; ty = -1; tz = 0; tl = 1; }
            tx /= tl; ty /= tl; tz /= tl;
            float ox = (float) (P[i * 3] - CX), oy = (float) (P[i * 3 + 1] - CY), oz = (float) (P[i * 3 + 2] - CZ);
            if (i > 0) { ox += px * 1.5f; oy += py * 1.5f; oz += pz * 1.5f; }       // carry the last "outward" along
            float sx = ty * oz - tz * oy, sy = tz * ox - tx * oz, sz = tx * oy - ty * ox;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-5f) {
                if (i > 0) { sx = S[(i - 1) * 3]; sy = S[(i - 1) * 3 + 1]; sz = S[(i - 1) * 3 + 2]; }
                else { sx = 1; sy = 0; sz = 0; }
                sl = 1;
            }
            sx /= sl; sy /= sl; sz /= sl;
            if (i > 0 && sx * S[(i - 1) * 3] + sy * S[(i - 1) * 3 + 1] + sz * S[(i - 1) * 3 + 2] < 0) { sx = -sx; sy = -sy; sz = -sz; }
            float ux = sy * tz - sz * ty, uy = sz * tx - sx * tz, uz = sx * ty - sy * tx;  // away from the scalp
            S[i * 3] = sx; S[i * 3 + 1] = sy; S[i * 3 + 2] = sz;
            U[i * 3] = ux; U[i * 3 + 1] = uy; U[i * 3 + 2] = uz;
            px = ux; py = uy; pz = uz;
        }
    }

    /** The four corners of the cross-section at a knot: across and away, in order round the band. */
    private static void corners(int i, float w, float t) {
        float hw = Math.max(.05f, w) * .5f, ht = Math.max(.04f, t) * .5f;

        for (int c = 0; c < 4; c++) {
            int o = (i * 4 + c) * 3;
            float a = SIGN[c * 2] * hw, b = SIGN[c * 2 + 1] * ht;
            C[o] = P[i * 3] + S[i * 3] * a + U[i * 3] * b;
            C[o + 1] = P[i * 3 + 1] + S[i * 3 + 1] * a + U[i * 3 + 1] * b;
            C[o + 2] = P[i * 3 + 2] + S[i * 3 + 2] * a + U[i * 3 + 2] * b;
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int i, int a, int b, float t0, float t1,
                             float r, float g, float bl, float tr, float tg, float tb, int light, int overlay) {
        int p0 = (i * 4 + a) * 3, p1 = (i * 4 + b) * 3, p2 = ((i + 1) * 4 + b) * 3, p3 = ((i + 1) * 4 + a) * 3;
        float ex = C[p2] - C[p0], ey = C[p2 + 1] - C[p0 + 1], ez = C[p2 + 2] - C[p0 + 2];
        float fx = C[p3] - C[p1], fy = C[p3 + 1] - C[p1 + 1], fz = C[p3 + 2] - C[p1 + 2];
        float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-7f) return;
        nx /= nl; ny /= nl; nz /= nl;
        float mx = (C[p0] + C[p1] + C[p2] + C[p3]) * .25f - (P[i * 3] + P[(i + 1) * 3]) * .5f;   // face outward
        float my = (C[p0 + 1] + C[p1 + 1] + C[p2 + 1] + C[p3 + 1]) * .25f - (P[i * 3 + 1] + P[(i + 1) * 3 + 1]) * .5f;
        float mz = (C[p0 + 2] + C[p1 + 2] + C[p2 + 2] + C[p3 + 2]) * .25f - (P[i * 3 + 2] + P[(i + 1) * 3 + 2]) * .5f;
        if (nx * mx + ny * my + nz * mz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        vertex(vc, m, n, p0, t0, r, g, bl, tr, tg, tb, nx, ny, nz, light, overlay);
        vertex(vc, m, n, p1, t0, r, g, bl, tr, tg, tb, nx, ny, nz, light, overlay);
        vertex(vc, m, n, p2, t1, r, g, bl, tr, tg, tb, nx, ny, nz, light, overlay);
        vertex(vc, m, n, p3, t1, r, g, bl, tr, tg, tb, nx, ny, nz, light, overlay);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int p, float t, float r, float g, float b,
                               float tr, float tg, float tb, float nx, float ny, float nz, int light, int overlay) {
        float shade = .8f + .2f * t;                                              // darker at the root
        float dye = Math.max(0, Math.min(1, (t - .5f) / .5f));
        dye = dye * dye * (3 - 2 * dye);
        float cr = (r + (tr - r) * dye) * shade, cg = (g + (tg - g) * dye) * shade, cb = (b + (tb - b) * dye) * shade;
        vc.vertex(m, C[p], C[p + 1], C[p + 2]).color(cr, cg, cb, 1f).uv(.5f, .5f).overlayCoords(overlay).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
    }
}
