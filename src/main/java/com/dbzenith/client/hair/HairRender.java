package com.dbzenith.client.hair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a head of hair in head space (the pose stack at the head) the way Minecraft models are built: each lock is a
 * short chain of boxes, one per segment, each turned along the lock and a step thinner than the one before, ending in
 * a small square tip. The boxes carry a hair texture at the model's own pixel size (strands running along the lock),
 * take Minecraft's face lighting, and are darker toward the root. Lock positions come from {@link HairSim} when the
 * hair moves, else from the rest shape. Render thread only (it reuses scratch arrays).
 */
public final class HairRender {
    /** A 16 x 16 tile of hair strands, greys the hair colour tints; it repeats along each lock. */
    public static final ResourceLocation MATERIAL = new ResourceLocation("dbzenith", "textures/entity/hair_block.png");
    private static final int N = HairLock.KNOTS;
    private static final double CX = 0, CY = -4.2, CZ = .6;                        // the middle of the head, for "outward"
    private static final float[] P = new float[N * 3], S = new float[N * 3], U = new float[N * 3], C = new float[8 * 3];

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
        float lift = .24f * (1 - Math.max(r, Math.max(g, b)));                       // black hair reads as dark grey, not a silhouette
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
            }
            frames();
            float along = (li * 5.3f) % 16, jitter = 1 + .05f * (float) Math.sin(li * 2.39 + lock.x(0));
            for (int i = 0; i < N - 1; i++) {
                float t = (i + .5f) / (N - 1);
                float shade = (.76f + .24f * t) * jitter;
                float dye = Math.max(0, Math.min(1, (t - .55f) / .45f));
                float cr = Math.min(1, (r + (tr - r) * dye) * shade), cg = Math.min(1, (g + (tg - g) * dye) * shade), cb = Math.min(1, (b + (tb - b) * dye) * shade);
                along = box(vc, m, n, i, (float) lock.w(i), (float) lock.t(i), along, cr, cg, cb, light, overlay);
            }
            li++;
        }
        pose.popPose();
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

    /**
     * One segment as a box from knot i to i + 1, {@code w} wide across the scalp and {@code t} thick, reaching a little
     * back into the segment before so the joints are closed. Returns how far along the lock the texture has got.
     */
    private static float box(VertexConsumer vc, Matrix4f m, Matrix3f n, int i, float w, float t, float along,
                             float r, float g, float b, int light, int overlay) {
        float ax = P[i * 3 + 3] - P[i * 3], ay = P[i * 3 + 4] - P[i * 3 + 1], az = P[i * 3 + 5] - P[i * 3 + 2];
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-4f) return along;
        ax /= len; ay /= len; az /= len;
        // the box's own square frame: across (S made square to the segment) and away (U)
        float sx = S[i * 3], sy = S[i * 3 + 1], sz = S[i * 3 + 2];
        float d = sx * ax + sy * ay + sz * az;
        sx -= ax * d; sy -= ay * d; sz -= az * d;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-5f) { sx = ay; sy = -ax; sz = 0; sl = Math.max(1e-5f, (float) Math.sqrt(sx * sx + sy * sy)); }
        sx /= sl; sy /= sl; sz /= sl;
        float ux = ay * sz - az * sy, uy = az * sx - ax * sz, uz = ax * sy - ay * sx;
        float hw = Math.max(.5f, w) * .5f, ht = Math.max(.5f, Math.max(t, w * .6f)) * .5f;
        float back = i == 0 ? 0 : Math.min(.45f, len * .3f);
        float bx = P[i * 3] - ax * back, by = P[i * 3 + 1] - ay * back, bz = P[i * 3 + 2] - az * back;
        float ex = P[i * 3 + 3], ey = P[i * 3 + 4], ez = P[i * 3 + 5];
        for (int c = 0; c < 8; c++) {                                                // 0-3 at the start, 4-7 at the end
            float qs = ((c & 1) == 0 ? -hw : hw), qu = ((c & 2) == 0 ? -ht : ht);
            float ox = c < 4 ? bx : ex, oy = c < 4 ? by : ey, oz = c < 4 ? bz : ez;
            C[c * 3] = ox + sx * qs + ux * qu;
            C[c * 3 + 1] = oy + sy * qs + uy * qu;
            C[c * 3 + 2] = oz + sz * qs + uz * qu;
        }
        float v0 = along / 16f, v1 = (along + len + back) / 16f, wu = hw * 2 / 16f, tu = ht * 2 / 16f;
        quad(vc, m, n, 2, 3, 7, 6, 0, v0, wu, v1, r, g, b, light, overlay);          // away from the scalp
        quad(vc, m, n, 1, 0, 4, 5, 0, v0, wu, v1, r, g, b, light, overlay);          // toward it
        quad(vc, m, n, 0, 2, 6, 4, 0, v0, tu, v1, r, g, b, light, overlay);          // one side
        quad(vc, m, n, 3, 1, 5, 7, 0, v0, tu, v1, r, g, b, light, overlay);          // the other
        quad(vc, m, n, 4, 6, 7, 5, 0, 0, wu, tu, r, g, b, light, overlay);           // the end
        if (i == 0) quad(vc, m, n, 1, 3, 2, 0, 0, 0, wu, tu, r, g, b, light, overlay);
        return along + len;
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int a, int b, int c, int d, float u0, float v0, float u1, float v1,
                             float r, float g, float bl, int light, int overlay) {
        float ex = C[c * 3] - C[a * 3], ey = C[c * 3 + 1] - C[a * 3 + 1], ez = C[c * 3 + 2] - C[a * 3 + 2];
        float fx = C[d * 3] - C[b * 3], fy = C[d * 3 + 1] - C[b * 3 + 1], fz = C[d * 3 + 2] - C[b * 3 + 2];
        float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-8f) return;
        nx /= nl; ny /= nl; nz /= nl;
        float mx = 0, my = 0, mz = 0, fx0 = 0, fy0 = 0, fz0 = 0;                       // face outward from the box's middle
        for (int k = 0; k < 8; k++) { mx += C[k * 3]; my += C[k * 3 + 1]; mz += C[k * 3 + 2]; }
        for (int k : new int[]{a, b, c, d}) { fx0 += C[k * 3]; fy0 += C[k * 3 + 1]; fz0 += C[k * 3 + 2]; }
        if (nx * (fx0 / 4 - mx / 8) + ny * (fy0 / 4 - my / 8) + nz * (fz0 / 4 - mz / 8) < 0) { nx = -nx; ny = -ny; nz = -nz; }
        vertex(vc, m, n, a, u0, v0, r, g, bl, nx, ny, nz, light, overlay);
        vertex(vc, m, n, b, u1, v0, r, g, bl, nx, ny, nz, light, overlay);
        vertex(vc, m, n, c, u1, v1, r, g, bl, nx, ny, nz, light, overlay);
        vertex(vc, m, n, d, u0, v1, r, g, bl, nx, ny, nz, light, overlay);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int k, float u, float v, float r, float g, float b,
                               float nx, float ny, float nz, int light, int overlay) {
        vc.vertex(m, C[k * 3], C[k * 3 + 1], C[k * 3 + 2]).color(r, g, b, 1f).uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
    }
}
