package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.network.KiChargePackets;
import com.dbzenith.skill.KiCharge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Who is charging a technique, on the client (CX-23), and the energy gathering where they hold it: a ball of light
 * that swells with the charge, drawing streaks in from around them, crackling near full. In front of the chest for a
 * blast, cupped at the hip for a beam, above the head for the big ones. Each player's pose comes from AnimController,
 * the local player's gauge from the HUD.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ClientKiCharge {
    /** A charge as last heard: how it is held, its colour, how far grown, the damage multiplier reached, the longest charge. */
    public record Charge(int kind, int color, float fraction, float power, int maxTicks, long heardAt) {}

    private static final Map<Integer, Charge> CHARGES = new HashMap<>();

    private ClientKiCharge() {}

    public static void set(KiChargePackets.State m) {
        if (m.fraction() < 0) {
            CHARGES.remove(m.entityId());
            return;
        }
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        CHARGES.put(m.entityId(), new Charge(m.kind(), m.color(), m.fraction(), m.power(), m.maxTicks(), now));
    }

    public static Charge get(int entityId) {
        return CHARGES.get(entityId);
    }

    /** The charge shown now: the last heard, grown on smoothly since (the server tells only every few ticks). */
    public static float shownFraction(Charge c, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return c.fraction();
        float since = mc.level.getGameTime() - c.heardAt() + partial;
        return Math.min(1f, c.fraction() + since / Math.max(1, c.maxTicks()));
    }

    public static void clear() {
        CHARGES.clear();
    }

    /** Where a figure holds the energy, and how big the ball is at a charge. */
    public static Vec3 hold(Entity e, int kind, float f) {
        return hold(e, e.position(), kind, f);
    }

    /** The same from a given position of the figure (interpolated, when drawing). */
    public static Vec3 hold(Entity e, Vec3 pos, int kind, float f) {
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRot : e.getYRot();
        double r = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(r), 0, Math.cos(r)), right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        float scale = e.getBbHeight() / 1.8f;
        return switch (kind) {
            case KiCharge.KIND_BEAM -> pos.add(0, 0.85 * scale, 0).add(right.scale(0.42 * scale)).add(forward.scale(-0.05));
            case KiCharge.KIND_OVERHEAD -> pos.add(0, (2.15 + radius(kind, f)) * scale, 0);
            default -> pos.add(0, 1.2 * scale, 0).add(forward.scale(0.75 * scale));
        };
    }

    public static float radius(int kind, float f) {
        return switch (kind) {
            case KiCharge.KIND_OVERHEAD -> 0.25f + 1.6f * f;
            case KiCharge.KIND_BEAM -> 0.12f + 0.5f * f;
            default -> 0.12f + 0.55f * f;
        };
    }


    private static final net.minecraft.resources.ResourceLocation GLOW = new net.minecraft.resources.ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png"),
            STAR = new net.minecraft.resources.ResourceLocation(DBZenith.MOD_ID, "textures/entity/impact_star.png");

    /** The orb itself: a soft glow in the technique's colour, a turning flare and a white-hot core, growing with the charge. */
    @SubscribeEvent
    public static void onRender(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES || CHARGES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        org.joml.Quaternionf rot = event.getCamera().rotation();
        com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float t = mc.level.getGameTime() + pt;
        for (Map.Entry<Integer, Charge> en : CHARGES.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) continue;
            Charge c = en.getValue();
            float f = shownFraction(c, pt);
            Vec3 at = hold(e, e.getPosition(pt), c.kind(), f);
            float r = radius(c.kind(), f) * e.getBbHeight() / 1.8f;
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) {   // your own, seen from the eyes: smaller, a little lower
                r *= 0.45f;
                at = at.add(0, -0.15, 0);
            }
            int hot = com.dbzenith.client.fx.FxDraw.mix(c.color(), 0xFFFFFF, 0.6f);
            float pulse = 1f + (0.06f + 0.08f * f) * Mth.sin(t * (0.6f + f));
            double x = at.x - cam.x, y = at.y - cam.y, z = at.z - cam.z;
            com.dbzenith.client.fx.FxDraw.billboard(pose, buffers.getBuffer(com.dbzenith.client.fx.FxRenderTypes.soft(GLOW)), rot, x, y, z, r * 3.2f * pulse, 0, 0, 0, 1, 1, c.color(), 200);
            com.dbzenith.client.fx.FxDraw.billboard(pose, buffers.getBuffer(com.dbzenith.client.fx.FxRenderTypes.additive(STAR)), rot, x, y, z, r * 2.8f, t * 0.3f, 0, 0, 1, 1, hot, (int) (80 + 120 * f));
            com.dbzenith.client.fx.FxDraw.billboard(pose, buffers.getBuffer(com.dbzenith.client.fx.FxRenderTypes.additive(GLOW)), rot, x, y, z, r * 1.6f, 0, 0, 0, 1, 1, 0xFFFFFF, 255);
        }
        buffers.endBatch();
    }
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CHARGES.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        RandomSource rnd = mc.level.random;
        Iterator<Map.Entry<Integer, Charge>> it = CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Charge> en = it.next();
            Entity e = mc.level.getEntity(en.getKey());
            Charge c = en.getValue();
            if (e == null || !e.isAlive() || now - c.heardAt() > 60) {
                it.remove();
                continue;
            }
            float f = shownFraction(c, 0);
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;   // your own sparks would fill the screen
            Vec3 at = hold(e, c.kind(), f);
            float rad = radius(c.kind(), f) * e.getBbHeight() / 1.8f;
            Vector3f rgb = new Vector3f(((c.color() >> 16) & 255) / 255f, ((c.color() >> 8) & 255) / 255f, (c.color() & 255) / 255f);
            Vector3f core = new Vector3f(Mth.lerp(0.6f, rgb.x(), 1f), Mth.lerp(0.6f, rgb.y(), 1f), Mth.lerp(0.6f, rgb.z(), 1f));
            int shell = 1 + (int) (f * 5);                                       // a light shell of sparks; the orb itself is drawn (onRender)
            for (int i = 0; i < shell; i++) {                                     // the ball: a dense, bright core and a coloured shell
                Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize().scale(rad * Math.cbrt(rnd.nextDouble()));
                boolean inner = d.length() < rad * 0.55;
                mc.level.addParticle(new DustParticleOptions(inner ? core : rgb, 0.7f + 1.6f * f * (inner ? 1.2f : 1f)), at.x + d.x, at.y + d.y, at.z + d.z, 0, 0, 0);
            }
            if (now % 2 == 0) {                                                   // streaks drawn in from around
                for (int i = 0; i < 1 + (int) (f * 4); i++) {
                    Vec3 from = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.6, rnd.nextGaussian()).normalize().scale(1.5 + rad * 2.5);
                    Vec3 v = from.scale(-0.18);
                    mc.level.addParticle(new DustParticleOptions(rgb, 0.6f + f), at.x + from.x, at.y + from.y, at.z + from.z, v.x, v.y, v.z);
                }
            }
            if (f > 0.6f && rnd.nextFloat() < f * 0.6f) {                        // crackling near full
                Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize().scale(rad * 1.2);
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + d.x, at.y + d.y, at.z + d.z, d.x * 0.2, d.y * 0.2, d.z * 0.2);
            }
        }
    }
}
