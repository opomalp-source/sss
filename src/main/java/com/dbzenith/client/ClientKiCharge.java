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
    public record Charge(int kind, int color, float fraction, float power, int maxTicks, long heardAt, String technique) {}

    /** The technique each fighter charged last, and when (so the beam that follows fires in its own pose, CX-49). */
    private static final Map<Integer, String> LAST = new HashMap<>();
    private static final Map<Integer, Long> LAST_AT = new HashMap<>();

    private static final Map<Integer, Charge> CHARGES = new HashMap<>();

    private ClientKiCharge() {}

    public static void set(KiChargePackets.State m) {
        if (m.fraction() < 0) {
            Charge was = CHARGES.remove(m.entityId());
            Minecraft mc = Minecraft.getInstance();
            if (was != null && mc.player != null && m.entityId() == mc.player.getId()) {   // your own let go (phase 7): the view rushes out, a jolt
                float f = shownFraction(was, 0);
                if (f > 0.3f) {
                    com.dbzenith.client.fx.CameraFx.rush();
                    com.dbzenith.client.fx.CameraFx.shake(0.15f + 0.35f * f);
                    com.dbzenith.client.fx.CameraFx.aberrate(0.2f + 0.5f * f);                // phase 9
                }
            }
            return;
        }
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        CHARGES.put(m.entityId(), new Charge(m.kind(), m.color(), m.fraction(), m.power(), m.maxTicks(), now, m.technique()));
        LAST.put(m.entityId(), m.technique());
        LAST_AT.put(m.entityId(), now);
    }

    /** The technique this fighter was charging in the last couple of seconds, or "". */
    public static String lastTechnique(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Long at = LAST_AT.get(entityId);
        if (mc.level == null || at == null || mc.level.getGameTime() - at > 40) return "";
        return LAST.getOrDefault(entityId, "");
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

    /** Where this charge is held: a signature technique's own place (CX-49), else by its kind. */
    public static Vec3 hold(Entity e, Vec3 pos, Charge c, float f) {
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRot : e.getYRot();
        double r = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(r), 0, Math.cos(r)), right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        float scale = e.getBbHeight() / 1.8f;
        return switch (c.technique() == null ? "" : c.technique()) {
            case "crimson_beam" -> pos.add(0, 1.3 * scale, 0).add(right.scale(1.05 * scale)).add(forward.scale(-0.25 * scale));   // out to the right side
            case "twin_wave" -> pos.add(0, 2.05 * scale, 0).add(forward.scale(0.3 * scale));                                   // above the forehead
            case "finger_beam" -> pos.add(0, 1.38 * scale, 0).add(forward.scale(0.85 * scale)).add(right.scale(0.28 * scale));   // the fingertip
            default -> hold(e, pos, c.kind(), f);
        };
    }

    /** The same from a given position of the figure (interpolated, when drawing). */
    public static Vec3 hold(Entity e, Vec3 pos, int kind, float f) {
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRot : e.getYRot();
        double r = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(r), 0, Math.cos(r)), right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        float scale = e.getBbHeight() / 1.8f;
        return switch (kind) {
            case KiCharge.KIND_BEAM -> pos.add(0, 0.88 * scale, 0).add(right.scale(0.52 * scale)).add(forward.scale(0.12 * scale));
            case KiCharge.KIND_OVERHEAD -> pos.add(0, (2.15 + radius(kind, f)) * scale, 0);
            default -> pos.add(0, 1.2 * scale, 0).add(forward.scale(0.75 * scale));
        };
    }

    public static float radius(int kind, float f) {
        return switch (kind) {
            case KiCharge.KIND_OVERHEAD -> 0.25f + 1.6f * f;
            case KiCharge.KIND_BEAM -> 0.2f + 0.6f * f;
            default -> 0.12f + 0.55f * f;
        };
    }


    private static final net.minecraft.resources.ResourceLocation GLOW = new net.minecraft.resources.ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png"),
            STAR = new net.minecraft.resources.ResourceLocation(DBZenith.MOD_ID, "textures/entity/impact_star.png");
    private static final Vector3f LEFT = new Vector3f(), UP = new Vector3f();
    /** How far each fighter's charge has cracked the ground (0 none, 1 at half, 2 near full). */
    private static final Map<Integer, Integer> CRACKED = new HashMap<>();
    /** Each fighter's charge hum. */
    private static final Map<Integer, Hum> HUMS = new HashMap<>();

    /**
     * The orb (the ki attack rework, phase 2): with the energy shader, a real sphere in layers (a shell glowing at its
     * rim, crackling strands, a white-hot core) in a halo, growing with the charge, arcs of ki snapping round it past
     * half; on the ground below, a glow of its colour rising with it. Without the shader, the old soft glow and flare.
     */
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
        boolean shader = com.dbzenith.client.fx.KiEnergy.available();
        LEFT.set(event.getCamera().getLeftVector());
        UP.set(event.getCamera().getUpVector());
        var m = pose.last().pose();
        var n = pose.last().normal();
        for (Map.Entry<Integer, Charge> en : CHARGES.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) continue;
            Charge c = en.getValue();
            float f = shownFraction(c, pt);
            Vec3 at = hold(e, e.getPosition(pt), c, f);
            float r = radius(c.kind(), f) * e.getBbHeight() / 1.8f;
            boolean firstPerson = e == mc.player && mc.options.getCameraType().isFirstPerson();
            if (firstPerson) {                                                   // your own, seen from the eyes: smaller, a little lower
                r *= 0.45f;
                at = at.add(0, -0.15, 0);
            }
            int hot = com.dbzenith.client.fx.FxDraw.mix(c.color(), 0xFFFFFF, 0.6f);
            float pulse = 1f + (0.06f + 0.08f * f) * Mth.sin(t * (0.6f + f));
            float x = (float) (at.x - cam.x), y = (float) (at.y - cam.y), z = (float) (at.z - cam.z);
            if (!shader) {
                com.dbzenith.client.fx.FxDraw.billboard(pose, buffers.getBuffer(com.dbzenith.client.fx.FxRenderTypes.soft(GLOW)), rot, x, y, z, r * 3.2f * pulse, 0, 0, 0, 1, 1, c.color(), 200);
                com.dbzenith.client.fx.FxDraw.billboard(pose, buffers.getBuffer(com.dbzenith.client.fx.FxRenderTypes.additive(STAR)), rot, x, y, z, r * 2.8f, t * 0.3f, 0, 0, 1, 1, hot, (int) (80 + 120 * f));
                com.dbzenith.client.fx.FxDraw.billboard(pose, buffers.getBuffer(com.dbzenith.client.fx.FxRenderTypes.additive(GLOW)), rot, x, y, z, r * 1.6f, 0, 0, 0, 1, 1, 0xFFFFFF, 255);
                continue;
            }
            if (c.kind() == KiCharge.KIND_OVERHEAD && !firstPerson) {           // a big sphere gathering overhead (phase 4): drawn as it will fly
                com.dbzenith.skill.Technique tech = com.dbzenith.skill.Techniques.byId(c.technique());
                com.dbzenith.skill.KiLook look = tech == null ? new com.dbzenith.skill.KiLook() : tech.look();
                int style = Math.max(com.dbzenith.client.fx.BigSphere.PLAIN, com.dbzenith.client.fx.BigSphere.style(look, 2.5f));
                int detail = com.dbzenith.client.fx.KiParticles.quality() >= 2 ? 2 : 1;
                com.dbzenith.client.fx.BigSphere.draw(buffers, m, n, x, y, z, r * 0.62f, c.color(), look.core, style, t, 0, 0, 0, LEFT, UP,
                        detail, look.glow, true, en.getKey());
                Vec3 ground = groundBelow(e, 4);
                if (ground != null)
                    com.dbzenith.client.fx.BigSphere.groundGlow(pose, buffers, ground.subtract(cam), r * 0.62f, (float) (at.y - ground.y), c.color(), look.light * (0.4f + 0.6f * f));
                continue;
            }
            var flat = buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.flat());
            com.dbzenith.client.fx.KiEnergy.billboard(flat, m, n, x, y, z, LEFT, UP, r * 2.2f * pulse, com.dbzenith.client.fx.KiEnergy.argb(c.color(), (int) (45 + 45 * f)), com.dbzenith.client.fx.KiEnergy.HALO);
            if (!firstPerson && f > 0.5f) {                                      // arcs of ki snapping round it
                java.util.Random rnd = new java.util.Random(en.getKey() * 31L + com.dbzenith.client.fx.CameraFx.strike(t, 2));
                int arcs = 1 + (int) ((f - 0.5f) * 6);
                for (int a = 0; a < arcs; a++) {
                    if (rnd.nextFloat() < 0.35f) continue;
                    float px = x, py = y, pz = z;
                    double ux = rnd.nextGaussian(), uy = rnd.nextGaussian(), uz = rnd.nextGaussian(), ul = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6;
                    for (int s = 1; s <= 4; s++) {
                        float reach = r * (1f + 0.35f * s);
                        float qx = x + (float) (ux / ul * reach + (rnd.nextFloat() - 0.5f) * r * 0.5f);
                        float qy = y + (float) (uy / ul * reach + (rnd.nextFloat() - 0.5f) * r * 0.5f);
                        float qz = z + (float) (uz / ul * reach + (rnd.nextFloat() - 0.5f) * r * 0.5f);
                        com.dbzenith.client.fx.KiEnergy.ribbon(flat, m, n, px, py, pz, qx, qy, qz, 0, 0, 0, r * 0.18f, r * 0.12f, hot, 230, 200, 0, 1, com.dbzenith.client.fx.KiEnergy.RIBBON);
                        px = qx; py = qy; pz = qz;
                    }
                }
            }
            buffers.endBatch(com.dbzenith.client.fx.KiEnergy.flat());
            var solid = buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.solid());
            float rr = r * 0.62f * pulse;
            int detail = com.dbzenith.client.fx.KiParticles.quality() >= 2 ? 2 : 1;
            com.dbzenith.client.fx.KiEnergy.sphere(buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.tint()), m, n, x, y, z, rr * 0.95f,   // its colour against a bright sky
                    com.dbzenith.client.fx.KiEnergy.argb(c.color(), 120), com.dbzenith.client.fx.KiEnergy.TINT, 1, 0);
            buffers.endBatch(com.dbzenith.client.fx.KiEnergy.tint());
            solid = buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.solid());
            com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, x, y, z, rr, com.dbzenith.client.fx.KiEnergy.argb(c.color(), 150), com.dbzenith.client.fx.KiEnergy.SHELL, detail, t * 0.03f);
            com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, x, y, z, rr * 1.07f, com.dbzenith.client.fx.KiEnergy.argb(c.color(), (int) (60 + 90 * f)), com.dbzenith.client.fx.KiEnergy.STRANDS, detail, -t * 0.05f);
            com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, x, y, z, rr * 0.5f, com.dbzenith.client.fx.KiEnergy.argb(hot, 210), com.dbzenith.client.fx.KiEnergy.CORE, 1, t * 0.02f);
            buffers.endBatch(com.dbzenith.client.fx.KiEnergy.solid());
            Vec3 ground = groundBelow(e, 4);                                    // its light on the ground, rising with it
            if (ground != null && !firstPerson) {
                var glowType = com.dbzenith.client.fx.FxRenderTypes.additive(GLOW);
                com.dbzenith.client.fx.FxDraw.plane(pose, buffers.getBuffer(glowType), ground.subtract(cam).add(0, 0.03, 0), new Vec3(0, 1, 0), 1.2f + 3.2f * f,
                        0, c.color(), (int) ((35 + 85 * f) * Math.max(0.2, 1 - (at.y - ground.y) / 6)), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                buffers.endBatch(glowType);
            }
        }
        buffers.endBatch();
    }

    private static Vec3 groundBelow(Entity e, int depth) {
        var hit = e.level().clip(new net.minecraft.world.level.ClipContext(e.position().add(0, 0.3, 0), e.position().add(0, -depth, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, e));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null : hit.getLocation();
    }

    /**
     * Each charge tick (the ki attack rework, phase 2): motes and streaks drawn in toward the orb from all round (more as
     * it grows), sparks crackling off it near full, the ground cracking under the fighter at half and near full, and the
     * hum, which rises in pitch and loudness with the charge.
     */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CHARGES.clear();
            CRACKED.clear();
            HUMS.clear();
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
            Hum hum = HUMS.get(en.getKey());
            if (e == mc.player) com.dbzenith.client.fx.CameraFx.focus(f);       // your own charge draws the view in a little (phase 7)
            if (hum == null || hum.isStopped()) {
                hum = new Hum(e);
                HUMS.put(en.getKey(), hum);
                mc.getSoundManager().play(hum);
            }
            hum.fraction = f;
            int stage = f > 0.85f ? 2 : f > 0.5f ? 1 : 0;                       // the ground gives way under it
            if (stage > CRACKED.getOrDefault(en.getKey(), 0) && e.onGround()) {
                CRACKED.put(en.getKey(), stage);
                com.dbzenith.client.fx.ImpactFx.cracks(e.position().add(0, 0.02, 0), stage == 1 ? 1.6f : 3.2f);
                com.dbzenith.client.fx.CameraFx.shakeAt(e.position(), 0.1f * stage, 20);
            }
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;   // your own sparks would fill the screen
            Vec3 at = hold(e, e.position(), c, f);
            float rad = radius(c.kind(), f) * e.getBbHeight() / 1.8f;
            if (c.kind() == KiCharge.KIND_OVERHEAD && f > 0.25f && com.dbzenith.client.fx.KiEnergy.available())   // the ground answers a big sphere overhead (phase 4)
                com.dbzenith.client.fx.BigSphere.tick(mc.level, at, rad * 0.62f, c.color(), f > 0.6f);
            int pull = com.dbzenith.client.fx.KiParticles.scaled(2 + (int) (f * 6));
            for (int i = 0; i < pull; i++) {                                    // drawn in from all round, arriving as they fade
                double ux = rnd.nextGaussian(), uy = rnd.nextGaussian() * 0.7, uz = rnd.nextGaussian(), ul = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6;
                double d = 1.6 + rad * 2.5 + rnd.nextDouble() * 1.5;
                int life = 10 + rnd.nextInt(6);
                double sx = at.x + ux / ul * d, sy = at.y + uy / ul * d, sz = at.z + uz / ul * d;
                boolean streak = rnd.nextInt(3) == 0;
                com.dbzenith.client.fx.KiParticles.add(streak ? com.dbzenith.client.fx.KiParticles.SPARK : com.dbzenith.client.fx.KiParticles.MOTE,
                        sx, sy, sz, (at.x - sx) / life, (at.y - sy) / life, (at.z - sz) / life, streak ? 0.5f + f : 0.12f + 0.25f * f,
                        com.dbzenith.client.fx.FxDraw.mix(c.color(), 0xFFFFFF, rnd.nextFloat() * 0.5f), life, 1f, 0f);
            }
            if (f > 0.6f && rnd.nextFloat() < f * 0.7f) {                       // crackling near full
                double ux = rnd.nextGaussian(), uy = rnd.nextGaussian(), uz = rnd.nextGaussian(), ul = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6;
                com.dbzenith.client.fx.KiParticles.add(com.dbzenith.client.fx.KiParticles.SPARK, at.x + ux / ul * rad, at.y + uy / ul * rad, at.z + uz / ul * rad,
                        ux / ul * 0.2, uy / ul * 0.2, uz / ul * 0.2, 0.6f, 0xFFFFFF, 4 + rnd.nextInt(4), 0.82f, 0);
            }
        }
        for (Map.Entry<Integer, Hum> h : HUMS.entrySet()) if (!CHARGES.containsKey(h.getKey())) h.getValue().fading = true;
        HUMS.entrySet().removeIf(h -> h.getValue().isStopped());
        CRACKED.keySet().removeIf(id -> !CHARGES.containsKey(id));
    }

    /** Planted while charging something big: no jumping either (the server stops you walking). */
    @SubscribeEvent
    public static void onMovement(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (KiCharge.planted(event.getEntity())) event.getInput().jumping = false;
    }

    /** The charge's hum: rising in pitch and loudness as it grows, fading out when it ends. */
    static final class Hum extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        final Entity who;
        float fraction, level;
        boolean fading;

        Hum(Entity who) {
            super(com.dbzenith.registry.ModSounds.KI_CHARGE.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                    net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.who = who;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = who.getX();
            this.y = who.getY();
            this.z = who.getZ();
        }

        @Override
        public void tick() {
            if (who.isRemoved()) {
                stop();
                return;
            }
            x = who.getX();
            y = who.getY() + 1;
            z = who.getZ();
            float want = fading ? 0 : 0.25f + 0.85f * fraction;
            level += (want - level) * (fading ? 0.25f : 0.15f);
            if (fading && level < 0.02f) {
                stop();
                return;
            }
            volume = Math.max(0.01f, level);
            pitch = 0.6f + 0.95f * fraction;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
