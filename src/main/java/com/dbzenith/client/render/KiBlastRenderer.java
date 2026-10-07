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
        if (vel.lengthSqr() > 1e-4) {
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

        if (entity.isSpiritBomb()) {                                         // the Spirit Bomb (CX-17a): a solid, bright sphere in a halo
            float breathe = 1f + 0.03f * Mth.sin(t * 0.25f);
            FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.soft(GLOW)), camera, 0, mid, 0, size * 1.9f * breathe, 0, 0, 0, 1, 1, c, 150);
            FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.soft(SPIRIT)), camera, 0, mid, 0, size * 1.02f * breathe, t * 0.01f, 0, 0, 1, 1, 0xFFFFFF, 255);
            FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.additive(STAR)), camera, 0, mid, 0, size * 1.15f, -t * 0.05f, 0, 0, 1, 1, hot, 90);
            super.render(entity, yaw, partialTicks, pose, buffers, light);
            return;
        }
        float pulse = 1f + 0.08f * Mth.sin(t * 0.8f);
        FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.soft(GLOW)), camera, 0, mid, 0, size * 2.6f * pulse, 0, 0, 0, 1, 1, c, 210);
        FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.additive(STAR)), camera, 0, mid, 0, size * 2.4f, t * 0.35f, 0, 0, 1, 1, hot, 170);
        FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.additive(GLOW)), camera, 0, mid, 0, size * 1.3f, 0, 0, 0, 1, 1, 0xFFFFFF, 255);
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KiBlastEntity entity) {
        return GLOW;
    }
}
