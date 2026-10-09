package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.kosmx.bendylib.ModelPartAccessor;
import io.github.kosmx.bendylib.MutableCuboid;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.impl.ICuboid;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Segmented limbs (CX-29, from the user's reference pictures): each arm is three plain blocks (upper arm, forearm, and
 * a fist a little bigger than the forearm), each leg two (thigh and shin) with a foot block sticking out in front,
 * toes painted on its top. No sculpting: the anatomy is painted (tools/gen_body.py).
 * <p>
 * The blocks sit over the vanilla limb (a little wider, so its own cube stays hidden inside) as children of it, so they
 * follow every animation; each face shows the stretch of the limb's own texture it covers (its rows of the four sides),
 * so any skin paints them. The blocks below the elbow and knee follow bendy-lib's bend of the limb, and the feet turn
 * about the ankle against the shin's swing while standing, so they stay flat on the ground.
 * <p>
 * {@link #proportions} lengthens the legs (the figure stands taller on them): set per figure before it is posed, and
 * applied after the pose by {@link PhysiqueModel} and the NPC model.
 */
public final class LimbSegments {
    private LimbSegments() {}

    static final int STATIC = 0, BENT = 1, FOOT = 2;
    private static final String PREFIX = "dbz_seg";
    /** How much longer the legs are, and how much the rest of the figure rises on them (pixels). */
    static final float LEG_SCALE = 1.2f, RISE = 12 * (LEG_SCALE - 1);
    /** How far each build's blocks stand out from the limb: upper arm, forearm, fist, thigh, shin (pixels). */
    static final float[][] GROW = {{0.25f, 0.1f, 0.35f, 0.2f, 0.08f}, {0.4f, 0.15f, 0.48f, 0.32f, 0.12f}, {0.6f, 0.25f, 0.65f, 0.48f, 0.2f}};

    /** Set per figure before it is drawn: lengthen its legs; whether its feet stand (flattened to the ground). */
    static boolean proportions;
    static boolean grounded = true;
    static float spread;

    // ------------------------------------------------------------------------------------------------ attach and show

    public static void attach(PlayerModel<?> m) {
        Map<String, ModelPart> rc = BodyShape.childrenOf(m.rightArm);
        if (rc == null || rc.containsKey(name(0, "arm_up"))) return;
        try {
            for (int b = 0; b < GROW.length; b++) {
                float[] g = GROW[b];
                for (int side = 0; side < 2; side++) {
                    boolean right = side == 0;
                    ModelPart arm = right ? m.rightArm : m.leftArm, leg = right ? m.rightLeg : m.leftLeg;
                    Box ab = right ? Box.of(arm, 40, 16) : Box.of(arm, 32, 48);
                    Box lb = right ? Box.of(leg, 0, 16) : Box.of(leg, 16, 48);
                    Map<String, ModelPart> ac = BodyShape.childrenOf(arm), lc = BodyShape.childrenOf(leg);
                    float top = ab.y0;                                                   // -2 for arms
                    // arms: rows 0..6 the upper arm, 6..9.4 the forearm, 9.4..12 the fist (as gen_body.py paints them)
                    add(ac, name(b, "arm_up"), arm, STATIC, ab.block(g[0], top - 0.3f, top + 6f, 0, 6, true, false));
                    add(ac, name(b, "arm_fore"), arm, BENT, ab.block(g[1], top + 6f, top + 10.2f, 6, 9.4f, false, false));
                    add(ac, name(b, "arm_fist"), arm, BENT, ab.block(g[2], top + 10.2f, top + 13.1f, 9.4f, 12, false, true));
                    // legs: the thigh to the knee, the shin to the ankle, then the foot
                    add(lc, name(b, "leg_up"), leg, STATIC, lb.block(g[3], 0f, 6.2f, 0, 6.2f, true, false));
                    add(lc, name(b, "leg_low"), leg, BENT, lb.block(g[4], 6.2f, 10.2f, 6.2f, 10f, false, false));
                    add(lc, name(b, "foot"), leg, FOOT, lb.foot());
                }
            }
        } catch (RuntimeException e) {
            DBZenith.LOGGER.warn("Could not build the segmented limbs", e);
        }
    }

    private static String name(int build, String part) {
        return PREFIX + build + "_" + part;
    }

    private static void add(Map<String, ModelPart> children, String name, ModelPart parent, int joint, float[] quads) {
        if (children == null) return;
        ModelPart part = new ModelPart(List.of(new BlockCube(quads, parent, joint)), Map.of());
        part.visible = false;
        children.put(name, part);
    }

    /** Shows one build's blocks (-1: none). {@code legs}/{@code feet} false where vanilla armour covers them. */
    public static void show(PlayerModel<?> m, int build, boolean arms, boolean legs, boolean feet) {
        for (int b = 0; b < GROW.length; b++) {
            boolean on = b == build;
            for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
                set(arm, name(b, "arm_up"), on && arms);
                set(arm, name(b, "arm_fore"), on && arms);
                set(arm, name(b, "arm_fist"), on && arms);
            }
            for (ModelPart leg : new ModelPart[]{m.rightLeg, m.leftLeg}) {
                set(leg, name(b, "leg_up"), on && legs);
                set(leg, name(b, "leg_low"), on && legs);
                set(leg, name(b, "foot"), on && feet);
            }
        }
        spread = build >= 0 ? 0.05f + 0.03f * build : 0f;
    }

    private static void set(ModelPart parent, String name, boolean visible) {
        Map<String, ModelPart> c = BodyShape.childrenOf(parent);
        ModelPart p = c == null ? null : c.get(name);
        if (p != null) p.visible = visible;
    }

    /** After the model's own pose: longer legs with the figure risen on them, arms hanging a little out. */
    public static void pose(PlayerModel<?> m) {
        if (!proportions) return;
        for (ModelPart p : new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve}) p.y -= RISE;
        for (ModelPart p : new ModelPart[]{m.rightLeg, m.leftLeg, m.rightPants, m.leftPants}) {
            p.y -= RISE;
            p.yScale = LEG_SCALE;
        }
        m.rightArm.zRot += spread;
        m.leftArm.zRot -= spread;
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
    }

    /** Back to vanilla's lengths (the model is shared). */
    public static void reset(PlayerModel<?> m) {
        for (ModelPart p : new ModelPart[]{m.rightLeg, m.leftLeg, m.rightPants, m.leftPants}) p.yScale = 1f;
        proportions = false;
        grounded = true;
    }

    // ------------------------------------------------------------------------------------------------ the blocks

    /** A limb's own box and where its skin layout starts (64-pixel layout). */
    record Box(float x0, float y0, float z0, float x1, float y1, float z1, float u, float v) {
        static Box of(ModelPart part, int u, int v) {
            ModelPart.Cube c = ModelPartAccessor.getCuboids(part).get(0);
            return new Box(c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ, u, v);
        }

        float w() { return x1 - x0; }
        float d() { return z1 - z0; }

        /**
         * A block around the limb from y0 to y1 (part pixels), standing out by {@code grow} all round, its sides showing
         * the limb's texture rows r0..r1; the top shows the limb's top face if {@code first}, the bottom its bottom face
         * if {@code last} (otherwise they are hidden inside the next block, and show the sides' edge rows).
         */
        float[] block(float grow, float ya, float yb, float r0, float r1, boolean first, boolean last) {
            List<float[]> q = new ArrayList<>();
            float xa = x0 - grow, xb = x1 + grow, za = z0 - grow, zb = z1 + grow;
            sides(q, xa, xb, ya, yb, za, zb, r0, r1);
            float W = w(), D = d();
            if (first) face(q, Direction.UP, xa, xb, ya, ya, za, zb, u + D, v, u + D + W, v + D, true);
            else face(q, Direction.UP, xa, xb, ya, ya, za, zb, u + D, v + D + r0, u + D + W, v + D + r0 + 0.5f, false);
            if (last) face(q, Direction.DOWN, xa, xb, yb, yb, za, zb, u + D + W, v, u + D + 2 * W, v + D, true);
            else face(q, Direction.DOWN, xa, xb, yb, yb, za, zb, u + D, v + D + r1 - 0.5f, u + D + W, v + D + r1, false);
            return flat(q);
        }

        /**
         * The foot: a block from the ankle to the ground, sticking out in front. Its top and front show the leg's front
         * rows 10..12 (gen_body.py paints the toes there, tips at the bottom row), its sides and back the same rows of
         * those faces, its sole the leg's bottom face.
         */
        float[] foot() {
            List<float[]> q = new ArrayList<>();
            float xa = x0 - 0.18f, xb = x1 + 0.18f, ya = 10.2f, yb = y1, za = z0 - 2.6f, zb = z1 + 0.25f;
            sides(q, xa, xb, ya, yb, za, zb, 10f, 12f);
            float W = w(), D = d();
            // the top: across = x as on the front, from the heel (row 10) to the toe tips (row 12)
            face(q, Direction.UP, xa, xb, ya, ya, za, zb, u + D, v + D + 10f, u + D + W, v + D + 12f, true);
            face(q, Direction.DOWN, xa, xb, yb, yb, za, zb, u + D + W, v, u + D + 2 * W, v + D, true);
            return flat(q);
        }

        /** The four sides of a block, each showing rows r0..r1 of the limb's matching side. */
        private void sides(List<float[]> q, float xa, float xb, float ya, float yb, float za, float zb, float r0, float r1) {
            float W = w(), D = d(), vt = v + D + r0, vb = v + D + r1;
            face(q, Direction.NORTH, xa, xb, ya, yb, za, za, u + D, vt, u + D + W, vb, false);              // front
            face(q, Direction.SOUTH, xa, xb, ya, yb, zb, zb, u + 2 * D + 2 * W, vt, u + 2 * D + W, vb, false); // back
            face(q, Direction.WEST, xa, xa, ya, yb, za, zb, u + D, vt, u, vb, false);                     // x0 side
            face(q, Direction.EAST, xb, xb, ya, yb, za, zb, u + D + W, vt, u + 2 * D + W, vb, false);     // x1 side
        }
    }

    /**
     * One face as a quad with (x, y, z, u, v) corners and its normal. Side faces run (ua -> ub) along x or z (from the
     * min to the max coordinate, the back and x0 side reversed through their u values) and (va -> vb) down y; top
     * and bottom run (ua -> ub) along x and (va -> vb) from the back (max z) to the front ({@code zv}).
     */
    private static void face(List<float[]> q, Direction d, float xa, float xb, float ya, float yb, float za, float zb,
                             float ua, float va, float ub, float vb, boolean zv) {
        float[] f = new float[23];
        float[][] c;
        float[][] uv;
        switch (d) {
            case NORTH, SOUTH -> {
                float z = d == Direction.NORTH ? za : zb;
                c = new float[][]{{xa, ya, z}, {xb, ya, z}, {xb, yb, z}, {xa, yb, z}};
                uv = new float[][]{{ua, va}, {ub, va}, {ub, vb}, {ua, vb}};
            }
            case WEST, EAST -> {
                float x = d == Direction.WEST ? xa : xb;
                c = new float[][]{{x, ya, za}, {x, ya, zb}, {x, yb, zb}, {x, yb, za}};
                uv = new float[][]{{ua, va}, {ub, va}, {ub, vb}, {ua, vb}};
            }
            default -> {                                                                    // UP = the top (min y), DOWN = the bottom
                float y = ya;
                // v: back (zb) at va, front (za) at vb
                c = new float[][]{{xa, y, zb}, {xb, y, zb}, {xb, y, za}, {xa, y, za}};
                uv = new float[][]{{ua, va}, {ub, va}, {ub, vb}, {ua, vb}};
            }
        }
        for (int k = 0; k < 4; k++) {
            f[k * 5] = c[k][0];
            f[k * 5 + 1] = c[k][1];
            f[k * 5 + 2] = c[k][2];
            f[k * 5 + 3] = uv[k][0] / 64f;
            f[k * 5 + 4] = uv[k][1] / 64f;
        }
        // model space has y down: the top face's normal points to -y
        Vector3f n = switch (d) {
            case NORTH -> new Vector3f(0, 0, -1);
            case SOUTH -> new Vector3f(0, 0, 1);
            case WEST -> new Vector3f(-1, 0, 0);
            case EAST -> new Vector3f(1, 0, 0);
            case UP -> new Vector3f(0, -1, 0);
            default -> new Vector3f(0, 1, 0);
        };
        f[20] = n.x;
        f[21] = n.y;
        f[22] = n.z;
        q.add(f);
    }

    private static float[] flat(List<float[]> q) {
        float[] out = new float[q.size() * 23];
        for (int i = 0; i < q.size(); i++) System.arraycopy(q.get(i), 0, out, i * 23, 23);
        return out;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /** A limb's blocks, drawn through the limb's pose, its bend past the joint, and for feet a level turn at the ankle. */
    static final class BlockCube extends ModelPart.Cube {
        private final float[] quads;
        private final ModelPart parent;
        private final int joint;
        private final Matrix4f m = new Matrix4f();
        private final Matrix3f n = new Matrix3f();
        private final Matrix3f level = new Matrix3f();
        private final AxisAngle4f aa = new AxisAngle4f();
        private final Vector4f pos = new Vector4f();
        private final Vector3f nor = new Vector3f();

        BlockCube(float[] quads, ModelPart parent, int joint) {
            super(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 64, 64, EnumSet.noneOf(Direction.class));
            this.quads = quads;
            this.parent = parent;
            this.joint = joint;
        }

        @Override
        public void compile(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
            m.set(pose.pose()).scale(1 / 16f);
            n.set(pose.normal());
            Matrix4f bend = joint != STATIC ? bend(parent) : null;
            if (bend != null) {
                m.mul(bend);
                n.mul(new Matrix3f(bend));
            }
            if (joint == FOOT && grounded) {
                // flat on the ground against the shin's whole turn (swing and knee bend), about the ankle; a big turn
                // (a kick) is followed less and less, so a kicking foot still points
                if (bend != null) level.set(new Matrix3f(bend).transpose());
                else level.identity();
                level.rotateX(-parent.xRot);
                aa.set(level);
                float t = Mth.clamp((Math.abs(aa.angle) - 0.7f) / 0.6f, 0f, 1f);
                aa.angle *= 1 - t * t * (3 - 2 * t);
                m.translate(0, 10.5f, 0).rotate(aa).translate(0, -10.5f, 0);
                n.rotate(aa);
            }
            for (int i = 0; i < quads.length; i += 23) {
                n.transform(nor.set(quads[i + 20], quads[i + 21], quads[i + 22]));
                for (int k = 0; k < 4; k++) {
                    int o = i + k * 5;
                    m.transform(pos.set(quads[o], quads[o + 1], quads[o + 2], 1f));
                    vc.vertex(pos.x(), pos.y(), pos.z(), r, g, b, a, quads[o + 3], quads[o + 4], overlay, light, nor.x(), nor.y(), nor.z());
                }
            }
        }
    }

    private static boolean bendFailed;

    /** The limb's current bend from bendy-lib (pixels), or null when it is straight. */
    static Matrix4f bend(ModelPart limb) {
        if (bendFailed) return null;
        try {
            MutableCuboid cuboid = ModelPartAccessor.optionalGetCuboid(limb, 0).orElse(null);
            if (cuboid == null) return null;
            Tuple<String, ICuboid> active = cuboid.getActiveMutator();
            if (active == null || !(active.getB() instanceof BendableCuboid bc) || bc.getBend() == 0) return null;
            // bendy-lib's own transform for the far half (IBendable.applyBend): a turn of `bend` about a level axis at
            // `bendAxis` (turned by the bend direction), through the bend point
            float axis = bc.getBendAxis();
            Vector3f dir = new Vector3f(Mth.cos(axis), 0, Mth.sin(axis)).mul(new Matrix3f().set(bc.getBendDirection().getRotation()));
            float x = bc.getBendX(), y = bc.getBendY(), z = bc.getBendZ();
            return new Matrix4f().translate(x, y, z).rotate(bc.getBend(), dir).translate(-x, -y, -z);
        } catch (RuntimeException | LinkageError e) {
            DBZenith.LOGGER.warn("Segmented limbs: no bend from bendy-lib", e);
            bendFailed = true;
            return null;
        }
    }
}
