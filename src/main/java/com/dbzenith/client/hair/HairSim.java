package com.dbzenith.client.hair;

import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hair that moves. Every lock is a chain of knots, its root fixed to the scalp, simulated in the head's own frame as
 * the head moves through the world: when the fighter turns, runs, stops or jumps the knots keep going where they were
 * going, so the hair lags, swings and settles; gravity pulls it the way down really is however the head tilts; moving
 * air streams it back; an aura lifts it. Each knot springs back towards its place in the style (stiff near the root,
 * looser to the tip; Super Saiyan hair barely gives, long hair swings), keeps its length, and is pushed out of the
 * head and the back. Units are head pixels and ticks.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.dbzenith.DBZenith.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HairSim {
    private static final int N = HairLock.KNOTS;
    private static final float GRAVITY = .7f, DRAG = .08f, UPDRAFT = .8f;
    /** How much of the head's own motion the hair lags behind (0: none, 1: all of it). */
    private static final float INERTIA = .3f;
    private static final double SIM_DISTANCE = 48;
    private static int frameNo;

    /** One fighter's hair in motion. */
    private static final class Body {
        int frameNo = -1;
        HairShape shape;
        float[] p = new float[0], prev = new float[0];
        float time = Float.NaN;
        final Matrix3f frame = new Matrix3f();
        double x, y, z;
    }

    private static final Map<LivingEntity, Body> BODIES = new WeakHashMap<>();
    private static final Matrix3f M = new Matrix3f(), INV = new Matrix3f(), HEAD = new Matrix3f(), BODY = new Matrix3f();
    private static final Vector3f V = new Vector3f();
    private static final float[] G = new float[3], AIR = new float[3], UP = new float[3];

    private HairSim() {}

    /**
     * The knot positions for this frame (3 per knot, locks in order), or null to draw the hair at rest (physics off, too
     * far away, nothing to move). {@code aura} 0..1 lifts the hair.
     */
    public static float[] step(LivingEntity e, ModelPart head, HairShape shape, float partial, float aura) {
        if (!DBZConfig.CLIENT.hairPhysics.get() || shape.locks().isEmpty()) return null;
        Body known = BODIES.get(e);
        int need = shape.locks().size() * N * 3;
        // The same fighter drawn again this frame (the HUD portrait, the inventory): show the hair as it is, and never
        // let that other pose count as the head having moved, or the next frame would fling the hair round.
        if (com.dbzenith.client.ui.PortraitRenderer.isDrawing() || (known != null && known.frameNo == frameNo)) {
            return known != null && known.p.length == need ? known.p : null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != null && e.distanceToSqr(mc.getCameraEntity()) > SIM_DISTANCE * SIM_DISTANCE) {
            BODIES.remove(e);
            return null;
        }
        Body b = known != null ? known : new Body();
        if (known == null) BODIES.put(e, b);
        b.frameNo = frameNo;
        float t = e.tickCount + partial;
        double ex = Mth.lerp(partial, e.xo, e.getX()), ey = Mth.lerp(partial, e.yo, e.getY()), ez = Mth.lerp(partial, e.zo, e.getZ());
        frame(e, head, partial);                                                    // head space -> world: M
        boolean reset = b.p.length != need || Float.isNaN(b.time) || Math.abs(t - b.time) > 20
                || (ex - b.x) * (ex - b.x) + (ey - b.y) * (ey - b.y) + (ez - b.z) * (ez - b.z) > 16;   // a teleport
        if (reset) {
            if (b.p.length != need) { b.p = new float[need]; b.prev = new float[need]; }
            rest(shape, b.p);
            System.arraycopy(b.p, 0, b.prev, 0, need);
        } else if (t > b.time) {
            float dt = Math.min(3, t - b.time);
            // carry last frame's knots part of the way into this frame's head space: the hair lags a little behind a
            // turn or a step, then follows (all the way would whip it round with every turn of the head)
            M.invert(INV);
            float mx = (float) ((b.x - ex) * 16), my = (float) ((b.y - ey) * 16), mz = (float) ((b.z - ez) * 16);
            carry(b.p, b.frame, mx, my, mz);
            carry(b.prev, b.frame, mx, my, mz);
            // the forces, in head space
            INV.transform(V.set(0, -1, 0)); G[0] = V.x; G[1] = V.y; G[2] = V.z;
            INV.transform(V.set(0, 1, 0)); UP[0] = V.x; UP[1] = V.y; UP[2] = V.z;
            float vx = (float) (ex - b.x) / dt * 16, vy = (float) (ey - b.y) / dt * 16, vz = (float) (ez - b.z) / dt * 16;
            INV.transform(V.set(-vx, -vy, -vz)); AIR[0] = V.x; AIR[1] = V.y; AIR[2] = V.z;
            int steps = Math.max(1, (int) Math.ceil(dt / .34f));
            float h = dt / steps;
            for (int s = 0; s < steps; s++) integrate(shape, b, h, aura, t + s * h, e.getId());
        }
        b.shape = shape;
        b.time = t;
        b.frame.set(M);
        b.x = ex; b.y = ey; b.z = ez;
        return b.p;
    }

    /** Head space to world space this frame: the body's turn, the model's flip, then the head's own turn. */
    private static void frame(LivingEntity e, ModelPart head, float partial) {
        float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        BODY.rotationY((float) Math.toRadians(180 - yaw)).scale(-1, -1, 1);
        HEAD.rotationZYX(head.zRot, head.yRot, head.xRot);
        M.set(BODY).mul(HEAD);
    }

    /** Re-expresses points given in an older head frame in this one, the head having moved by (mx, my, mz) world px. */
    private static void carry(float[] p, Matrix3f old, float mx, float my, float mz) {
        for (int i = 0; i < p.length; i += 3) {
            old.transform(V.set(p[i], p[i + 1], p[i + 2]));
            V.add(mx, my, mz);
            INV.transform(V);
            p[i] += (V.x - p[i]) * INERTIA;
            p[i + 1] += (V.y - p[i + 1]) * INERTIA;
            p[i + 2] += (V.z - p[i + 2]) * INERTIA;
        }
    }

    /** Counts the frames the world draws, so the hair steps once a frame however often a fighter is drawn. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onFrame(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) frameNo++;
    }

    private static void rest(HairShape shape, float[] out) {
        int li = 0;
        for (HairLock l : shape.locks()) {
            for (int i = 0; i < N; i++) {
                int o = (li * N + i) * 3;
                out[o] = (float) l.x(i); out[o + 1] = (float) l.y(i); out[o + 2] = (float) l.z(i);
            }
            li++;
        }
    }

    private static void integrate(HairShape shape, Body b, float h, float aura, float t, int seed) {
        float[] p = b.p, q = b.prev;
        float damp = (float) Math.pow(.84, h);                                       // hair is heavily damped: it sways, it doesn't ring
        float flutter = aura > 0 ? aura * (.6f + .4f * Mth.sin(t * 1.7f + seed)) : 0;
        int li = 0;
        for (HairLock l : shape.locks()) {
            float stiff = l.stiff(), weight = l.weight();
            int base = li * N * 3;
            p[base] = q[base] = (float) l.x(0); p[base + 1] = q[base + 1] = (float) l.y(0); p[base + 2] = q[base + 2] = (float) l.z(0);
            for (int i = 1; i < N; i++) {
                int o = base + i * 3;
                float u = i / (float) (N - 1);
                for (int c = 0; c < 3; c++) {
                    float v = (p[o + c] - q[o + c]) / h;
                    float a = GRAVITY * weight * G[c] + DRAG * weight * (AIR[c] - v) + UPDRAFT * weight * flutter * UP[c];
                    float next = p[o + c] + (p[o + c] - q[o + c]) * damp + a * h * h;
                    q[o + c] = p[o + c];
                    p[o + c] = next;
                }
                // spring back towards the style: firm at the root, looser at the tip
                float k = (float) (1 - Math.pow(1 - Math.min(.95, (.12f + stiff) * (1 - .45 * u) * .9), h));
                p[o] += ((float) l.x(i) - p[o]) * k;
                p[o + 1] += ((float) l.y(i) - p[o + 1]) * k;
                p[o + 2] += ((float) l.z(i) - p[o + 2]) * k;
            }
            float arc = 0;                                                          // never swing far from the style:
            for (int i = 1; i < N; i++) {                                           // a stiff lock a little, a loose one more
                int o = base + i * 3;
                arc += (float) Math.sqrt(sq(l.x(i) - l.x(i - 1)) + sq(l.y(i) - l.y(i - 1)) + sq(l.z(i) - l.z(i - 1)));
                float max = (.18f + .42f * (1 - stiff)) * arc;
                float dx = p[o] - (float) l.x(i), dy = p[o + 1] - (float) l.y(i), dz = p[o + 2] - (float) l.z(i);
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > max && d > 1e-5f) {
                    float f = max / d;
                    p[o] = (float) l.x(i) + dx * f; p[o + 1] = (float) l.y(i) + dy * f; p[o + 2] = (float) l.z(i) + dz * f;
                    q[o] += (p[o] - q[o]) * .6f; q[o + 1] += (p[o + 1] - q[o + 1]) * .6f; q[o + 2] += (p[o + 2] - q[o + 2]) * .6f;
                }
            }
            for (int i = 1; i < N; i++) {                                           // keep each segment its length
                int o = base + i * 3, a = o - 3;
                float rx = (float) (l.x(i) - l.x(i - 1)), ry = (float) (l.y(i) - l.y(i - 1)), rz = (float) (l.z(i) - l.z(i - 1));
                float len = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                float dx = p[o] - p[a], dy = p[o + 1] - p[a + 1], dz = p[o + 2] - p[a + 2];
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 1e-5f) {
                    float f = len / d;
                    p[o] = p[a] + dx * f; p[o + 1] = p[a + 1] + dy * f; p[o + 2] = p[a + 2] + dz * f;
                }
                collide(p, o);
                dx = p[o] - p[a]; dy = p[o + 1] - p[a + 1]; dz = p[o + 2] - p[a + 2];   // and its length again after the push
                d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 1e-5f) {
                    float f = len / d;
                    p[o] = p[a] + dx * f; p[o + 1] = p[a + 1] + dy * f; p[o + 2] = p[a + 2] + dz * f;
                }
            }
            li++;
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Out of the head (a box) and out of the back (the torso, through the head's turn). */
    private static void collide(float[] p, int o) {
        float x = p[o], y = p[o + 1], z = p[o + 2];
        if (x > -4.3f && x < 4.3f && y > -8.3f && y < .2f && z > -4.3f && z < 4.3f) {
            float dx0 = x + 4.3f, dx1 = 4.3f - x, dy0 = y + 8.3f, dz0 = z + 4.3f, dz1 = 4.3f - z;
            float m = Math.min(Math.min(dx0, dx1), Math.min(dy0, Math.min(dz0, dz1)));
            if (m == dx0) x = -4.3f; else if (m == dx1) x = 4.3f; else if (m == dy0) y = -8.3f; else if (m == dz0) z = -4.3f; else z = 4.3f;
        }
        HEAD.transform(V.set(x, y, z));                                             // head space -> model space
        if (V.x > -4.4f && V.x < 4.4f && V.y > 0 && V.y < 12.5f && V.z > -2.5f && V.z < 2.5f) {
            float dx0 = V.x + 4.4f, dx1 = 4.4f - V.x, dz0 = V.z + 2.5f, dz1 = 2.5f - V.z;
            float m = Math.min(Math.min(dx0, dx1), Math.min(dz0, dz1));
            if (m == dz1) V.z = 2.5f; else if (m == dz0) V.z = -2.5f; else if (m == dx0) V.x = -4.4f; else V.x = 4.4f;
            HEAD.transformTranspose(V);                                             // back to head space
            x = V.x; y = V.y; z = V.z;
        }
        p[o] = x; p[o + 1] = y; p[o + 2] = z;
    }

    /**
     * For the offline checks: steps a shape's hair {@code steps} times by {@code h} ticks from {@code p} / {@code prev}
     * under the given gravity and airflow (head space), the head upright.
     */
    static void simulate(HairShape shape, float[] p, float[] prev, float[] gravity, float[] air, float aura, float h, int steps) {
        System.arraycopy(gravity, 0, G, 0, 3);
        System.arraycopy(air, 0, AIR, 0, 3);
        UP[0] = 0; UP[1] = -1; UP[2] = 0;
        HEAD.identity();
        Body b = new Body();
        b.p = p;
        b.prev = prev;
        for (int s = 0; s < steps; s++) integrate(shape, b, h, aura, s * h, 7);
    }

    /** The rest positions of a shape's knots (for the offline checks). */
    static float[] restOf(HairShape shape) {
        float[] out = new float[shape.locks().size() * N * 3];
        rest(shape, out);
        return out;
    }

    /** For the offline checks: the head turning {@code degrees} about its vertical axis under the hair, as a frame would. */
    static void turn(float[] p, float[] prev, float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        for (float[] a : new float[][]{p, prev}) for (int i = 0; i < a.length; i += 3) {
            float x = a[i] * c - a[i + 2] * s, z = a[i] * s + a[i + 2] * c;
            a[i] += (x - a[i]) * INERTIA;
            a[i + 2] += (z - a[i + 2]) * INERTIA;
        }
    }
}
