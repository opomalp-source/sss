package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.FxDraw;
import com.dbzenith.client.fx.FxRenderTypes;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.Technique;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Ki balls: a saturated shell, a white-hot core, a spinning crackle of light and a tapering comet trail. Disks spin
 * flat with a bright rim. Full-bright.
 */
public class KiBlastRenderer extends EntityRenderer<KiBlastEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final ResourceLocation STAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/impact_star.png");
    private static final ResourceLocation STREAK = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fx_streak.png");
    private static final ResourceLocation RING = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/shock_ring.png");
    private static final ResourceLocation SPIRIT = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/spirit_bomb.png");

    public KiBlastRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(KiBlastEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        int c = entity.getColor();
        int hot = FxDraw.mix(c, 0xFFFFFF, 0.6f);
        float size = entity.getSize();
        float t = entity.tickCount + partialTicks;
        Quaternionf camera = entityRenderDispatcher.cameraOrientation();
        double mid = entity.getBbHeight() / 2.0;

        if (entity.getStyle() == Technique.Style.DISK) {
            pose.pushPose();
            pose.translate(0, mid, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            Vec3 flat = new Vec3(0, 1, 0);
            FxDraw.plane(pose, buffers.getBuffer(FxRenderTypes.soft(GLOW)), Vec3.ZERO, flat, size * 1.1f, t * 0.7f, c, 220, LightTexture.FULL_BRIGHT);
            FxDraw.plane(pose, buffers.getBuffer(FxRenderTypes.additive(RING)), Vec3.ZERO, flat, size * 1.05f, -t * 0.7f, hot, 255, LightTexture.FULL_BRIGHT);
            FxDraw.plane(pose, buffers.getBuffer(FxRenderTypes.additive(GLOW)), Vec3.ZERO, flat, size * 0.55f, 0, 0xFFFFFF, 255, LightTexture.FULL_BRIGHT);
            pose.popPose();
            super.render(entity, yaw, partialTicks, pose, buffers, light);
            return;
        }

        // comet trail, drawn back along the flight path and tapering away
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() > 1e-4 && !com.dbzenith.client.fx.KiEnergy.available()) {   // (with the energy shader, its own smooth trail)
            Vec3 eye = entityRenderDispatcher.camera.getPosition().subtract(
                    Mth.lerp(partialTicks, entity.xo, entity.getX()), Mth.lerp(partialTicks, entity.yo, entity.getY()),
                    Mth.lerp(partialTicks, entity.zo, entity.getZ()));
            Vec3 back = vel.normalize().scale(-Math.min(4.0, vel.length() * 3.5 + size));
            VertexConsumer streak = buffers.getBuffer(FxRenderTypes.additive(STREAK));
            Vec3 start = new Vec3(0, mid, 0);
            int segments = 4;
            for (int i = 0; i < segments; i++) {
                Vec3 a = start.add(back.scale(i / (double) segments));
                Vec3 b = start.add(back.scale((i + 1) / (double) segments));
                float fade = 1 - i / (float) segments;
                FxDraw.ribbon(pose, streak, a, b, eye, size * 1.6f * fade, c, (int) (190 * fade));
                FxDraw.ribbon(pose, streak, a, b, eye, size * 0.55f * fade, 0xFFFFFF, (int) (200 * fade));
            }
        }

        if (com.dbzenith.client.fx.KiEnergy.available()) {                     // the ki attack rework, phase 4: the big spheres
            int big = com.dbzenith.client.fx.BigSphere.style(com.dbzenith.client.fx.KiFx.look(entity), size);
            if (big < 0 && entity.isSpiritBomb()) big = com.dbzenith.client.fx.BigSphere.GATHER;
            if (big >= 0) {
                renderBig(entity, partialTicks, pose, buffers, c, size, t, mid, big);
                super.render(entity, yaw, partialTicks, pose, buffers, light);
                return;
            }
        }
        if (entity.isSpiritBomb()) {                                         // the Spirit Bomb (CX-17a): a solid, bright sphere in a halo
            float breathe = 1f + 0.03f * Mth.sin(t * 0.25f);
            FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.soft(GLOW)), camera, 0, mid, 0, size * 1.9f * breathe, 0, 0, 0, 1, 1, c, 150);
            FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.soft(SPIRIT)), camera, 0, mid, 0, size * 1.02f * breathe, t * 0.01f, 0, 0, 1, 1, 0xFFFFFF, 255);
            FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.additive(STAR)), camera, 0, mid, 0, size * 1.15f, -t * 0.05f, 0, 0, 1, 1, hot, 90);
            super.render(entity, yaw, partialTicks, pose, buffers, light);
            return;
        }
        if (com.dbzenith.client.fx.KiEnergy.available()) {                     // the ki attack rework: a real glowing sphere
            renderEnergy(entity, partialTicks, pose, buffers, c, size, t, mid);
            super.render(entity, yaw, partialTicks, pose, buffers, light);
            return;
        }
        float pulse = 1f + 0.08f * Mth.sin(t * 0.8f);
        FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.soft(GLOW)), camera, 0, mid, 0, size * 2.6f * pulse, 0, 0, 0, 1, 1, c, 210);
        FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.additive(STAR)), camera, 0, mid, 0, size * 2.4f, t * 0.35f, 0, 0, 1, 1, hot, 170);
        FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.additive(GLOW)), camera, 0, mid, 0, size * 1.3f, 0, 0, 0, 1, 1, 0xFFFFFF, 255);
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }


    private static final org.joml.Vector3f LEFT = new org.joml.Vector3f(), UP = new org.joml.Vector3f();

    /**
     * A ki ball through the energy shader: a soft halo, a shell glowing at its rim with plasma boiling over it,
     * crackling strands, a white-hot core and a spinning flare; behind it a smooth tapered trail of where it has been,
     * a white core running down it. Less detail far away and at lower quality.
     */
    private void renderEnergy(KiBlastEntity e, float pt, PoseStack pose, MultiBufferSource buffers, int c, float size, float t, double mid) {
        com.dbzenith.skill.KiLook look = com.dbzenith.client.fx.KiFx.look(e);
        double ex0 = Mth.lerp(pt, e.xo, e.getX()), ey0 = Mth.lerp(pt, e.yo, e.getY()), ez0 = Mth.lerp(pt, e.zo, e.getZ());
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        float ex = (float) (cam.x - ex0), ey = (float) (cam.y - ey0), ez = (float) (cam.z - ez0);
        double dist = Math.sqrt(ex * ex + ey * ey + ez * ez);
        int q = com.dbzenith.client.fx.KiParticles.quality();
        int detail = Math.min(q == 0 ? 0 : q == 1 ? 1 : 2, dist < 16 ? 2 : dist < 48 ? 1 : 0);
        float flick = 1f + (0.05f * Mth.sin(t * 0.9f) + 0.03f * Mth.sin(t * 2.3f)) * look.flicker;
        float r = size * 0.5f * flick;
        float my = (float) mid;
        var last = pose.last();
        org.joml.Matrix4f m = last.pose();
        org.joml.Matrix3f n = last.normal();
        LEFT.set(entityRenderDispatcher.camera.getLeftVector());
        UP.set(entityRenderDispatcher.camera.getUpVector());

        com.dbzenith.client.fx.KiEnergy.sphere(buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.tint()), m, n, 0, my, 0, r * 0.95f,   // its colour against a bright sky
                com.dbzenith.client.fx.KiEnergy.argb(c, 130), com.dbzenith.client.fx.KiEnergy.TINT, Math.max(0, detail - 1), 0);
        // flat things: the trail, the halo
        VertexConsumer flat = buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.flat());
        trail(e, flat, m, n, ex0, ey0, ez0, ex, ey, ez, my, size, c, look, t, 1f);
        com.dbzenith.client.fx.KiEnergy.billboard(flat, m, n, 0, my, 0, LEFT, UP, size * 1.05f * look.glow * flick, com.dbzenith.client.fx.KiEnergy.argb(c, 95), com.dbzenith.client.fx.KiEnergy.HALO);
        com.dbzenith.client.fx.KiEnergy.billboard(flat, m, n, 0, my, 0, LEFT, UP, size * 0.55f * flick, com.dbzenith.client.fx.KiEnergy.argb(FxDraw.mix(c, 0xFFFFFF, 0.4f), 90), com.dbzenith.client.fx.KiEnergy.HALO);

        // the sphere in layers
        VertexConsumer solid = buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.solid());
        float spin = t * 0.02f;
        com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, 0, my, 0, r, com.dbzenith.client.fx.KiEnergy.argb(c, 210), com.dbzenith.client.fx.KiEnergy.SHELL, detail, spin);
        if (detail > 0 && look.plasma > 0)
            com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, 0, my, 0, r * 1.06f, com.dbzenith.client.fx.KiEnergy.argb(c, (int) (220 * Math.min(1, look.plasma))), com.dbzenith.client.fx.KiEnergy.STRANDS, detail, -spin * 1.7f);
        com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, 0, my, 0, r * 0.5f, com.dbzenith.client.fx.KiEnergy.argb(look.core, 255), com.dbzenith.client.fx.KiEnergy.CORE, Math.max(0, detail - 1), spin * 0.5f);

        // a spinning flare of light across it
        VertexConsumer star = buffers.getBuffer(FxRenderTypes.additive(STAR));
        FxDraw.billboard(pose, star, entityRenderDispatcher.cameraOrientation(), 0, mid, 0, size * 1.6f * flick, t * 0.35f, 0, 0, 1, 1, FxDraw.mix(c, 0xFFFFFF, 0.4f), 60);
    }

    /** Its smooth tapered trail of where it has been, a core-coloured line down the middle; {@code wide} scales it. */
    private static void trail(KiBlastEntity e, VertexConsumer flat, org.joml.Matrix4f m, org.joml.Matrix3f n, double ex0, double ey0, double ez0,
                              float ex, float ey, float ez, float my, float size, int c, com.dbzenith.skill.KiLook look, float t, float wide) {
        int pts = e.trailCount;
        if (pts < 2 || look.trail <= 0) return;
        int used = Math.min(pts, Math.max(3, Math.round(12 * look.trail)));
        float px = 0, py = my, pz = 0;                                         // newest: where it is drawn now
        float vNow = t * 0.15f;
        for (int k = 0; k < used - 1; k++) {
            int i = (pts - 2 - k) * 3;
            if (i < 0) break;
            float qx = (float) (e.trail[i] - ex0), qy = (float) (e.trail[i + 1] - ey0), qz = (float) (e.trail[i + 2] - ez0);
            float fa = 1 - k / (float) (used - 1), fb = 1 - (k + 1) / (float) (used - 1);
            com.dbzenith.client.fx.KiEnergy.ribbon(flat, m, n, px, py, pz, qx, qy, qz, ex, ey, ez, size * wide * fa, size * wide * fb,
                    c, (int) (150 * fa), (int) (150 * fb), vNow + k * 0.2f, vNow + (k + 1) * 0.2f, com.dbzenith.client.fx.KiEnergy.RIBBON);
            com.dbzenith.client.fx.KiEnergy.ribbon(flat, m, n, px, py, pz, qx, qy, qz, ex, ey, ez, size * 0.45f * wide * fa, size * 0.45f * wide * fb,
                    look.core, (int) (170 * fa), (int) (170 * fb), vNow + k * 0.2f, vNow + (k + 1) * 0.2f, com.dbzenith.client.fx.KiEnergy.RIBBON);
            px = qx; py = qy; pz = qz;
        }
    }

    /**
     * A big sphere (the ki attack rework, phase 4, {@link com.dbzenith.client.fx.BigSphere}): its trail when it flies,
     * the swirling body, and its light on the ground below. Energy flows into it while it hangs still (gathering).
     */
    private void renderBig(KiBlastEntity e, float pt, PoseStack pose, MultiBufferSource buffers, int c, float size, float t, double mid, int style) {
        com.dbzenith.skill.KiLook look = com.dbzenith.client.fx.KiFx.look(e);
        double ex0 = Mth.lerp(pt, e.xo, e.getX()), ey0 = Mth.lerp(pt, e.yo, e.getY()), ez0 = Mth.lerp(pt, e.zo, e.getZ());
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        float ex = (float) (cam.x - ex0), ey = (float) (cam.y - ey0), ez = (float) (cam.z - ez0);
        double dist = Math.sqrt(ex * ex + ey * ey + ez * ez);
        int q = com.dbzenith.client.fx.KiParticles.quality();
        int detail = Math.min(q == 0 ? 0 : q == 1 ? 1 : 2, dist < 32 ? 2 : dist < 80 ? 1 : 0);
        float my = (float) mid, r = size * 0.5f;
        var last = pose.last();
        LEFT.set(entityRenderDispatcher.camera.getLeftVector());
        UP.set(entityRenderDispatcher.camera.getUpVector());
        boolean still = e.getDeltaMovement().lengthSqr() < 1e-3;
        if (!still) trail(e, buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.flat()), last.pose(), last.normal(), ex0, ey0, ez0, ex, ey, ez, my, size, c, look, t, 0.8f);
        com.dbzenith.client.fx.BigSphere.draw(buffers, last.pose(), last.normal(), 0, my, 0, r, c, look.core, style, t, ex, ey, ez, LEFT, UP,
                detail, look.glow, still, e.getId());
        Vec3 centre = new Vec3(ex0, ey0 + mid, ez0);
        Vec3 ground = com.dbzenith.client.fx.BigSphere.groundBelow(e.level(), centre, r * 5);
        if (ground != null && look.light > 0)
            com.dbzenith.client.fx.BigSphere.groundGlow(pose, buffers, ground.subtract(ex0, ey0, ez0), r, (float) (centre.y - ground.y), c, look.light);
    }

    @Override
    public ResourceLocation getTextureLocation(KiBlastEntity entity) {
        return GLOW;
    }
}
