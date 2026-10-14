package com.dbzenith.client.anim;

import com.dbzenith.combat.Limbs;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Broken limbs (CX-53), over every other animation of a figure (players and NPCs): a broken arm hangs limp and straight
 * from the shoulder, swinging a little with the body; a broken leg drags behind, barely bent. Whatever the layers below
 * want those limbs to do, they don't.
 */
public final class InjuryLayer implements IAnimation {
    private final LivingEntity entity;

    public InjuryLayer(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean isActive() {
        Limbs.State s = Limbs.of(entity);
        for (Limbs.Limb l : Limbs.Limb.values()) if (s.broken(l)) return true;
        return false;
    }

    @Override
    public void setupAnim(float tickDelta) {
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
        Limbs.Limb l = switch (part) {
            case "rightArm" -> Limbs.Limb.RIGHT_ARM;
            case "leftArm" -> Limbs.Limb.LEFT_ARM;
            case "rightLeg" -> Limbs.Limb.RIGHT_LEG;
            case "leftLeg" -> Limbs.Limb.LEFT_LEG;
            default -> null;
        };
        if (l == null || !Limbs.of(entity).broken(l)) return value0;
        float t = entity.tickCount + tickDelta;
        boolean right = l == Limbs.Limb.RIGHT_ARM || l == Limbs.Limb.RIGHT_LEG;
        double moved = Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo);
        float swing = (float) Math.min(1, moved * 6) * Mth.sin(t * 0.6f) * 0.18f + Mth.sin(t * 0.08f) * 0.04f;
        if (l.arm()) {
            return switch (type) {
                case ROTATION -> new Vec3f(0.08f + swing, 0, right ? 0.1f : -0.1f);  // hanging, a touch out from the side
                case BEND -> new Vec3f(value0.getX(), -0.12f, value0.getZ());       // nearly straight
                default -> value0;
            };
        }
        return switch (type) {
            case ROTATION -> new Vec3f(0.3f + swing * 0.4f, 0, right ? 0.06f : -0.06f);  // trailing behind
            case BEND -> new Vec3f(value0.getX(), 0.12f, value0.getZ());
            default -> value0;
        };
    }
}
