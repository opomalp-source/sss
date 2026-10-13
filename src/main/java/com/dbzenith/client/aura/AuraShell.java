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
    static final int MAX_RINGS = 44, MAX_SEGS = 72;
    private static final int N = (MAX_RINGS + 1) * MAX_SEGS;

    private final float[] x = new float[N], y = new float[N], z = new float[N];
    private final float[] nx = new float[N], ny = new float[N], nz = new float[N];
    /** Each ring's middle (it moves with the lean and the trail). */
    private final float[] rcx = new float[MAX_RINGS + 1], rcy = new float[MAX_RINGS + 1], rcz = new float[MAX_RINGS + 1];
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
     * the feet; {@code t} in seconds; {@code lobes} and {@code sway} scale the data's bulges and lean; {@code peak}
     * stretches the top into one tall flame (share of the height); {@code tx, ty, tz} is how far the top streams
     * away (blocks) when the fighter moves fast, the back of the shell going further than the front.
     */
    void build(AuraDef d, float radius, float height, float bottom, float t, float seed, int rings, int segs, float lobes, float sway,
               float peak, float tx, float ty, float tz) {
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
        // A bulge finer than the mesh can carry (fewer than about six rings or segments a wave) would come out as sharp
        // polygon zigzags, so each wave fades out as it nears that limit.
        float rows0 = d.lobeRows;
        // The cap is a fixed level of detail too (about five waves up, seven round), so a finer mesh up close shows the
        // same broad, cohesive bulges and not a busier outline.
        int vr = Math.min(this.rings, 30), sr = Math.min(this.segs, 42);
        float k0 = 0.5f * fine(rows0, vr) * fine(c0, sr);
        float k1 = 0.3f * fine(rows0 * 1.67f, vr) * fine(c1, sr);
        float k2 = 0.2f * fine(rows0 * 2.6f, vr) * fine(c2, sr);
        float leanX = sway * height * Mth.sin(t * 0.9f + seed), leanZ = sway * height * Mth.cos(t * 0.7f + seed * 1.3f);
        float th = Mth.sqrt(tx * tx + tz * tz);
        float hx = th > 1e-4f ? tx / th : 0, hz = th > 1e-4f ? tz / th : 0;
        float across = th / Math.max(1e-4f, Mth.sqrt(tx * tx + ty * ty + tz * tz));   // how much of the motion is sideways
        for (int i = 0; i <= this.rings; i++) {
            float s = i / (float) this.rings;
            float base = profile(d, s) * radius;
            if (d.flare > 0) base *= 1 + d.flare * (1 - AuraShell.smooth(0, Math.max(0.05f, d.widest), s));
            float env = Mth.sqrt(Math.max(0, Mth.sin(Mth.PI * s)));
            float q = Mth.clamp((s - 0.6f) / 0.4f, 0, 1);
            float yy = bottom + (s + peak * q * q) * height;
            float lean = s * s;
            float drag = 0.15f + 0.85f * (float) Math.pow(s, 1.3);
            float rows = d.lobeRows;
            float w0 = Mth.TWO_PI * (rows * s - rise), w1 = Mth.TWO_PI * (rows * 1.67f * s - rise * 1.3f), w2 = Mth.TWO_PI * (rows * 2.6f * s - rise * 1.7f);
            for (int j = 0; j < this.segs; j++) {
                float a = j * Mth.TWO_PI / this.segs;
                float l = k0 * Mth.sin(c0 * a + seed + 0.3f * t) * Mth.sin(w0 + seed * 1.7f)
                        + k1 * Mth.sin(c1 * a + seed * 2.1f - 0.4f * t) * Mth.sin(w1 + seed * 0.6f)
                        + k2 * Mth.sin(c2 * a + seed * 3.3f + 0.5f * t) * Mth.sin(w2 + seed * 2.9f);
                // round puffs: a softened |l| keeps the dip between two puffs rounded (a plain |l| makes a sharp V
                // there, which zigzags into jagged edges when the puffs are big)
                if (d.lobeBillow > 0) l = Mth.lerp(d.lobeBillow, l, 2.2f * (Mth.sqrt(l * l + 0.04f) - 0.2f) - 0.45f);
                // soft-limited, so even a wild charge never pushes a bulge far enough to fold the surface over itself
                float bulge = lobes * env * l;
                float r = base * (1 + bulge / (1 + Math.abs(bulge) * 2.5f));
                int k = i * MAX_SEGS + j;
                // a comet: sideways motion pulls the back of the shell out behind while the front stays round the
                // fighter; up or down motion streams the top (or the bottom) the other way
                float back = Math.max(0, cos[j] * hx + sin[j] * hz);
                float w = Mth.lerp(across, drag, 0.12f * drag + 1.05f * back * back * (0.35f + 0.65f * env));
                x[k] = cos[j] * r + leanX * lean + tx * w;
                y[k] = yy + ty * w;
                z[k] = sin[j] * r + leanZ * lean + tz * w;
            }
        }
        for (int i = 0; i <= this.rings; i++) {                                      // each ring's middle
            float sx = 0, sy = 0, sz = 0;
            for (int j = 0; j < this.segs; j++) {
                int k = i * MAX_SEGS + j;
                sx += x[k];
                sy += y[k];
                sz += z[k];
            }
            rcx[i] = sx / this.segs;
            rcy[i] = sy / this.segs;
            rcz[i] = sz / this.segs;
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
     * A point on the built shell (relative to the feet, unscaled) at height share {@code s} and angle {@code a}, pulled
     * {@code inset} of the way in towards that height's middle; and how far the surface lies from the middle there.
     * Things placed with it stay inside the shell whatever its bulges, sway or trail. Writes x, y, z, radius to {@code out}.
     */
    void sample(float s, float a, float inset, float[] out) {
        float fi = Mth.clamp(s, 0f, 1f) * rings;
        int i0 = Math.min(rings - 1, (int) fi);
        float ti = fi - i0;
        float aa = a / Mth.TWO_PI;
        aa -= Mth.floor(aa);
        float fj = aa * segs;
        int j0 = (int) fj % segs, j1 = (j0 + 1) % segs;
        float tj = fj - (int) fj;
        float px = 0, py = 0, pz = 0, cx = 0, cy = 0, cz = 0;
        for (int di = 0; di <= 1; di++) {
            int ri = i0 + di;
            float wi = di == 0 ? 1 - ti : ti;
            int k0 = ri * MAX_SEGS + j0, k1 = ri * MAX_SEGS + j1;
            px += wi * Mth.lerp(tj, x[k0], x[k1]);
            py += wi * Mth.lerp(tj, y[k0], y[k1]);
            pz += wi * Mth.lerp(tj, z[k0], z[k1]);
            cx += wi * rcx[ri];
            cy += wi * rcy[ri];
            cz += wi * rcz[ri];
        }
        float dx = px - cx, dz = pz - cz;
        out[0] = cx + dx * (1 - inset);
        out[1] = py;
        out[2] = cz + dz * (1 - inset);
        out[3] = Mth.sqrt(dx * dx + dz * dz);
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
        float px = ox + x[k] * scale, py = oy + centerY + (y[k] - centerY) * scale, pz = oz + z[k] * scale;
        float qx = nx[k], qy = ny[k], qz = nz[k];
        if (tiltSin != 0) {
            tilt(px - pivotX, py - pivotY, pz - pivotZ);
            px = pivotX + T[0];
            py = pivotY + T[1];
            pz = pivotZ + T[2];
            tilt(qx, qy, qz);
            qx = T[0];
            qy = T[1];
            qz = T[2];
        }
        vc.vertex(px, py, pz)
                .color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(qx, qy, qz).endVertex();
    }

    // The lean towards the eye (CX-26): looked down on, a drawn aura still shows its flame outline, so the shell is
    // turned about a level axis through its middle, its top away from the eye, by part of the angle the eye looks down
    // at it. Seen from above it then keeps its flames instead of flattening into a ring round the feet. Set per aura
    // before it is written; tiltSin 0 leaves it upright.
    private static float pivotX, pivotY, pivotZ, dirX, dirZ, tiltSin, tiltCos = 1;
    private static final float[] T = new float[3];

    /** Leans the shells written next: pivot camera-relative, (dx, dz) the level direction from the eye, angle in radians. */
    static void setTilt(float px, float py, float pz, float dx, float dz, float angle) {
        pivotX = px;
        pivotY = py;
        pivotZ = pz;
        dirX = dx;
        dirZ = dz;
        tiltSin = Mth.sin(angle);
        tiltCos = Mth.cos(angle);
    }

    /** Turns (x, y, z) in the plane of up and the eye's direction: up goes towards the direction, away from the eye. */
    private static void tilt(float x, float y, float z) {
        float d = x * dirX + z * dirZ;
        float u = y * tiltCos - d * tiltSin, d2 = y * tiltSin + d * tiltCos;
        T[0] = x + dirX * (d2 - d);
        T[1] = u;
        T[2] = z + dirZ * (d2 - d);
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
        float qx = nx[k], qy = ny[k], qz = nz[k];
        if (tiltSin != 0) {
            tilt(px - pivotX, py - pivotY, pz - pivotZ);
            px = pivotX + T[0];
            py = pivotY + T[1];
            pz = pivotZ + T[2];
            tilt(qx, qy, qz);
            qx = T[0];
            qy = T[1];
            qz = T[2];
        }
        float len = Mth.sqrt(px * px + py * py + pz * pz);
        float facing = len < 1e-4f ? 1 : Math.abs(qx * px + qy * py + qz * pz) / len;
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

    /** 1 for a wave of {@code cycles} the {@code steps} vertices carry smoothly, fading to 0 when it gets too fine. */
    private static float fine(float cycles, int steps) {
        float most = steps / 6f;
        return Mth.clamp(2f - cycles / most, 0f, 1f);
    }

    static float smooth(float e0, float e1, float v) {
        float t = Mth.clamp((v - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
