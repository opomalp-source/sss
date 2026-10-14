package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.KiBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Water parting (CX-48). A fast flier skimming low over water, or a beam passing over it, pushes the water apart: a
 * dark trench where it is pushed down, two lines of foam spreading out behind in a V, and sheets of spray thrown up to
 * both sides that fall back. Each wake point lives a couple of seconds on the surface.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterFx {
    private static final ResourceLocation FOAM = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/foam.png");
    private static final ResourceLocation PUFF = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/nimbus_puff.png");
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final int LIFE = 46, MAX = 500;

    private record Wake(Vec3 at, Vec3 dir, int born, float strength, float len) {}

    private static final List<Wake> WAKES = new ArrayList<>();
    private static int ticks;

    private WaterFx() {}

    /** The water surface within {@code depth} blocks under {@code from}, or null when there is none. */
    public static Vec3 surfaceBelow(Level level, Vec3 from, int depth) {
        BlockPos.MutableBlockPos p = BlockPos.containing(from).mutable();
        for (int i = 0; i <= depth; i++, p.move(0, -1, 0)) {
            FluidState f = level.getFluidState(p);
            if (!f.isEmpty()) {
                while (!level.getFluidState(p.above()).isEmpty()) p.move(0, 1, 0);
                return new Vec3(from.x, p.getY() + level.getFluidState(p).getHeight(level, p) + 0.01, from.z);
            }
            if (!level.getBlockState(p).isAir()) return null;                    // ground first: no water to part
        }
        return null;
    }

    /** Something rushes over the water at {@code surface}, heading {@code dir}, {@code strength} 0..1. */
    public static void part(Vec3 surface, Vec3 dir, float strength) {
        part(surface, dir, strength, (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z));
    }

    /** As above, wake points {@code spacing} apart (each piece is drawn long enough to overlap the next). */
    public static void part(Vec3 surface, Vec3 dir, float strength, float spacing) {
        Vec3 d = new Vec3(dir.x, 0, dir.z);
        if (d.lengthSqr() < 1e-6) return;
        if (WAKES.size() >= MAX) WAKES.remove(0);
        WAKES.add(new Wake(surface, d.normalize(), ticks, Mth.clamp(strength, 0.15f, 1f), Math.max(1.2f, spacing * 1.35f)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            WAKES.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        WAKES.removeIf(w -> ticks - w.born > LIFE);
        // beams skimming the water drive a trench and spray along their length
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof KiBeamEntity beam) || ((ticks + e.getId()) & 1) != 0) continue;
            float length = beam.getLength();
            if (length < 1) continue;
            Vec3 dir = beam.direction();
            for (float s = 1; s < length; s += 1.6f) {
                Vec3 at = beam.position().add(dir.scale(s));
                Vec3 surf = surfaceBelow(mc.level, at, 3);
                if (surf == null) continue;
                float close = 1 - (float) (at.y - surf.y) / 4f;
                part(surf, dir, Math.min(1f, 0.4f + beam.getWidth() * 0.4f) * close, 1.6f);
                if (mc.level.random.nextInt(3) == 0)
                    mc.level.addParticle(ParticleTypes.SPLASH, surf.x, surf.y, surf.z, (mc.level.random.nextDouble() - 0.5) * 0.3, 0.3, (mc.level.random.nextDouble() - 0.5) * 0.3);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || WAKES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        // the trench: darker water where it is pushed down, closing up again
        RenderType trench = RenderType.entityTranslucent(GLOW);
        VertexConsumer vc = buffers.getBuffer(trench);
        for (Wake w : WAKES) {
            float age = (ticks - w.born + partial) / LIFE;
            if (age > 0.6f) continue;
            float width = (0.45f + 0.5f * w.strength) * (1 - age / 0.6f);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(w.at));
            flat(vc, m, n, w.at.add(0, 0.012, 0), w.dir, 0, width, w.len, 0x0A2030, (int) (90 * (1 - age / 0.6f)), light, -2, 0.5f);
        }
        buffers.endBatch(trench);

        // the foam: two lines spreading out behind in a V, fading
        RenderType foam = RenderType.entityTranslucent(FOAM);
        vc = buffers.getBuffer(foam);
        for (Wake w : WAKES) {
            float age = (ticks - w.born + partial) / LIFE;
            float spread = 0.5f + age * 2.6f * (0.5f + w.strength);
            int alpha = (int) (210 * Math.pow(1 - age, 1.3));
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(w.at));
            for (int side = -1; side <= 1; side += 2)
                flat(vc, m, n, w.at.add(0, 0.016, 0), w.dir, side * spread, 0.3f + age * 0.5f, w.len, 0xFFFFFF, alpha, light, -1, -1);
        }
        buffers.endBatch(foam);

        // the spray: puffs thrown up to both sides of a fresh wake, rising and falling back
        RenderType spray = RenderType.entityTranslucent(PUFF);
        vc = buffers.getBuffer(spray);
        org.joml.Quaternionf camRot = event.getCamera().rotation();
        for (Wake w : WAKES) {
            float age = (ticks - w.born + partial) / 16f;
            if (age > 1) continue;
            Vec3 across = new Vec3(-w.dir.z, 0, w.dir.x);
            for (int k = 0; k < 4; k++) {
                long h = (long) w.born * 31 + k * 7919L + (long) (w.at.x * 13) + (long) (w.at.z * 17);
                float r1 = (float) ((h * 2654435761L >>> 8) & 1023) / 1023f, r2 = (float) ((h * 40503L >>> 4) & 1023) / 1023f;
                int side = (k & 1) == 0 ? -1 : 1;
                float rise = (1.0f + r1 * 0.9f) * w.strength * Mth.sin(age * Mth.PI);
                Vec3 at = w.at.add(across.scale(side * (0.5f + age * (1.2f + r2) * w.strength))).add(w.dir.scale((r2 - 0.5f) * w.len)).add(0, rise, 0);
                float size = (0.45f + r1 * 0.5f) * (0.6f + w.strength) * (0.7f + age * 0.6f);
                FxDraw.billboard(pose, vc, camRot, at.x, at.y, at.z, size, r2 * 6, 0, 0, 1, 1, 0xF2F8FF, (int) (150 * (1 - age) * (1 - age)));
            }
        }
        buffers.endBatch(spray);
        pose.popPose();
    }

    /** A flat strip on the surface along {@code dir}, {@code offset} to the side, {@code width} across, {@code length} along. */
    private static void flat(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, Vec3 dir, float offset, float width, float length,
                             int rgb, int alpha, int light, float u, float v) {
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        Vec3 mid = c.add(across.scale(offset));
        Vec3 a = across.scale(width / 2), l = dir.scale(length / 2);
        boolean tex = u == -1, soft = u == -2;                                  // -1: the whole texture; -2: its middle row across (soft edges)
        float u0 = tex || soft ? 0 : u, u1 = tex || soft ? 1 : u, v0 = tex ? 0 : v, v1 = tex ? 1 : v;
        corner(vc, m, n, mid.subtract(a).subtract(l), u0, v0, rgb, alpha, light);
        corner(vc, m, n, mid.add(a).subtract(l), u1, v0, rgb, alpha, light);
        corner(vc, m, n, mid.add(a).add(l), u1, v1, rgb, alpha, light);
        corner(vc, m, n, mid.subtract(a).add(l), u0, v1, rgb, alpha, light);
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int rgb, int alpha, int light) {
        FxDraw.vertex(vc, m, n, (float) p.x, (float) p.y, (float) p.z, u, v, rgb, alpha, light);
    }
}
