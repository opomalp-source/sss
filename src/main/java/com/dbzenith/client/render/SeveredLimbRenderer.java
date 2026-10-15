package com.dbzenith.client.render;

import com.dbzenith.combat.SeveredLimb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A limb cut off in battle (the knocked-out rework): the arm or leg in the fighter's skin tone, the hand or foot a
 * shade darker, the cut end raw in their blood's colour. It tumbles while it flies and settles on its side.
 */
public class SeveredLimbRenderer extends EntityRenderer<SeveredLimb> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public SeveredLimbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.15f;
    }

    @Override
    public void render(SeveredLimb e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.13, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.spinO, e.spin)));
        pose.mulPose(Axis.ZP.rotationDegrees(e.roll));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float w = 0.125f, h = 0.375f;
        int skin = e.skin(), end = shade(skin, 0.82f), cut = e.blood();
        box(vc, m, n, -w, -h, -w, w, h, w, skin, end, cut, light);
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    private static int shade(int rgb, float k) {
        return (int) ((rgb >> 16 & 255) * k) << 16 | (int) ((rgb >> 8 & 255) * k) << 8 | (int) ((rgb & 255) * k);
    }

    /** A box: its sides in {@code side}, the bottom (hand or foot) in {@code bottom}, the top (the cut) in {@code top}. */
    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float z0, float x1, float y1, float z1,
                            int side, int bottom, int top, int light) {
        face(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, top, light);
        face(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, bottom, light);
        face(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, side, light);
        face(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, side, light);
        face(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, shade(side, 0.92f), light);
        face(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, shade(side, 0.92f), light);
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int rgb, int light) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        vc.vertex(m, ax, ay, az).color(r, g, b, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SeveredLimb e) {
        return WHITE;
    }
}
