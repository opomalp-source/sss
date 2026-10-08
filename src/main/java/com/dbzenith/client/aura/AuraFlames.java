package com.dbzenith.client.aura;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Flame tongues (CX-24): the licks that make an aura burn. Each is a ribbon bent along a short curve, set on the
 * aura's outline as the camera sees it (left, right and over the top), so it always reads as flame against the sky and
 * never across the fighter. Over its life it shoots out of the outline, climbs, waves, breaks off at the base and fades;
 * moving fast streams them back like a comet's tail. Nothing is allocated: the points live in fixed arrays.
 */
final class AuraFlames {
    private static final int SEGS = 6;
    private static final float[] PX = new float[SEGS + 1], PY = new float[SEGS + 1], PZ = new float[SEGS + 1];
    private static final float[] SX = new float[SEGS + 1], SY = new float[SEGS + 1], SZ = new float[SEGS + 1];

    private AuraFlames() {}

    /**
     * Writes one layer's tongues. Positions are camera-relative ({@code ox, oy, oz} the feet). With {@code m} null they
     * are written for the aura shader (the tongue's seed in the red channel, its life fade in alpha); otherwise through
     * {@code m} for the plain shaders, coloured {@code rgb} and textured across {@code frames} flame frames.
     */
    static int emit(VertexConsumer vc, Matrix4f m, AuraDef d, AuraDef.Layer l, float ox, float oy, float oz, float radius, float height,
                    float bottom, float t, float wild, float seed, float tx, float ty, float tz, float fade, int rgb, int frames, int budget) {
        int count = Math.min(budget, Math.round(l.tongueCount * (0.75f + 0.25f * Math.min(2f, wild))));
        if (count <= 0 || fade <= 0.003f) return 0;
        // the eye's bearing round the aura: the outline lies a quarter turn either side of it
        float view = (float) Math.atan2(-oz, -ox);
        float camDist = Mth.sqrt(ox * ox + oz * oz);
        float lr = radius * l.scale, lh = height * l.heightScale, lb = bottom + l.lift * height;
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            float period = l.tongueLife * (0.75f + 0.5f * frac(i * 0.618f + seed * 0.13f)) / Math.max(0.5f, 0.7f + 0.3f * wild);
            float phase = (t + i * 0.37f + seed) / period;
            int cycle = Mth.floor(phase);
            float life = phase - cycle;
            int h = hash((int) (seed * 1013) + i * 7919 + cycle * 104729);
            float r0 = (h & 1023) / 1023f, r1 = ((h >>> 10) & 1023) / 1023f, r2 = ((h >>> 20) & 1023) / 1023f;

            // where it starts: up a side of the outline, or off the top
            boolean top = r0 < l.tongueTop;
            float side = (h & (1 << 30)) != 0 ? 1 : -1;
            float s0 = top ? 0.82f + 0.12f * r1 : l.tongueLow + (0.85f - l.tongueLow) * (float) Math.sqrt(r1);
            float a = view + side * (Mth.HALF_PI + (r2 - 0.5f) * (top ? 1.1f : 0.7f));
            float s = Math.min(0.97f, s0 + l.tongueRise * life);
            float rr = AuraShell.profile(d, s) * lr * (1 - l.tongueInset);
            if (d.flare > 0) rr *= 1 + d.flare * (1 - AuraShell.smooth(0, Math.max(0.05f, d.widest), s));
            float ca = Mth.cos(a), sa = Mth.sin(a);
            float bx = ox + ca * rr, by = oy + lb + s * lh, bz = oz + sa * rr;

            // which way it shoots: up the outline, leaning out, more upright the higher it starts
            float ds = 0.04f;
            float dr = (AuraShell.profile(d, Math.min(1f, s + ds)) - AuraShell.profile(d, Math.max(0f, s - ds))) * lr / (2 * ds);
            float ux = ca * dr, uy = lh, uz = sa * dr;
            float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul;
            uy /= ul;
            uz /= ul;
            float out = top ? 0.12f : 0.45f;
            float dx = ux + ca * out, dy = uy + (top ? 0.6f : 0.25f), dz = uz + sa * out;
            float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= dl;
            dy /= dl;
            dz /= dl;
            // moving fast, they stream out behind like a comet's tail
            float tl = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            float streamK = Math.min(0.85f, tl / Math.max(0.2f, lh * 0.35f));
            if (streamK > 0.01f) {
                dx = Mth.lerp(streamK, dx, tx / tl);
                dy = Mth.lerp(streamK, dy, ty / tl);
                dz = Mth.lerp(streamK, dz, tz / tl);
                dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl;
                dy /= dl;
                dz /= dl;
            }

            // how long: shoots out fast, then the base lets go and it shrinks away upward
            float len = l.tongueLength * lh * (0.6f + 0.7f * r2) * (top ? 1.25f : 1f) * (0.8f + 0.2f * wild) * (1 + 1.6f * streamK);
            float grow = AuraShell.smooth(0f, 0.3f, life);
            float letGo = AuraShell.smooth(0.45f, 1f, life);
            float tip = len * grow, base = len * letGo * 0.85f;
            float alpha = fade * (1 - AuraShell.smooth(0.7f, 1f, life));
            if (tip - base < 0.01f || alpha < 0.01f) continue;
            float width = len * l.tongueWidth * (0.7f + 0.6f * r1) * (1 - 0.4f * letGo);
            float wavePhase = t * 6.0f + i * 1.7f + seed;

            for (int k = 0; k <= SEGS; k++) {
                float q = k / (float) SEGS;
                float along = Mth.lerp(q, base, tip);
                float bend = q * q;
                float w = l.tongueWave * len * Mth.sin(wavePhase - q * 4.0f) * q;
                PX[k] = bx + dx * along + ca * w + tx * bend * along / Math.max(0.2f, lh) * 1.5f;
                PY[k] = by + dy * along + ty * bend * along / Math.max(0.2f, lh) * 1.5f;
                PZ[k] = bz + dz * along + sa * w + tz * bend * along / Math.max(0.2f, lh) * 1.5f;
            }

            int seedByte = (int) (frac(r0 * 7.31f + r1 * 3.17f) * 255);
            int a8 = Mth.clamp((int) (alpha * 255), 0, 255);
            int frame = frames > 0 ? (int) ((t * 18f + i * 5) % frames) : 0;
            float u0 = frames > 0 ? frame / (float) frames : 0f, u1 = frames > 0 ? (frame + 1) / (float) frames : 1f;
            // across the ribbon: square to its length and to the line of sight, kept facing one way so it never twists
            for (int k = 0; k <= SEGS; k++) {
                float sx = side(k, 0), sy = side(k, 1), sz = side(k, 2);
                if (k > 0 && sx * SX[k - 1] + sy * SY[k - 1] + sz * SZ[k - 1] < 0) {
                    sx = -sx;
                    sy = -sy;
                    sz = -sz;
                }
                SX[k] = sx;
                SY[k] = sy;
                SZ[k] = sz;
            }
            for (int k = 0; k < SEGS; k++) {
                float q0 = k / (float) SEGS, q1 = (k + 1) / (float) SEGS;
                float w0 = width, w1 = width;
                float sx0 = SX[k], sy0 = SY[k], sz0 = SZ[k];
                float sx1 = SX[k + 1], sy1 = SY[k + 1], sz1 = SZ[k + 1];
                if (m == null) {
                    put(vc, PX[k] - sx0 * w0, PY[k] - sy0 * w0, PZ[k] - sz0 * w0, 0, q0, seedByte, a8);
                    put(vc, PX[k] + sx0 * w0, PY[k] + sy0 * w0, PZ[k] + sz0 * w0, 1, q0, seedByte, a8);
                    put(vc, PX[k + 1] + sx1 * w1, PY[k + 1] + sy1 * w1, PZ[k + 1] + sz1 * w1, 1, q1, seedByte, a8);
                    put(vc, PX[k + 1] - sx1 * w1, PY[k + 1] - sy1 * w1, PZ[k + 1] - sz1 * w1, 0, q1, seedByte, a8);
                } else {
                    int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                    plain(vc, m, PX[k] - sx0 * w0, PY[k] - sy0 * w0, PZ[k] - sz0 * w0, u0, 1 - q0, r, g, b, a8);
                    plain(vc, m, PX[k] + sx0 * w0, PY[k] + sy0 * w0, PZ[k] + sz0 * w0, u1, 1 - q0, r, g, b, a8);
                    plain(vc, m, PX[k + 1] + sx1 * w1, PY[k + 1] + sy1 * w1, PZ[k + 1] + sz1 * w1, u1, 1 - q1, r, g, b, a8);
                    plain(vc, m, PX[k + 1] - sx1 * w1, PY[k + 1] - sy1 * w1, PZ[k + 1] - sz1 * w1, u0, 1 - q1, r, g, b, a8);
                }
            }
            drawn++;
        }
        return drawn;
    }

    private static final float[] SIDE = new float[3];

    /** One axis of the unit vector across the ribbon at point k: its length direction crossed with the line of sight. */
    private static float side(int k, int axis) {
        if (axis == 0) {
            int a = Math.max(0, k - 1), b = Math.min(SEGS, k + 1);
            float lx = PX[b] - PX[a], ly = PY[b] - PY[a], lz = PZ[b] - PZ[a];
            float vx = PX[k], vy = PY[k], vz = PZ[k];                           // the eye is at the origin
            float cx = ly * vz - lz * vy, cy = lz * vx - lx * vz, cz = lx * vy - ly * vx;
            float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            if (cl < 1e-6f) {
                SIDE[0] = 1;
                SIDE[1] = 0;
                SIDE[2] = 0;
            } else {
                SIDE[0] = cx / cl;
                SIDE[1] = cy / cl;
                SIDE[2] = cz / cl;
            }
        }
        return SIDE[axis];
    }

    private static void put(VertexConsumer vc, float x, float y, float z, float u, float v, int seed, int a) {
        vc.vertex(x, y, z).color(seed, 0, 0, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0, 1, 0).endVertex();
    }

    private static void plain(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0, 1, 0).endVertex();
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    private static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }
}
