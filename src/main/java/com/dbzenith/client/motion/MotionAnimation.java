package com.dbzenith.client.motion;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.util.Mth;

/**
 * The engine's output as a playerAnimator animation (CX-18). For players it is a layer under the combat actions; for
 * NPCs {@link MotionModel} runs it through playerAnimator's own part applier, so both get the same pose, elbow and knee
 * bends included. The engine owns the limbs while it is on; the head's own look is kept and only nudged; an arm busy
 * with an item fades back to vanilla.
 */
public final class MotionAnimation implements IAnimation {
    private Motion motion;
    private Pose pose;
    private float weight;
    private float pt;

    MotionAnimation(Motion motion) {
        this.motion = motion;
    }

    /** The animation for this entity (made once, kept with its motion). */
    public static MotionAnimation of(Motion m) {
        if (m.animation == null) m.animation = new MotionAnimation(m);
        return m.animation;
    }

    public Motion motion() {
        return motion;
    }

    @Override
    public boolean isActive() {
        return MotionEngine.enabled() && (motion.engine > 0.001f || motion.engineO > 0.001f);
    }

    @Override
    public void setupAnim(float tickDelta) {
        Motion cur = MotionEngine.peek(motion.entity);                       // the engine may have made it a new one (a new level)
        if (cur == null) cur = MotionEngine.get(motion.entity);
        if (cur != motion) {
            motion = cur;
            if (cur.animation == null) cur.animation = this;
        }
        pt = tickDelta;
        weight = motion.engineWeight(tickDelta);
        weight = weight * weight * (3f - 2f * weight);
        pose = MotionEngine.sample(motion, tickDelta);
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        Bone b = Bone.byId(modelName);
        if (b == null || pose == null || weight <= 0f) return value0;
        float w = weight;
        if (b == Bone.RIGHT_ARM || b == Bone.LEFT_ARM) w *= Mth.lerp(pt, motion.maskO[b.ordinal()], motion.mask[b.ordinal()]);
        if (w <= 0f) return value0;
        float x0 = value0.getX(), y0 = value0.getY(), z0 = value0.getZ();
        if (b == Bone.BODY) {
            if (type == TransformType.ROTATION) {
                return new Vec3f(x0 + r(b, Bone.PITCH) * w, y0 + r(b, Bone.YAW) * w, z0 + r(b, Bone.ROLL) * w);
            }
            if (type == TransformType.POSITION) {
                return new Vec3f(x0 + pose.get(b, Bone.X) * w, y0 + pose.get(b, Bone.Y) * w, z0 + pose.get(b, Bone.Z) * w);
            }
            return value0;
        }
        switch (type) {
            case ROTATION -> {
                if (b == Bone.HEAD) {                                        // the look stays the player's; the pose only nudges it
                    return new Vec3f(x0 + r(b, Bone.PITCH) * w, y0 + r(b, Bone.YAW) * w, z0 + r(b, Bone.ROLL) * w);
                }
                return new Vec3f(x0 + (r(b, Bone.PITCH) - x0) * w, y0 + (r(b, Bone.YAW) - y0) * w, z0 + (r(b, Bone.ROLL) - z0) * w);
            }
            case POSITION -> {
                return new Vec3f(x0 + pose.get(b, Bone.X) * w, y0 + pose.get(b, Bone.Y) * w, z0 + pose.get(b, Bone.Z) * w);
            }
            case BEND -> {
                if (b == Bone.HEAD) return value0;
                float bend = pose.get(b, Bone.BEND) * Mth.DEG_TO_RAD;
                if (b == Bone.RIGHT_ARM || b == Bone.LEFT_ARM) bend = -bend;  // bendy-lib folds arms the other way
                return new Vec3f(x0, y0 + (bend - y0) * w, z0);
            }
            default -> {
                return value0;
            }
        }
    }

    private float r(Bone b, int channel) {
        return pose.get(b, channel) * Mth.DEG_TO_RAD;
    }
}
