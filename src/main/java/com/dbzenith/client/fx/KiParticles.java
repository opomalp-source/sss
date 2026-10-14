package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The ki attacks' own particles (the ki attack rework): glowing sparks (streaks drawn along their motion), motes
 * (soft glowing dots drifting and shrinking), embers (falling sparks that slow and fade), all additive and drawn through
 * the energy shader into the bloom. One fixed pool of plain arrays, nothing allocated while they live; the oldest make
 * way when it is full. How many there may be follows the ki effects quality ({@code kiQuality}).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KiParticles {
    public static final int SPARK = 0, MOTE = 1, EMBER = 2;
    private static final int MAX = 6000;
    private static final float[] x = new float[MAX], y = new float[MAX], z = new float[MAX], vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private static final float[] size = new float[MAX], drag = new float[MAX], gravity = new float[MAX];
    private static final int[] color = new int[MAX], kind = new int[MAX], age = new int[MAX], life = new int[MAX];
    private static int count, next;
    private static final Vector3f LEFT = new Vector3f(), UP = new Vector3f();

    private KiParticles() {}

    /** Quality 0 low .. 3 ultra. */
    public static int quality() {
        return DBZConfig.CLIENT.kiQuality.get();
    }

    /** How many particles at this quality, out of what full quality would make. */
    public static int scaled(int n) {
        return switch (quality()) {
            case 0 -> (n + 3) / 4;
            case 1 -> (n + 1) / 2;
            case 2 -> n;
            default -> n + n / 2;
        };
    }

    private static int cap() {
        return switch (quality()) {
            case 0 -> 800;
            case 1 -> 2000;
            case 2 -> 4000;
            default -> MAX;
        };
    }

    /** One particle. */
    public static void add(int k, double px, double py, double pz, double mx, double my, double mz, float s, int rgb, int ticks, float drg, float grav) {
        int i;
        if (count < cap()) i = count++;
        else {                                                               // full: the oldest goes
            i = next;
            next = (next + 1) % count;
        }
        kind[i] = k;
        x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz;
        vx[i] = (float) mx; vy[i] = (float) my; vz[i] = (float) mz;
        size[i] = s;
        color[i] = rgb;
        age[i] = 0;
        life[i] = Math.max(1, ticks);
        drag[i] = drg;
        gravity[i] = grav;
    }

    /** Sparks flung out from a point in every direction (an impact). */
    public static void burst(Vec3 at, int rgb, int n, float speed, float s) {
        RandomSource r = Minecraft.getInstance().level == null ? RandomSource.create() : Minecraft.getInstance().level.random;
        n = scaled(n);
        for (int i = 0; i < n; i++) {
            double ux = r.nextGaussian(), uy = r.nextGaussian(), uz = r.nextGaussian();
            double len = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6, v = speed * (0.4 + r.nextDouble() * 0.8);
            int c = r.nextInt(3) == 0 ? 0xFFFFFF : FxDraw.mix(rgb, 0xFFFFFF, r.nextFloat() * 0.5f);
            add(r.nextInt(4) == 0 ? EMBER : SPARK, at.x, at.y, at.z, ux / len * v, uy / len * v + 0.05, uz / len * v, s * (0.6f + r.nextFloat() * 0.8f), c,
                    10 + r.nextInt(14), 0.88f, r.nextInt(4) == 0 ? 0.03f : 0.008f);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            count = 0;
            return;
        }
        if (mc.isPaused()) return;
        for (int i = 0; i < count; i++) {
            if (++age[i] >= life[i]) {                                       // gone: the last one moves into its place
                int last = --count;
                copy(last, i);
                if (next > count) next = 0;
                i--;
                continue;
            }
            x[i] += vx[i]; y[i] += vy[i]; z[i] += vz[i];
            vx[i] *= drag[i]; vy[i] = vy[i] * drag[i] - gravity[i]; vz[i] *= drag[i];
        }
    }

    private static void copy(int from, int to) {
        if (from == to) return;
        x[to] = x[from]; y[to] = y[from]; z[to] = z[from]; vx[to] = vx[from]; vy[to] = vy[from]; vz[to] = vz[from];
        size[to] = size[from]; drag[to] = drag[from]; gravity[to] = gravity[from]; color[to] = color[from];
        kind[to] = kind[from]; age[to] = age[from]; life[to] = life[from];
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || count == 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean shader = KiEnergy.available();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        LEFT.set(camera.getLeftVector());
        UP.set(camera.getUpVector());
        RenderType type = shader ? KiEnergy.flat() : FxRenderTypes.additive(new net.minecraft.resources.ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png"));
        VertexConsumer vc = buffers.getBuffer(type);
        float ex = 0, ey = 0, ez = 0;
        for (int i = 0; i < count; i++) {
            float k = (age[i] + pt) / life[i];
            float px = x[i] + vx[i] * pt - (float) cam.x, py = y[i] + vy[i] * pt - (float) cam.y, pz = z[i] + vz[i] * pt - (float) cam.z;
            float fade = 1 - k * k;
            int alpha = (int) (255 * fade);
            if (alpha <= 2) continue;
            if (kind[i] == SPARK || kind[i] == EMBER) {                       // a streak along its motion
                float sp = Mth.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
                float tail = Math.min(1.2f, sp * 2.5f + 0.05f);
                float tx = sp > 1e-4f ? vx[i] / sp * tail : 0, ty = sp > 1e-4f ? vy[i] / sp * tail : 0, tz = sp > 1e-4f ? vz[i] / sp * tail : 0;
                float w = size[i] * 0.12f * (1 - k * 0.5f);
                if (shader) KiEnergy.ribbon(vc, m, n, px - tx, py - ty, pz - tz, px, py, pz, ex, ey, ez, w * 0.3f, w, color[i], 0, alpha, 0, 1, KiEnergy.RIBBON);
                else FxDraw.ribbon(pose, vc, new Vec3(px - tx, py - ty, pz - tz), new Vec3(px, py, pz), Vec3.ZERO, w, color[i], alpha);
            } else {                                                          // a mote: a soft dot, shrinking
                float s = size[i] * (1 - k * 0.6f);
                if (shader) KiEnergy.billboard(vc, m, n, px, py, pz, LEFT, UP, s * 0.5f, KiEnergy.argb(color[i], alpha), KiEnergy.HALO);
            }
        }
        buffers.endBatch(type);
        pose.popPose();
    }
}
