package com.dbzenith.client.hair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a head of hair in head space (the pose stack at the head), shaded like anime hair:
 * <ul>
 *   <li>each lock is a tapering band ending in a point, its frame carried along the lock, textured along its length
 *       ({@code hair_lock.png}: dark edges that outline every lock, strand lines, a broken gloss streak);</li>
 *   <li>cel tones: every face takes one of three tones by how it faces a key light above and in front, with crisp
 *       steps between them, the shadow tone deeper and richer than the colour (warm for gold, deep for blue), dark hair
 *       lifted with a cool sheen so it never goes dead black;</li>
 *   <li>darker toward the roots and the scalp, a touch of difference from lock to lock;</li>
 *   <li>the tones are painted, not lit: every vertex faces up, so Minecraft's soft entity lighting doesn't dull them
 *       (gold would go olive); the world's light level still darkens hair in shade.</li>
 * </ul>
 * Lock positions come from {@link HairSim} when the hair moves, else from the rest shape. Render thread only.
 */
public final class HairRender {
    public static final ResourceLocation MATERIAL = new ResourceLocation("dbzenith", "textures/entity/hair_lock.png");
    private static final int N = HairLock.KNOTS;
    private static final double CX = 0, CY = -4.2, CZ = .6;                        // the middle of the head, for "outward"
    /** The key light, in head space (y down): above, in front, a little to the wearer's right. */
    private static final float LX = -.28f, LY = -.86f, LZ = -.42f;
    private static final float[] SIGN = {1, 1, -1, 1, -1, -1, 1, -1};
    private static final float[] P = new float[N * 3], S = new float[N * 3], U = new float[N * 3], C = new float[N * 4 * 3];
    /** The tones this frame: lit, mid, shadow, tip dye (r, g, b each). */
    private static final float[] LIT = new float[3], MID = new float[3], DARK = new float[3], DYE = new float[3], GLOSS = new float[3];
    /** Where along a lock the gloss lies. */
    private static final float GLOSS_FROM = .2f, GLOSS_TO = .62f;
    private static boolean dyed;

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
        palette(rgb);
        dyed = tips >= 0;
        if (dyed) { DYE[0] = ((tips >> 16) & 255) / 255f; DYE[1] = ((tips >> 8) & 255) / 255f; DYE[2] = (tips & 255) / 255f; }

        float[] q = shape.cap().quads();                                          // the scalp, in the shadow tone
        for (int f = 0; f + 15 <= q.length; f += 15) {
            float tone = q[f + 13] < -.5f ? .9f : .78f;                             // the scalp in the mid tone, its sides darker
            for (int c = 0; c < 4; c++) {
                vc.vertex(m, q[f + c * 3], q[f + c * 3 + 1], q[f + c * 3 + 2]).color(MID[0] * tone, MID[1] * tone, MID[2] * tone, 1f)
                        .uv(c == 1 || c == 2 ? .45f : .05f, .62f).overlayCoords(overlay).uv2(light).normal(n, 0, -1, 0).endVertex();
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
            frames();
            for (int i = 0; i < N; i++) corners(i, (float) lock.w(i), (float) lock.t(i));
            float jitter = 1 + .07f * (float) Math.sin(li * 2.39 + lock.x(0) * 1.7);    // no two locks quite the same
            for (int i = 0; i < N - 1; i++) {
                float t0 = i / (float) (N - 1), t1 = (i + 1) / (float) (N - 1);
                for (int side = 0; side < 4; side++) quad(vc, m, n, i, side, (side + 1) & 3, t0, t1, jitter, light, overlay);
            }
            li++;
        }
        pose.popPose();
    }

    /**
     * The three tones from the colour: lit is the colour itself (dark hair lifted with a cool sheen), mid a step down,
     * shadow deeper and more saturated (gold goes amber, blue goes deep blue).
     */
    private static void palette(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float sheen = Math.max(0, .34f - max) * .9f;                                // black and dark hair: a blue-grey sheen
        LIT[0] = Math.min(1, r * 1.1f + sheen * .82f); LIT[1] = Math.min(1, g * 1.1f + sheen * .88f); LIT[2] = Math.min(1, b * 1.1f + sheen * 1.1f);
        // shadows shift hue the way painted hair does: warm colours toward red-orange (gold goes amber, not olive),
        // cool ones toward blue; the stronger the colour, the stronger the shift
        float warm = Math.max(-1, Math.min(1, (r - b) / Math.max(.05f, max)));
        LIT[1] *= 1 - .05f * Math.max(0, warm);                                    // warm hair a touch warmer: gold, not lemon
        float mr = .9f + .06f * warm, mg = .84f - .04f * Math.abs(warm), mb = .86f - .1f * warm;
        float dr = .74f + .12f * warm, dg = .56f - .02f * Math.abs(warm), db = .58f - .14f * warm;
        MID[0] = LIT[0] * mr; MID[1] = LIT[1] * mg; MID[2] = LIT[2] * mb;
        DARK[0] = LIT[0] * dr + sheen * .1f; DARK[1] = LIT[1] * dg + sheen * .12f; DARK[2] = LIT[2] * db + sheen * .2f;
        float white = max < .34f ? .42f : .62f;                                     // the gloss: toward white (cooler on dark hair)
        GLOSS[0] = LIT[0] + (1 - LIT[0]) * white * (max < .34f ? .85f : 1);
        GLOSS[1] = LIT[1] + (1 - LIT[1]) * white * (max < .34f ? .9f : 1);
        GLOSS[2] = LIT[2] + (1 - LIT[2]) * white;
    }

    /** Along the lock: the direction across the scalp (S) and away from it (U) at each knot, carried smoothly. */
    private static void frames() {
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i < N; i++) {
            int a = Math.max(0, i - 1), c = Math.min(N - 1, i + 1);
            float tx = P[c * 3] - P[a * 3], ty = P[c * 3 + 1] - P[a * 3 + 1], tz = P[c * 3 + 2] - P[a * 3 + 2];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-5f) { tx = 0; ty = -1; tz = 0; tl = 1; }
            tx /= tl; ty /= tl; tz /= tl;
            float ox = (float) (P[i * 3] - CX), oy = (float) (P[i * 3 + 1] - CY), oz = (float) (P[i * 3 + 2] - CZ);
            if (i > 0) { ox += px * 1.5f; oy += py * 1.5f; oz += pz * 1.5f; }
            float sx = ty * oz - tz * oy, sy = tz * ox - tx * oz, sz = tx * oy - ty * ox;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-5f) {
                if (i > 0) { sx = S[(i - 1) * 3]; sy = S[(i - 1) * 3 + 1]; sz = S[(i - 1) * 3 + 2]; }
                else { sx = 1; sy = 0; sz = 0; }
                sl = 1;
            }
            sx /= sl; sy /= sl; sz /= sl;
            if (i > 0 && sx * S[(i - 1) * 3] + sy * S[(i - 1) * 3 + 1] + sz * S[(i - 1) * 3 + 2] < 0) { sx = -sx; sy = -sy; sz = -sz; }
            float ux = sy * tz - sz * ty, uy = sz * tx - sx * tz, uz = sx * ty - sy * tx;
            S[i * 3] = sx; S[i * 3 + 1] = sy; S[i * 3 + 2] = sz;
            U[i * 3] = ux; U[i * 3 + 1] = uy; U[i * 3 + 2] = uz;
            px = ux; py = uy; pz = uz;
        }
    }

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

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int i, int a, int b, float t0, float t1, float jitter, int light, int overlay) {
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
        float d = nx * LX + ny * LY + nz * LZ;                                       // cel steps: lit, mid, shadow
        float[] tone = d > .12f ? LIT : d > -.5f ? MID : DARK;
        boolean wide = a == 0 || a == 2;                                              // the broad faces carry the gloss
        float u0, u1;
        if (wide) { u0 = 0; u1 = .5f; } else { u0 = .03f; u1 = .15f; }              // the lock half of the atlas; thin sides edge and strand only
        vertex(vc, m, n, p0, u0, t0, tone, jitter, nx, ny, nz, light, overlay);
        vertex(vc, m, n, p1, u1, t0, tone, jitter, nx, ny, nz, light, overlay);
        vertex(vc, m, n, p2, u1, t1, tone, jitter, nx, ny, nz, light, overlay);
        vertex(vc, m, n, p3, u0, t1, tone, jitter, nx, ny, nz, light, overlay);
        if (wide && tone == LIT && t1 > GLOSS_FROM && t0 < GLOSS_TO) {            // the gloss, laid just over the lit side
            float o = .05f, v0 = (t0 - GLOSS_FROM) / (GLOSS_TO - GLOSS_FROM), v1 = (t1 - GLOSS_FROM) / (GLOSS_TO - GLOSS_FROM);
            gloss(vc, m, n, p0, .5f, v0, nx, ny, nz, o, light, overlay);
            gloss(vc, m, n, p1, 1f, v0, nx, ny, nz, o, light, overlay);
            gloss(vc, m, n, p2, 1f, v1, nx, ny, nz, o, light, overlay);
            gloss(vc, m, n, p3, .5f, v1, nx, ny, nz, o, light, overlay);
        }
    }

    private static void gloss(VertexConsumer vc, Matrix4f m, Matrix3f n, int p, float u, float v, float nx, float ny, float nz, float o, int light, int overlay) {
        vc.vertex(m, C[p] + nx * o, C[p + 1] + ny * o, C[p + 2] + nz * o).color(GLOSS[0], GLOSS[1], GLOSS[2], 1f)
                .uv(u, Math.max(0, Math.min(1, v))).overlayCoords(overlay).uv2(light).normal(n, 0, -1, 0).endVertex();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int p, float u, float t, float[] tone, float jitter,
                               float nx, float ny, float nz, int light, int overlay) {
        float shade = (.7f + .3f * (float) Math.pow(t, .7)) * jitter;               // darker toward the roots
        float r = tone[0], g = tone[1], b = tone[2];
        if (dyed) {
            float dye = Math.max(0, Math.min(1, (t - .5f) / .5f));
            dye = dye * dye * (3 - 2 * dye);
            float k = tone == LIT ? 1 : tone == MID ? .8f : .58f;
            r += (DYE[0] * k - r) * dye; g += (DYE[1] * k - g) * dye; b += (DYE[2] * k - b) * dye;
        }
        vc.vertex(m, C[p], C[p + 1], C[p + 2]).color(Math.min(1, r * shade), Math.min(1, g * shade), Math.min(1, b * shade), 1f)
                .uv(u, t).overlayCoords(overlay).uv2(light).normal(n, 0, -1, 0).endVertex();
    }
}
