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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The fighter's physique in real 3D (CX-27, at the user's request: "muscles, hands, fists, feet and toes should be part
 * of the actual 3D model"). Muscles are chamfered blocks laid over the player model's six parts: pecs split down the
 * middle, a six-pack in three rows, serratus, obliques, lats flaring into a V, traps sloping from a thick neck, an upper
 * back and spinal erectors; deltoid caps, biceps, triceps and forearms; quads with the teardrop above the knee, glutes,
 * hamstrings, kneecaps and two-headed calves; clenched fists (palm, four fingers with knuckles, a thumb across the
 * front) and bare feet (heel, instep, ankle bones and five toes, the big one largest).
 * <p>
 * Every piece is a child of the part it sits on, so it follows every animation and every layer (skin, generated body,
 * gi, form overlays) draws it with its own texture: each face takes the texture of the part's face below it (box
 * projection onto the part's own skin layout), so painted shading, clothes and colours run over the muscles. The
 * pieces below the elbow and knee follow bendy-lib's bend of the limb (read from the limb's bent cuboid when it is
 * drawn), and the feet also turn about the ankle against the leg's swing so they stay flat on the ground while walking
 * (toes never sink in). Three builds (lean, athletic, bulky) differ in how far the muscles stand out.
 */
public final class Physique {
    private Physique() {}

    /** How far each build's muscles stand out (multiplier on the athletic build). */
    static final float[] OUT = {0.75f, 1f, 1.3f};
    /** How far each build's arms hang out from the body (radians), clearing the lats. */
    static final float[] SPREAD = {0.06f, 0.1f, 0.15f};

    static final int STATIC = 0, BENT = 1, FOOT = 2;
    private static final int ATHLETIC_INDEX = 1;
    private static final String PREFIX = "dbz_phys";

    /** Set per entity before it is drawn: the arms' outward hang, and whether its feet stand (flattened to the ground). */
    static float spread;
    static boolean grounded = true;

    // --------------------------------------------------------------------------------------------- attach and show

    /** Adds the three builds' pieces to a player-shaped model (once). */
    public static void attach(PlayerModel<?> m) {
        Map<String, ModelPart> body = BodyShape.childrenOf(m.body);
        if (body == null || body.containsKey(name(0, "torso"))) return;
        try {
            for (int b = 0; b < OUT.length; b++) {
                float p = OUT[b];
                Box torso = Box.of(m.body, 16, 16);
                add(body, name(b, "torso"), m.body, STATIC, torso, torso(p, false));
                add(body, name(b, "torso_up"), m.body, STATIC, torso, torso(p, true));   // the motion engine never bends the torso
                for (int side = 0; side < 2; side++) {
                    boolean right = side == 0;
                    ModelPart arm = right ? m.rightArm : m.leftArm, leg = right ? m.rightLeg : m.leftLeg;
                    Box ab = right ? Box.of(arm, 40, 16) : Box.of(arm, 32, 48);
                    Box lb = right ? Box.of(leg, 0, 16) : Box.of(leg, 16, 48);
                    float as = right ? -1 : 1;                                  // outward along x
                    Map<String, ModelPart> ac = BodyShape.childrenOf(arm), lc = BodyShape.childrenOf(leg);
                    add(ac, name(b, "arm_up"), arm, STATIC, ab, upperArm(p, ab, as));
                    add(ac, name(b, "arm_low"), arm, BENT, ab, lowerArm(p, ab, as));
                    add(lc, name(b, "leg_up"), leg, STATIC, lb, upperLeg(p, lb, as));
                    add(lc, name(b, "leg_low"), leg, BENT, lb, lowerLeg(p, lb, as));
                    add(lc, name(b, "foot"), leg, FOOT, lb, foot(lb, as));
                }
            }
        } catch (RuntimeException e) {
            DBZenith.LOGGER.warn("Could not build the 3D physique", e);
        }
        DBZenith.LOGGER.info("Physique: {} quads per figure", quads);
        quads = 0;
    }

    private static String name(int build, String part) {
        return PREFIX + build + "_" + part;
    }

    /** Quads in the athletic build, for the log (performance). */
    private static int quads;

    private static void add(Map<String, ModelPart> children, String name, ModelPart parent, int joint, Box box, Mesh mesh) {
        if (children == null) return;
        float[] baked = mesh.bake(box);
        if (name.startsWith(PREFIX + ATHLETIC_INDEX)) quads += baked.length / Q;
        ModelPart part = new ModelPart(List.of(new MeshCube(baked, parent, joint)), Map.of());
        part.visible = false;
        children.put(name, part);
    }

    /**
     * Shows one build's pieces (-1: none). {@code torso}, {@code legs}, {@code feet} false hide what vanilla armour
     * would have the muscles poke through.
     */
    public static void show(PlayerModel<?> m, int build, boolean torso, boolean legs, boolean feet) {
        for (int b = 0; b < OUT.length; b++) {
            boolean on = b == build;
            set(m.body, name(b, "torso"), on && torso);
            set(m.body, name(b, "torso_up"), on && torso);
            for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
                set(arm, name(b, "arm_up"), on && torso);
                set(arm, name(b, "arm_low"), on);
            }
            for (ModelPart leg : new ModelPart[]{m.rightLeg, m.leftLeg}) {
                set(leg, name(b, "leg_up"), on && legs);
                set(leg, name(b, "leg_low"), on && legs);
                set(leg, name(b, "foot"), on && feet);
            }
        }
        spread = build >= 0 ? SPREAD[build] : 0f;
    }

    private static void set(ModelPart parent, String name, boolean visible) {
        Map<String, ModelPart> c = BodyShape.childrenOf(parent);
        ModelPart p = c == null ? null : c.get(name);
        if (p != null) p.visible = visible;
    }

    /** After the model's own pose: the arms hang a little out from the body, past the lats (players and NPCs). */
    public static void spreadArms(PlayerModel<?> m) {
        if (spread == 0) return;
        m.rightArm.zRot += spread;
        m.leftArm.zRot -= spread;
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
    }

    // --------------------------------------------------------------------------------------------- the anatomy

    /*
     * Coordinates are model pixels (y down, -z the front). The torso is written for its right half (x < 0) and mirrored;
     * limbs are written along "a", the axis pointing away from the body (outward), and mirrored for each side.
     * Every face of a piece either stands clear of the part's own surface or lies inside it, never on it (no z-fighting).
     */

    private static Mesh torso(float p, boolean upper) {
        Mesh m = new Mesh();
        for (float s : new float[]{-1, 1}) {                                    // right half, then the mirrored left
            m.side(0, s);
            if (upper) {
                // pecs: two heavy slabs split down the middle, their lower edge overhanging the abs
                m.piece(0.15f, 4.25f, 0.4f, 4.45f, -2 - 1.15f * p, -0.4f, 0.7f).rot(-0.3f, -0.16f, 0).round().skip(Direction.SOUTH);
                // traps: sloping from the neck out to the shoulder
                m.piece(0.6f, 4.0f, -1.3f - 0.3f * p, 0.9f, -1.1f, 1.5f, 0.45f).rot(0, 0, 0.36f).skip(Direction.UP);
                // upper back (rhomboids, middle traps)
                m.piece(0.3f, 3.7f, 0.3f, 5.0f, 1.0f, 2 + 0.55f * p, 0.6f).skip(Direction.NORTH);
                // lats: wings flaring out under the arms, narrowing to the waist (the V)
                m.piece(2.4f, 4 + 0.7f * p, 1.0f, 8.2f, -0.9f, 2 + 0.25f * p, 0.7f).taper(1f, 0.5f, 2.4f).skip(Direction.WEST);
                // serratus: two short slanted fingers under the pec
                m.piece(3.0f, 4.15f + 0.2f * p, 4.6f, 5.5f, -2 - 0.32f * p, -0.6f, 0f).rot(0, 0, -0.25f).skip();
                m.piece(3.0f, 4.1f + 0.2f * p, 5.8f, 6.7f, -2 - 0.3f * p, -0.6f, 0f).rot(0, 0, -0.25f).skip();
            } else {
                // the six-pack: two columns either side of the centre line, three rows
                float[][] rows = {{4.95f, 6.4f}, {6.75f, 8.15f}, {8.5f, 9.9f}};
                for (int r = 0; r < 3; r++) {
                    float in = 0.22f, out = r == 2 ? 2.05f : 2.3f;
                    m.piece(in, out, rows[r][0], rows[r][1], -2 - 0.55f * p, -1.1f, 0.32f).skip(Direction.SOUTH);
                }
                // obliques: down the sides of the waist
                m.piece(2.75f, 4 + 0.3f * p, 6.6f, 10.7f, -2 - 0.3f * p, 0.9f, 0.4f).taper(1f, 0.85f, 2.75f).skip(Direction.WEST, Direction.SOUTH);
                // spinal erectors
                m.piece(0.25f, 1.75f, 6.0f, 11.3f, 1.0f, 2 + 0.42f * p, 0.35f).skip(Direction.NORTH);
            }
        }
        m.side(0, 1);
        if (upper) {
            // a thick neck under the head (its top tucked inside the head)
            m.piece(-2.1f, 2.1f, -2.3f, 0.4f, -1.9f, 1.7f, 0.5f).skip(Direction.DOWN, Direction.UP);
        } else {
            // the lower abdomen, narrowing into a V
            m.piece(-1.75f, 1.75f, 10.2f, 11.8f, -2 - 0.35f * p, -1.1f, 0.4f).taper(1f, 0.72f, 0f).skip(Direction.SOUTH);
        }
        return m;
    }

    private static Mesh upperArm(float p, Box b, float s) {
        Mesh m = new Mesh();
        float hw = b.halfX();
        m.side(b.cx(), s);
        // deltoid: a cap over the top and outside of the shoulder
        m.piece(-0.5f, hw + 0.6f * p, -2.3f - 0.4f * p, 3.0f, -2 - 0.45f * p, 2 + 0.45f * p, 1.0f).taper(0.78f, 0.6f, -0.5f).round().skip(Direction.WEST);
        // biceps (front) and triceps (back)
        m.piece(-hw + 0.45f, hw - 0.35f, 0.3f, 3.85f, -2 - 0.55f * p, -0.6f, 0.6f).round().skip(Direction.SOUTH);
        m.piece(-hw + 0.8f, hw - 0.7f, 1.1f, 3.1f, -2 - 0.9f * p, -1.0f, 0.5f).skip(Direction.SOUTH);
        m.piece(-hw + 0.3f, hw - 0.2f, -0.3f, 3.7f, 0.6f, 2 + 0.52f * p, 0.6f).skip(Direction.NORTH);
        return m;
    }

    private static Mesh lowerArm(float p, Box b, float s) {
        Mesh m = new Mesh();
        float hw = b.halfX();
        m.side(b.cx(), s);
        // forearm: thick below the elbow, narrowing to the wrist
        m.piece(-hw - 0.32f * p, hw + 0.4f * p, 4.35f, 7.6f, -2 - 0.35f * p, 2 + 0.32f * p, 0.8f).taper(0.88f, 0.84f, 0f).round().skip();
        // the fist (palm facing back, so a straight punch lands palm down): the hand...
        float a0 = -hw - 0.2f, a1 = hw + 0.2f;
        m.piece(a0, a1, 7.7f, 10.9f, -2.25f, 2.25f, 0.4f).skip();
        // ...four fingers across it, index finger nearest the body: the first bones form the flat face of the fist,
        // knuckles stand on its front edge, the middle bones fold up against the palm
        float w = (a1 - a0 - 0.24f) / 4f;
        for (int k = 0; k < 4; k++) {
            float f0 = a0 + k * (w + 0.08f), f1 = f0 + w;
            m.piece(f0, f1, 10.7f, 11.95f, -2.35f, 1.9f, 0f).skip();
            m.piece(f0 + 0.14f, f1 - 0.14f, 10.15f, 11.35f, -2.65f, -1.9f, 0f).skip();
            m.piece(f0 + 0.06f, f1 - 0.06f, 9.0f, 11.8f, 1.5f, 2.6f, 0f).skip();
        }
        // the thumb: up the inner side, then across the curled index and middle fingers
        m.piece(-hw - 0.85f, -hw + 0.15f, 8.2f, 10.2f, -1.0f, 2.2f, 0f).skip();
        m.piece(-hw - 0.6f, -hw + 1.95f, 9.45f, 10.65f, 2.3f, 3.05f, 0f).skip();
        return m;
    }

    private static Mesh upperLeg(float p, Box b, float s) {
        Mesh m = new Mesh();
        m.side(b.cx(), s);
        // quads: the outer sweep, the middle (rectus), the teardrop above the knee on the inside
        m.piece(-0.3f, 2 + 0.42f * p, 0.6f, 5.6f, -2 - 0.5f * p, 0.6f, 0.75f).taper(1f, 0.8f, -0.3f).round().skip(Direction.WEST, Direction.SOUTH);
        m.piece(-1.4f, 0.8f, 0.2f, 4.2f, -2 - 0.65f * p, -1.1f, 0.6f).skip(Direction.SOUTH);
        m.piece(-2 - 0.35f * p, -0.4f, 3.0f, 5.65f, -2 - 0.45f * p, -0.3f, 0.65f).skip(Direction.EAST, Direction.SOUTH);
        // hamstrings and glutes
        m.piece(-1.8f, 1.8f, 1.6f, 5.6f, 0.6f, 2 + 0.45f * p, 0.5f).skip(Direction.NORTH);
        m.piece(-1.85f, 2.25f, -0.6f, 2.5f, 0.4f, 2 + 0.85f * p, 0.8f).skip(Direction.NORTH, Direction.DOWN);
        // kneecap
        m.piece(-1.0f, 1.0f, 5.0f, 6.55f, -2 - 0.35f * p, -1.0f, 0.45f).skip(Direction.SOUTH);
        return m;
    }

    private static Mesh lowerLeg(float p, Box b, float s) {
        Mesh m = new Mesh();
        m.side(b.cx(), s);
        // calves: two heads at the back, the inner one lower and fuller
        m.piece(-1.8f, -0.05f, 6.6f, 9.6f, 0.9f, 2 + 0.75f * p, 0.65f).taper(1f, 0.72f, -0.9f).round().skip(Direction.NORTH);
        m.piece(0.1f, 1.75f, 6.4f, 9.0f, 0.9f, 2 + 0.6f * p, 0.65f).taper(1f, 0.72f, 0.9f).skip(Direction.NORTH);
        // shin (tibialis)
        m.piece(-0.6f, 1.5f, 6.5f, 9.5f, -2 - 0.25f * p, -1.0f, 0.35f).skip(Direction.SOUTH);
        return m;
    }

    private static Mesh foot(Box b, float s) {
        Mesh m = new Mesh();
        m.side(b.cx(), s);
        // instep rising to the ankle, the forefoot, the heel, the ankle bones
        m.piece(-2.15f, 2.15f, 9.9f, 11.4f, -2.7f, 0.5f, 0.45f).skip();
        m.piece(-2.2f, 2.2f, 10.95f, 12f, -3.55f, -0.8f, 0.35f).skip();
        m.piece(-1.5f, 1.5f, 10.6f, 12f, 1.3f, 2.6f, 0.45f).skip();
        m.piece(1.9f, 2.45f, 9.6f, 10.7f, -0.5f, 0.6f, 0f).skip();
        m.piece(-2.45f, -1.9f, 9.3f, 10.4f, -0.5f, 0.6f, 0f).skip();
        // five toes, the big toe on the inside, each shorter and lower than the last
        float[][] toes = {{1.15f, 1.45f, 10.85f}, {0.62f, 1.3f, 11.1f}, {0.62f, 1.1f, 11.2f}, {0.62f, 0.9f, 11.3f}, {0.62f, 0.7f, 11.4f}};
        float a = -2.1f;
        for (float[] t : toes) {
            m.piece(a, a + t[0], t[2], 12f, -3.25f - t[1], -3.2f, 0f).skip();
            a += t[0] + 0.12f;
        }
        return m;
    }

    // --------------------------------------------------------------------------------------------- mesh building

    /** The part's own box and where its skin layout starts, for projecting its texture onto the pieces. */
    record Box(float x0, float y0, float z0, float x1, float y1, float z1, float u, float v) {
        static Box of(ModelPart part, int u, int v) {
            List<ModelPart.Cube> cubes = ModelPartAccessor.getCuboids(part);
            ModelPart.Cube c = cubes.get(0);
            return new Box(c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ, u, v);
        }

        float cx() {
            return (x0 + x1) / 2;
        }

        float halfX() {
            return (x1 - x0) / 2;
        }

        /** The texture coordinate (pixels) of a point on a face with outward normal (nx, ny, nz). */
        void uv(float x, float y, float z, float nx, float ny, float nz, float[] out) {
            float w = x1 - x0, h = y1 - y0, d = z1 - z0;
            float lx = Mth.clamp(x - x0, 0.6f, w - 0.6f), ly = Mth.clamp(y - y0, 0.6f, h - 0.6f), lz = Mth.clamp(z - z0, 0.6f, d - 0.6f);   // off the outline pixels
            // only the four side faces' texture: the top and bottom of a part are often left unpainted by the
            // generated bodies (hidden under the head, or the waistband), and the skin beneath would show through
            float ax = Math.abs(nx), az = Math.abs(nz);
            if (az >= ax) {
                out[0] = nz <= 0 ? u + d + lx : u + 2 * d + 2 * w - lx;
            } else {
                out[0] = nx < 0 ? u + d - lz : u + d + w + lz;
            }
            out[1] = v + d + ly;
        }
    }

    /** Pieces gathered for one group, then baked into quads with positions, texture coordinates and normals. */
    static final class Mesh {
        private final List<Piece> pieces = new ArrayList<>();
        private float cx, sx = 1;

        /** Following pieces are written along "a" (outward) and placed at x = cx + sx * a. */
        void side(float cx, float sx) {
            this.cx = cx;
            this.sx = sx;
        }

        Piece piece(float a0, float a1, float y0, float y1, float z0, float z1, float bevel) {
            Piece p = new Piece(a0, a1, y0, y1, z0, z1, bevel, cx, sx);
            pieces.add(p);
            return p;
        }

        float[] bake(Box box) {
            List<float[]> quads = new ArrayList<>();
            for (Piece p : pieces) p.emit(box, quads);
            float[] out = new float[quads.size() * Q];
            for (int i = 0; i < quads.size(); i++) System.arraycopy(quads.get(i), 0, out, i * Q, Q);
            return out;
        }
    }

    /** Per quad: four corners of (x, y, z, u, v), then the normal. */
    static final int Q = 4 * 5 + 3;

    /** One chamfered block: a box with its edges and corners cut at 45 degrees, optionally tapered and turned. */
    static final class Piece {
        final float a0, a1, y0, y1, z0, z1, r, cx, sx;
        float rx, ry, rz, taperA = 1, taperZ = 1, anchor;
        final EnumSet<Direction> skip = EnumSet.noneOf(Direction.class);
        boolean smooth;

        /** Two cuts on each edge instead of one: a rounder muscle (for the big ones; three times the faces). */
        Piece round() {
            smooth = true;
            return this;
        }

        Piece(float a0, float a1, float y0, float y1, float z0, float z1, float bevel, float cx, float sx) {
            this.a0 = Math.min(a0, a1);
            this.a1 = Math.max(a0, a1);
            this.y0 = Math.min(y0, y1);
            this.y1 = Math.max(y0, y1);
            this.z0 = Math.min(z0, z1);
            this.z1 = Math.max(z0, z1);
            float most = Math.min(this.a1 - this.a0, Math.min(this.y1 - this.y0, this.z1 - this.z0)) * 0.45f;
            this.r = Math.min(bevel, most);
            this.cx = cx;
            this.sx = sx;
        }

        /** Turned about its centre (radians, in the outward frame). */
        Piece rot(float x, float y, float z) {
            rx = x;
            ry = y;
            rz = z;
            return this;
        }

        /** Narrowed towards its lower end: widths along a and z scaled to these at the bottom, a about {@code anchor}. */
        Piece taper(float z, float a, float anchor) {
            taperZ = z;
            taperA = a;
            this.anchor = anchor;
            return this;
        }

        /** Leaves out faces lying inside the part (in the outward frame; null keeps all). */
        Piece skip(Direction... d) {
            for (Direction x : d) if (x != null) skip.add(x);
            return this;
        }

        void emit(Box box, List<float[]> out) {
            float[][] grid = new float[3][];
            grid[0] = steps(a0, a1);
            grid[1] = steps(y0, y1);
            grid[2] = steps(z0, z1);
            float[] lo = {a0, y0, z0}, hi = {a1, y1, z1};
            float[] centre = place(new float[]{(a0 + a1) / 2, (y0 + y1) / 2, (z0 + z1) / 2});
            for (Direction d : Direction.values()) {
                if (skip.contains(d)) continue;
                int axis = d.getAxis().ordinal();                               // X 0, Y 1, Z 2
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                float fixed = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? hi[axis] : lo[axis];
                for (int i = 0; i < grid[u].length - 1; i++) {
                    for (int j = 0; j < grid[v].length - 1; j++) {
                        float[][] c = new float[4][];
                        int[][] ij = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                        for (int k = 0; k < 4; k++) {
                            float[] pt = new float[3];
                            pt[axis] = fixed;
                            pt[u] = grid[u][ij[k][0]];
                            pt[v] = grid[v][ij[k][1]];
                            c[k] = place(round(pt));
                        }
                        quad(c, box, out, centre);
                    }
                }
            }
        }

        private float[] steps(float lo, float hi) {
            if (r < 0.01f) return new float[]{lo, hi};                       // a plain block (small pieces)
            return smooth ? new float[]{lo, lo + r * 0.3f, lo + r, hi - r, hi - r * 0.3f, hi} : new float[]{lo, lo + r, hi - r, hi};
        }

        /** The chamfer: pulled in towards the inner box by the bevel, at 45 degrees on edges and corners. */
        private float[] round(float[] p) {
            float[] lo = {a0 + r, y0 + r, z0 + r}, hi = {a1 - r, y1 - r, z1 - r};
            float[] in = new float[3], d = new float[3];
            float len = 0;
            for (int k = 0; k < 3; k++) {
                in[k] = Mth.clamp(p[k], lo[k], hi[k]);
                d[k] = p[k] - in[k];
                len += d[k] * d[k];
            }
            len = Mth.sqrt(len);
            if (len < 1e-5f) return p;
            for (int k = 0; k < 3; k++) p[k] = in[k] + d[k] / len * r;
            return p;
        }

        /** Taper, turn, then into the part's frame (mirrored for the side). */
        private float[] place(float[] p) {
            float f = (p[1] - y0) / Math.max(1e-4f, y1 - y0);
            float ka = Mth.lerp(f, 1f, taperA), kz = Mth.lerp(f, 1f, taperZ);
            float a = anchor + (p[0] - anchor) * ka, y = p[1], z = (z0 + z1) / 2 + (p[2] - (z0 + z1) / 2) * kz;
            if (rx != 0 || ry != 0 || rz != 0) {
                float ca = (a0 + a1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
                Vector3f v = new Vector3f(a - ca, y - cy, z - cz);
                v.rotateX(rx).rotateY(ry).rotateZ(rz);
                a = ca + v.x;
                y = cy + v.y;
                z = cz + v.z;
            }
            return new float[]{cx + sx * a, y, z};
        }

        private void quad(float[][] c, Box box, List<float[]> out, float[] centre) {
            float ex = c[2][0] - c[0][0], ey = c[2][1] - c[0][1], ez = c[2][2] - c[0][2];
            float fx = c[3][0] - c[1][0], fy = c[3][1] - c[1][1], fz = c[3][2] - c[1][2];
            float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
            float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-5f) return;                                            // a collapsed corner patch
            nx /= len;
            ny /= len;
            nz /= len;
            // outward, and the corners turning as vanilla's do about their outward normal
            float mx = (c[0][0] + c[2][0]) / 2 - centre[0], my = (c[0][1] + c[2][1]) / 2 - centre[1], mz = (c[0][2] + c[2][2]) / 2 - centre[2];
            float[][] q = c;
            if (nx * mx + ny * my + nz * mz < 0) {
                q = new float[][]{c[0], c[3], c[2], c[1]};
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float[] data = new float[Q];
            float[] uv = new float[2];
            for (int k = 0; k < 4; k++) {
                box.uv(q[k][0], q[k][1], q[k][2], nx, ny, nz, uv);
                System.arraycopy(new float[]{q[k][0], q[k][1], q[k][2], uv[0] / 64f, uv[1] / 64f}, 0, data, k * 5, 5);
            }
            data[20] = nx;
            data[21] = ny;
            data[22] = nz;
            out.add(data);
        }
    }

    // --------------------------------------------------------------------------------------------- drawing

    /**
     * A part's pieces, drawn when the part is: through the part's pose, the limb's bend for the pieces past the joint,
     * and for the feet a turn about the ankle that keeps them level on the ground.
     */
    static final class MeshCube extends ModelPart.Cube {
        private final float[] quads;
        private final ModelPart parent;
        private final int joint;
        private final Matrix4f m = new Matrix4f();
        private final Matrix3f n = new Matrix3f();
        private final Vector4f pos = new Vector4f();
        private final Vector3f nor = new Vector3f();
        private final Matrix3f level = new Matrix3f();
        private final org.joml.AxisAngle4f aa = new org.joml.AxisAngle4f();

        MeshCube(float[] quads, ModelPart parent, int joint) {
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
                // level with the ground against the shin's whole turn (the leg's swing, then the knee's bend), about the
                // ankle (pixels: y 10); a big turn (a kick) is followed less and less, so a kicking foot still points
                if (bend != null) level.set(new Matrix3f(bend).transpose());
                else level.identity();
                level.rotateX(-parent.xRot);                                    // (Rx(swing) Rbend)^-1
                aa.set(level);
                aa.angle *= 1 - smooth(0.7f, 1.3f, Math.abs(aa.angle));
                m.translate(0, 10, 0).rotate(aa).translate(0, -10, 0);
                n.rotate(aa);
            }
            for (int i = 0; i < quads.length; i += Q) {
                n.transform(nor.set(quads[i + 20], quads[i + 21], quads[i + 22]));
                for (int k = 0; k < 4; k++) {
                    int o = i + k * 5;
                    m.transform(pos.set(quads[o], quads[o + 1], quads[o + 2], 1f));
                    vc.vertex(pos.x(), pos.y(), pos.z(), r, g, b, a, quads[o + 3], quads[o + 4], overlay, light, nor.x(), nor.y(), nor.z());
                }
            }
        }
    }

    static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
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
            DBZenith.LOGGER.warn("Physique: no bend from bendy-lib", e);
            bendFailed = true;
            return null;
        }
    }
}
