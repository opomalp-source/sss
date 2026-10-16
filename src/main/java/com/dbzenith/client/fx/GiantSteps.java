package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.client.motion.Motion;
import com.dbzenith.client.motion.MotionEngine;
import com.dbzenith.transform.GreatApe;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A giant's weight (the user: "when I stomp or walk it shakes", v0.134). Every footfall of a giant form (the Great
 * Ape, the Giant Namekian) thuds and shakes the view of everyone near it, the walker too, with a puff of dust and
 * earth kicked up at the foot; landing from a jump or a fall is a stomp with both feet, harder the faster it came
 * down; and the Great Ape's roar on the change rumbles. Footfalls follow the animation's gait (two a cycle), so the
 * shake lands with the foot.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GiantSteps {
    private GiantSteps() {}

    /** The smallest form scale that walks like a giant. */
    public static final float GIANT = 2f;

    private static final class Walker {
        int half = Integer.MIN_VALUE;
        boolean ground = true;
        double vy;
        int roarAt = -1;
    }

    private static final Int2ObjectOpenHashMap<Walker> WALKERS = new Int2ObjectOpenHashMap<>();

    /** The Great Ape's roar on the change: the rumble comes with the roar itself ({@code Anims.APE_ROAR}). */
    public static void roar(Player player) {
        WALKERS.computeIfAbsent(player.getId(), k -> new Walker()).roarAt = player.tickCount + 13;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            WALKERS.clear();
            return;
        }
        if (mc.isPaused()) return;
        WALKERS.int2ObjectEntrySet().removeIf(e -> mc.level.getEntity(e.getIntKey()) == null);
        for (Player p : mc.level.players()) {
            float s = GreatApe.scaleOf(p);
            Walker w = WALKERS.get(p.getId());
            if (s < GIANT) {
                if (w != null && w.roarAt < 0) WALKERS.remove(p.getId());
                continue;
            }
            if (w == null) WALKERS.put(p.getId(), w = new Walker());
            tick(mc.level, p, s, w);
        }
    }

    private static void tick(ClientLevel level, Player p, float s, Walker w) {
        if (w.roarAt >= 0 && p.tickCount >= w.roarAt) {                          // the roar: the ground trembles under it
            w.roarAt = -1;
            CameraFx.shakeAt(p.position(), 0.8f, 14 * s);
            ImpactFx.dustRing(p.position(), 1.6f * s, 20);
        }
        boolean ground = p.onGround();
        double vy = p.getY() - p.yo;
        double moved = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
        if (ground && !w.ground && w.vy < -0.25) {                               // a landing: both feet at once
            float k = (float) Mth.clamp(-w.vy / 1.2, 0.5, 1.6);
            stomp(level, p, s, 0, k);
        }
        Motion m = MotionEngine.peek(p);
        float gait = m != null && MotionEngine.enabled() ? m.gait() : p.walkAnimation.position() * 0.6662f / Mth.TWO_PI;
        int half = Mth.floor(gait * 2);
        if (w.half != Integer.MIN_VALUE && half != w.half && ground && moved > 0.02) {
            stomp(level, p, s, (half & 1) == 0 ? 1 : -1, p.isSprinting() ? 1.15f : 1f);
        }
        w.half = half;
        w.ground = ground;
        w.vy = vy;
    }

    /** A footfall ({@code side} 1 right, -1 left, 0 both feet) of strength {@code k}. */
    private static void stomp(ClientLevel level, Player p, float s, int side, float k) {
        float big = Mth.clamp((s - 1) / 11f, 0.15f, 1f);                       // a Giant Namekian treads lighter than a Great Ape
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 foot = p.position().add(right.scale(side * 0.14 * s));
        CameraFx.shakeAt(foot, (0.25f + 0.4f * big) * k, 10 * s);
        level.playLocalSound(foot.x, foot.y, foot.z, SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.2f + 1.6f * big * k, 0.75f - 0.3f * big, false);
        level.playLocalSound(foot.x, foot.y, foot.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.25f * big * k, 0.45f, false);
        float radius = (side == 0 ? 0.35f : 0.22f) * s * k;
        ImpactFx.dustRing(foot, radius, side == 0 ? 16 : 9);
        BlockState below = level.getBlockState(BlockPos.containing(foot.x, foot.y - 0.5, foot.z));
        if (below.getRenderShape() == RenderShape.INVISIBLE) return;
        RandomSource rnd = level.random;
        BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, below);
        int n = (int) (6 + 10 * big * k);
        for (int i = 0; i < n; i++) {                                            // earth kicked up round the foot
            double a = rnd.nextDouble() * Mth.TWO_PI, r = rnd.nextDouble() * radius * 0.7, out = 0.1 + rnd.nextDouble() * 0.2;
            level.addParticle(chunk, foot.x + Math.cos(a) * r, foot.y + 0.1, foot.z + Math.sin(a) * r,
                    Math.cos(a) * out, 0.2 + rnd.nextDouble() * 0.35 * big, Math.sin(a) * out);
        }
    }
}
