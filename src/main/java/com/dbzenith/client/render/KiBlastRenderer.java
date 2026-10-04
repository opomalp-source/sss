package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.Technique;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Glowing billboard for ball techniques; a spinning flat quad for disks. Full-bright. */
public class KiBlastRenderer extends EntityRenderer<KiBlastEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(GLOW);

    public KiBlastRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(KiBlastEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        int c = entity.getColor();
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        float size = entity.getSize();
        VertexConsumer vc = buffers.getBuffer(TYPE);

        pose.pushPose();
        pose.translate(0, entity.getBbHeight() / 2.0, 0);
        if (entity.getStyle() == Technique.Style.DISK) {
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTicks) * 40f));
            quad(pose, vc, size * 2.2f, r, g, b, 220);
            quad(pose, vc, size * 1.1f, 255, 255, 255, 255);
        } else {
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            float pulse = 1f + 0.08f * (float) Math.sin((entity.tickCount + partialTicks) * 0.8f);
            quad(pose, vc, size * 2.6f * pulse, r, g, b, 200);
            quad(pose, vc, size * 1.2f, 255, 255, 255, 255);
        }
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, float scale, int r, int g, int b, int a) {
        pose.pushPose();
        pose.scale(scale, scale, scale);
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        vertex(vc, m, n, -0.5f, -0.5f, 0, 1, r, g, b, a);
        vertex(vc, m, n, 0.5f, -0.5f, 1, 1, r, g, b, a);
        vertex(vc, m, n, 0.5f, 0.5f, 1, 0, r, g, b, a);
        vertex(vc, m, n, -0.5f, 0.5f, 0, 0, r, g, b, a);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int r, int g, int b, int a) {
        vc.vertex(m, x, y, 0).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(KiBlastEntity entity) {
        return GLOW;
    }
}
