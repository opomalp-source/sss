package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.dragonball.DragonSpiritEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Placeholder Eternal Dragon: a body of glowing green segments coiling up a helix into the sky, with a large
 * head and red eyes at the top. (A real model is listed in ASSETS_TODO.md.)
 */
public class DragonSpiritRenderer extends EntityRenderer<DragonSpiritEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(GLOW);
    private static final int SEGMENTS = 70;

    public DragonSpiritRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(DragonSpiritEntity dragon, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(DragonSpiritEntity dragon, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(TYPE);
        float t = (dragon.tickCount + partialTicks) * 0.02f;
        float rise = Mth.clamp((dragon.tickCount + partialTicks) / 60f, 0f, 1f); // emerges over 3 s
        float hx = 0, hy = 0, hz = 0;
        for (int i = 0; i < SEGMENTS * rise; i++) {
            float f = i / (float) SEGMENTS;
            float angle = f * 6.5f * Mth.PI + t;
            float radius = 6f * (1 - f * 0.6f);
            float x = radius * Mth.cos(angle);
            float z = radius * Mth.sin(angle);
            float y = f * 30f;
            float size = 2.6f - f * 0.9f;
            ball(pose, vc, x, y, z, size * 1.6f, 0x30, 0xC0, 0x50, 170);
            ball(pose, vc, x, y, z, size * 0.7f, 0xB0, 0xFF, 0xB0, 230);
            hx = x;
            hy = y;
            hz = z;
        }
        if (rise >= 1f) {
            ball(pose, vc, hx, hy + 1.5f, hz, 6f, 0x40, 0xD0, 0x60, 200); // head
            ball(pose, vc, hx - 0.9f, hy + 2f, hz, 0.9f, 0xFF, 0x20, 0x20, 255); // eyes
            ball(pose, vc, hx + 0.9f, hy + 2f, hz, 0.9f, 0xFF, 0x20, 0x20, 255);
        }
        super.render(dragon, yaw, partialTicks, pose, buffers, light);
    }

    private void ball(PoseStack pose, VertexConsumer vc, float x, float y, float z, float size, int r, int g, int b, int a) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180f));
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
    public ResourceLocation getTextureLocation(DragonSpiritEntity dragon) {
        return GLOW;
    }
}
