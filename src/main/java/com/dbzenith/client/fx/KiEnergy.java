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
    public static final int SHELL = 0, CORE = 1, STRANDS = 2, HALO = 3, RIBBON = 4;
    private static final ResourceLocation NOISE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_noise.png");
    static ShaderInstance shader;

    private KiEnergy() {}

    @Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "ki_energy"),
                    DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
        }
    }

    /** Whether the energy shader can be used now (else draw the plain way). */
    public static boolean available() {
        return shader != null && !com.dbzenith.client.aura.AuraShaders.packOn();
    }

    /** Solid shapes, drawn front faces only (spheres). */
    public static RenderType solid() {
        return Types.SOLID;
    }

    /** Flat things seen from both sides (halos, ribbons). */
    public static RenderType flat() {
        return Types.FLAT;
    }

    private static final class Types extends RenderType {
        private static final ShaderStateShard SHADER = new ShaderStateShard(() -> shader);
        private static final OutputStateShard GLOW = new OutputStateShard("dbzenith_ki_glow", KiBloom::begin, KiBloom::end);
        static final RenderType SOLID = create("dbzenith_ki_energy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder().setShaderState(SHADER).setTextureState(new TextureStateShard(NOISE, true, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(CULL).setWriteMaskState(COLOR_WRITE).setOutputState(GLOW)
                        .createCompositeState(false));
        static final RenderType FLAT = create("dbzenith_ki_energy_flat", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder().setShaderState(SHADER).setTextureState(new TextureStateShard(NOISE, true, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setOutputState(GLOW)
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
