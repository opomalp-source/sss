package com.dbzenith.client.render;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Things that hang and swing (CX-44): capes, sash ends, tails. Each is a short chain of segments with a pitch (swinging
 * back) and a roll (swinging sideways) per segment, on springs, driven by how the body actually moves: running and
 * flying stream it back, falling lifts it, strafing and turning fling it sideways, speeding up and stopping swing it
 * the other way, landing and bobbing bounce it; at speed the free end flutters. The root follows quickly and each
 * segment further out lags the one before it, so a wave runs down the chain. Stepped once per game tick, at render
 * time (whole ticks since the last step), and interpolated between ticks.
 */
public final class Cloth {
    /** How a kind of chain behaves. */
    public record Kind(int segments, float rest, float streamGain, float streamMax, float sideGain, float turnGain,
                       float stiffness, float damping, float follow, float flutter, float sway) {}

    /** A cape: heavy, streams out flat behind a flier, a slow wave down it. */
    public static final Kind CAPE = new Kind(4, 0.06f, 3.4f, 1.45f, 2.2f, 0.035f, 0.16f, 0.30f, 0.55f, 0.10f, 0.03f);
    /** A gi's sash ends: light, quick, flapping. */
    public static final Kind SASH = new Kind(2, -0.08f, 4.5f, 1.25f, 3.0f, 0.05f, 0.24f, 0.26f, 0.45f, 0.28f, 0.05f);
    /** A Saiyan tail: springy and alive, swaying even at rest. */
    public static final Kind TAIL = new Kind(5, 0f, 4.5f, 1.05f, 2.6f, 0.04f, 0.20f, 0.28f, 0.5f, 0.12f, 0.10f);

    /** One chain's state. */
    public static final class Chain {
        final Kind kind;
        final float[] p, pv, r, rv, pO, rO;
        int lastTick = Integer.MIN_VALUE;
        Vec3 last;
        float lastYaw, lastForward, lastUp;
        float partial;

        Chain(Kind kind) {
            this.kind = kind;
            int n = kind.segments;
            p = new float[n]; pv = new float[n]; r = new float[n]; rv = new float[n]; pO = new float[n]; rO = new float[n];
        }

        /** Absolute pitch of segment {@code i} (0 hanging straight down, + swung back), interpolated. */
        public float pitch(int i) {
            return Mth.lerp(partial, pO[i], p[i]);
        }

        public float roll(int i) {
            return Mth.lerp(partial, rO[i], r[i]);
        }

        /** Pitch of segment {@code i} relative to the one it hangs from (what a model child part needs). */
        public float relPitch(int i) {
            return i == 0 ? pitch(0) : pitch(i) - pitch(i - 1);
        }

        public float relRoll(int i) {
            return i == 0 ? roll(0) : roll(i) - roll(i - 1);
        }

        public int segments() {
            return kind.segments;
        }

        void update(Entity e, float age) {
            int tick = Mth.floor(age);
            partial = age - tick;
            float yaw = e instanceof LivingEntity l ? l.yBodyRot : e.getYRot();
            if (lastTick == Integer.MIN_VALUE || tick - lastTick > 40 || tick < lastTick) {   // new, or long unseen: start at rest
                lastTick = tick;
                last = e.position();
                lastYaw = yaw;
                for (int i = 0; i < p.length; i++) p[i] = pO[i] = kind.rest;
                return;
            }
            int steps = Math.min(4, tick - lastTick);
            if (steps <= 0) return;
            Vec3 now = e.position();
            Vec3 v = now.subtract(last).scale(1.0 / steps);
            float yawRate = Mth.wrapDegrees(yaw - lastYaw) / steps;
            for (int s = 0; s < steps; s++) step(v, yaw, yawRate, lastTick + s + 1, e.getId(), e.isInWater());
            lastTick = tick;
            last = now;
            lastYaw = yaw;
        }

        private void step(Vec3 v, float yaw, float yawRate, int tick, int seed, boolean water) {
            Kind k = kind;
            double yr = Math.toRadians(yaw);
            float forward = (float) (-v.x * Math.sin(yr) + v.z * Math.cos(yr));
            float side = (float) (v.x * Math.cos(yr) + v.z * Math.sin(yr));         // + to the body's left
            float up = (float) v.y;
            float accel = forward - lastForward, jolt = up - lastUp;
            lastForward = forward;
            lastUp = up;
            float speed = (float) v.length();
            float drag = water ? 0.4f : 1f;

            // where the wind pushes the chain: back with forward speed, up when falling, sideways when drifting or turning
            float pitchTarget = k.rest + Mth.clamp(forward * k.streamGain * drag + Math.max(0, -up) * 1.6f, -0.35f, k.streamMax);
            float rollTarget = Mth.clamp(side * k.sideGain - yawRate * k.turnGain, -0.7f, 0.7f);
            int n = p.length;
            for (int i = 0; i < n; i++) {
                pO[i] = p[i];
                rO[i] = r[i];
            }
            for (int i = 0; i < n; i++) {
                float tip = n == 1 ? 1 : i / (float) (n - 1);
                float stiff = k.stiffness * (1 - 0.35f * tip);                     // the free end is lazier
                // each segment wants what the wind wants, but is held toward the one it hangs from
                float pt = i == 0 ? pitchTarget : Mth.lerp(k.follow, pitchTarget, p[i - 1]);
                float rt = i == 0 ? rollTarget : Mth.lerp(k.follow, rollTarget, r[i - 1]);
                // alive: a lazy sway at rest, a flutter at speed toward the free end
                float t = tick + seed * 13;
                pt += k.sway * Mth.sin(t * 0.09f - i * 0.8f) + k.flutter * tip * Math.min(1, speed * 2.5f) * Mth.sin(t * 0.95f - i * 1.3f);
                rt += k.sway * 0.8f * Mth.sin(t * 0.07f - i * 0.9f + 1.7f) + k.flutter * 0.6f * tip * Math.min(1, speed * 2.5f) * Mth.sin(t * 0.8f - i * 1.1f + 0.5f);
                // inertia: speeding up swings it back, stopping throws it forward; a jolt up or down bounces it
                pv[i] += -accel * 2.2f * (0.6f + tip) + jolt * 1.4f * tip;
                pv[i] += (pt - p[i]) * stiff - pv[i] * k.damping;
                rv[i] += (rt - r[i]) * stiff - rv[i] * k.damping;
                pv[i] = Mth.clamp(pv[i], -0.5f, 0.5f);
                rv[i] = Mth.clamp(rv[i], -0.5f, 0.5f);
                p[i] = Mth.clamp(p[i] + pv[i], -0.6f, 1.6f);
                r[i] = Mth.clamp(r[i] + rv[i], -0.9f, 0.9f);
            }
        }
    }

    private static final Map<Entity, Map<String, Chain>> CHAINS = new WeakHashMap<>();

    private Cloth() {}

    /** The chain {@code key} on this entity, stepped up to {@code ageInTicks}. */
    public static Chain chain(Entity e, String key, Kind kind, float ageInTicks) {
        Chain c = CHAINS.computeIfAbsent(e, k -> new HashMap<>()).computeIfAbsent(key, k -> new Chain(kind));
        c.update(e, ageInTicks);
        return c;
    }
}
