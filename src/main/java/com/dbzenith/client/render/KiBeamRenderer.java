package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.FxDraw;
import com.dbzenith.client.fx.FxRenderTypes;
import com.dbzenith.skill.KiBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * A beam: a saturated body, a white-hot core whose energy flows forward, two strands of light winding around it,
 * a flare at the caster's hands and a swollen, flickering head. Camera-facing ribbons, with beads of glow along the
 * axis so the beam still reads when seen head-on (as the caster sees it).
 */
public class KiBeamRenderer extends EntityRenderer<KiBeamEntity> {
    private static final ResourceLocation BEAM = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_beam.png");
    private static final ResourceLocation FLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/beam_flow.png");
    private static final ResourceLocation GLOW = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/ki_glow.png");
    private static final ResourceLocation STAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/impact_star.png");
    private static final ResourceLocation STREAK = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fx_streak.png");

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
        int hot = FxDraw.mix(c, 0xFFFFFF, 0.6f);
        float width = beam.getWidth();
        float t = beam.tickCount + partialTicks;
        float pulse = 1f + 0.1f * Mth.sin(t * 1.3f);

        float xRot = Mth.lerp(partialTicks, beam.xRotO, beam.getXRot());
        float yRot = Mth.lerp(partialTicks, beam.yRotO, beam.getYRot());
        Vec3 dir = Vec3.directionFromRotation(xRot, yRot);
        Vec3 origin = new Vec3(Mth.lerp(partialTicks, beam.xo, beam.getX()),
                Mth.lerp(partialTicks, beam.yo, beam.getY()), Mth.lerp(partialTicks, beam.zo, beam.getZ()));
        Vec3 eye = entityRenderDispatcher.camera.getPosition().subtract(origin);
        Vec3 side = dir.cross(eye.subtract(dir.scale(length / 2)));
        if (side.lengthSqr() < 1e-6) side = dir.cross(new Vec3(0, 1, 0));
        side = side.normalize();
        Vec3 end = dir.scale(length);
        PoseStack.Pose last = pose.last();

        // saturated body, then the flowing core on top
        ribbon(buffers.getBuffer(FxRenderTypes.soft(BEAM)), last, end, side.scale(width * 1.3f * pulse), 0, length / 4f, c, 200);
        float scroll = -t * 0.35f;
        ribbon(buffers.getBuffer(FxRenderTypes.additive(FLOW)), last, end, side.scale(width * 0.8f), scroll, scroll + length / 3f, hot, 255);

        // two strands winding around the axis
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(ref).normalize(), b = dir.cross(a).normalize();
        VertexConsumer strands = buffers.getBuffer(FxRenderTypes.additive(STREAK));
        float radius = width * 0.75f;
        float step = Math.max(0.3f, length / 64f);
        for (int k = 0; k < 2; k++) {
            Vec3 prev = null;
            for (float s = 0; s <= length; s += step) {
                double ang = s * 2.2 - t * 0.5 + k * Math.PI;
                Vec3 p = dir.scale(s).add(a.scale(Math.cos(ang) * radius)).add(b.scale(Math.sin(ang) * radius));
                if (prev != null) FxDraw.ribbon(pose, strands, prev, p, eye, width * 0.3f, hot, 150);
                prev = p;
            }
        }

        // beads along the axis (head-on view), the muzzle flare and the head
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.soft(GLOW));
        Quaternionf camera = entityRenderDispatcher.cameraOrientation();
        float bead = Math.max(0.6f, width * 1.2f);
        for (float s = bead; s < length; s += bead) {
            Vec3 p = dir.scale(s);
            FxDraw.billboard(pose, glow, camera, p.x, p.y, p.z, width * 2.0f * pulse, 0, 0, 0, 1, 1, c, 200);
        }
        FxDraw.billboard(pose, glow, camera, 0, 0, 0, width * 2.6f * pulse, 0, 0, 0, 1, 1, c, 230);
        FxDraw.billboard(pose, glow, camera, end.x, end.y, end.z, width * 3.6f * pulse, 0, 0, 0, 1, 1, c, 230);
        VertexConsumer star = buffers.getBuffer(FxRenderTypes.additive(STAR));
        FxDraw.billboard(pose, star, camera, 0, 0, 0, width * 3.2f, t * 0.4f, 0, 0, 1, 1, hot, 200);
        float flicker = 0.85f + 0.15f * Mth.sin(t * 3.1f);
        FxDraw.billboard(pose, star, camera, end.x, end.y, end.z, width * 4.5f * flicker, -t * 0.6f, 0, 0, 1, 1, hot, 230);
        FxDraw.billboard(pose, buffers.getBuffer(FxRenderTypes.additive(GLOW)), camera, end.x, end.y, end.z, width * 1.8f, 0, 0, 0, 1, 1, 0xFFFFFF, 255);
        super.render(beam, yaw, partialTicks, pose, buffers, light);
    }

    /** Quad from the origin to {@code end}, offset +-{@code halfWidth}; texture v runs from {@code v0} to {@code v1} along the beam. */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 end, Vec3 halfWidth, float v0, float v1, int rgb, int a) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int light = LightTexture.FULL_BRIGHT;
        FxDraw.vertex(vc, m, n, (float) halfWidth.x, (float) halfWidth.y, (float) halfWidth.z, 0, v0, rgb, a, light);
        FxDraw.vertex(vc, m, n, (float) -halfWidth.x, (float) -halfWidth.y, (float) -halfWidth.z, 1, v0, rgb, a, light);
        Vec3 e1 = end.subtract(halfWidth), e2 = end.add(halfWidth);
        FxDraw.vertex(vc, m, n, (float) e1.x, (float) e1.y, (float) e1.z, 1, v1, rgb, a, light);
        FxDraw.vertex(vc, m, n, (float) e2.x, (float) e2.y, (float) e2.z, 0, v1, rgb, a, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KiBeamEntity beam) {
        return BEAM;
    }
}
