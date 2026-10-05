package com.dbzenith.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Quad builders for effects, in whatever space the pose stack is in. Colours are 0xRRGGBB plus a separate alpha. */
public final class FxDraw {
    private FxDraw() {}

    public static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                              int rgb, int alpha, int light) {
        vc.vertex(m, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, alpha)))
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    /** A square facing the camera ({@code camera} is the camera's orientation), spun by {@code spin} radians. */
    public static void billboard(PoseStack pose, VertexConsumer vc, Quaternionf camera, double x, double y, double z, float size,
                                 float spin, float u0, float v0, float u1, float v1, int rgb, int alpha) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera);
        if (spin != 0) pose.mulPose(new Quaternionf().rotationZ(spin));
        pose.scale(size, size, size);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        vertex(vc, m, n, -0.5f, -0.5f, 0, u0, v1, rgb, alpha, light);
        vertex(vc, m, n, 0.5f, -0.5f, 0, u1, v1, rgb, alpha, light);
        vertex(vc, m, n, 0.5f, 0.5f, 0, u1, v0, rgb, alpha, light);
        vertex(vc, m, n, -0.5f, 0.5f, 0, u0, v0, rgb, alpha, light);
        pose.popPose();
    }

    /** A square of side {@code 2 * radius} centred at {@code c}, lying in the plane whose normal is {@code normal}. */
    public static void plane(PoseStack pose, VertexConsumer vc, Vec3 c, Vec3 normal, float radius, float spin, int rgb, int alpha, int light) {
        Vec3 nrm = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 ref = Math.abs(nrm.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = nrm.cross(ref).normalize();
        Vec3 b = nrm.cross(a).normalize();
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 ua = a.scale(cs).add(b.scale(sn)).scale(radius);
        Vec3 ub = b.scale(cs).subtract(a.scale(sn)).scale(radius);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        corner(vc, m, n, c.subtract(ua).subtract(ub), 0, 0, rgb, alpha, light);
        corner(vc, m, n, c.add(ua).subtract(ub), 1, 0, rgb, alpha, light);
        corner(vc, m, n, c.add(ua).add(ub), 1, 1, rgb, alpha, light);
        corner(vc, m, n, c.subtract(ua).add(ub), 0, 1, rgb, alpha, light);
    }

    /** A strip from {@code a} to {@code b}, {@code width} wide, turned to face {@code eye}. Texture u runs across. */
    public static void ribbon(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 eye, float width, int rgb, int alpha) {
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(eye.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width / 2);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int light = LightTexture.FULL_BRIGHT;
        corner(vc, m, n, a.add(side), 0, 0, rgb, alpha, light);
        corner(vc, m, n, a.subtract(side), 1, 0, rgb, alpha, light);
        corner(vc, m, n, b.subtract(side), 1, 1, rgb, alpha, light);
        corner(vc, m, n, b.add(side), 0, 1, rgb, alpha, light);
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int rgb, int alpha, int light) {
        vertex(vc, m, n, (float) p.x, (float) p.y, (float) p.z, u, v, rgb, alpha, light);
    }

    /** {@code a} blended towards {@code b} by {@code t}. */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    public static Vector3f rgbVector(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }
}
