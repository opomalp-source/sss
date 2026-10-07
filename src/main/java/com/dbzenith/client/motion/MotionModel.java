package com.dbzenith.client.motion;

import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * The player model for NPCs, driven by the motion engine (CX-18). After vanilla's own pose it runs the entity's
 * {@link MotionAnimation} through playerAnimator's part applier, exactly as for players, so NPCs walk, run, fly and land
 * with the same rig, bends and all. The model is shared by every entity of its renderer, so the bend source is set per
 * entity each time it is posed.
 */
public class MotionModel<T extends LivingEntity> extends PlayerModel<T> {
    private static final String[] PARTS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    public MotionModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    /** The applier for an entity (kept with its motion). */
    static AnimationApplier applier(Motion m) {
        if (m.applier == null) m.applier = new AnimationApplier(com.dbzenith.client.anim.NpcActions.stack(m.entity(), MotionAnimation.of(m)));   // with the combat layers (CX-19e)
        return (AnimationApplier) m.applier;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch);
        IMutableModel mutable = (IMutableModel) this;
        if (!MotionEngine.enabled()) {
            mutable.getEmoteSupplier().set(null);
            return;
        }
        Motion m = MotionEngine.get(entity);
        float pt = age - entity.tickCount;
        AnimationApplier a = applier(m);
        a.setTickDelta(Math.max(0f, Math.min(1f, pt)));
        if (!a.isActive()) {
            mutable.getEmoteSupplier().set(null);
            return;
        }
        for (String part : PARTS) a.updatePart(part, partOf(part));
        hat.copyFrom(head);
        jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        mutable.getEmoteSupplier().set((AnimationProcessor) a);
    }

    private ModelPart partOf(String name) {
        return switch (name) {
            case "head" -> head;
            case "torso" -> body;
            case "rightArm" -> rightArm;
            case "leftArm" -> leftArm;
            case "rightLeg" -> rightLeg;
            default -> leftLeg;
        };
    }
}
