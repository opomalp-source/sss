package com.dbzenith.client.aura;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The aura's shell (CX-24): an egg of rings round the fighter, rounded under the feet, widest low down and tapering
 * well above the head, with slow bulges rising through it. Built anew each frame into fixed arrays (nothing is
 * allocated), then written out as quads whose outside faces are wound to be culled, so only the far half shows.
 */
final class AuraShell {
    static final int MAX_RINGS = 32, MAX_SEGS = 48;
    private static final int N = (MAX_RINGS + 1) * MAX_SEGS;

    private final float[] x = new float[N], y = new float[N], z = new float[N];
    private final float[] nx = new float[N], ny = new float[N], nz = new float[N];
    private final float[] cos = new float[MAX_SEGS], sin = new float[MAX_SEGS];
    private int trigSegs = -1;
    int rings, segs;
    /** Where the shell is centred in height (relative to the feet), for scaling the glow about it. */
    float centerY;

    /** The bare outline at height share s (0 bottom .. 1 top), 0..1 of the widest radius. */
    static float profile(AuraDef d, float s) {
        float w = Mth.clamp(d.widest, 0.05f, 0.9f);
        if (s < w) {
            float q = (w - s) / w;
            return Mth.sqrt(Math.max(0, 1 - q * q));
        }
        float u = (s - w) / (1 - w);
        return (float) Math.pow(Math.max(0, 1 - Math.pow(u, d.taper)), d.tip);
    }

    /**
     * Builds the shell: {@code radius} at its widest and {@code height} tall (blocks), starting {@code bottom} above
     * the feet; {@code t} in seconds; {@code lobes} and {@code sway} scale the data's bulges and lean.
     */
    void build(AuraDef d, float radius, float height, float bottom, float t, float seed, int rings, int segs, float lobes, float sway) {
        this.rings = Math.min(rings, MAX_RINGS);
        this.segs = Math.min(segs, MAX_SEGS);
        if (trigSegs != this.segs) {
            for (int j = 0; j < this.segs; j++) {
                float a = j * Mth.TWO_PI / this.segs;
                cos[j] = Mth.cos(a);
                sin[j] = Mth.sin(a);
            }
            trigSegs = this.segs;
        }
        centerY = bottom + height * 0.45f;
        int c0 = d.lobeCount, c1 = d.lobeCount + 2, c2 = d.lobeCount * 2 + 1;   // whole numbers round, so no seam
        float rise = d.lobeRise * t;
        float leanX = sway * height * Mth.sin(t * 0.9f + seed), leanZ = sway * height * Mth.cos(t * 0.7f + seed * 1.3f);
        for (int i = 0; i <= this.rings; i++) {
            float s = i / (float) this.rings;
            float base = profile(d, s) * radius;
            float env = Mth.sqrt(Math.max(0, Mth.sin(Mth.PI * s)));
            float yy = bottom + s * height;
            float lean = s * s;
            float w0 = Mth.TWO_PI * (1.2f * s - rise), w1 = Mth.TWO_PI * (2.0f * s - rise * 1.3f), w2 = Mth.TWO_PI * (3.1f * s - rise * 1.7f);
            for (int j = 0; j < this.segs; j++) {
                float a = j * Mth.TWO_PI / this.segs;
                float l = 0.5f * Mth.sin(c0 * a + seed + 0.3f * t) * Mth.sin(w0 + seed * 1.7f)
                        + 0.3f * Mth.sin(c1 * a + seed * 2.1f - 0.4f * t) * Mth.sin(w1 + seed * 0.6f)
                        + 0.2f * Mth.sin(c2 * a + seed * 3.3f + 0.5f * t) * Mth.sin(w2 + seed * 2.9f);
                float r = base * (1 + lobes * env * l);
                int k = i * MAX_SEGS + j;
                x[k] = cos[j] * r + leanX * lean;
                y[k] = yy;
                z[k] = sin[j] * r + leanZ * lean;
            }
        }
        for (int i = 0; i <= this.rings; i++) {                                      // normals from the neighbours
            int up = Math.min(this.rings, i + 1), dn = Math.max(0, i - 1);
            for (int j = 0; j < this.segs; j++) {
                int k = i * MAX_SEGS + j;
                int jn = (j + 1) % this.segs, jp = (j + this.segs - 1) % this.segs;
                int ku = up * MAX_SEGS + j, kd = dn * MAX_SEGS + j, kn = i * MAX_SEGS + jn, kp = i * MAX_SEGS + jp;
                float sx = x[ku] - x[kd], sy = y[ku] - y[kd], sz = z[ku] - z[kd];
                float ax = x[kn] - x[kp], ay = y[kn] - y[kp], az = z[kn] - z[kp];
                float cx = sy * az - sz * ay, cy = sz * ax - sx * az, cz = sx * ay - sy * ax;
                float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
                if (len < 1e-6f) {                                                   // the poles
                    nx[k] = 0;
                    ny[k] = i == 0 ? -1 : 1;
                    nz[k] = 0;
                } else {
                    nx[k] = cx / len;
                    ny[k] = cy / len;
                    nz[k] = cz / len;
                }
            }
        }
    }

    /**
     * Writes the shell for the aura shader: positions camera-relative ({@code ox, oy, oz} is the feet), scaled by
     * {@code scale} about its centre; texture coordinates run round (u) and up (v) for the shader's noise.
     */
    void emit(VertexConsumer vc, float ox, float oy, float oz, float scale, int alpha) {
        for (int i = 0; i < rings; i++) {
            float v0 = i / (float) rings, v1 = (i + 1) / (float) rings;
            for (int j = 0; j < segs; j++) {
                int jn = (j + 1) % segs;
                float u0 = j / (float) segs, u1 = (j + 1) / (float) segs;
                put(vc, i * MAX_SEGS + j, ox, oy, oz, scale, u0, v0, alpha);
                put(vc, i * MAX_SEGS + jn, ox, oy, oz, scale, u1, v0, alpha);
                put(vc, (i + 1) * MAX_SEGS + jn, ox, oy, oz, scale, u1, v1, alpha);
                put(vc, (i + 1) * MAX_SEGS + j, ox, oy, oz, scale, u0, v1, alpha);
            }
        }
    }

    private void put(VertexConsumer vc, int k, float ox, float oy, float oz, float scale, float u, float v, int alpha) {
        vc.vertex(ox + x[k] * scale, oy + centerY + (y[k] - centerY) * scale, oz + z[k] * scale)
                .color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(nx[k], ny[k], nz[k]).endVertex();
    }

    /**
     * The same for the plain built-in shaders (shader packs): the colour worked out here per corner from how squarely
     * it faces the eye, edge to core, with no noise cut. {@code glow} draws the soft outer band instead.
     */
    void emitPlain(VertexConsumer vc, Matrix4f m, Matrix3f n, float ox, float oy, float oz, float scale, boolean glow,
                   int core, int mid, int edge, float coreAlpha, float edgeAlpha, float fade) {
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segs; j++) {
                int jn = (j + 1) % segs;
                plain(vc, m, n, i * MAX_SEGS + j, ox, oy, oz, scale, glow, core, mid, edge, coreAlpha, edgeAlpha, fade);
                plain(vc, m, n, i * MAX_SEGS + jn, ox, oy, oz, scale, glow, core, mid, edge, coreAlpha, edgeAlpha, fade);
                plain(vc, m, n, (i + 1) * MAX_SEGS + jn, ox, oy, oz, scale, glow, core, mid, edge, coreAlpha, edgeAlpha, fade);
                plain(vc, m, n, (i + 1) * MAX_SEGS + j, ox, oy, oz, scale, glow, core, mid, edge, coreAlpha, edgeAlpha, fade);
            }
        }
    }

    private void plain(VertexConsumer vc, Matrix4f m, Matrix3f n, int k, float ox, float oy, float oz, float scale, boolean glow,
                       int core, int mid, int edge, float coreAlpha, float edgeAlpha, float fade) {
        float px = ox + x[k] * scale, py = oy + centerY + (y[k] - centerY) * scale, pz = oz + z[k] * scale;
        float len = Mth.sqrt(px * px + py * py + pz * pz);
        float facing = len < 1e-4f ? 1 : Math.abs(nx[k] * px + ny[k] * py + nz[k] * pz) / len;
        float depth = 1 - Mth.sqrt(Math.max(0, 1 - facing * facing));
        int rgb;
        float a;
        if (glow) {
            rgb = edge;
            float band = Math.max(0.005f, coreAlpha);                          // for the glow: how far in the shell's outline lies
            a = (float) Math.pow(smooth(0, band, depth), 1.5) * (1 - 0.8f * smooth(band, band * 4, depth)) * fade;
        } else {
            rgb = AuraDef.mix(edge, mid, smooth(0.02f, 0.2f, depth));
            rgb = AuraDef.mix(rgb, core, smooth(0.16f, 0.48f, depth));
            a = Mth.lerp(smooth(0.1f, 0.5f, depth), edgeAlpha, coreAlpha) * smooth(0, 0.03f, depth) * fade;
        }
        vc.vertex(m, px, py, pz).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Mth.clamp((int) (a * 255), 0, 255))
                .uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    static float smooth(float e0, float e1, float v) {
        float t = Mth.clamp((v - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
