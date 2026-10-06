package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.skill.KiBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * VFX v3 (CX-11): vanishes (anyone who jumps a few blocks in a tick, a chase, a counter, Instant Transmission, leaves a
 * flickering ghost of static bars where they stood, a streak to where they went and a ring as they appear), beams that
 * scorch furrows into the ground beneath them, and anime speed lines at the edges of the screen when you fly flat out
 * or dash.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VfxV3 {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final ResourceLocation STREAK = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fx_streak.png");
    private static final ResourceLocation RING = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/shock_ring.png");

    private record Vanish(Vec3 from, Vec3 to, int color, int born) {}

    private static final List<Vanish> VANISHES = new ArrayList<>();
    private static final Map<Integer, Vec3> LAST = new HashMap<>();
    private static final Map<Integer, Vec3> SCORCHED = new HashMap<>();   // per beam: where it last scorched
    private static int ticks;
    private static float speedLines;                                        // eased 0..1 for the local player
    /** Dev automation: speed lines forced on for a screenshot. */
    public static boolean devSpeedLines;

    private VfxV3() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            VANISHES.clear();
            LAST.clear();
            SCORCHED.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        for (Player p : mc.level.players()) {                                 // vanishes: a jump of more than a few blocks in one tick
            Vec3 now = p.position(), before = LAST.put(p.getId(), now);
            if (before == null || p.isPassenger() || p.tickCount < 5) continue;
            double d = now.distanceTo(before);
            if (d > 2.5 && d < 96) {
                PublicStatePacket s = ClientPublicStates.get(p.getId());
                VANISHES.add(new Vanish(before, now, s == null ? 0xD9F2FF : s.auraColor(), ticks));
                if (VANISHES.size() > 32) VANISHES.remove(0);
            }
        }
        if (LAST.size() > 256) LAST.clear();
        VANISHES.removeIf(v -> ticks - v.born > 18);

        for (Entity e : mc.level.entitiesForRendering()) {                    // beams scorching the ground beneath them
            if (!(e instanceof KiBeamEntity beam) || beam.getLength() < 2) continue;
            scorch(mc.level, beam);
        }
        if (SCORCHED.size() > 64) SCORCHED.clear();

        float want = 0;
        if (mc.player != null && mc.options.getCameraType().isFirstPerson()) {
            PublicStatePacket s = ClientPublicStates.get(mc.player.getId());
            double speed = mc.player.getDeltaMovement().length();
            if (s != null && s.has(PublicStatePacket.FLYING)) want = (float) Mth.clamp((speed - 0.7) / 0.8, 0, 1);
            if (speed > 1.2) want = Math.max(want, (float) Mth.clamp((speed - 1.2) / 1.0, 0, 1));   // a dash
        }
        if (devSpeedLines) want = 1;
        speedLines += (want - speedLines) * 0.25f;
    }

    /** Lays a scorch mark where the beam passes low over the ground, every block and a half along it. */
    private static void scorch(ClientLevel level, KiBeamEntity beam) {
        Vec3 start = beam.position(), dir = beam.direction();
        float len = beam.getLength();
        Vec3 last = SCORCHED.get(beam.getId());
        for (float s = 1.5f; s < len; s += 1.5f) {
            Vec3 p = start.add(dir.scale(s));
            if (last != null && last.distanceToSqr(p) < 1.2 * 1.2) continue;
            BlockPos.MutableBlockPos at = BlockPos.containing(p).mutable();
            for (int k = 0; k < 3; k++, at.move(0, -1, 0)) {
                BlockState b = level.getBlockState(at);
                if (b.isAir()) continue;
                if (!b.getCollisionShape(level, at).isEmpty()) {
                    Vec3 ground = new Vec3(p.x, at.getY() + b.getCollisionShape(level, at).max(net.minecraft.core.Direction.Axis.Y), p.z);
                    if (p.y - ground.y < 1.8) {
                        ImpactFx.scorch(ground, 0.6f + beam.getWidth() * 0.6f);
                        SCORCHED.put(beam.getId(), p);
                        last = p;
                    }
                }
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || VANISHES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        RenderType streakType = FxRenderTypes.additive(STREAK);
        VertexConsumer streaks = buffers.getBuffer(streakType);
        for (Vanish v : VANISHES) {
            float age = ticks - v.born + partial, fade = Mth.clamp(1 - age / 18f, 0, 1);
            int core = FxDraw.mix(v.color, 0xFFFFFF, 0.6f);
            // the ghost where they stood: flickering bars of static the height of a body, thinning out
            RandomSource rnd = RandomSource.create(v.born * 31L + (long) (age * 2));
            org.joml.Vector3f left = camera.getLeftVector();
            Vec3 side = new Vec3(left.x(), left.y(), left.z());
            for (int i = 0; i < 14; i++) {
                if (rnd.nextFloat() > 0.35f + 0.65f * fade) continue;
                double y = v.from.y + 0.1 + i * 0.13;
                double half = (0.32 + 0.25 * rnd.nextFloat()) * (0.6 + 0.4 * fade);
                Vec3 c = v.from.add(side.scale((rnd.nextFloat() - 0.5) * 0.3)).add(0, y - v.from.y, 0);
                FxDraw.ribbon(pose, streaks, c.subtract(side.scale(half)), c.add(side.scale(half)), cam, 0.12f, core, (int) (255 * fade));
            }
            // the streak to where they went
            Vec3 a = v.from.add(0, 1.0, 0), b = v.to.add(0, 1.0, 0);
            FxDraw.ribbon(pose, streaks, a, b, cam, 0.45f * fade, v.color, (int) (160 * fade));
            FxDraw.ribbon(pose, streaks, a, b, cam, 0.12f * fade, 0xFFFFFF, (int) (230 * fade));
        }
        buffers.endBatch(streakType);
        RenderType ghostType = FxRenderTypes.soft(GLOW);                       // a pale shape where the body stood, visible in daylight
        VertexConsumer ghosts = buffers.getBuffer(ghostType);
        for (Vanish v : VANISHES) {
            float fade = Mth.clamp(1 - (ticks - v.born + partial) / 18f, 0, 1);
            for (int k = 0; k < 3; k++) {
                FxDraw.billboard(pose, ghosts, camera.rotation(), v.from.x, v.from.y + 0.45 + k * 0.5, v.from.z, 1.3f,
                        0, 0, 0, 1, 1, FxDraw.mix(v.color, 0xFFFFFF, 0.7f), (int) (170 * fade));
            }
        }
        buffers.endBatch(ghostType);
        RenderType ringType = FxRenderTypes.additive(RING);
        VertexConsumer rings = buffers.getBuffer(ringType);
        for (Vanish v : VANISHES) {                                            // a ring as they appear
            float t = Mth.clamp((ticks - v.born + partial) / 7f, 0, 1);
            FxDraw.plane(pose, rings, v.to.add(0, 1.0, 0), cam.subtract(v.to.add(0, 1.0, 0)), 0.4f + 1.6f * t, 0,
                    FxDraw.mix(v.color, 0xFFFFFF, 0.5f), (int) (220 * (1 - t)), LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch(ringType);
        pose.popPose();
    }

    /** Speed lines: thin white streaks raking in from the edges of the screen, re-drawn every tick. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (speedLines < 0.03f || !com.dbzenith.config.DBZConfig.CLIENT.speedLines.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        float cx = w / 2f, cy = h / 2f, reach = (float) Math.hypot(cx, cy);
        RandomSource rnd = RandomSource.create(ticks * 7919L);
        for (int i = 0; i < 46; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float outer = reach * 1.05f, inner = reach * (0.55f + 0.3f * rnd.nextFloat() + 0.25f * (1 - speedLines));
            float width = 0.8f + 2.2f * rnd.nextFloat();
            float ca = Mth.cos(a), sa = Mth.sin(a), px = -sa * width, py = ca * width;
            int alpha = (int) ((110 + 120 * rnd.nextFloat()) * speedLines);
            int col = DbzTheme.withAlpha(0xFFFFFF, alpha), clear = DbzTheme.withAlpha(0xFFFFFF, 0);
            DbzTheme.quad(event.getGuiGraphics(), cx + ca * inner, cy + sa * inner, cx + ca * outer + px, cy + sa * outer + py,
                    cx + ca * outer - px, cy + sa * outer - py, cx + ca * inner, cy + sa * inner, clear, col, col, clear);
        }
    }
}
