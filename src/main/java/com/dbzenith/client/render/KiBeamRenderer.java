package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.KiBeamEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders a beam as a camera-facing ribbon (colored glow + white core) from the entity (origin)
 * along its direction, with glow balls at the hands and the head.
 */
public class KiBeamRenderer extends EntityRenderer<KiBeamEntity> {
    private static final ResourceLocation BEAM = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_beam.png");
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final RenderType BEAM_TYPE = RenderType.entityTranslucentEmissive(BEAM);
    private static final RenderType GLOW_TYPE = RenderType.entityTranslucentEmissive(GLOW);

    public KiBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(KiBeamEntity beam, Frustum frustum, double camX, double camY, double camZ) {
        Vec3 start = beam.position();
        AABB box = new AABB(start, start.add(beam.direction().scale(beam.getLength()))).inflate(beam.getWidth() + 1);
        return frustum.isVisible(box);
    }

    @Override
    public void render(KiBeamEntity beam, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        float length = beam.getLength();
        if (length <= 0.01f) return;
        int c = beam.getColor();
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        float width = beam.getWidth();
        float pulse = 1f + 0.1f * Mth.sin((beam.tickCount + partialTicks) * 1.3f);

        float xRot = Mth.lerp(partialTicks, beam.xRotO, beam.getXRot());
        float yRot = Mth.lerp(partialTicks, beam.yRotO, beam.getYRot());
        Vec3 dir = Vec3.directionFromRotation(xRot, yRot);
        Vec3 origin = new Vec3(Mth.lerp(partialTicks, beam.xo, beam.getX()),
                Mth.lerp(partialTicks, beam.yo, beam.getY()), Mth.lerp(partialTicks, beam.zo, beam.getZ()));
        Vec3 toCamera = entityRenderDispatcher.camera.getPosition().subtract(origin.add(dir.scale(length / 2)));
        Vec3 side = dir.cross(toCamera);
        if (side.lengthSqr() < 1e-6) side = dir.cross(new Vec3(0, 1, 0));
        side = side.normalize();
        Vec3 end = dir.scale(length);

        VertexConsumer vc = buffers.getBuffer(BEAM_TYPE);
        PoseStack.Pose last = pose.last();
        ribbon(vc, last, end, side.scale(width * 1.3f * pulse), length, r, g, b, 210);
        ribbon(vc, last, end, side.scale(width * 0.45f), length, 255, 255, 255, 255);

        VertexConsumer glow = buffers.getBuffer(GLOW_TYPE);
        // Beads of glow along the axis keep the beam visible when viewed head-on (e.g. from the caster).
        float step = Math.max(0.6f, width * 1.2f);
        for (float s = step; s < length; s += step) ball(pose, glow, dir.scale(s), width * 2.0f * pulse, r, g, b);
        ball(pose, glow, Vec3.ZERO, width * 2.4f * pulse, r, g, b);
        ball(pose, glow, end, width * 3.2f * pulse, r, g, b);
        super.render(beam, yaw, partialTicks, pose, buffers, light);
    }

    /** Quad from the origin to {@code end}, offset +-{@code halfWidth}; texture v runs along the beam. */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 end, Vec3 halfWidth, float length, int r, int g, int b, int a) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float vEnd = length / 4f; // texture repeats every 4 blocks
        vertex(vc, m, n, halfWidth, 0, 0, r, g, b, a);
        vertex(vc, m, n, halfWidth.reverse(), 1, 0, r, g, b, a);
        vertex(vc, m, n, end.subtract(halfWidth), 1, vEnd, r, g, b, a);
        vertex(vc, m, n, end.add(halfWidth), 0, vEnd, r, g, b, a);
    }

    private void ball(PoseStack pose, VertexConsumer vc, Vec3 at, float size, int r, int g, int b) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        pose.scale(size, size, size);
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        vertex(vc, m, n, new Vec3(-0.5, -0.5, 0), 0, 1, r, g, b, 230);
        vertex(vc, m, n, new Vec3(0.5, -0.5, 0), 1, 1, r, g, b, 230);
        vertex(vc, m, n, new Vec3(0.5, 0.5, 0), 1, 0, r, g, b, 230);
        vertex(vc, m, n, new Vec3(-0.5, 0.5, 0), 0, 0, r, g, b, 230);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int r, int g, int b, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(KiBeamEntity beam) {
        return BEAM;
    }
}
