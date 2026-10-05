package com.dbzenith.client.render;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The wind in a fighter's hair: a spring-damped push from how they move (hair streams back when running and flying,
 * lifts when falling, swings when the head turns, breathes when idle, rises and flickers in an aura). The spring
 * overshoots a little, so hair bounces when you stop. Returned in head space, with the head-space direction of
 * gravity so long hair keeps hanging when the head tilts.
 */
public final class HairWind {
    /** Wind and gravity in head space, and how stiff the hair is (transformed hair is stiff). */
    public record State(Vec3 wind, Vec3 gravity, float stiffness) {
        public static final State STILL = new State(Vec3.ZERO, new Vec3(0, 1, 0), 1f);
    }

    private static final class Spring {
        double x, y, z, vx, vy, vz;
        float time = Float.NaN;
    }

    private static final Map<AbstractClientPlayer, Spring> SPRINGS = new WeakHashMap<>();
    private static final double K = 0.45, DAMP = 0.55, MAX = 2.6;

    private HairWind() {}

    public static State of(AbstractClientPlayer p, ModelPart head, PublicStatePacket state, boolean transformed, float partial) {
        Quaternionf toHead = new Quaternionf().rotationZYX(head.zRot, head.yRot, head.xRot).conjugate();
        Vector3f g = toHead.transform(new Vector3f(0, 1, 0));
        Vec3 gravity = new Vec3(g.x, g.y, g.z);
        float stiff = transformed ? 0.45f : 1f;
        if (!DBZConfig.CLIENT.hairPhysics.get()) return new State(Vec3.ZERO, gravity, stiff);

        float time = p.tickCount + partial;
        // the push, in model space (x = the wearer's left, y down, z behind)
        double vx = p.getX() - p.xo, vy = p.getY() - p.yo, vz = p.getZ() - p.zo;
        double yaw = Math.toRadians(Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot));
        double fwd = -vx * Math.sin(yaw) + vz * Math.cos(yaw), left = vx * Math.cos(yaw) + vz * Math.sin(yaw);
        double k = 2.4;
        double tx = -left * k, ty = vy * k, tz = fwd * k;
        tx += Mth.clamp(Mth.wrapDegrees(p.yHeadRot - p.yHeadRotO) * 0.05, -1.2, 1.2);   // turning: the hair lags behind
        double seed = p.getId() * 1.7;
        tx += Math.sin(time * 0.071 + seed) * 0.12;                                   // breathing sway
        tz += 0.08 + Math.sin(time * 0.053 + seed) * 0.08;
        boolean aura = state != null && (state.powering() || transformed);
        if (aura) {                                                                   // the aura's updraft, flickering
            double heat = state.powering() ? 1.3 : 0.55;
            ty -= heat * (0.8 + 0.2 * Math.sin(time * 0.9 + seed));
            tx += Math.sin(time * 1.37 + seed) * 0.25 * heat;
            tz += Math.sin(time * 1.11 + seed * 2) * 0.25 * heat;
        }
        double mag = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (mag > 1e-6) {                                                             // soft cap
            double s = MAX * Math.tanh(mag / MAX) / mag;
            tx *= s;
            ty *= s;
            tz *= s;
        }

        Spring sp = SPRINGS.computeIfAbsent(p, q -> new Spring());
        if (Float.isNaN(sp.time) || time < sp.time || time - sp.time > 20) {
            sp.x = tx;
            sp.y = ty;
            sp.z = tz;
            sp.vx = sp.vy = sp.vz = 0;
        } else {
            double dt = time - sp.time;
            while (dt > 1e-4) {                                                       // semi-implicit steps of at most a quarter tick
                double h = Math.min(0.25, dt);
                sp.vx += (K * (tx - sp.x) - DAMP * sp.vx) * h;
                sp.vy += (K * (ty - sp.y) - DAMP * sp.vy) * h;
                sp.vz += (K * (tz - sp.z) - DAMP * sp.vz) * h;
                sp.x += sp.vx * h;
                sp.y += sp.vy * h;
                sp.z += sp.vz * h;
                dt -= h;
            }
        }
        sp.time = time;
        Vector3f w = toHead.transform(new Vector3f((float) sp.x, (float) sp.y, (float) sp.z));
        return new State(new Vec3(w.x, w.y, w.z), gravity, stiff);
    }
}
