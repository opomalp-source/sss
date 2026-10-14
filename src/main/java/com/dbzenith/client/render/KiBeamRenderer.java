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
    private static final ResourceLocation RING = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/shock_ring.png");

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
        if (com.dbzenith.client.fx.KiEnergy.available()) {                     // the ki attack rework, phase 3
            renderEnergy(beam, pose, buffers, dir, length, width, c, hot, t, eye);
            super.render(beam, yaw, partialTicks, pose, buffers, light);
            return;
        }

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
        VertexConsumer light2 = buffers.getBuffer(FxRenderTypes.additive(GLOW));
        FxDraw.billboard(pose, light2, camera, end.x, end.y, end.z, width * 1.8f, 0, 0, 0, 1, 1, 0xFFFFFF, 255);

        // CX-46: the head churns, energy boiling round it as it pushes forward
        for (int i = 0; i < 6; i++) {
            float ang = t * (0.35f + 0.07f * i) + i * 1.047f;
            float r = width * (0.55f + 0.25f * Mth.sin(t * 0.9f + i * 2.1f));
            Vec3 p = end.add(a.scale(Mth.cos(ang) * r)).add(b.scale(Mth.sin(ang) * r)).subtract(dir.scale(width * 0.4f * (i % 3)));
            FxDraw.billboard(pose, light2, camera, p.x, p.y, p.z, width * (1.6f + 0.5f * Mth.sin(t * 1.7f + i)), 0, 0, 0, 1, 1, hot, 150);
        }
        // shock rings running from the hands to the head, and the pressure ring the head drives before it
        VertexConsumer rings = buffers.getBuffer(FxRenderTypes.additive(RING));
        float speed = Math.max(1.2f, length / 12f);
        for (int k = 0; k < 4; k++) {
            float s = ((t * speed) + k * length / 4f) % Math.max(0.01f, length);
            float fade = Mth.clamp(s / 1.5f, 0, 1) * Mth.clamp((length - s) / 2f, 0, 1);
            FxDraw.plane(pose, rings, dir.scale(s), dir, width * (1.5f + 0.6f * s / Math.max(1, length)), t * 0.2f + k, hot, (int) (170 * fade), LightTexture.FULL_BRIGHT);
        }
        float throb = 0.8f + 0.2f * Mth.sin(t * 2.3f);
        FxDraw.plane(pose, rings, end.subtract(dir.scale(width * 0.3f)), dir, width * 2.9f * throb, -t * 0.3f, hot, 210, LightTexture.FULL_BRIGHT);
        FxDraw.plane(pose, rings, end.add(dir.scale(width * 0.5f)), dir, width * 1.9f / throb, t * 0.5f, 0xFFFFFF, 160, LightTexture.FULL_BRIGHT);
        super.render(beam, yaw, partialTicks, pose, buffers, light);
    }

    private static final org.joml.Vector3f LEFT = new org.joml.Vector3f(), UP = new org.joml.Vector3f();

    /**
     * A beam through the energy shader (the ki attack rework, phase 3): a smooth tube widening as it goes, energy flowing
     * down it and glowing at its edges, a white-hot core tube inside, two spiral strands winding round it, lightning
     * crackling off its edges, a big churning sphere driving at its tip and a flare at the hands; it flickers. Just
     * fired, it flashes.
     */

    private void renderEnergy(KiBeamEntity beam, PoseStack pose, MultiBufferSource buffers, Vec3 dir, float length, float width, int c, int hot, float t, Vec3 eye) {
        var m = pose.last().pose();
        var n = pose.last().normal();
        float dx = (float) dir.x, dy = (float) dir.y, dz = (float) dir.z;
        float ex = (float) eye.x, ey = (float) eye.y, ez = (float) eye.z;
        LEFT.set(entityRenderDispatcher.camera.getLeftVector());
        UP.set(entityRenderDispatcher.camera.getUpVector());
        int q = com.dbzenith.client.fx.KiParticles.quality();
        double far = eye.length();
        int sides = q == 0 || far > 64 ? 8 : q == 1 || far > 24 ? 12 : 16;
        int rings = Math.max(4, Math.min(q >= 2 ? 40 : 20, (int) (length / Math.max(0.5f, width))));
        float flick = 1f + 0.06f * Mth.sin(t * 1.9f) + 0.04f * Mth.sin(t * 4.7f);
        float r0 = width * 0.55f * flick, r1 = width * 0.78f * flick;           // it widens as it travels
        float flow = -t * 0.12f;
        float born = Math.min(1, beam.tickCount / 4f);                          // fired: a flash that settles

        // the tube: the glowing body, energy flowing down it; inside, the white-hot core
        var solid = buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.solid());
        com.dbzenith.client.fx.KiEnergy.tube(solid, m, n, 0, 0, 0, dx, dy, dz, length, r0, r1, com.dbzenith.client.fx.KiEnergy.argb(c, 175),
                com.dbzenith.client.fx.KiEnergy.SHELL, sides, rings, flow, 0.05f, t);
        com.dbzenith.client.fx.KiEnergy.tube(solid, m, n, 0, 0, 0, dx, dy, dz, length, r0 * 1.08f, r1 * 1.1f, com.dbzenith.client.fx.KiEnergy.argb(c, 75),
                com.dbzenith.client.fx.KiEnergy.STRANDS, sides, rings, flow * 1.6f, 0.08f, t + 3);
        com.dbzenith.client.fx.KiEnergy.tube(solid, m, n, 0, 0, 0, dx, dy, dz, length, r0 * 0.42f, r1 * 0.42f, com.dbzenith.client.fx.KiEnergy.argb(0xFFFFFF, 255),
                com.dbzenith.client.fx.KiEnergy.CORE, Math.max(6, sides / 2), Math.max(4, rings / 2), flow * 2, 0.03f, t);
        // the head: a big churning sphere driving before it
        float head = r1 * (1.7f + 0.15f * Mth.sin(t * 2.3f));
        float hx = dx * length, hy = dy * length, hz = dz * length;
        com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, hx, hy, hz, head, com.dbzenith.client.fx.KiEnergy.argb(c, 230), com.dbzenith.client.fx.KiEnergy.SHELL, 2, t * 0.05f);
        com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, hx, hy, hz, head * 1.08f, com.dbzenith.client.fx.KiEnergy.argb(c, 200), com.dbzenith.client.fx.KiEnergy.STRANDS, 2, -t * 0.08f);
        com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, hx, hy, hz, head * 0.6f, com.dbzenith.client.fx.KiEnergy.argb(0xFFFFFF, 255), com.dbzenith.client.fx.KiEnergy.CORE, 1, t * 0.03f);
        // the hands: where it pours out
        com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, 0, 0, 0, r0 * 1.25f, com.dbzenith.client.fx.KiEnergy.argb(c, 220), com.dbzenith.client.fx.KiEnergy.SHELL, 1, t * 0.06f);
        com.dbzenith.client.fx.KiEnergy.sphere(solid, m, n, 0, 0, 0, r0 * 0.8f, com.dbzenith.client.fx.KiEnergy.argb(0xFFFFFF, 255), com.dbzenith.client.fx.KiEnergy.CORE, 1, 0);

        // flat things: two spiral strands, lightning off the edges, halos at both ends, the release flash
        var flat = buffers.getBuffer(com.dbzenith.client.fx.KiEnergy.flat());
        float ax, ay, az;
        if (Math.abs(dy) > 0.9f) { ax = 1; ay = 0; az = 0; } else { ax = 0; ay = 1; az = 0; }
        float px = dy * az - dz * ay, py = dz * ax - dx * az, pz = dx * ay - dy * ax;
        float pl = Mth.sqrt(px * px + py * py + pz * pz);
        px /= pl; py /= pl; pz /= pl;
        float qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
        float step = 0.18f;                                                     // fine enough that the spirals stay smooth
        for (int k = 0; k < 2; k++) {
            float lx = 0, ly = 0, lz = 0;
            boolean first = true;
            for (float s = 0; s <= length; s += step) {
                float rad = (r0 + (r1 - r0) * s / Math.max(0.01f, length)) * 1.35f;
                double ang = s * 0.7 - t * 0.35 + k * Math.PI;                      // long, sweeping turns
                float cx = dx * s + (float) (px * Math.cos(ang) + qx * Math.sin(ang)) * rad;
                float cy = dy * s + (float) (py * Math.cos(ang) + qy * Math.sin(ang)) * rad;
                float cz = dz * s + (float) (pz * Math.cos(ang) + qz * Math.sin(ang)) * rad;
                if (!first) com.dbzenith.client.fx.KiEnergy.ribbon(flat, m, n, lx, ly, lz, cx, cy, cz, ex, ey, ez, width * 0.14f, width * 0.14f, hot, 120, 120, s * 0.3f, s * 0.3f + 0.3f, com.dbzenith.client.fx.KiEnergy.RIBBON);
                lx = cx; ly = cy; lz = cz;
                first = false;
            }
        }
        java.util.Random rnd = new java.util.Random(beam.getId() * 977L + (long) (t / 2));   // lightning off its edges, re-struck every other tick
        int arcs = q == 0 ? 1 : 2 + (int) (length / 16);
        for (int a = 0; a < arcs; a++) {
            if (rnd.nextFloat() < 0.4f) continue;
            float s = rnd.nextFloat() * length;
            double ang = rnd.nextDouble() * Math.PI * 2;
            float rad = r0 + (r1 - r0) * s / Math.max(0.01f, length);
            float ox = (float) (px * Math.cos(ang) + qx * Math.sin(ang)), oy = (float) (py * Math.cos(ang) + qy * Math.sin(ang)), oz = (float) (pz * Math.cos(ang) + qz * Math.sin(ang));
            float lx = dx * s + ox * rad, ly = dy * s + oy * rad, lz = dz * s + oz * rad;
            for (int seg = 1; seg <= 3; seg++) {
                float out = rad * (1 + 0.5f * seg);
                float along = s + (rnd.nextFloat() - 0.5f) * width;
                float nx = dx * along + ox * out + (rnd.nextFloat() - 0.5f) * width * 0.4f;
                float ny = dy * along + oy * out + (rnd.nextFloat() - 0.5f) * width * 0.4f;
                float nz = dz * along + oz * out + (rnd.nextFloat() - 0.5f) * width * 0.4f;
                com.dbzenith.client.fx.KiEnergy.ribbon(flat, m, n, lx, ly, lz, nx, ny, nz, ex, ey, ez, width * 0.07f, width * 0.05f, FxDraw.mix(c, 0xFFFFFF, 0.6f), 190, 120, 0, 1, com.dbzenith.client.fx.KiEnergy.RIBBON);
                lx = nx; ly = ny; lz = nz;
            }
        }
        com.dbzenith.client.fx.KiEnergy.billboard(flat, m, n, hx, hy, hz, LEFT, UP, head * 2.2f, com.dbzenith.client.fx.KiEnergy.argb(c, 90), com.dbzenith.client.fx.KiEnergy.HALO);
        com.dbzenith.client.fx.KiEnergy.billboard(flat, m, n, 0, 0, 0, LEFT, UP, r0 * 2.6f, com.dbzenith.client.fx.KiEnergy.argb(c, 90), com.dbzenith.client.fx.KiEnergy.HALO);
        if (born < 1) com.dbzenith.client.fx.KiEnergy.billboard(flat, m, n, 0, 0, 0, LEFT, UP, width * (3 + 5 * (1 - born)),
                com.dbzenith.client.fx.KiEnergy.argb(0xFFFFFF, (int) (255 * (1 - born))), com.dbzenith.client.fx.KiEnergy.HALO);
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
