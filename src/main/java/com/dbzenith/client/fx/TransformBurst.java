package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
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
 * The transformation burst (CX-24 phase 6): the moment the new form breaks out, scaled by how high the form stands
 * (its tier):
 * <ul>
 *   <li>a pillar of light shooting up into the sky from where they stand, white-hot in the middle, the aura's colour
 *       round it, thinning and fading;</li>
 *   <li>a dome of energy blasting out with a ring racing across the ground ({@link KiWaveFx#dome}), and the flash,
 *       rings and shake of {@link ImpactFx#transformBurst};</li>
 *   <li>the ground answering: a glow spreading under them, cracks left behind, a ring of dust rolling out and earth
 *       thrown up;</li>
 *   <li>sparks of the new power rising round them for a couple of seconds after;</li>
 *   <li>for the great forms (tier 3 up), close by: a colour split and, at tier 4 up, an anime impact frame.</li>
 * </ul>
 * Client setting {@code transformBurst} (on by default) turns all but the plain flash off.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TransformBurst {
    private record Burst(int entityId, Vec3 ground, float power, int color, int born) {}

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final int PILLAR = 34, SPARKS = 50;
    private static int ticks;

    private TransformBurst() {}

    /** A fighter has just changed into {@code formId}. */
    public static void burst(Entity who, String formId, int color) {
        ImpactFx.transformBurst(who, color);
        if (!DBZConfig.CLIENT.transformBurst.get()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Form form = Forms.byId(formId);
        float power = Mth.clamp(0.5f + form.tier() * 0.28f, 0.6f, 2.4f) * Math.max(1f, form.scale());
        Vec3 feet = who.position();
        Vec3 ground = BigSphere.groundBelow(level, feet.add(0, 0.5, 0), 4);
        if (ground == null) ground = feet;
        BURSTS.add(new Burst(who.getId(), ground, power, color, ticks));
        Vec3 mid = feet.add(0, who.getBbHeight() * 0.5, 0);
        KiWaveFx.dome(mid, 2.5f + 2.2f * power, color, 16 + (int) (4 * power));
        if (ground != feet || who.onGround()) ground(level, ground, power, color);
        double seen = mc.gameRenderer.getMainCamera().getPosition().distanceTo(mid);
        if (form.tier() >= 3 && seen < 24) CameraFx.aberrate(0.25f + 0.1f * form.tier());
        if (form.tier() >= 4 && seen < 18) CameraFx.impactFrame();
        CameraFx.shakeAt(mid, 0.25f + 0.2f * power, 30 + 10 * power);
    }

    /** The ground answers: cracks, a ring of dust rolling out, earth thrown up. */
    private static void ground(ClientLevel level, Vec3 ground, float power, int color) {
        RandomSource rnd = level.random;
        float r = 2.5f + 1.6f * power;
        if (power >= 1.2f) ImpactFx.cracks(ground, r * 0.8f);
        ImpactFx.dustRing(ground, r, 10 + (int) (6 * power));
        BlockState below = level.getBlockState(BlockPos.containing(ground.x, ground.y - 0.5, ground.z));
        if (below.isAir()) return;
        BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, below);
        int debris = 10 + (int) (14 * power);
        for (int i = 0; i < debris; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * r * 0.7, out = 0.1 + rnd.nextDouble() * 0.25;
            level.addParticle(chunk, ground.x + Math.cos(a) * d, ground.y + 0.1, ground.z + Math.sin(a) * d,
                    Math.cos(a) * out, 0.35 + rnd.nextDouble() * 0.5 * power, Math.sin(a) * out);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BURSTS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        BURSTS.removeIf(b -> ticks - b.born > Math.max(PILLAR, SPARKS));
        RandomSource rnd = mc.level.random;
        for (Burst b : BURSTS) {                                                    // sparks of the new power rising round them
            int age = ticks - b.born;
            Entity who = mc.level.getEntity(b.entityId);
            if (age > SPARKS || who == null) continue;
            int n = age < 10 ? 4 : age % 2 == 0 ? 1 : 0;
            int c = FxDraw.mix(b.color, 0xFFFFFF, 0.4f);
            for (int i = 0; i < n * (KiParticles.quality() == 0 ? 1 : 2); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 0.4 + rnd.nextDouble() * who.getBbWidth() * 1.4;
                KiParticles.add(KiParticles.SPARK, who.getX() + Math.cos(a) * d, who.getY() + rnd.nextDouble() * who.getBbHeight(), who.getZ() + Math.sin(a) * d,
                        rnd.nextGaussian() * 0.01, 0.06 + rnd.nextDouble() * 0.08, rnd.nextGaussian() * 0.01, 0.3f, c, 12 + rnd.nextInt(10), 0.92f, -0.002f);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BURSTS.isEmpty() || !KiEnergy.available()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (Burst b : BURSTS) {
            float age = ticks - b.born + pt;
            if (age > PILLAR) continue;
            float k = age / PILLAR;
            float x = (float) (b.ground.x - cam.x), y = (float) (b.ground.y - cam.y), z = (float) (b.ground.z - cam.z);
            pillar(buffers, m, n, x, y, z, b, age, k);
            BigSphere.groundGlow(pose, buffers, new Vec3(x, y, z), 1.2f + 1.1f * b.power, 0, b.color, (1 - k) * (1 - k) * 0.8f);
        }
        buffers.endBatch();
    }

    /**
     * The pillar of light: it shoots up in a few ticks, high into the sky, a white-hot core in a wider column of the
     * aura's colour; then it thins from the bottom and fades.
     */
    private static void pillar(MultiBufferSource.BufferSource buffers, Matrix4f m, Matrix3f n, float x, float y, float z, Burst b, float age, float k) {
        float rise = Math.min(1, age / 5f);
        float top = y + (16 + 22 * b.power) * (1 - (1 - rise) * (1 - rise));
        float bottom = y + Math.max(0, (k - 0.45f) / 0.55f) * (top - y) * 0.7f;    // it lifts off the ground as it ends
        float fade = 1 - k * k, w = (0.7f + 0.55f * b.power) * (1 + 0.6f * Math.max(0, 0.2f - k) / 0.2f) * (1 - 0.5f * k);
        int deep = BigSphere.vivid(b.color), hot = FxDraw.mix(b.color, 0xFFFFFF, 0.55f);
        for (int pass = 0; pass < 2; pass++) {                                      // its colour against a bright sky, then the light
            var type = pass == 0 ? KiEnergy.tint() : KiEnergy.flat();
            VertexConsumer vc = buffers.getBuffer(type);
            if (pass == 0) {
                KiEnergy.ribbon(vc, m, n, x, bottom, z, x, top, z, 0, 0, 0, w * 1.6f, w * 0.9f, deep, (int) (120 * fade), 0, 0, 1, KiEnergy.RIBBON);
            } else {
                KiEnergy.ribbon(vc, m, n, x, bottom, z, x, top, z, 0, 0, 0, w * 2.2f, w * 1.2f, b.color, (int) (170 * fade), 0, age * 0.1f, age * 0.1f + 3, KiEnergy.RIBBON);
                KiEnergy.ribbon(vc, m, n, x, bottom, z, x, top, z, 0, 0, 0, w * 1.0f, w * 0.5f, hot, (int) (230 * fade), (int) (40 * fade), age * 0.2f, age * 0.2f + 3, KiEnergy.RIBBON);
                KiEnergy.ribbon(vc, m, n, x, bottom, z, x, top, z, 0, 0, 0, w * 0.35f, w * 0.15f, 0xFFFFFF, (int) (255 * fade), (int) (60 * fade), 0, 1, KiEnergy.RIBBON);
            }
            buffers.endBatch(type);
        }
    }
}
