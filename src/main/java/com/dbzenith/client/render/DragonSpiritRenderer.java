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
 * Porunga ({@link PorungaModel}) stands up from a shorter coil instead.
 */
public class DragonSpiritRenderer extends EntityRenderer<DragonSpiritEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final RenderType GLOW_TYPE = RenderType.entityTranslucentEmissive(GLOW);
    private static final int SEGMENTS = 56;
    private static final int GLOW_LIGHT = LightTexture.pack(15, 15);
    private static final ResourceLocation BLACK_STAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/black_star_dragon.png");
    private static final ResourceLocation SUPER = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/super_dragon.png");

    /** Porunga's body: one model pixel is this many blocks (a torso of about nine blocks over a sixteen-block coil). */
    private static final float PORUNGA_PX = 0.4f;

    private final DragonModel model;
    private final PorungaModel porunga;

    public DragonSpiritRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new DragonModel(context.bakeLayer(DragonModel.LAYER));
        porunga = new PorungaModel(context.bakeLayer(PorungaModel.LAYER));
    }

    /** Porunga's tail (0 = its end on the ground, 1 = his waist): a lower, looser coil that straightens to stand him upright. */
    private static Vec3 porungaCoil(float f, float t) {
        float angle = f * 4f * Mth.PI + t;
        float radius = 6f * (1 - f * 0.6f) * Mth.clamp((1 - f) / 0.18f, 0f, 1f);
        return new Vec3(radius * Mth.cos(angle), f * 16f, radius * Mth.sin(angle));
    }

    /**
     * Porunga (Namek's set, v0.167.0): the coiled tail rises over three seconds, then the body stands from it, turned to
     * whoever looks at him, hunched, arms hanging forward, breathing slowly; the head tips toward the viewer.
     */
    private void renderPorunga(DragonSpiritEntity dragon, float partialTicks, PoseStack pose, MultiBufferSource buffers) {
        float age = dragon.tickCount + partialTicks;
        float t = age * 0.012f;
        float rise = Mth.clamp(age / 60f, 0f, 1f);
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(PorungaModel.TEXTURE));
        int shown = (int) (SEGMENTS * rise);
        for (int i = 0; i < shown; i++) {
            float f = i / (float) SEGMENTS;
            Vec3 p = porungaCoil(f, t), next = porungaCoil((i + 1) / (float) SEGMENTS, t);
            Vec3 d = next.subtract(p);
            float thick = 1.2f + f * 2.4f;
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z) + Mth.PI));
            pose.mulPose(Axis.XP.rotation((float) Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
            pose.scale(thick * 2f, -thick * 2f, (float) (d.length() * 1.45 / 0.75));
            porunga.segment.render(pose, body, GLOW_LIGHT, OverlayTexture.NO_OVERLAY);
            if (i % 5 == 0 && f > 0.1f && f < 0.75f) porunga.spike.render(pose, body, GLOW_LIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (rise < 1f) return;
        Vec3 waist = porungaCoil(1f, t).add(0, 0.6 + 0.12 * Mth.sin(age * 0.06f), 0);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(dragon.getPosition(partialTicks)).subtract(waist);
        float facing = (float) Math.atan2(cam.x, cam.z) + Mth.PI;
        float s = PORUNGA_PX * 16f;                                                               // model space: 16 units a block
        pose.pushPose();
        pose.translate(waist.x, waist.y, waist.z);
        pose.mulPose(Axis.YP.rotation(facing));
        pose.pushPose();
        float breath = 1f + 0.015f * Mth.sin(age * 0.08f);
        pose.scale(s * breath, -s, s * breath);
        porunga.torso.render(pose, body, GLOW_LIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        double neckUp = PorungaModel.NECK_UP * PORUNGA_PX;
        Vec3 toCam = cam.subtract(0, neckUp, 0);
        float pitch = (float) Math.atan2(toCam.y, Math.sqrt(toCam.x * toCam.x + toCam.z * toCam.z));
        pose.translate(0, neckUp, -PorungaModel.NECK_FORWARD * PORUNGA_PX);                       // the head low and forward between the shoulders
        pose.mulPose(Axis.XP.rotation(Mth.clamp(pitch, -0.25f, 0.25f)));                          // (more, and the lit face turns to shadow)
        pose.scale(s, -s, s);
        porunga.head.render(pose, body, GLOW_LIGHT, OverlayTexture.NO_OVERLAY);
        porunga.eyes.render(pose, body, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        float pulse = 0.85f + 0.15f * Mth.sin(age * 0.1f);                                        // faint magic about him
        ball(pose, buffers.getBuffer(GLOW_TYPE), (float) waist.x, (float) (waist.y + neckUp * 0.6), (float) waist.z, 16f * pulse, 0x70, 0xFF, 0x60, 50);
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
        if (set == com.dbzenith.dragonball.BallSet.NAMEK) {                                         // Porunga has a body of his own
            renderPorunga(dragon, partialTicks, pose, buffers);
            super.render(dragon, yaw, partialTicks, pose, buffers, light);
            return;
        }
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
            case NAMEK -> PorungaModel.TEXTURE;
            default -> DragonModel.TEXTURE;
        };
    }
}
