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
    /**
     * One segment: eight corners (bit 0 = +side, bit 1 = +side2, bit 2 = towards the tip), its shade tier, its strand
     * and how far along the strand its two ends lie (for the wind).
     */
    record Segment(float[][] corners, Vec3 axis, Vec3 side, Vec3 side2, int tier, Root strand, float d0, float d1) {}

    /** A strand as the wind sees it: its root, root-to-tip direction, outward normal, flexibility and how it hangs. */
    record Root(Vec3 root, Vec3 dir, Vec3 normal, float flex, float hang) {}

    /** Pixels a strand's tip moves for a unit of wind at sixteen pixels long. */
    private static final float SWAY = 7f;

    private static final Map<String, List<Segment>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Segment>> e) {
            return size() > 128;
        }
    };
    /** How far each tier (root, shaft, tip) is lifted towards white, so black hair keeps a gradient. */
    static final float[] LIFT = {0.05f, 0.12f, 0.22f};
    private static final Vec3 DOWN = new Vec3(0, 1, 0);
    private static final Vec3 UP = new Vec3(0, -1, 0);

    private HairMesh() {}

    public static List<Segment> of(String code) {
        boolean chunky = ArtStyle.get() != ArtStyle.CLASSIC;
        return CACHE.computeIfAbsent((chunky ? "c" : "t") + code, k -> {
            List<Strand> strands = HairCode.decode(code);
            List<Segment> out = new ArrayList<>();
            if (strands != null) for (Strand s : strands) build(s, out, chunky);
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

    /**
     * One strand. Chunky (the painted and HD styles, CX-14c): clumps half as wide again, tapering less, in fewer and
     * blockier segments rooted deeper, like the stacked voxel hair of the references; thin: the original spikes.
     */
    static void build(Strand s, List<Segment> out, boolean chunky) {
        Vec3[] f = frame(s);
        double yaw = Math.toRadians(s.yaw() * 15), pitch = Math.toRadians(s.pitch() * 15);
        Vec3 dir = f[1].scale(Math.cos(pitch) * Math.cos(yaw)).add(f[2].scale(Math.sin(yaw) * Math.cos(pitch)))
                .add(f[3].scale(-Math.sin(pitch))).normalize();
        int segs = chunky ? (s.length() <= 5 ? 2 : s.length() <= 10 ? 3 : 4) : s.length() <= 5 ? 3 : s.length() <= 10 ? 4 : 5;
        float segLen = s.length() / (float) segs;
        Vec3 p = f[0].subtract(f[1].scale(chunky ? 0.7 : 0.3));                              // root sunk a little into the scalp
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
        Vec3[] pts = new Vec3[segs + 1];
        pts[0] = p;
        for (int i = 0; i < segs; i++) {
            if (i > 0 && target != null) dir = dir.add(target.subtract(dir).scale(rate)).normalize();
            pts[i + 1] = pts[i].add(dir.scale(segLen));
        }
        Vec3 span = pts[segs].subtract(pts[0]);
        float flex = switch (s.bend()) {
            case HANG -> 1f;
            case DROOP -> 0.8f;
            case LIFT -> 0.55f;
            case STRAIGHT -> 0.35f;
        };
        float hang = switch (s.bend()) {
            case HANG -> 1f;
            case DROOP -> 0.5f;
            default -> 0f;
        };
        Root root = new Root(pts[0], span.lengthSqr() < 1e-6 ? f[1] : span.normalize(), f[1], flex, hang);
        for (int i = 0; i < segs; i++) {
            float w = chunky ? Math.max(1.1f, s.width() * 1.5f * (1 - 0.55f * i / Math.max(1, segs - 1)))
                    : Math.max(0.45f, s.width() * (1 - 0.7f * i / Math.max(1, segs - 1)));
            int tier = i == 0 ? 0 : i == segs - 1 ? 2 : 1;
            out.add(box(pts[i], pts[i + 1], w, tier, root, i * segLen, (i + 1) * segLen));
        }
    }

    static Segment box(Vec3 from, Vec3 to, float width, int tier, Root strand, float d0, float d1) {
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
        return new Segment(corners, axis, side, side2, tier, strand, d0, d1);
    }

    // faces as corner indices, with the axis (0 side, 1 side2, 2 axis) and sign of their normal
    private static final int[][] FACES = {{1, 3, 7, 5}, {0, 4, 6, 2}, {2, 6, 7, 3}, {0, 1, 5, 4}, {4, 5, 7, 6}, {0, 2, 3, 1}};
    private static final int[] FACE_AXIS = {0, 0, 1, 1, 2, 2};
    private static final int[] FACE_SIGN = {1, -1, 1, -1, 1, -1};

    /**
     * Texture corners per face vertex, so that on the four long faces v always runs root to tip (strands in the hair
     * texture follow the spike) and u across; the two end caps keep a plain quad mapping. [face][vertex] = {u1?, v1?}.
     */
    private static final boolean[][][] UV = new boolean[6][4][];

    static {
        for (int f = 0; f < 6; f++) {
            int[] q = FACES[f];
            boolean cap = ((q[0] ^ q[1]) & 4) == 0 && ((q[0] ^ q[2]) & 4) == 0;
            int firstBase = -1;
            for (int k = 0; k < 4; k++) {
                if (cap) {
                    UV[f][k] = new boolean[]{k == 1 || k == 2, k >= 2};
                    continue;
                }
                int c = q[k], base = c & ~4;
                if (firstBase < 0 && (c & 4) == 0) firstBase = base;
                UV[f][k] = new boolean[]{false, (c & 4) != 0};
            }
            if (cap) continue;
            for (int k = 0; k < 4; k++) {                                      // the first root corner takes u0, its partner too
                int fb = -1;
                for (int j = 0; j < 4; j++) if ((q[j] & 4) == 0) { fb = q[j]; break; }
                UV[f][k][0] = (q[k] & ~4) != fb;
            }
        }
    }

    /**
     * Draw in head space: the pose must already carry the head's transform (ModelPart.translateAndRotate);
     * coordinates are in model pixels.
     */
    public static void render(PoseStack pose, VertexConsumer vc, List<Segment> mesh, int light, int overlay, float r, float g, float b) {
        render(pose, vc, mesh, HairWind.State.STILL, light, overlay, r, g, b);
    }

    /**
     * Draw with the wind: each strand swings about its root, away from the wind and towards gravity, more the
     * further along it is and the more flexible it is (hanging hair most, stiff spikes least).
     */
    public static void render(PoseStack pose, VertexConsumer vc, List<Segment> mesh, HairWind.State wind, int light, int overlay,
                              float r, float g, float b) {
        render(pose, vc, mesh, wind, light, overlay, r, g, b, -1);
    }

    /** {@code tips}: a highlight colour for the strand tips (0xRRGGBB), or -1 for none. */
    public static void render(PoseStack pose, VertexConsumer vc, List<Segment> mesh, HairWind.State wind, int light, int overlay,
                              float r, float g, float b, int tips) {
        float tr = tips < 0 ? r : ((tips >> 16) & 255) / 255f, tg = tips < 0 ? g : ((tips >> 8) & 255) / 255f, tb = tips < 0 ? b : (tips & 255) / 255f;
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vec3 sag = wind.gravity().subtract(DOWN);
        boolean calm = wind.wind().lengthSqr() < 1e-4 && sag.lengthSqr() < 1e-4;
        Root last = null;
        Vec3 push = Vec3.ZERO;
        double pushLen = 0;
        float[][] moved = new float[8][3];
        for (Segment s : mesh) {
            float[][] corners = s.corners;
            if (!calm) {
                if (s.strand != last) {
                    last = s.strand;
                    Vec3 w = wind.wind().scale(last.flex() * wind.stiffness()).add(sag.scale(last.hang()));
                    double along = w.dot(last.dir());
                    push = w.subtract(last.dir().scale(along));
                    if (along < 0) push = push.add(last.normal().scale(-along * 0.8));   // head-on: the strand flips outward
                    pushLen = push.length();
                    if (pushLen > 1e-6) push = push.scale(1 / pushLen);
                }
                if (pushLen > 1e-3) {
                    Vec3 dir = last.dir();
                    for (int i = 0; i < 8; i++) {
                        float d = (i & 4) != 0 ? s.d1 : s.d0;
                        double delta = Math.min(0.9 * d, pushLen * SWAY * Math.pow(d / 16.0, 1.3));
                        double back = d - Math.sqrt(Math.max(0, d * d - delta * delta));          // swinging, not stretching
                        float[] c = s.corners[i];
                        moved[i][0] = (float) (c[0] + push.x * delta - dir.x * back);
                        moved[i][1] = (float) (c[1] + push.y * delta - dir.y * back);
                        moved[i][2] = (float) (c[2] + push.z * delta - dir.z * back);
                    }
                    corners = moved;
                }
            }
            float u0 = (s.tier * 16 + 3) / 64f, u1 = (s.tier * 16 + 9) / 64f, v0 = 50 / 64f, v1 = 56 / 64f;
            float sr = s.tier == 2 ? tr : s.tier == 1 && tips >= 0 ? (r + tr) / 2 : r;          // dyed tips blend in along the shaft
            float sg = s.tier == 2 ? tg : s.tier == 1 && tips >= 0 ? (g + tg) / 2 : g;
            float sb = s.tier == 2 ? tb : s.tier == 1 && tips >= 0 ? (b + tb) / 2 : b;
            float lift = LIFT[s.tier];                                                          // dark hair still shows its shape: roots to tips
            sr += (1 - sr) * lift;
            sg += (1 - sg) * lift;
            sb += (1 - sb) * lift;
            for (int f = 0; f < 6; f++) {
                Vec3 axis = FACE_AXIS[f] == 0 ? s.side : FACE_AXIS[f] == 1 ? s.side2 : s.axis;
                float nx = (float) axis.x * FACE_SIGN[f], ny = (float) axis.y * FACE_SIGN[f], nz = (float) axis.z * FACE_SIGN[f];
                int[] q = FACES[f];
                for (int k = 0; k < 4; k++) {
                    boolean[] uv = UV[f][k];
                    vertex(vc, m, n, corners[q[k]], uv[0] ? u1 : u0, uv[1] ? v1 : v0, sr, sg, sb, light, overlay, nx, ny, nz);
                }
            }
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] p, float u, float v, float r, float g, float b,
                               int light, int overlay, float nx, float ny, float nz) {
        vc.vertex(m, p[0], p[1], p[2]).color(r, g, b, 1f).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
