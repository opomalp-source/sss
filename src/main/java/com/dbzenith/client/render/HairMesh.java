package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Strand;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and draws the geometry of a {@link HairCode}: each strand becomes a chain of square segments that taper
 * towards the tip and curve as the strand's bend says, coloured dark at the root, mid along the shaft and bright at
 * the tip (texture rows of textures/entity/form_hair.png). Meshes are cached by code.
 */
public final class HairMesh {
    /** One segment: eight corners (bit 0 = +side, bit 1 = +side2, bit 2 = towards the tip) and its shade tier. */
    record Segment(float[][] corners, Vec3 axis, Vec3 side, Vec3 side2, int tier) {}

    private static final Map<String, List<Segment>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Segment>> e) {
            return size() > 128;
        }
    };
    private static final Vec3 DOWN = new Vec3(0, 1, 0);
    private static final Vec3 UP = new Vec3(0, -1, 0);

    private HairMesh() {}

    public static List<Segment> of(String code) {
        return CACHE.computeIfAbsent(code, c -> {
            List<Strand> strands = HairCode.decode(c);
            List<Segment> out = new ArrayList<>();
            if (strands != null) for (Strand s : strands) build(s, out);
            return out;
        });
    }

    /** Head-space root point, outward normal and the face's u / v directions. */
    static Vec3[] frame(Strand s) {
        float a = s.u() + 0.5f, b = s.v() + 0.5f;
        return switch (s.face()) {
            case TOP -> new Vec3[]{new Vec3(-4 + a, -8, -4 + b), new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
            case FRONT -> new Vec3[]{new Vec3(-4 + a, -8 + b, -4), new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, 1, 0)};
            case BACK -> new Vec3[]{new Vec3(4 - a, -8 + b, 4), new Vec3(0, 0, 1), new Vec3(-1, 0, 0), new Vec3(0, 1, 0)};
            case LEFT -> new Vec3[]{new Vec3(4, -8 + b, -4 + a), new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)};
            case RIGHT -> new Vec3[]{new Vec3(-4, -8 + b, 4 - a), new Vec3(-1, 0, 0), new Vec3(0, 0, -1), new Vec3(0, 1, 0)};
        };
    }

    static void build(Strand s, List<Segment> out) {
        Vec3[] f = frame(s);
        double yaw = Math.toRadians(s.yaw() * 15), pitch = Math.toRadians(s.pitch() * 15);
        Vec3 dir = f[1].scale(Math.cos(pitch) * Math.cos(yaw)).add(f[2].scale(Math.sin(yaw) * Math.cos(pitch)))
                .add(f[3].scale(-Math.sin(pitch))).normalize();
        int segs = s.length() <= 5 ? 3 : s.length() <= 10 ? 4 : 5;
        float segLen = s.length() / (float) segs;
        Vec3 p = f[0].subtract(f[1].scale(0.3));                                // root sunk a little into the scalp
        Vec3 target = switch (s.bend()) {
            case DROOP, HANG -> DOWN;
            case LIFT -> UP;
            case STRAIGHT -> null;
        };
        float rate = switch (s.bend()) {
            case DROOP -> 0.22f;
            case HANG -> 0.55f;
            case LIFT -> 0.3f;
            case STRAIGHT -> 0f;
        };
        for (int i = 0; i < segs; i++) {
            if (i > 0 && target != null) dir = dir.add(target.subtract(dir).scale(rate)).normalize();
            float w = Math.max(0.45f, s.width() * (1 - 0.7f * i / Math.max(1, segs - 1)));
            Vec3 end = p.add(dir.scale(segLen));
            int tier = i == 0 ? 0 : i == segs - 1 ? 2 : 1;
            out.add(box(p, end, w, tier));
            p = end;
        }
    }

    static Segment box(Vec3 from, Vec3 to, float width, int tier) {
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        axis = axis.normalize();
        Vec3 ref = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = axis.cross(ref).normalize();
        Vec3 side2 = axis.cross(side).normalize();
        Vec3 c = from.add(to).scale(0.5);
        double half = len / 2 + 0.2, hw = width / 2;
        float[][] corners = new float[8][];
        for (int i = 0; i < 8; i++) {
            Vec3 p = c.add(side.scale((i & 1) != 0 ? hw : -hw)).add(side2.scale((i & 2) != 0 ? hw : -hw))
                    .add(axis.scale((i & 4) != 0 ? half : -half));
            corners[i] = new float[]{(float) p.x, (float) p.y, (float) p.z};
        }
        return new Segment(corners, axis, side, side2, tier);
    }

    // faces as corner indices, with the axis (0 side, 1 side2, 2 axis) and sign of their normal
    private static final int[][] FACES = {{1, 3, 7, 5}, {0, 4, 6, 2}, {2, 6, 7, 3}, {0, 1, 5, 4}, {4, 5, 7, 6}, {0, 2, 3, 1}};
    private static final int[] FACE_AXIS = {0, 0, 1, 1, 2, 2};
    private static final int[] FACE_SIGN = {1, -1, 1, -1, 1, -1};

    /**
     * Draw in head space: the pose must already carry the head's transform (ModelPart.translateAndRotate);
     * coordinates are in model pixels.
     */
    public static void render(PoseStack pose, VertexConsumer vc, List<Segment> mesh, int light, int overlay, float r, float g, float b) {
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (Segment s : mesh) {
            float u0 = (s.tier * 16 + 3) / 64f, u1 = (s.tier * 16 + 9) / 64f, v0 = 50 / 64f, v1 = 56 / 64f;
            for (int f = 0; f < 6; f++) {
                Vec3 axis = FACE_AXIS[f] == 0 ? s.side : FACE_AXIS[f] == 1 ? s.side2 : s.axis;
                float nx = (float) axis.x * FACE_SIGN[f], ny = (float) axis.y * FACE_SIGN[f], nz = (float) axis.z * FACE_SIGN[f];
                int[] q = FACES[f];
                vertex(vc, m, n, s.corners[q[0]], u0, v0, r, g, b, light, overlay, nx, ny, nz);
                vertex(vc, m, n, s.corners[q[1]], u1, v0, r, g, b, light, overlay, nx, ny, nz);
                vertex(vc, m, n, s.corners[q[2]], u1, v1, r, g, b, light, overlay, nx, ny, nz);
                vertex(vc, m, n, s.corners[q[3]], u0, v1, r, g, b, light, overlay, nx, ny, nz);
            }
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] p, float u, float v, float r, float g, float b,
                               int light, int overlay, float nx, float ny, float nz) {
        vc.vertex(m, p[0], p[1], p[2]).color(r, g, b, 1f).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
