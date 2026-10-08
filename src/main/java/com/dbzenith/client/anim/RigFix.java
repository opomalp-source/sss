package com.dbzenith.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * The last layer of every figure's animation stack (players and NPCs): keeps the upper body on the hips.
 * <p>
 * The model's torso turns about the neck, so a lean or a twist on its own swings the hips off the legs (the torso seems
 * to hover over them, or sink into them). Whatever the layers below did (the motion engine's clips, the combat moves,
 * hit reactions), this reads the torso's final lean and twist and moves the torso so it turns about the waist instead,
 * and carries the head and the shoulders with it. The motion engine leaves this to it.
 */
public final class RigFix implements IAnimation {
    /** The waist to the neck, and to the shoulder pivots (pixels). */
    private static final float NECK = 12f, SHOULDER = 10f, SHOULDER_DROP = 2f, SHOULDER_X = 5f;
    private final Supplier<IAnimation> stack;
    private boolean busy;

    public RigFix(Supplier<IAnimation> stack) {
        this.stack = stack;
    }

    @Override
    public boolean isActive() {
        if (busy) return false;
        IAnimation s = stack.get();
        if (s == null) return false;
        busy = true;
        try {
            return s.isActive();                                       // only when something below animates the figure
        } finally {
            busy = false;
        }
    }

    @Override
    public void setupAnim(float tickDelta) {
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
        if (busy || type != TransformType.POSITION) return value0;
        boolean torso = part.equals("torso"), head = part.equals("head"), right = part.equals("rightArm"), left = part.equals("leftArm");
        if (!torso && !head && !right && !left) return value0;
        IAnimation s = stack.get();
        if (s == null) return value0;
        Vec3f rot;
        busy = true;
        try {
            rot = s.get3DTransform("torso", TransformType.ROTATION, tickDelta, Vec3f.ZERO);
        } finally {
            busy = false;
        }
        float lean = rot.getX(), twist = rot.getY();
        if (Math.abs(lean) < 1e-4f && Math.abs(twist) < 1e-4f) return value0;
        float x = value0.getX(), y = value0.getY(), z = value0.getZ();
        if (torso || head) {                                           // the neck swings forward and down about the waist
            return new Vec3f(x, y + NECK - NECK * Mth.cos(lean), z - NECK * Mth.sin(lean));
        }
        float x0 = right ? -SHOULDER_X : SHOULDER_X;                   // the shoulders ride along, and turn with a twist
        float shY = NECK - SHOULDER * Mth.cos(lean) - SHOULDER_DROP, shZ = -SHOULDER * Mth.sin(lean);
        return new Vec3f(x + x0 * (Mth.cos(twist) - 1f), y + shY, z + shZ + x0 * Mth.sin(twist));
    }
}
