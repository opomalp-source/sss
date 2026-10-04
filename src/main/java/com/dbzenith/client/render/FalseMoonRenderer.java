package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.transform.FalseMoonEntity;
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
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A big pale glowing disc that grows as it rises. */
public class FalseMoonRenderer extends EntityRenderer<FalseMoonEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(GLOW);

    public FalseMoonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FalseMoonEntity moon, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        float rise = Mth.clamp((moon.tickCount + partialTicks) / FalseMoonEntity.RISE_TICKS, 0.15f, 1f);
        VertexConsumer vc = buffers.getBuffer(TYPE);
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        quad(pose, vc, 14f * rise, 0xE6, 0xEE, 0xFF, 140);
        quad(pose, vc, 7f * rise, 0xFF, 0xFF, 0xFF, 255);
        pose.popPose();
        super.render(moon, yaw, partialTicks, pose, buffers, light);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, float size, int r, int g, int b, int a) {
        pose.pushPose();
        pose.scale(size, size, size);
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
    public ResourceLocation getTextureLocation(FalseMoonEntity moon) {
        return GLOW;
    }
}
