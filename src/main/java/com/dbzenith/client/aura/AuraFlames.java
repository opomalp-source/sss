package com.dbzenith.client.aura;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Inner flames (CX-24): soft licks of fire flowing up just inside the aura's outline, where the camera sees it (left
 * and right, and higher up near the top), so they give the aura its upward rush without ever breaking its silhouette.
 * Each one is a ribbon laid along the shell's own surface (bulges, sway and trail included, read from the built
 * {@link AuraShell}) and pulled in from it by more than its own width, so no part of it can stick out. Over its life
 * it grows up the surface, drifts upward and fades where it is; nothing breaks off. Nothing is allocated: the points
 * live in fixed arrays.
 */
final class AuraFlames {
    private static final int SEGS = 6;
    private static final float[] PX = new float[SEGS + 1], PY = new float[SEGS + 1], PZ = new float[SEGS + 1];
    private static final float[] SX = new float[SEGS + 1], SY = new float[SEGS + 1], SZ = new float[SEGS + 1];
    private static final float[] AT = new float[4];

    private AuraFlames() {}

    /**
     * Writes one layer's flames along {@code shell} (built for this aura this frame). Positions are camera-relative
     * ({@code ox, oy, oz} the feet). With {@code m} null they are written for the aura shader (the flame's seed in the
     * red channel, its fade in alpha); otherwise through {@code m} for the plain shaders, coloured {@code rgb} and
     * textured across {@code frames} flame frames.
     */
    static int emit(VertexConsumer vc, Matrix4f m, AuraShell shell, AuraDef.Layer l, float ox, float oy, float oz, float height,
                    float t, float wild, float seed, float fade, int rgb, int frames, int budget) {
        int count = Math.min(budget, Math.round(l.tongueCount * (0.75f + 0.25f * Math.min(2f, wild))));
        if (count <= 0 || fade <= 0.003f) return 0;
        float view = (float) Math.atan2(-oz, -ox);                               // the outline lies a quarter turn either side
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            float period = l.tongueLife * (0.75f + 0.5f * frac(i * 0.618f + seed * 0.13f)) / Math.max(0.5f, 0.7f + 0.3f * wild);
            float phase = (t + i * 0.37f + seed) / period;
            int cycle = Mth.floor(phase);
            float life = phase - cycle;
            int h = hash((int) (seed * 1013) + i * 7919 + cycle * 104729);
            float r0 = (h & 1023) / 1023f, r1 = ((h >>> 10) & 1023) / 1023f, r2 = ((h >>> 20) & 1023) / 1023f;

            boolean high = r0 < l.tongueTop;
            float side = (h & (1 << 30)) != 0 ? 1 : -1;
            float s0 = high ? 0.62f + 0.18f * r1 : l.tongueLow + (0.72f - l.tongueLow) * (float) Math.sqrt(r1);
            float a = view + side * (Mth.HALF_PI + (r2 - 0.5f) * 0.6f);
            float grow = AuraShell.smooth(0f, 0.35f, life);
            float base = Math.min(0.9f, s0 + l.tongueRise * 0.5f * life);
            float span = l.tongueLength * (0.6f + 0.7f * r2) * (0.8f + 0.2f * Math.min(2f, wild)) * grow;
            float tip = Math.min(0.96f, base + span);
            float alpha = fade * AuraShell.smooth(0f, 0.15f, life) * (1 - AuraShell.smooth(0.6f, 1f, life));
            if (tip - base < 0.01f || alpha < 0.01f) continue;
            float width = l.tongueLength * height * l.tongueWidth * (0.7f + 0.6f * r1) * (0.6f + 0.4f * grow) * 0.5f;
            float wavePhase = t * 4.0f + i * 1.7f + seed;

            for (int k = 0; k <= SEGS; k++) {
                float q = k / (float) SEGS;
                float sq = Mth.lerp(q, base, tip);
                float ak = a + side * l.tongueWave * 0.35f * q * Mth.sin(wavePhase - q * 3.0f);   // sways round the surface
                shell.sample(sq, ak, 0f, AT);
                float inset = Math.min(0.9f, l.tongueInset + width * 1.2f / Math.max(0.05f, AT[3]));   // in by more than its width
                shell.sample(sq, ak, inset, AT);
                PX[k] = ox + AT[0];
                PY[k] = oy + AT[1];
                PZ[k] = oz + AT[2];
            }

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
            int seedByte = (int) (frac(r0 * 7.31f + r1 * 3.17f) * 255);
            int a8 = Mth.clamp((int) (alpha * 255), 0, 255);
            int frame = frames > 0 ? (int) ((t * 18f + i * 5) % frames) : 0;
            float u0 = frames > 0 ? frame / (float) frames : 0f, u1 = frames > 0 ? (frame + 1) / (float) frames : 1f;
            for (int k = 0; k < SEGS; k++) {
                float q0 = k / (float) SEGS, q1 = (k + 1) / (float) SEGS;
                float w0 = width * (1 - 0.5f * q0), w1 = width * (1 - 0.5f * q1);     // narrower towards the tip
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
