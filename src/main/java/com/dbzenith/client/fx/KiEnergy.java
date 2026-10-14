package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;

/**
 * Ki energy drawing (the ki attack rework): the {@code ki_energy} shader (glowing, additive, never lit by the world,
 * drawn into the bloom), its render types, and smooth meshes to draw with it: spheres in layers (a shell bright at its
 * rim with plasma boiling over it, a white-hot core, crackling strands, a soft halo) and soft ribbons for trails and
 * beams. With a shader pack on, or if the shader didn't load, {@link #available} is false and callers draw their plain
 * fallback.
 */
public final class KiEnergy {
    public static final int SHELL = 0, CORE = 1, STRANDS = 2, HALO = 3, RIBBON = 4, TINT = 5;
    private static final ResourceLocation NOISE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_noise.png");
    static ShaderInstance shader, tintShader;

    private KiEnergy() {}

    @Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class EnergyShader {          // not "Shaders": the event bus mixes up subscribers that share a simple class and method name (KiBloom.Shaders)
        @SubscribeEvent
        public static void registerEnergy(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "ki_energy"),
                    DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "ki_energy_tint"),
                    DefaultVertexFormat.NEW_ENTITY), s -> tintShader = s);
        }
    }

    /** Whether the energy shader can be used now (else draw the plain way). */
    public static boolean available() {
        return shader != null && tintShader != null && !com.dbzenith.client.aura.AuraShaders.packOn();
    }

    /** Solid shapes, drawn front faces only (spheres). */
    public static RenderType solid() {
        return Types.SOLID;
    }

    /** Flat things seen from both sides (halos, ribbons). */
    public static RenderType flat() {
        return Types.FLAT;
    }

    /**
     * A see-through body in the attack's own colour, blended normally (not added) and kept out of the bloom: drawn
     * first, under the glow, it tints what lies behind, so the energy keeps its colour against a bright sky instead of
     * washing out to white.
     */
    public static RenderType tint() {
        return Types.TINT;
    }

    private static final class Types extends RenderType {
        private static final ShaderStateShard SHADER = new ShaderStateShard(() -> shader);
        private static final ShaderStateShard TINT_SHADER = new ShaderStateShard(() -> tintShader);   // the same, blended normally
        private static final OutputStateShard GLOW = new OutputStateShard("dbzenith_ki_glow", KiBloom::begin, KiBloom::end);
        static final RenderType SOLID = create("dbzenith_ki_energy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder().setShaderState(SHADER).setTextureState(new TextureStateShard(NOISE, true, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(CULL).setWriteMaskState(COLOR_WRITE).setOutputState(GLOW)
                        .createCompositeState(false));
        static final RenderType FLAT = create("dbzenith_ki_energy_flat", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder().setShaderState(SHADER).setTextureState(new TextureStateShard(NOISE, true, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setOutputState(GLOW)
                        .createCompositeState(false));
        static final RenderType TINT = create("dbzenith_ki_energy_tint", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder().setShaderState(TINT_SHADER).setTextureState(new TextureStateShard(NOISE, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL).setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    // ------------------------------------------------------------------ meshes

    /** A unit sphere at a few levels of detail: per vertex x, y, z (also the normal), u, v; quads, four vertices each. */
    private static final float[][] SPHERES = {sphere(8, 12), sphere(12, 20), sphere(18, 30)};

    private static float[] sphere(int lat, int lon) {
        float[] out = new float[lat * lon * 4 * 5];
        int k = 0;
        for (int i = 0; i < lat; i++) for (int j = 0; j < lon; j++) {
            int[][] corners = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
            for (int[] c : corners) {
                double th = Math.PI * c[0] / lat, ph = Math.PI * 2 * c[1] / lon;
                out[k++] = (float) (Math.sin(th) * Math.cos(ph));
                out[k++] = (float) Math.cos(th);
                out[k++] = (float) (Math.sin(th) * Math.sin(ph));
                out[k++] = c[1] / (float) lon;
                out[k++] = c[0] / (float) lat;
            }
        }
        return out;
    }

    /**
     * A sphere of radius {@code r} at (x, y, z) in the pose's space, in one mode, 0xAARRGGBB colour; {@code detail} 0..2;
     * {@code spin} turns its texture round it (the plasma drifts).
     */
    public static void sphere(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, int argb, int mode, int detail, float spin) {
        float[] s = SPHERES[Math.max(0, Math.min(2, detail))];
        int a = argb >>> 24, red = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        for (int i = 0; i < s.length; i += 5) {
            float px = s[i], py = s[i + 1], pz = s[i + 2];
            vc.vertex(m, x + px * r, y + py * r, z + pz * r).color(red, g, b, a).uv(s[i + 3] + spin, s[i + 4])
                    .overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, px, py, pz).endVertex();
        }
    }

    /** The same turned by {@code rot} round its centre (the swirling layers of a big sphere, each on its own axis). */
    public static void sphere(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, int argb, int mode, int detail, float spin, Matrix3f rot) {
        float[] s = SPHERES[Math.max(0, Math.min(2, detail))];
        int a = argb >>> 24, red = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        Vector3f p = new Vector3f();
        for (int i = 0; i < s.length; i += 5) {
            rot.transform(p.set(s[i], s[i + 1], s[i + 2]));
            vc.vertex(m, x + p.x * r, y + p.y * r, z + p.z * r).color(red, g, b, a).uv(s[i + 3] + spin, s[i + 4])
                    .overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, p.x, p.y, p.z).endVertex();
        }
    }

    /**
     * A tube along {@code dir} (a unit vector) from (sx, sy, sz), {@code length} long, its radius going from {@code r0}
     * to {@code r1}, {@code sides} round and {@code rings} along; texture u round it, v down it from {@code v0} (one per
     * radius-ish, so the plasma keeps its size), {@code wobble} rippling its radius along the length at time {@code t}.
     */
    public static void tube(VertexConsumer vc, Matrix4f m, Matrix3f n, float sx, float sy, float sz, float dx, float dy, float dz, float length,
                            float r0, float r1, int argb, int mode, int sides, int rings, float v0, float wobble, float t) {
        float ax, ay, az;                                                     // a, b: round the tube
        if (Math.abs(dy) > 0.9f) { ax = 1; ay = 0; az = 0; } else { ax = 0; ay = 1; az = 0; }
        float px = dy * az - dz * ay, py = dz * ax - dx * az, pz = dx * ay - dy * ax;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        px /= pl; py /= pl; pz /= pl;
        float qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
        int a = argb >>> 24, red = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        for (int i = 0; i < rings; i++) {
            float k0 = i / (float) rings, k1 = (i + 1) / (float) rings;
            float rad0 = (r0 + (r1 - r0) * k0) * (1 + wobble * (float) Math.sin(k0 * 17 - t * 0.9f)),
                    rad1 = (r0 + (r1 - r0) * k1) * (1 + wobble * (float) Math.sin(k1 * 17 - t * 0.9f));
            float c0x = sx + dx * length * k0, c0y = sy + dy * length * k0, c0z = sz + dz * length * k0;
            float c1x = sx + dx * length * k1, c1y = sy + dy * length * k1, c1z = sz + dz * length * k1;
            float vv0 = v0 + length * k0 / Math.max(0.3f, r1 * 12), vv1 = v0 + length * k1 / Math.max(0.3f, r1 * 12);   // stretched along it: long flowing streaks
            for (int j = 0; j < sides; j++) {
                double th0 = Math.PI * 2 * j / sides, th1 = Math.PI * 2 * (j + 1) / sides;
                float n0x = (float) (px * Math.cos(th0) + qx * Math.sin(th0)), n0y = (float) (py * Math.cos(th0) + qy * Math.sin(th0)), n0z = (float) (pz * Math.cos(th0) + qz * Math.sin(th0));
                float n1x = (float) (px * Math.cos(th1) + qx * Math.sin(th1)), n1y = (float) (py * Math.cos(th1) + qy * Math.sin(th1)), n1z = (float) (pz * Math.cos(th1) + qz * Math.sin(th1));
                float u0 = j / (float) sides, u1 = (j + 1) / (float) sides;
                vc.vertex(m, c0x + n0x * rad0, c0y + n0y * rad0, c0z + n0z * rad0).color(red, g, b, a).uv(u0, vv0).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, n0x, n0y, n0z).endVertex();
                vc.vertex(m, c0x + n1x * rad0, c0y + n1y * rad0, c0z + n1z * rad0).color(red, g, b, a).uv(u1, vv0).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, n1x, n1y, n1z).endVertex();
                vc.vertex(m, c1x + n1x * rad1, c1y + n1y * rad1, c1z + n1z * rad1).color(red, g, b, a).uv(u1, vv1).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, n1x, n1y, n1z).endVertex();
                vc.vertex(m, c1x + n0x * rad1, c1y + n0y * rad1, c1z + n0z * rad1).color(red, g, b, a).uv(u0, vv1).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, n0x, n0y, n0z).endVertex();
            }
        }
    }

    /** A square facing the camera (left/up are the camera's axes, already scaled to half the size). */
    public static void billboard(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, Vector3f left, Vector3f up, float half, int argb, int mode) {
        int a = argb >>> 24, red = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        float lx = left.x() * half, ly = left.y() * half, lz = left.z() * half, ux = up.x() * half, uy = up.y() * half, uz = up.z() * half;
        vc.vertex(m, x - lx - ux, y - ly - uy, z - lz - uz).color(red, g, b, a).uv(0, 1).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, x + lx - ux, y + ly - uy, z + lz - uz).color(red, g, b, a).uv(1, 1).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, x + lx + ux, y + ly + uy, z + lz + uz).color(red, g, b, a).uv(1, 0).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, x - lx + ux, y - ly + uy, z - lz + uz).color(red, g, b, a).uv(0, 0).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
    }

    /**
     * One piece of a ribbon from a to b (positions in the pose's space), turned to the eye, {@code wa}/{@code wb} wide at
     * each end, alpha {@code aa}/{@code ab}; {@code va}/{@code vb} run down its length (the flow).
     */
    public static void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz,
                              float ex, float ey, float ez, float wa, float wb, int rgb, int aa, int ab, float va, float vb, int mode) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = ex - (ax + bx) * 0.5f, my = ey - (ay + by) * 0.5f, mz = ez - (az + bz) * 0.5f;
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;     // across: along x toward the eye
        float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6f) return;
        sx /= len; sy /= len; sz /= len;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float ha = wa * 0.5f, hb = wb * 0.5f;
        vc.vertex(m, ax - sx * ha, ay - sy * ha, az - sz * ha).color(r, g, b, aa).uv(0, va).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, ax + sx * ha, ay + sy * ha, az + sz * ha).color(r, g, b, aa).uv(1, va).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, bx + sx * hb, by + sy * hb, bz + sz * hb).color(r, g, b, ab).uv(1, vb).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, bx - sx * hb, by - sy * hb, bz - sz * hb).color(r, g, b, ab).uv(0, vb).overlayCoords(mode, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    /** 0xAARRGGBB from a colour and an alpha. */
    public static int argb(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb & 0xFFFFFF;
    }
}
