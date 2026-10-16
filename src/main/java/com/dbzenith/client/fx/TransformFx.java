package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Transformation cinematics (CX-43). While a fighter powers up into a form: cracks spread through the ground from their
 * feet, glowing in the new aura's colour and pulsing; lightning arcs flicker round the body; the ground trembles for
 * those nearby. When the form bursts out the cracks stay behind (the burst itself is {@link ImpactFx#transformBurst}).
 * Your own transformation, in third person, swings the camera round to your front on a slow low orbit, holds on the
 * burst and eases back (client config {@code transformCinematic}).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TransformFx {
    private static final ResourceLocation CRACKS = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ground_cracks.png");
    private static final ResourceLocation CRACKS_GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ground_cracks_glow.png");
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final Method MOVE = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90568_", double.class, double.class, double.class);
    /** Ticks for the cracks to reach full size, and their full radius. */
    private static final float GROW = 70, RADIUS = 4.2f;
    /** The camera: how far it circles round (degrees), how far from you, how long it holds on the burst. */
    private static final float ORBIT = 165, DISTANCE = 3.8f;
    private static final int HOLD = 26;

    private static final class Track {
        boolean was;
        int start;
        String formBefore = "";
        Vec3 ground;
        int color = 0xFFFFFF;
        float progress;
        int ended = -100;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static int ticks;
    private static float camW, camWO, orbit, orbitO, startYaw = Float.NaN;
    private static int hold;

    private TransformFx() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            TRACKS.clear();
            camW = camWO = orbit = orbitO = 0;
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        for (AbstractClientPlayer p : mc.level.players()) {
            PublicStatePacket s = ClientPublicStates.get(p.getId());
            boolean on = s != null && s.has(PublicStatePacket.TRANSFORMING) && !p.isInvisible();
            Track t = TRACKS.computeIfAbsent(p.getId(), k -> new Track());
            if (on && !t.was) {
                t.start = ticks;
                t.formBefore = s.form();
            }
            if (on) {
                t.progress = Math.min(1, (ticks - t.start) / GROW);
                t.ground = ground(p);
                t.color = Forms.exists(s.transformTarget()) ? Forms.byId(s.transformTarget()).auraColor() : s.auraColor();
                if (t.ground != null) CameraFx.shakeAt(t.ground, 0.012f + 0.03f * t.progress, 28);   // the ground trembles
            } else if (t.was) {
                t.ended = ticks;
                boolean burst = s != null && !s.form().equals(t.formBefore);
                if (burst && t.ground != null) ImpactFx.cracks(t.ground, RADIUS * (0.6f + 0.4f * t.progress));   // they stay behind
                if (burst && p == mc.player) hold = HOLD;
            }
            t.was = on;
        }
        TRACKS.keySet().removeIf(id -> mc.level.getEntity(id) == null);

        // the camera
        Track mine = TRACKS.get(mc.player.getId());
        boolean transforming = mine != null && mine.was;
        boolean want = (transforming || hold > 0) && DBZConfig.CLIENT.transformCinematic.get() && !mc.options.getCameraType().isFirstPerson();
        camWO = camW;
        camW += ((want ? 1 : 0) - camW) * (want ? 0.07f : 0.1f);
        orbitO = orbit;
        if (transforming) orbit = Math.min(1, orbit + 1 / 75f);
        else if (hold > 0) orbit = Math.min(1, orbit + 1 / 22f);                 // a short power-up: finish the swing on the burst
        else if (camW < 0.01f) {
            orbit = orbitO = 0;
            startYaw = Float.NaN;
        }
        if (hold > 0) hold--;
    }

    /** The ground under a fighter (within four blocks), or null in the air. */
    private static Vec3 ground(AbstractClientPlayer p) {
        Vec3 from = p.position().add(0, 0.2, 0);
        BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation().add(0, 0.024, 0);
    }

    // ------------------------------------------------------------------ the camera

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();
        float w = Mth.lerp(partial, camWO, camW);
        Minecraft mc = Minecraft.getInstance();
        Camera cam = event.getCamera();
        if (w < 0.005f || mc.player == null || mc.level == null || !cam.isDetached()) return;
        w = w * w * (3 - 2 * w);
        if (Float.isNaN(startYaw)) startYaw = event.getYaw();
        float o = Mth.lerp(partial, orbitO, orbit);
        o = o * o * (3 - 2 * o);
        float yaw = startYaw + ORBIT * o, pitch = -6 - 6 * o;                // round to the front, a little from below
        Vec3 focus = mc.player.getPosition(partial).add(0, mc.player.getEyeHeight(), 0);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 want = focus.subtract(look.scale(DISTANCE));
        BlockHitResult hit = mc.level.clip(new ClipContext(focus, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) want = focus.add(hit.getLocation().subtract(focus).scale(0.9));
        Vec3 delta = want.subtract(cam.getPosition()).scale(w);
        Vec3 f = new Vec3(cam.getLookVector()), u = new Vec3(cam.getUpVector()), l = new Vec3(cam.getLeftVector());
        try {
            MOVE.invoke(cam, delta.dot(f), delta.dot(u), delta.dot(l));       // move(forward, up, left) in the camera's own axes
        } catch (ReflectiveOperationException ignored) {
            return;
        }
        event.setYaw(event.getYaw() + Mth.wrapDegrees(yaw - event.getYaw()) * w);
        event.setPitch(Mth.lerp(w, event.getPitch(), pitch));
    }

    // ------------------------------------------------------------------ cracks and lightning

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || TRACKS.isEmpty()) return;
        boolean any = false;
        for (Track t : TRACKS.values()) any |= t.was || ticks - t.ended < 24;
        if (!any) return;
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Vec3 up = new Vec3(0, 1, 0);

        // the cracks: dark, spreading, then their glow pulsing in the new aura's colour (one buffer at a time)
        RenderType dark = RenderType.entityTranslucent(CRACKS);
        VertexConsumer vc = buffers.getBuffer(dark);
        for (Map.Entry<Integer, Track> e : TRACKS.entrySet()) {
            Track t = e.getValue();
            if (!t.was || t.ground == null) continue;
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(t.ground.x, t.ground.y + 0.3, t.ground.z));
            FxDraw.plane(pose, vc, t.ground, up, radius(t, partial), e.getKey() * 1.7f, 0xFFFFFF, 235, light);
        }
        buffers.endBatch(dark);
        RenderType glowType = FxRenderTypes.additive(CRACKS_GLOW);
        vc = buffers.getBuffer(glowType);
        for (Map.Entry<Integer, Track> e : TRACKS.entrySet()) {
            Track t = e.getValue();
            if (!t.was || t.ground == null) continue;
            float pulse = 0.65f + 0.35f * Mth.sin((ticks + partial) * 0.45f + e.getKey());
            FxDraw.plane(pose, vc, t.ground.add(0, 0.004, 0), up, radius(t, partial), e.getKey() * 1.7f,
                    FxDraw.mix(t.color, 0xFFFFFF, 0.3f), (int) (210 * pulse * Math.min(1, t.progress * 3)), LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch(glowType);

        // lightning arcing round the body, re-struck every other tick
        RenderType boltType = FxRenderTypes.additive(GLOW);
        vc = buffers.getBuffer(boltType);
        for (Map.Entry<Integer, Track> e : TRACKS.entrySet()) {
            Track t = e.getValue();
            float fade = t.was ? 1 : 1 - (ticks - t.ended + partial) / 24f;     // still crackling a moment after the burst
            if (fade <= 0 || !(mc.level.getEntity(e.getKey()) instanceof AbstractClientPlayer p)) continue;
            Vec3 at = p.getPosition(partial);
            float h = p.getBbHeight();
            Random r = new Random(e.getKey() * 7919L + ticks / 2);
            int bolts = 2 + (int) (t.progress * 3);
            for (int b = 0; b < bolts; b++) {
                if (r.nextFloat() < 0.25f) continue;                              // a flicker: not every bolt every time
                double a0 = r.nextDouble() * Math.PI * 2, a1 = a0 + (r.nextDouble() - 0.5) * 2.4;
                double r0 = 0.55 + r.nextDouble() * 0.6, r1 = 0.7 + r.nextDouble() * 0.9;
                Vec3 from = at.add(Math.cos(a0) * r0, h * (0.1 + r.nextDouble() * 0.85), Math.sin(a0) * r0);
                Vec3 to = at.add(Math.cos(a1) * r1, h * (0.1 + r.nextDouble() * 0.95), Math.sin(a1) * r1);
                Vec3 prev = from;
                int segs = 5;
                for (int i = 1; i <= segs; i++) {
                    Vec3 next = i == segs ? to : from.lerp(to, i / (double) segs).add((r.nextDouble() - 0.5) * 0.35, (r.nextDouble() - 0.5) * 0.35, (r.nextDouble() - 0.5) * 0.35);
                    strip(pose, vc, prev, next, cam, 0.3f, FxDraw.mix(t.color, 0xFFFFFF, 0.35f), (int) (220 * fade));
                    strip(pose, vc, prev, next, cam, 0.09f, 0xFFFFFF, (int) (255 * fade));
                    prev = next;
                }
            }
        }
        buffers.endBatch(boltType);
        pose.popPose();
    }

    private static float radius(Track t, float partial) {
        float p = Math.min(1, t.progress + partial / GROW);
        return 0.8f + (RADIUS - 0.8f) * (1 - (1 - p) * (1 - p));
    }

    /** A strip from a to b facing the eye, soft across its width (the glow's middle row). */
    private static void strip(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 eye, float width, int rgb, int alpha) {
        Vec3 side = b.subtract(a).cross(eye.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width / 2);
        var m = pose.last().pose();
        var n = pose.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        FxDraw.vertex(vc, m, n, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), 0, 0.5f, rgb, alpha, light);
        FxDraw.vertex(vc, m, n, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), 1, 0.5f, rgb, alpha, light);
        FxDraw.vertex(vc, m, n, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), 1, 0.5f, rgb, alpha, light);
        FxDraw.vertex(vc, m, n, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), 0, 0.5f, rgb, alpha, light);
    }
}
