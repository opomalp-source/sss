package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.network.ImpactPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Bursts and waves of ki (the ki attack rework, phase 5), drawn through the energy shader:
 * <ul>
 *   <li><b>A burst all round</b> (Explosive Wave, Perfect Barrier): a dome of energy swelling out to the wave's reach and
 *       thinning as it goes, a ring racing out across the ground ahead of it, earth thrown outward along it, sparks,
 *       a flash and a shake for those inside.</li>
 *   <li><b>A paralysing discharge</b> (Paralysis Wave): a fan of lightning thrown forward, re-struck every tick.</li>
 *   <li><b>A blinding flash</b> (Solar Flare): a burst of white light with rays; anyone facing it is whited out.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KiWaveFx {
    private record Wave(int kind, Vec3 pos, Vec3 dir, float reach, int color, int born, int life, int seed) {}

    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Vector3f LEFT = new Vector3f(), UP = new Vector3f();
    private static int ticks;
    private static final int DOME = 100;

    private KiWaveFx() {}

    /** An explosion's dome of energy (phase 6), swelling to {@code reach} and thinning over {@code life} ticks, with its ring across the ground. */
    public static void dome(Vec3 pos, float reach, int color, int life) {
        WAVES.add(new Wave(DOME, pos, new Vec3(0, 1, 0), Math.max(1, reach), color, ticks, life, 0));
    }

    /** One arrives from the server (through {@link ImpactFx#onImpact}). */
    public static void start(ImpactPacket m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 pos = new Vec3(m.x(), m.y(), m.z()), dir = new Vec3(m.dx(), m.dy(), m.dz());
        float reach = Math.max(1, m.scale());
        RandomSource rnd = level.random;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        switch (m.kind()) {
            case ImpactPacket.WAVE -> {
                WAVES.add(new Wave(m.kind(), pos, dir, reach, m.color(), ticks, 12 + (int) reach, rnd.nextInt()));
                KiParticles.burst(pos, m.color(), (int) (30 + 6 * reach), 0.25f + 0.05f * reach, 0.8f);
                CameraFx.shakeAt(pos, 0.3f + 0.04f * reach, reach * 3);
                if (eye.distanceTo(pos) < reach + 1) {                                 // inside it: the light and the jolt
                    CameraFx.flash(FxDraw.mix(m.color(), 0xFFFFFF, 0.6f), 0.3f);
                    CameraFx.kick(0.6f);
                }
                Vec3 ground = BigSphere.groundBelow(level, pos, 3);
                if (ground != null) {
                    int n = KiParticles.scaled((int) (20 + 6 * reach));
                    for (int i = 0; i < n; i++) {                                       // earth thrown outward along the ground
                        double a = rnd.nextDouble() * Math.PI * 2, d = 0.5 + rnd.nextDouble() * reach * 0.6;
                        double px = ground.x + Math.cos(a) * d, pz = ground.z + Math.sin(a) * d;
                        BlockState s = level.getBlockState(BlockPos.containing(px, ground.y - 0.5, pz));
                        if (s.isAir()) continue;
                        double v = 0.25 + rnd.nextDouble() * 0.35;
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, s), px, ground.y + 0.1, pz, Math.cos(a) * v, 0.15 + rnd.nextDouble() * 0.25, Math.sin(a) * v);
                    }
                    ImpactFx.cracks(ground.add(0, 0.02, 0), Math.min(5, reach * 0.6f));
                }
            }
            case ImpactPacket.STUN_WAVE -> {
                WAVES.add(new Wave(m.kind(), pos, dir.normalize(), reach, m.color(), ticks, 10, rnd.nextInt()));
                KiParticles.burst(pos.add(dir.scale(0.6)), m.color(), 20, 0.3f, 0.5f);
            }
            case ImpactPacket.FLARE -> {
                WAVES.add(new Wave(m.kind(), pos, dir.normalize(), reach, m.color(), ticks, 16, rnd.nextInt()));
                Vec3 to = pos.subtract(eye);
                double d = to.length();
                if (d < 24) {                                                           // facing it: the world goes white
                    Vec3 look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
                    double facing = d < 1.5 ? 1 : look.dot(to.normalize());
                    if (facing > 0.2) CameraFx.flash(0xFFFFFF, (float) Mth.clamp(facing * (1 - d / 24) * 1.2, 0.2, 0.95));
                }
            }
            default -> { }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            WAVES.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        WAVES.removeIf(w -> ticks - w.born > w.life);
        RandomSource rnd = mc.level.random;
        for (Wave w : WAVES) {
            if (w.kind != ImpactPacket.STUN_WAVE) continue;
            for (int i = 0; i < 2; i++) {                                               // sparks crackling along the fan
                Vec3 at = w.pos.add(cone(w.dir, 0.6, rnd).scale(1 + rnd.nextDouble() * w.reach));
                KiParticles.add(KiParticles.SPARK, at.x, at.y, at.z, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05,
                        0.4f, 0xFFFFFF, 4 + rnd.nextInt(4), 0.8f, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || WAVES.isEmpty() || !KiEnergy.available()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        LEFT.set(event.getCamera().getLeftVector());
        UP.set(event.getCamera().getUpVector());
        float pt = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f m = event.getPoseStack().last().pose();
        Matrix3f n = event.getPoseStack().last().normal();
        for (Wave w : WAVES) {
            float age = ticks - w.born + pt, k = Mth.clamp(age / w.life, 0, 1);
            float x = (float) (w.pos.x - cam.x), y = (float) (w.pos.y - cam.y), z = (float) (w.pos.z - cam.z);
            switch (w.kind) {
                case ImpactPacket.WAVE, DOME -> wave(buffers, m, n, x, y, z, w, age, k, cam);
                case ImpactPacket.STUN_WAVE -> stun(buffers, m, n, x, y, z, w, age, k);
                case ImpactPacket.FLARE -> flare(buffers, m, n, x, y, z, w, k);
                default -> { }
            }
        }
        buffers.endBatch();
    }

    /** The dome swelling out and thinning, its tinted body, and the ring racing out across the ground ahead of it. */
    private static void wave(MultiBufferSource.BufferSource buffers, Matrix4f m, Matrix3f n, float x, float y, float z, Wave w, float age, float k, Vec3 cam) {
        float grow = 1 - (1 - k) * (1 - k) * (1 - k);                                  // fast, then easing out
        float r = 0.6f + (w.reach - 0.6f) * grow, fade = 1 - k;
        int hot = FxDraw.mix(w.color, 0xFFFFFF, 0.5f), deep = BigSphere.vivid(w.color);
        boolean inside = x * x + y * y + z * z < r * r;                               // seen from inside, its far wall (both sides drawn)
        if (!inside) {
            KiEnergy.sphere(buffers.getBuffer(KiEnergy.tint()), m, n, x, y, z, r * 0.98f, KiEnergy.argb(deep, (int) (110 * fade * fade)), KiEnergy.TINT, 2, 0);
            buffers.endBatch(KiEnergy.tint());
        }
        var bodyType = inside ? KiEnergy.flat() : KiEnergy.solid();
        VertexConsumer solid = buffers.getBuffer(bodyType);
        KiEnergy.sphere(solid, m, n, x, y, z, r, KiEnergy.argb(deep, (int) (230 * fade)), KiEnergy.SHELL, 2, age * 0.02f);
        KiEnergy.sphere(solid, m, n, x, y, z, r * 1.02f, KiEnergy.argb(hot, (int) (200 * fade)), KiEnergy.STRANDS, 2, -age * 0.04f);
        if (k < 0.35f) KiEnergy.sphere(solid, m, n, x, y, z, r * 0.5f, KiEnergy.argb(0xFFFFFF, (int) (255 * (1 - k / 0.35f))), KiEnergy.CORE, 1, 0);
        buffers.endBatch(bodyType);
        Vec3 ground = Minecraft.getInstance().level == null ? null : BigSphere.groundBelow(Minecraft.getInstance().level, w.pos, 3);
        if (ground == null) return;
        VertexConsumer flat = buffers.getBuffer(KiEnergy.flat());
        float gy = (float) (ground.y - cam.y) + 0.08f, ring = 0.8f + w.reach * 1.35f * (1 - (1 - k) * (1 - k));
        float ex = 0, ey = 0, ez = 0;                                                   // the eye: this space is the camera's
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
            KiEnergy.ribbon(flat, m, n, x + Mth.cos(a0) * ring, gy, z + Mth.sin(a0) * ring, x + Mth.cos(a1) * ring, gy, z + Mth.sin(a1) * ring,
                    ex, ey, ez, 0.35f + 0.25f * w.reach * 0.2f, 0.35f + 0.25f * w.reach * 0.2f, hot, (int) (220 * fade), (int) (220 * fade), 0, 1, KiEnergy.RIBBON);
        }
        buffers.endBatch(KiEnergy.flat());
    }

    /** A fan of forked lightning thrown forward, re-struck every tick, fading. */
    private static void stun(MultiBufferSource.BufferSource buffers, Matrix4f m, Matrix3f n, float x, float y, float z, Wave w, float age, float k) {
        int fade = (int) (240 * (1 - k)), hot = FxDraw.mix(w.color, 0xFFFFFF, 0.5f), deep = BigSphere.vivid(w.color);
        float reach = w.reach * Math.min(1, 0.4f + age / 3f);
        for (int pass = 0; pass < 2; pass++) {                                         // a tinted underlay (its colour against the sky), then the glow
            var type = pass == 0 ? KiEnergy.tint() : KiEnergy.flat();
            VertexConsumer vc = buffers.getBuffer(type);
            RandomSource rnd = RandomSource.create(w.seed + (long) age);               // the same bolts in both passes
            for (int b = 0; b < 7; b++) {
                Vec3 d = cone(w.dir, 0.65, rnd);
                float px = x, py = y, pz = z;
                int steps = 6;
                for (int s = 1; s <= steps; s++) {
                    Vec3 p = d.scale(reach * s / steps);
                    float qx = x + (float) p.x + (float) rnd.nextGaussian() * 0.25f, qy = y + (float) p.y + (float) rnd.nextGaussian() * 0.25f,
                            qz = z + (float) p.z + (float) rnd.nextGaussian() * 0.25f;
                    float wd = 0.2f * (1 - s / (float) (steps + 1));
                    if (pass == 0) KiEnergy.ribbon(vc, m, n, px, py, pz, qx, qy, qz, 0, 0, 0, wd * 2.2f, wd * 1.8f, deep, fade * 3 / 4, fade * 3 / 4, 0, 1, KiEnergy.RIBBON);
                    else {
                        KiEnergy.ribbon(vc, m, n, px, py, pz, qx, qy, qz, 0, 0, 0, wd * 2.5f, wd * 2f, w.color, fade / 2, fade / 2, 0, 1, KiEnergy.RIBBON);
                        KiEnergy.ribbon(vc, m, n, px, py, pz, qx, qy, qz, 0, 0, 0, wd, wd * 0.8f, hot, fade, fade, 0, 1, KiEnergy.RIBBON);
                    }
                    if (rnd.nextFloat() < 0.3f) {                                     // a fork
                        Vec3 f = cone(d, 0.9, rnd).scale(0.6 + rnd.nextDouble());
                        if (pass == 1) KiEnergy.ribbon(vc, m, n, qx, qy, qz, qx + (float) f.x, qy + (float) f.y, qz + (float) f.z, 0, 0, 0, wd * 0.8f, 0.01f,
                                w.color, fade, 0, 0, 1, KiEnergy.RIBBON);
                    }
                    px = qx; py = qy; pz = qz;
                }
            }
            if (pass == 1) KiEnergy.billboard(vc, m, n, x, y, z, LEFT, UP, 1.6f * (1 - k), KiEnergy.argb(w.color, fade), KiEnergy.HALO);
            buffers.endBatch(type);
        }
    }

    /** A burst of white light at the face, rays shooting out from it, all fading fast. */
    private static void flare(MultiBufferSource.BufferSource buffers, Matrix4f m, Matrix3f n, float x, float y, float z, Wave w, float k) {
        VertexConsumer flat = buffers.getBuffer(KiEnergy.flat());
        float f = (1 - k) * (1 - k);
        KiEnergy.billboard(flat, m, n, x, y, z, LEFT, UP, 2 + 10 * (float) Math.sqrt(k), KiEnergy.argb(0xFFFFFF, (int) (255 * f)), KiEnergy.HALO);
        KiEnergy.billboard(flat, m, n, x, y, z, LEFT, UP, 1.2f + 4 * k, KiEnergy.argb(w.color, (int) (255 * f)), KiEnergy.HALO);
        RandomSource rnd = RandomSource.create(w.seed);
        for (int i = 0; i < 14; i++) {                                                 // rays
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
            float len = (2 + rnd.nextFloat() * 4) * (0.4f + k);
            KiEnergy.ribbon(flat, m, n, x, y, z, x + (float) d.x * len, y + (float) d.y * len, z + (float) d.z * len, 0, 0, 0, 0.18f, 0.02f, 0xFFFFFF,
                    (int) (230 * f), 0, 0, 1, KiEnergy.RIBBON);
        }
        buffers.endBatch(KiEnergy.flat());
    }

    /** A random direction within {@code spread} (radians, roughly) of {@code dir}. */
    private static Vec3 cone(Vec3 dir, double spread, RandomSource rnd) {
        return dir.add(rnd.nextGaussian() * spread * 0.5, rnd.nextGaussian() * spread * 0.35, rnd.nextGaussian() * spread * 0.5).normalize();
    }
}
