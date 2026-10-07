package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.client.anim.AnimController;
import com.dbzenith.client.anim.NpcActions;
import com.dbzenith.network.ImpactPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * World-space hit effects: star flashes, expanding shockwave rings, crater decals with flying debris, and the camera
 * shake and hitstop that go with them. Driven by {@link ImpactPacket}s from the server and by landings this client
 * sees for itself (any fighter hitting the ground hard).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ImpactFx {
    private static final ResourceLocation STAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/impact_star.png");
    private static final ResourceLocation RING = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/shock_ring.png");
    private static final ResourceLocation CRATER = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/crater.png");
    private static final ResourceLocation CRACKS = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ground_cracks.png");
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int MAX_EFFECTS = 256;
    private static final int MAX_DECALS = 48;

    private enum Kind { FLASH, RING, FACING_RING, DECAL, CRACKS }

    /** One effect. RING grows from {@code size0} to {@code size}; everything fades over its life. */
    private record Fx(Kind kind, Vec3 pos, Vec3 normal, int color, float size0, float size, int born, int life, float spin, int alpha) {}

    private static final List<Fx> EFFECTS = new ArrayList<>();
    private static final Int2FloatOpenHashMap LAST_VY = new Int2FloatOpenHashMap();
    private static int ticks;

    private ImpactFx() {}

    // ------------------------------------------------------------------ server impacts

    public static void onImpact(ImpactPacket m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 pos = new Vec3(m.x(), m.y(), m.z());
        Vec3 dir = new Vec3(m.dx(), m.dy(), m.dz());
        float s = m.scale();
        RandomSource rnd = level.random;
        boolean mine = mc.player != null && (m.attackerId() == mc.player.getId() || m.victimId() == mc.player.getId());
        if (mc.player != null && m.attackerId() == mc.player.getId() && m.kind() != ImpactPacket.KI_HIT && m.kind() != ImpactPacket.EXPLOSION) com.dbzenith.client.Prediction.landed();
        int tint = FxDraw.mix(m.color(), 0xFFFFFF, 0.55f);
        com.dbzenith.client.ClientSounds.impact(level, pos, m.kind(), s, m.flags());
        switch (m.kind()) {
            case ImpactPacket.PUNCH -> {
                add(Kind.FLASH, pos, dir, 0xFFFFFF, 0, 0.9f * s, 3, 230);
                add(Kind.RING, pos, dir, 0xFFFFFF, 0.15f * s, 1.2f * s, 6, 190);
                burst(level, ParticleTypes.CRIT, pos, dir, 6, 0.35);
                CameraFx.shakeAt(pos, 0.12f, 16);
                if (mine) { CameraFx.shake(0.1f); hitstop(m, 2); }
            }
            case ImpactPacket.HEAVY, ImpactPacket.SPIKE -> {
                add(Kind.FLASH, pos, dir, 0xFFFFFF, 0, 2.0f * s, 4, 255);
                add(Kind.RING, pos, dir, 0xFFFFFF, 0.3f * s, 2.8f * s, 8, 210);
                add(Kind.FACING_RING, pos, dir, 0xFFF4D0, 0.2f * s, 1.6f * s, 5, 170);
                if (m.kind() == ImpactPacket.SPIKE) add(Kind.RING, pos, UP, 0xFFFFFF, 0.4f * s, 3.2f * s, 10, 160);
                burst(level, ParticleTypes.CRIT, pos, dir, 16, 0.6);
                burst(level, ParticleTypes.POOF, pos, dir, 3, 0.25);
                CameraFx.shakeAt(pos, 0.35f, 24);
                if (mine) { CameraFx.shake(0.15f); CameraFx.kick(1f); hitstop(m, 4); }
            }
            case ImpactPacket.GUARD -> {
                add(Kind.FLASH, pos, dir, 0xAEE6FF, 0, 0.8f * s, 3, 220);
                add(Kind.RING, pos, dir, 0xAEE6FF, 0.1f * s, 0.9f * s, 5, 200);
                burst(level, ParticleTypes.ELECTRIC_SPARK, pos, dir.reverse(), 8, 0.4);
                CameraFx.shakeAt(pos, 0.06f, 12);
                if (mine) hitstop(m, 1);
            }
            case ImpactPacket.PARRY -> {
                add(Kind.FLASH, pos, dir, 0xFFE6A0, 0, 1.8f * s, 5, 255);
                add(Kind.RING, pos, dir, 0xFFD27A, 0.2f * s, 2.2f * s, 8, 230);
                add(Kind.FACING_RING, pos, dir, 0xFFFFFF, 0.1f * s, 1.2f * s, 5, 200);
                burst(level, ParticleTypes.CRIT, pos, dir.reverse(), 18, 0.6);
                burst(level, ParticleTypes.ENCHANTED_HIT, pos, dir.reverse(), 10, 0.4);
                CameraFx.shakeAt(pos, 0.22f, 20);
                if (mine) { CameraFx.kick(0.7f); hitstop(m, 6); }
                AnimController.playOn(m.attackerId(), com.dbzenith.client.anim.Anims.HIT_HEAVY);   // the attacker reels
                AnimController.playOn(m.victimId(), com.dbzenith.client.anim.Anims.KI_WAVE);       // the defender throws them off
            }
            case ImpactPacket.GUARD_BREAK -> {
                add(Kind.FLASH, pos, dir, 0xAEE6FF, 0, 2.4f * s, 6, 255);
                add(Kind.RING, pos, dir, 0xAEE6FF, 0.4f * s, 3.0f * s, 10, 230);
                add(Kind.FACING_RING, pos, dir, 0xFFFFFF, 0.2f * s, 2.0f * s, 7, 220);
                burst(level, ParticleTypes.ELECTRIC_SPARK, pos, dir, 24, 0.7);
                burst(level, ParticleTypes.END_ROD, pos, dir, 10, 0.3);
                CameraFx.shakeAt(pos, 0.4f, 24);
                if (mc.player != null && m.victimId() == mc.player.getId()) CameraFx.flash(0xAEE6FF, 0.35f);
                if (mine) hitstop(m, 5);
            }
            case ImpactPacket.DEFLECT -> {
                add(Kind.FLASH, pos, dir, FxDraw.mix(m.color(), 0xFFFFFF, 0.6f), 0, 1.6f * s, 4, 255);
                add(Kind.FACING_RING, pos, dir, 0xFFFFFF, 0.1f * s, 1.6f * s, 6, 220);
                burst(level, ParticleTypes.ELECTRIC_SPARK, pos, dir, 12, 0.5);
                CameraFx.shakeAt(pos, 0.15f, 16);
                AnimController.playOn(m.attackerId(), com.dbzenith.client.anim.Anims.HOOK);         // the swat
            }
            case ImpactPacket.KI_HIT -> {
                add(Kind.FLASH, pos, dir, tint, 0, 1.4f * s, 3, 230);
                add(Kind.FACING_RING, pos, dir, m.color(), 0.1f * s, 1.3f * s, 5, 170);
                burst(level, ParticleTypes.END_ROD, pos, dir, 3, 0.15);
                CameraFx.shakeAt(pos, 0.05f, 12);
            }
            case ImpactPacket.EXPLOSION -> {
                add(Kind.FLASH, pos, dir, tint, 0, 2.4f * s, 5, 255);
                add(Kind.RING, pos, UP, m.color(), 0.3f * s, 2.6f * s, 12, 200);
                add(Kind.FACING_RING, pos, dir, 0xFFFFFF, 0.2f * s, 1.8f * s, 8, 180);
                Vec3 ground = groundBelow(level, pos, 3);
                if (ground != null) crater(level, ground, Math.min(6f, 0.9f * s), (int) (8 + 6 * s));
                CameraFx.shakeAt(pos, 0.25f + 0.08f * s, 32 + 4 * s);
                if (mc.gameRenderer.getMainCamera().getPosition().distanceTo(pos) < 4 + 2 * s) {
                    CameraFx.kick(0.6f);
                    CameraFx.flash(tint, 0.35f);
                }
            }
            default -> { }
        }
        feel(m, level, pos, dir, s, mine);
    }

    /**
     * What makes a blow special (CX-19e): a critical (a red-orange burst), a counter (gold, with speed lines for the two
     * fighters), a Z-hit (an afterimage ring); the words over the victim; and the NPC victim's reel.
     */
    private static void feel(ImpactPacket m, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine) {
        int k = m.kind(), f = m.flags();
        if (k == ImpactPacket.HEAVY || k == ImpactPacket.SPIKE || k == ImpactPacket.GUARD_BREAK) NpcActions.heavyHint(m.victimId());
        if ((f & ImpactPacket.CRIT) != 0) {
            add(Kind.FLASH, pos, dir, 0xFF6A3A, 0, 2.2f * s, 4, 255);
            add(Kind.FACING_RING, pos, dir, 0xFFB050, 0.2f * s, 2.0f * s, 6, 210);
            burst(level, ParticleTypes.CRIT, pos, dir, 12, 0.7);
            if (mine) CameraFx.speedLines(0xFFB070, 0.55f);
            DamagePopups.word(m.victimId(), "critical", 0xFFFF7A4A);
        }
        if ((f & ImpactPacket.COUNTER) != 0) {
            add(Kind.FLASH, pos, dir, 0xFFE07A, 0, 2.8f * s, 5, 255);
            add(Kind.RING, pos, dir, 0xFFD040, 0.3f * s, 3.4f * s, 9, 230);
            burst(level, ParticleTypes.ENCHANTED_HIT, pos, dir, 16, 0.6);
            if (mine) {
                CameraFx.speedLines(0xFFE890, 1f);
                CameraFx.kick(0.8f);
            }
            DamagePopups.word(m.victimId(), "counter", 0xFFFFD34A);
        }
        if ((f & ImpactPacket.ZHIT) != 0) {
            add(Kind.FACING_RING, pos, dir, 0x9FE8FF, 0.3f * s, 2.4f * s, 7, 200);
            burst(level, ParticleTypes.CLOUD, pos, dir.reverse(), 6, 0.2);
        }
        if (k == ImpactPacket.GUARD_BREAK) DamagePopups.word(m.victimId(), "guard_break", 0xFFAEE6FF);
        if (k == ImpactPacket.PARRY) DamagePopups.word(m.victimId(), "perfect_guard", 0xFFFFE6A0);
        if (!mine) hitstop(m, ImpactPacket.hitstopFor(k, f));                    // fights you watch freeze too
    }

    private static void hitstop(ImpactPacket m, int frames) {
        if (m.hitstop() >= 0) frames = m.hitstop();                                // the server's freeze, so both sides match (CX-19e)
        if (frames <= 0) return;
        AnimController.hitstop(m.attackerId(), frames);
        AnimController.hitstop(m.victimId(), frames);
    }

    // ------------------------------------------------------------------ effects other client code asks for

    /** A transformation bursting out: flash, two rings and a shake for whoever is close. */
    public static void transformBurst(Entity who, int color) {
        Vec3 c = who.position().add(0, who.getBbHeight() * 0.55, 0);
        int tint = FxDraw.mix(color, 0xFFFFFF, 0.5f);
        add(Kind.FLASH, c, UP, tint, 0, 4.5f, 6, 255);
        add(Kind.RING, who.position().add(0, 0.1, 0), UP, color, 0.5f, 6f, 16, 200);
        add(Kind.FACING_RING, c, UP, 0xFFFFFF, 0.4f, 3.5f, 10, 190);
        CameraFx.shakeAt(c, 0.45f, 40);
        Minecraft mc = Minecraft.getInstance();
        if (who == mc.player) CameraFx.flash(tint, 0.55f);
    }

    /** A crater where something struck the ground: decal, a ring along the ground, debris and dust. */
    /** A scorch mark and nothing else (beams burning the ground as they pass). */
    public static void scorch(Vec3 ground, float radius) {
        trimDecals();
        add(Kind.DECAL, ground.add(0, 0.021, 0), UP, 0xFFFFFF, radius, radius, 260, 200, Minecraft.getInstance().level.random.nextFloat() * Mth.TWO_PI);
    }

    /** Cracks spreading through the ground (a long charge). */
    public static void cracks(Vec3 ground, float radius) {
        trimDecals();
        add(Kind.CRACKS, ground.add(0, 0.022, 0), UP, 0xFFFFFF, radius, radius, 400, 230, Minecraft.getInstance().level.random.nextFloat() * Mth.TWO_PI);
    }

    private static void trimDecals() {
        long decals = EFFECTS.stream().filter(f -> f.kind == Kind.DECAL || f.kind == Kind.CRACKS).count();
        if (decals >= MAX_DECALS) EFFECTS.stream().filter(f -> f.kind == Kind.DECAL || f.kind == Kind.CRACKS).findFirst().ifPresent(EFFECTS::remove);
    }

    public static void crater(ClientLevel level, Vec3 ground, float radius, int debris) {
        RandomSource rnd = level.random;
        long decals = EFFECTS.stream().filter(f -> f.kind == Kind.DECAL).count();
        if (decals >= MAX_DECALS) EFFECTS.stream().filter(f -> f.kind == Kind.DECAL).findFirst().ifPresent(EFFECTS::remove);
        add(Kind.DECAL, ground.add(0, 0.02, 0), UP, 0xFFFFFF, radius, radius, 320, 235, rnd.nextFloat() * Mth.TWO_PI);
        add(Kind.RING, ground.add(0, 0.15, 0), UP, 0xFFFFFF, radius * 0.3f, radius * 2.2f, 9, 150);
        BlockState below = level.getBlockState(BlockPos.containing(ground.x, ground.y - 0.5, ground.z));
        ParticleOptions chunk = below.getRenderShape() != RenderShape.INVISIBLE ? new BlockParticleOption(ParticleTypes.BLOCK, below) : ParticleTypes.POOF;
        for (int i = 0; i < debris; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * radius * 0.6, out = 0.12 + rnd.nextDouble() * 0.25;
            level.addParticle(chunk, ground.x + Math.cos(a) * r, ground.y + 0.1, ground.z + Math.sin(a) * r,
                    Math.cos(a) * out, 0.25 + rnd.nextDouble() * 0.45, Math.sin(a) * out);
        }
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            level.addParticle(ParticleTypes.CLOUD, ground.x + Math.cos(a) * radius * 0.5, ground.y + 0.15, ground.z + Math.sin(a) * radius * 0.5,
                    Math.cos(a) * 0.22, 0.02, Math.sin(a) * 0.22);
        }
    }

    // ------------------------------------------------------------------ landings

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            EFFECTS.clear();
            LAST_VY.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        Iterator<Fx> it = EFFECTS.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            if (ticks - f.born > f.life) it.remove();
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Player || e instanceof com.dbzenith.npc.KiFighter) || !(e instanceof LivingEntity living)) continue;
            if (e.distanceToSqr(cam) > 64 * 64) continue;
            float vy = (float) (e.getY() - e.yo);
            float before = LAST_VY.getOrDefault(e.getId(), 0f);
            float fall = Math.min(before, vy);
            if (living.onGround() && before < 0 && fall < -0.85f && living.isAlive()) {
                float speed = -fall;
                crater(level, e.position(), Math.min(3.5f, 0.6f + speed * 0.9f), (int) (10 + speed * 12));
                CameraFx.shakeAt(e.position(), Math.min(0.6f, 0.18f * speed), 32);
            }
            LAST_VY.put(e.getId(), living.onGround() ? 0f : vy);
        }
        if (LAST_VY.size() > 512) LAST_VY.clear();
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer decals = buffers.getBuffer(RenderType.entityTranslucent(CRATER));
        for (Fx f : EFFECTS) {
            if (f.kind != Kind.DECAL) continue;
            float age = ticks - f.born + partial;
            float fade = Math.min(1f, (f.life - age) / 80f);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(f.pos.x, f.pos.y + 0.3, f.pos.z));
            FxDraw.plane(pose, decals, f.pos, UP, f.size, f.spin, f.color, (int) (f.alpha * fade), light);
        }
        buffers.endBatch(RenderType.entityTranslucent(CRATER));

        VertexConsumer cracks = buffers.getBuffer(RenderType.entityTranslucent(CRACKS));
        for (Fx f : EFFECTS) {
            if (f.kind != Kind.CRACKS) continue;
            float age = ticks - f.born + partial;
            float fade = Math.min(1f, (f.life - age) / 80f) * Math.min(1f, age / 8f);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(f.pos.x, f.pos.y + 0.3, f.pos.z));
            FxDraw.plane(pose, cracks, f.pos, UP, f.size * Math.min(1f, 0.5f + age / 30f), f.spin, f.color, (int) (f.alpha * fade), light);
        }
        buffers.endBatch(RenderType.entityTranslucent(CRACKS));

        RenderType ringType = FxRenderTypes.additive(RING);
        VertexConsumer rings = buffers.getBuffer(ringType);
        for (Fx f : EFFECTS) {
            if (f.kind != Kind.RING && f.kind != Kind.FACING_RING) continue;
            float t = Math.min(1f, (ticks - f.born + partial) / f.life);
            float eased = 1 - (1 - t) * (1 - t) * (1 - t);                    // fast out, slow settle
            float size = Mth.lerp(eased, f.size0, f.size);
            Vec3 normal = f.kind == Kind.FACING_RING ? cam.subtract(f.pos) : f.normal;
            FxDraw.plane(pose, rings, f.pos, normal, size, f.spin, f.color, (int) (f.alpha * (1 - t)), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch(ringType);

        RenderType starType = FxRenderTypes.additive(STAR);
        VertexConsumer stars = buffers.getBuffer(starType);
        for (Fx f : EFFECTS) {
            if (f.kind != Kind.FLASH) continue;
            float t = Math.min(1f, (ticks - f.born + partial) / f.life);
            float size = f.size * (0.7f + 0.6f * (float) Math.sin(Math.min(1f, t * 1.6f) * Math.PI * 0.5));
            FxDraw.billboard(pose, stars, camera.rotation(), f.pos.x, f.pos.y, f.pos.z, size, f.spin, 0, 0, 1, 1,
                    f.color, (int) (f.alpha * (1 - t * t)));
        }
        buffers.endBatch(starType);
        pose.popPose();
    }

    // ------------------------------------------------------------------ helpers

    private static void add(Kind kind, Vec3 pos, Vec3 normal, int color, float size0, float size, int life, int alpha) {
        RandomSource rnd = Minecraft.getInstance().level.random;
        add(kind, pos, normal, color, size0, size, life, alpha, rnd.nextFloat() * Mth.TWO_PI);
    }

    private static void add(Kind kind, Vec3 pos, Vec3 normal, int color, float size0, float size, int life, int alpha, float spin) {
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.remove(0);
        EFFECTS.add(new Fx(kind, pos, normal, color, size0, size, ticks, life, spin, alpha));
    }

    /** Particles thrown out roughly along {@code dir}. */
    private static void burst(ClientLevel level, ParticleOptions type, Vec3 pos, Vec3 dir, int count, double speed) {
        RandomSource rnd = level.random;
        Vec3 d = dir.lengthSqr() < 1e-4 ? UP : dir.normalize();
        for (int i = 0; i < count; i++) {
            Vec3 v = d.scale(0.5).add(rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5).normalize().scale(speed * (0.5 + rnd.nextDouble()));
            level.addParticle(type, pos.x, pos.y, pos.z, v.x, v.y, v.z);
        }
    }

    /** The top of the first solid block within {@code depth} blocks under {@code pos}, or null. */
    private static Vec3 groundBelow(ClientLevel level, Vec3 pos, int depth) {
        BlockPos.MutableBlockPos p = BlockPos.containing(pos).mutable();
        for (int i = 0; i <= depth; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty()) return new Vec3(pos.x, p.getY() + s.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y), pos.z);
        }
        return null;
    }
}
