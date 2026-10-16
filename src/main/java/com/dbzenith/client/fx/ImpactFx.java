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
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final ResourceLocation PUFF = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/nimbus_puff.png");
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int MAX_EFFECTS = 512;
    private static final int MAX_DECALS = 48;

    private enum Kind { FLASH, RING, FACING_RING, DECAL, CRACKS, DOME, SMOKE }

    /** One effect. RING grows from {@code size0} to {@code size}; everything fades over its life. */
    private record Fx(Kind kind, Vec3 pos, Vec3 normal, int color, float size0, float size, int born, int life, float spin, int alpha) {}

    private static final List<Fx> EFFECTS = new ArrayList<>();
    private static final Int2FloatOpenHashMap LAST_VY = new Int2FloatOpenHashMap();
    private static int ticks;

    private ImpactFx() {}

    // ------------------------------------------------------------------ server impacts

    public static void onImpact(ImpactPacket m) {
        if (m.kind() == ImpactPacket.APE_STOMP) {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Vec3 at = new Vec3(m.x(), m.y(), m.z());
            crater(mc.level, at, m.scale() * 0.7f, 36);
            dustRing(at, m.scale(), 32);
            CameraFx.shakeAt(at, 0.65f, 80);
            return;
        }
        if (m.kind() == ImpactPacket.BLOOD) {                                   // the knocked-out rework: blood, not a blow
            BloodFx.onImpact(m);
            return;
        }
        if (m.kind() == ImpactPacket.UI_DODGE) {                                // Ultra Instinct: an afterimage left standing, a silver streak
            Minecraft mc0 = Minecraft.getInstance();
            if (mc0.level != null && mc0.level.getEntity(m.attackerId()) instanceof net.minecraft.world.entity.player.Player who)
                Afterimages.ghost(who, m.x(), m.y() - 1, m.z(), 14);
            for (int i = 0; i <= 12; i++) {
                float k = i / 12f;
                KiParticles.add(KiParticles.MOTE, m.x() + m.dx() * k, m.y() + (Math.random() - 0.3) * 1.2, m.z() + m.dz() * k,
                        m.dx() * 0.02, 0.02, m.dz() * 0.02, 0.18f, m.color(), 8 + i / 2, 0.8f, 0f);
            }
            return;
        }
        if (m.kind() == ImpactPacket.REGROW) {                                  // the limbs rework: a limb growing back
            com.dbzenith.client.render.LimbRender.onRegrow(m);
            return;
        }
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
                KiParticles.burst(pos, m.color(), (int) (14 + 10 * s), 0.18f + 0.06f * s, 0.6f + 0.3f * s);   // radial sparks (the ki attack rework)
                CameraFx.shakeAt(pos, 0.05f, 12);
            }
            case ImpactPacket.EXPLOSION -> {
                add(Kind.FLASH, pos, dir, tint, 0, 2.4f * s, 5, 255);
                add(Kind.RING, pos, UP, m.color(), 0.3f * s, 2.6f * s, 12, 200);
                add(Kind.FACING_RING, pos, dir, 0xFFFFFF, 0.2f * s, 1.8f * s, 8, 180);
                KiParticles.burst(pos, m.color(), (int) (30 + 18 * s), 0.3f + 0.08f * s, 0.9f + 0.3f * s);
                // CX-46: a dome of light swelling out and thinning, then smoke left hanging and rising
                if (KiEnergy.available()) KiWaveFx.dome(pos, 1.3f * s, m.color(), 9 + (int) (2 * s));   // phase 6: a real dome of energy
                else add(Kind.DOME, pos, UP, tint, 0.3f * s, 1.25f * s, 9 + (int) (2 * s), 230);
                int puffs = Math.min(22, 5 + (int) (3 * s));
                for (int i = 0; i < puffs; i++) {
                    Vec3 at = pos.add(rnd.nextGaussian() * 0.35 * s, rnd.nextDouble() * 0.6 * s, rnd.nextGaussian() * 0.35 * s);
                    Vec3 drift = new Vec3(rnd.nextGaussian() * 0.012, 0.015 + rnd.nextDouble() * 0.03, rnd.nextGaussian() * 0.012);
                    int grey = 0x3C3834 + 0x0A0A0A * rnd.nextInt(4);
                    add(Kind.SMOKE, at, drift, grey, 0.45f * s, (0.9f + rnd.nextFloat() * 0.7f) * s, 60 + rnd.nextInt(50), 190);
                }
                if (s >= 2.5f) plume(pos, s, rnd);                                     // a big one sends up a column of smoke and a cap (phase 6)
                Vec3 ground = groundBelow(level, pos, 3);
                if (ground != null && (m.flags() & ImpactPacket.DIGS) == 0) crater(level, ground, Math.min(6f, 0.9f * s), (int) (8 + 6 * s));   // a real crater brings its own dust
                CameraFx.shakeAt(pos, 0.25f + 0.08f * s, 32 + 4 * s);
                if (mc.gameRenderer.getMainCamera().getPosition().distanceTo(pos) < 4 + 2 * s) {
                    CameraFx.kick(0.6f);
                    CameraFx.flash(tint, 0.35f);
                }
                double seen = mc.gameRenderer.getMainCamera().getPosition().distanceTo(pos);   // phase 9: the colour split, and for the biggest an impact frame
                if (seen < 12 + 3 * s) CameraFx.aberrate(Math.min(1f, s / 5f) * (float) (1 - seen / (12 + 3 * s)) * 1.4f);
                if (s >= 5 && seen < 10 + 3 * s) CameraFx.impactFrame();
            }
            case ImpactPacket.WAVE, ImpactPacket.STUN_WAVE, ImpactPacket.FLARE -> KiWaveFx.start(m);   // the ki attack rework, phase 5
            case ImpactPacket.CRATER -> torn(level, pos, s, rnd);                // phase 6: the ground torn open
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
        int hs = m.hitstop() >= 0 ? m.hitstop() : ImpactPacket.hitstopFor(k, f);   // the frame data waits out the freeze too
        HitboxOverlay.freeze(m.attackerId(), hs);
        HitboxOverlay.freeze(m.victimId(), hs);
    }

    private static void hitstop(ImpactPacket m, int frames) {
        if (m.hitstop() >= 0) frames = m.hitstop();                                // the server's freeze, so both sides match (CX-19e)
        if (frames <= 0) return;
        AnimController.hitstop(m.attackerId(), frames);
        AnimController.hitstop(m.victimId(), frames);
    }

    // ------------------------------------------------------------------ effects other client code asks for


    /** A flight boost (CX-38): a ring of air thrown back across the flight path, a puff of light, a jolt. */
    public static void boostBurst(Entity who, Vec3 dir, int color) {
        Vec3 c = who.position().add(0, who.getBbHeight() * 0.5, 0);
        Vec3 d = dir.lengthSqr() < 1e-6 ? UP : dir.normalize();
        add(Kind.RING, c.subtract(d.scale(0.6)), d, 0xFFFFFF, 0.6f, 3.6f, 9, 210);
        add(Kind.RING, c.subtract(d.scale(1.4)), d, FxDraw.mix(color, 0xFFFFFF, 0.6f), 0.4f, 2.4f, 12, 150);
        add(Kind.FLASH, c, UP, FxDraw.mix(color, 0xFFFFFF, 0.7f), 0, 1.6f, 4, 170);
        Minecraft mc = Minecraft.getInstance();
        if (who == mc.player) {
            CameraFx.kick(0.35f);
            CameraFx.rush();
        }
    }
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
        for (int i = 0; i < 14; i++) {                                          // a ring of dust rolling out (its own smoke, not vanilla's clouds)
            double a = i * Math.PI * 2 / 14;
            add(Kind.SMOKE, ground.add(Math.cos(a) * radius * 0.5, 0.2, Math.sin(a) * radius * 0.5), new Vec3(Math.cos(a) * 0.05, 0.008, Math.sin(a) * 0.05),
                    DUST + 0x0A0A0A * rnd.nextInt(3), 0.3f * radius, (0.6f + rnd.nextFloat() * 0.5f) * radius, 40 + rnd.nextInt(30), 170);
        }
    }

    private static final int DUST = 0x7A6A55;

    /** A ring of dust rolling out across the ground from {@code ground} to about {@code radius} (a transformation, CX-24 phase 6). */
    public static void dustRing(Vec3 ground, float radius, int puffs) {
        RandomSource rnd = Minecraft.getInstance().level == null ? RandomSource.create() : Minecraft.getInstance().level.random;
        for (int i = 0; i < puffs; i++) {
            double a = i * Math.PI * 2 / puffs + rnd.nextDouble() * 0.3;
            add(Kind.SMOKE, ground.add(Math.cos(a) * radius * 0.35, 0.25, Math.sin(a) * radius * 0.35), new Vec3(Math.cos(a) * 0.09, 0.01, Math.sin(a) * 0.09),
                    DUST + 0x0A0A0A * rnd.nextInt(3), 0.35f * radius, (0.5f + rnd.nextFloat() * 0.4f) * radius, 35 + rnd.nextInt(25), 160);
        }
    }

    /**
     * The ground torn open (the ki attack rework, phase 6), for a crater of radius {@code r}: a ring of dust rolling out
     * over the rim, earth flung up and out, and for a big one a column of dust rising from the middle. The crater itself
     * was dug for real; this is what hangs over it.
     */
    private static void torn(ClientLevel level, Vec3 pos, float r, RandomSource rnd) {
        CameraFx.shakeAt(pos, 0.15f + 0.05f * r, 16 + 3 * r);
        double seen = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(pos);
        if (r >= 6 && seen < 3 * r) CameraFx.aberrate(0.5f * (float) (1 - seen / (3 * r)));
        if (r >= 10 && seen < 2.5 * r) CameraFx.impactFrame();
        if (r < 0.9f) {                                                         // only cracked: a scuff and a puff
            Vec3 ground = groundBelow(level, pos, 3);
            if (ground != null) crater(level, ground, 0.9f, 6);
            return;
        }
        BlockState below = level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.5, pos.z));
        if (below.isAir()) below = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        ParticleOptions chunk = new BlockParticleOption(ParticleTypes.BLOCK, below);
        int ring = Math.min(40, 8 + (int) (3 * r));
        for (int i = 0; i < ring; i++) {                                        // dust rolling out over the rim
            double a = rnd.nextDouble() * Math.PI * 2, d = r * (0.7 + rnd.nextDouble() * 0.6), out = 0.03 + rnd.nextDouble() * 0.06;
            add(Kind.SMOKE, pos.add(Math.cos(a) * d, 0.3 + rnd.nextDouble() * 0.6, Math.sin(a) * d), new Vec3(Math.cos(a) * out, 0.01 + rnd.nextDouble() * 0.02, Math.sin(a) * out),
                    DUST + 0x0A0A0A * rnd.nextInt(3), 0.4f + 0.12f * r, (0.9f + rnd.nextFloat() * 0.6f) * (0.6f + 0.16f * r), 70 + rnd.nextInt(60), 190);
        }
        int flung = Math.min(70, 10 + (int) (4 * r));
        for (int i = 0; i < flung; i++) {                                       // earth flung up and out
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * r * 0.8, v = 0.2 + rnd.nextDouble() * (0.25 + 0.03 * r);
            level.addParticle(chunk, pos.x + Math.cos(a) * d, pos.y + 0.2, pos.z + Math.sin(a) * d, Math.cos(a) * v, 0.35 + rnd.nextDouble() * (0.4 + 0.04 * r), Math.sin(a) * v);
        }
        if (r >= 4) {                                                           // a column of dust rising from the middle
            int column = Math.min(24, (int) (2 * r));
            for (int i = 0; i < column; i++) {
                Vec3 at = pos.add(rnd.nextGaussian() * r * 0.2, rnd.nextDouble() * r * 0.8, rnd.nextGaussian() * r * 0.2);
                add(Kind.SMOKE, at, new Vec3(rnd.nextGaussian() * 0.01, 0.03 + rnd.nextDouble() * 0.04, rnd.nextGaussian() * 0.01),
                        DUST - 0x101010 + 0x0A0A0A * rnd.nextInt(3), 0.5f + 0.1f * r, (1f + rnd.nextFloat() * 0.6f) * (0.5f + 0.14f * r), 90 + rnd.nextInt(60), 200);
            }
        }
    }

    /** A column of smoke rising from a big explosion and spreading into a cap at the top. */
    private static void plume(Vec3 pos, float s, RandomSource rnd) {
        int column = Math.min(26, 6 + (int) (3 * s));
        for (int i = 0; i < column; i++) {
            double h = rnd.nextDouble() * s * 1.6;
            Vec3 at = pos.add(rnd.nextGaussian() * s * 0.15, h, rnd.nextGaussian() * s * 0.15);
            add(Kind.SMOKE, at, new Vec3(rnd.nextGaussian() * 0.008, 0.05 + rnd.nextDouble() * 0.05, rnd.nextGaussian() * 0.008),
                    0x3A3632 + 0x0A0A0A * rnd.nextInt(3), 0.5f * s, (0.7f + rnd.nextFloat() * 0.5f) * s * 0.55f, 80 + rnd.nextInt(50), 200);
        }
        for (int i = 0; i < 12; i++) {                                          // the cap
            double a = i * Math.PI * 2 / 12;
            Vec3 at = pos.add(Math.cos(a) * s * 0.4, s * 1.7, Math.sin(a) * s * 0.4);
            add(Kind.SMOKE, at, new Vec3(Math.cos(a) * 0.03, 0.05, Math.sin(a) * 0.03), 0x48423C, 0.6f * s, 0.9f * s, 100 + rnd.nextInt(40), 190);
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

        // CX-46: smoke hanging after an explosion, lit by the world, swelling, rising and thinning
        RenderType smokeType = RenderType.entityTranslucent(PUFF);
        VertexConsumer smoke = buffers.getBuffer(smokeType);
        for (Fx f : EFFECTS) {
            if (f.kind != Kind.SMOKE) continue;
            float age = ticks - f.born + partial, t = Math.min(1f, age / f.life);
            Vec3 at = f.pos.add(f.normal.scale(age));
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
            float size = Mth.lerp(1 - (1 - t) * (1 - t), f.size0, f.size);
            int alpha = (int) (f.alpha * Math.min(1f, age / 6f) * (1 - t) * (1 - t));
            FxDraw.billboard(pose, smoke, camera.rotation(), at.x, at.y, at.z, size, f.spin + age * 0.01f, 0, 0, 1, 1, f.color, alpha);
        }
        buffers.endBatch(smokeType);

        // CX-46: the dome of light, brightest at its rim, swelling fast and thinning out
        RenderType domeType = FxRenderTypes.additive(GLOW);
        VertexConsumer dome = buffers.getBuffer(domeType);
        for (Fx f : EFFECTS) {
            if (f.kind != Kind.DOME) continue;
            float t = Math.min(1f, (ticks - f.born + partial) / f.life);
            float r = Mth.lerp(1 - (1 - t) * (1 - t) * (1 - t), f.size0, f.size);
            sphere(pose, dome, f.pos, r, cam, f.color, f.alpha * (1 - t) * (1 - t));
        }
        buffers.endBatch(domeType);

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

    /** A ball of light (the middle of the glow texture, so plain colour), see-through in the middle, bright at the rim. */
    private static void sphere(PoseStack pose, VertexConsumer vc, Vec3 c, float r, Vec3 eye, int rgb, float alpha) {
        int lat = 10, lon = 18;
        var m = pose.last().pose();
        var n = pose.last().normal();
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                Vec3[] q = {dir(i, j, lat, lon), dir(i + 1, j, lat, lon), dir(i + 1, j + 1, lat, lon), dir(i, j + 1, lat, lon)};
                for (Vec3 d : q) {
                    Vec3 p = c.add(d.scale(r));
                    Vec3 view = eye.subtract(p).normalize();
                    float rim = 1 - (float) Math.abs(view.dot(d));
                    int a = (int) (alpha * (0.18f + 0.82f * rim * rim));
                    FxDraw.vertex(vc, m, n, (float) p.x, (float) p.y, (float) p.z, 0.5f, 0.5f, rgb, a, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    private static Vec3 dir(int i, int j, int lat, int lon) {
        double th = Math.PI * i / lat, ph = Math.PI * 2 * j / lon;
        return new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
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
