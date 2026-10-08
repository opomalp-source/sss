package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.LockOnPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lock-on (CX-19 phase 6). The Lock-on key (N) locks onto the best foe in view, or lets go; with Shift it moves to the
 * next foe round. While locked:
 * <ul>
 *   <li>the camera keeps the foe framed: each frame the view turns smoothly toward the foe's chest, in first or third
 *       person, on the ground and in flight. The mouse still looks about a little (up to {@code lockOnFreeLook} degrees)
 *       and the view springs back. Movement stays yours, relative to the view, so forward closes in and the sides circle
 *       the foe;</li>
 *   <li>brackets turn round the foe, with its name and distance;</li>
 *   <li>the server knows the target: the super dash goes for it from any angle and ki blasts curve toward it.</li>
 * </ul>
 * The lock lets go when the foe dies or leaves, gets too far, or stays out of sight for three seconds.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class LockOn {
    private static final int LOST_SIGHT_TICKS = 60;
    private static final int HOSTILE = 0xFFFF5A4A, NEUTRAL = 0xFFFFE070;

    private static LivingEntity target;
    private static int unseen;
    private static float baseYaw, basePitch, offYaw, offPitch, appliedYaw, appliedPitch;
    private static boolean fresh;
    private static long lastNanos;
    /** Over-the-shoulder framing in third person: degrees the drawn view turns left and down, and how far it has eased in. */
    private static final float SHOULDER_YAW = 12f, SHOULDER_PITCH = 7f;
    private static float shoulder, renderYaw, renderPitch;
    private static boolean biased;

    private LockOn() {}

    public static LivingEntity target() {
        return target;
    }

    public static boolean active() {
        return target != null;
    }

    // ------------------------------------------------------------------ choosing

    /** The Lock-on key: lock onto the best foe, let go, or ({@code next}) move to the next foe round. */
    public static void keyPressed(boolean next) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (target != null && !next) {
            release(true);
            return;
        }
        LivingEntity pick = next && target != null ? nextAfter(mc, target) : best(mc);
        if (pick != null) lock(pick, true);
        else if (target == null) mc.player.displayClientMessage(Component.translatable("message.dbzenith.lock_none"), true);
    }

    /** From the server: lock onto this (or let go, -1). */
    public static void forceTarget(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (entityId < 0) release(false);
        else if (mc.level.getEntity(entityId) instanceof LivingEntity e) lock(e, false);
    }

    private static void lock(LivingEntity e, boolean tellServer) {
        LocalPlayer p = Minecraft.getInstance().player;
        target = e;
        unseen = 0;
        fresh = true;
        if (p != null) {
            baseYaw = p.getYRot();
            basePitch = p.getXRot();
        }
        if (tellServer) ModNetwork.sendToServer(new LockOnPacket(e.getId()));
        ClientSounds.uiClick();
    }

    private static void release(boolean tellServer) {
        if (target == null) return;
        target = null;
        if (tellServer) ModNetwork.sendToServer(new LockOnPacket(-1));
    }

    private static double range() {
        return DBZConfig.CLIENT.lockOnRange.get();
    }

    private static List<LivingEntity> candidates(Minecraft mc) {
        LocalPlayer p = mc.player;
        double r = range();
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity l) || e == p || !l.isAlive() || e.isSpectator() || e instanceof ArmorStand || e.isInvisible()) continue;
            if (e.distanceToSqr(p) > r * r || !p.hasLineOfSight(e)) continue;
            out.add(l);
        }
        return out;
    }

    /** A foe worth fighting: a player in PvP mode, a fighter, a monster. */
    static boolean hostile(LivingEntity e) {
        if (e instanceof Player) return true;                                // another player is always a foe here (no PvP marks: change request)
        return e instanceof Enemy || e instanceof com.dbzenith.npc.KiFighter;
    }

    /** Nearest the crosshair, then nearest; foes first. Anything in the front half of the view, else the closest about. */
    static LivingEntity best(Minecraft mc) {
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : candidates(mc)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length(), angle = Math.toDegrees(Math.acos(Mth.clamp(to.dot(look) / Math.max(1e-6, dist), -1, 1)));
            if (angle > 90 && dist > 12) continue;
            double score = angle + dist * 0.6 + (hostile(e) ? 0 : 45);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** The next foe round to the right of the current one (wrapping round). */
    static LivingEntity nextAfter(Minecraft mc, LivingEntity current) {
        LocalPlayer p = mc.player;
        List<LivingEntity> all = candidates(mc);
        if (all.size() < 2 && all.contains(current)) return current;
        all.sort(Comparator.comparingDouble(e -> bearing(p, e)));
        double now = bearing(p, current);
        for (LivingEntity e : all) if (e != current && bearing(p, e) > now) return e;
        return all.isEmpty() ? null : all.get(0) == current && all.size() > 1 ? all.get(1) : all.get(0);
    }

    /** Degrees round to the right of the view, 0..360. */
    private static double bearing(LocalPlayer p, LivingEntity e) {
        Vec3 to = e.position().subtract(p.position());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        return (Mth.wrapDegrees(yaw - p.getYRot()) + 360) % 360;
    }

    // ------------------------------------------------------------------ keeping it

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !p.isAlive() || target.level() != mc.level) {
            target = null;
            return;
        }
        if (target.isRemoved() || !target.isAlive() || target.distanceToSqr(p) > Math.pow(range() * 1.25, 2)) {
            release(true);
            return;
        }
        unseen = p.hasLineOfSight(target) ? 0 : unseen + 1;
        if (unseen > LOST_SIGHT_TICKS) release(true);
    }

    /**
     * Each frame, before the world is drawn: what the mouse turned since the last frame becomes free look (clamped,
     * springing back), and the view is set to the smoothed aim at the foe plus that free look.
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long nanos = System.nanoTime();
        float dt = Mth.clamp((nanos - lastNanos) / 1e9f, 0f, 0.1f);
        lastNanos = nanos;
        if (target == null || p == null || mc.isPaused()) return;
        double speed = DBZConfig.CLIENT.lockOnCameraSpeed.get();
        if (speed <= 0) return;
        float free = DBZConfig.CLIENT.lockOnFreeLook.get().floatValue();
        if (fresh) {
            fresh = false;
            appliedYaw = p.getYRot();
            appliedPitch = p.getXRot();
            offYaw = offPitch = 0;
        }
        offYaw = Mth.clamp(offYaw + (p.getYRot() - appliedYaw), -free, free);           // the mouse since last frame
        offPitch = Mth.clamp(offPitch + (p.getXRot() - appliedPitch), -free * 0.8f, free * 0.8f);
        float spring = 1 - (float) Math.exp(-dt * 3.0);
        offYaw -= offYaw * spring;
        offPitch -= offPitch * spring;

        float pt = event.renderTickTime;
        Vec3 eye = p.getEyePosition(pt);
        Vec3 at = target.getPosition(pt).add(0, target.getBbHeight() * 0.62, 0);
        Vec3 d = at.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float follow = 1 - (float) Math.exp(-dt * 12.0 * speed);
        if (horiz > 0.6) {
            float aimYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
            baseYaw += Mth.wrapDegrees(aimYaw - baseYaw) * follow;
        }
        float aimPitch = (float) (-Mth.atan2(d.y, Math.max(horiz, 0.3)) * Mth.RAD_TO_DEG);
        basePitch += (aimPitch - basePitch) * follow;

        float yaw = baseYaw + offYaw, pitch = Mth.clamp(basePitch + offPitch, -89.9f, 89.9f);
        appliedYaw = yaw;
        appliedPitch = pitch;
        // third person: over the shoulder. The view turns a little left and down for this frame only, so the camera,
        // which orbits behind, sees the foe up and to the right of you instead of hidden behind your back
        boolean behind = mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK;
        shoulder += ((behind ? 1f : 0f) - shoulder) * (1 - (float) Math.exp(-dt * 6.0));
        float steep = Math.max(0.45f, Mth.cos(pitch * Mth.DEG_TO_RAD));               // looking far up or down, a turn shows less
        renderYaw = yaw - SHOULDER_YAW / steep * shoulder;
        renderPitch = Mth.clamp(pitch + SHOULDER_PITCH * shoulder, -89.9f, 89.9f);
        set(p, renderYaw, renderPitch);
        biased = true;
    }

    /** After the frame: back to the true aim (which the game tick sends to the server), keeping any mouse turn made meanwhile. */
    @SubscribeEvent
    public static void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !biased) return;
        biased = false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        set(p, appliedYaw + (p.getYRot() - renderYaw), Mth.clamp(appliedPitch + (p.getXRot() - renderPitch), -90f, 90f));
    }

    private static void set(LocalPlayer p, float yaw, float pitch) {
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    // ------------------------------------------------------------------ the marker

    /** Four brackets turning round the foe, red for a foe and gold for anyone else, with its name and distance. */
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        float pt = event.getPartialTick();
        Vec3 at = target.getPosition(pt).add(0, target.getBbHeight() * 0.55, 0);
        double dist = c.distanceTo(at);
        if (dist < 0.8) return;
        int color = hostile(target) ? HOSTILE : NEUTRAL;
        float size = Math.max(target.getBbHeight(), target.getBbWidth()) * 0.62f + 0.25f;
        float time = (mc.level.getGameTime() + pt);
        float pulse = 1 + 0.06f * Mth.sin(time * 0.4f);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(at.x - c.x, at.y - c.y, at.z - c.z);
        pose.mulPose(cam.rotation());
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(time * 2.5f));
        brackets(pose.last().pose(), size * pulse, size * 0.38f, Math.max(0.06f, (float) dist * 0.0065f), color);
        pose.popPose();

        Font font = mc.font;
        float s = 0.03f * (float) Math.max(1, dist / 10);
        pose.translate(0, size * pulse + 0.2f + 9 * s, 0);                    // over the top bracket (under it, the ground would hide it)
        pose.scale(-s, -s, s);
        String label = target.getDisplayName().getString() + "  " + Math.round(mc.player.distanceTo(target)) + "m";
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        net.minecraft.util.FormattedCharSequence text = Component.literal(label).getVisualOrderText();
        font.drawInBatch8xOutline(text, -font.width(text) / 2f, 0, color, 0xFF101018, pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        pose.popPose();
    }

    /** Four L-shaped corners of a square {@code half} from the middle, each arm {@code arm} long and {@code w} thick. */
    private static void brackets(Matrix4f m, float half, float arm, float w, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        for (int i = 0; i < 4; i++) {
            float sx = (i & 1) == 0 ? -1 : 1, sy = (i & 2) == 0 ? -1 : 1;
            float x = sx * half, y = sy * half;
            quad(buf, m, x, y, x - sx * arm, y - sy * w, r, g, b);                // along x
            quad(buf, m, x, y, x - sx * w, y - sy * arm, r, g, b);                // along y
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buf, Matrix4f m, float x0, float y0, float x1, float y1, int r, int g, int b) {
        buf.vertex(m, x0, y0, 0).color(r, g, b, 230).endVertex();
        buf.vertex(m, x0, y1, 0).color(r, g, b, 230).endVertex();
        buf.vertex(m, x1, y1, 0).color(r, g, b, 230).endVertex();
        buf.vertex(m, x1, y0, 0).color(r, g, b, 230).endVertex();
    }
}
