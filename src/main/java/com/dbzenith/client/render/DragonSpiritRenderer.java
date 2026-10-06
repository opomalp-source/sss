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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Eternal Dragon: a scaled serpent ({@link DragonModel}) coiling up a helix into the sky, thick at the neck and
 * thin at the tail, dorsal spikes along its back, its head turned towards whoever is looking. It glows faintly.
 */
public class DragonSpiritRenderer extends EntityRenderer<DragonSpiritEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final RenderType GLOW_TYPE = RenderType.entityTranslucentEmissive(GLOW);
    private static final int SEGMENTS = 56;
    private static final int GLOW_LIGHT = LightTexture.pack(15, 15);
    private static final ResourceLocation BLACK_STAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/black_star_dragon.png");
    private static final ResourceLocation SUPER = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/super_dragon.png");

    private final DragonModel model;

    public DragonSpiritRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new DragonModel(context.bakeLayer(DragonModel.LAYER));
    }

    @Override
    public boolean shouldRender(DragonSpiritEntity dragon, Frustum frustum, double x, double y, double z) {
        return true;
    }

    /** Point {@code f} (0 = tail on the ground, 1 = neck in the sky) of the coil at time {@code t}. */
    private static Vec3 coil(float f, float t) {
        float angle = f * 5.5f * Mth.PI + t;
        float radius = 7f * (1 - f * 0.55f);
        return new Vec3(radius * Mth.cos(angle), f * 30f, radius * Mth.sin(angle));
    }

    @Override
    public void render(DragonSpiritEntity dragon, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = dragon.tickCount + partialTicks;
        float t = age * 0.015f;
        float rise = Mth.clamp(age / 60f, 0f, 1f);                                                  // emerges over 3 s
        com.dbzenith.dragonball.BallSet set = dragon.set();
        pose.pushPose();
        if (set == com.dbzenith.dragonball.BallSet.SUPER) pose.scale(2.4f, 2.4f, 2.4f);              // the Super dragon dwarfs the sky
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(dragon)));
        int shown = (int) (SEGMENTS * rise);
        for (int i = 0; i < shown; i++) {
            float f = i / (float) SEGMENTS;
            Vec3 p = coil(f, t), next = coil((i + 1) / (float) SEGMENTS, t);
            Vec3 d = next.subtract(p);
            float thick = 0.9f + f * 2.1f;
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z) + Mth.PI));
            pose.mulPose(Axis.XP.rotation((float) Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
            // the segment cube is 8x8x12 px = 0.5 x 0.5 x 0.75 blocks; overlap neighbours a little so the body is seamless
            pose.scale(thick * 2f, -thick * 2f, (float) (d.length() * 1.45 / 0.75));                // -y: spikes point up
            model.segment.render(pose, body, GLOW_LIGHT, OverlayTexture.NO_OVERLAY);
            if (i % 3 == 0) model.spike.render(pose, body, GLOW_LIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (rise >= 1f) {
            Vec3 neck = coil(1f, t);
            Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(dragon.getPosition(partialTicks)).subtract(neck.scale(set == com.dbzenith.dragonball.BallSet.SUPER ? 2.4 : 1));
            float headYaw = (float) Math.atan2(cam.x, cam.z);
            float headPitch = (float) Math.atan2(cam.y, Math.sqrt(cam.x * cam.x + cam.z * cam.z));
            pose.pushPose();
            pose.translate(neck.x, neck.y + 1.6, neck.z);
            pose.mulPose(Axis.YP.rotation(headYaw + Mth.PI));
            pose.mulPose(Axis.XP.rotation(Mth.clamp(headPitch, -0.8f, 0.8f)));
            pose.scale(6f, -6f, 6f);                                                           // 8 px skull = 3 blocks; model space is y-down
            model.head.render(pose, body, GLOW_LIGHT, OverlayTexture.NO_OVERLAY);
            model.eyes.render(pose, body, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            VertexConsumer glow = buffers.getBuffer(GLOW_TYPE);                                // faint magic around the head
            float pulse = 0.85f + 0.15f * Mth.sin(age * 0.1f);
            int c = set == com.dbzenith.dragonball.BallSet.BLACK_STAR ? 0xFF3020 : set == com.dbzenith.dragonball.BallSet.SUPER ? 0xFFD040 : 0x40FF70;
            ball(pose, glow, (float) neck.x, (float) neck.y + 1.6f, (float) neck.z, 9f * pulse, c >> 16, (c >> 8) & 255, c & 255, 60);
        }
        pose.popPose();
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
        return switch (dragon.set()) {
            case BLACK_STAR -> BLACK_STAR;
            case SUPER -> SUPER;
            default -> DragonModel.TEXTURE;
        };
    }
}
