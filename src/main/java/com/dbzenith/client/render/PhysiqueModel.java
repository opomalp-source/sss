package com.dbzenith.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * The player model with the fighter's proportions (CX-29): vanilla's (and playerAnimator's) pose, then longer legs
 * and arms hanging a little out ({@link LimbSegments#pose}). Swapped in for the player renderers' own model.
 */
public class PhysiqueModel<T extends LivingEntity> extends PlayerModel<T> {
    private float apeShoulders, apeHips;

    /** Undo only our joint offsets: the renderer shares this model across players and first-person arms. */
    public void resetApeSpacing() {
        rightArm.x += apeShoulders;
        leftArm.x -= apeShoulders;
        rightLeg.x += apeHips;
        leftLeg.x -= apeHips;
        apeShoulders = apeHips = 0;
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg);
        leftPants.copyFrom(leftLeg);
    }

    public PhysiqueModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    /** Puts a physique model in place of the renderer's own (same parts, same layout), once. */
    public static void install(net.minecraft.client.renderer.entity.player.PlayerRenderer renderer,
                               net.minecraft.client.model.geom.EntityModelSet models, boolean slim) {
        if (renderer.getModel() instanceof PhysiqueModel) return;
        try {
            for (java.lang.reflect.Field f : net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredFields()) {
                if (net.minecraft.client.model.EntityModel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    var layer = slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM : net.minecraft.client.model.geom.ModelLayers.PLAYER;
                    f.set(renderer, new PhysiqueModel<>(models.bakeLayer(layer), slim));
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.dbzenith.DBZenith.LOGGER.warn("Could not install the physique model; vanilla proportions kept", e);
        }
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        resetApeSpacing();
        // Front F5 is an inspection view: orbiting it must not tip the ape's face away from the viewer.
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (entity == mc.player && mc.player != null && mc.options.getCameraType().isMirrored()
                && ApeLook.is(mc.player)) headPitch = 0;
        super.setupAnim(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch);
        LimbSegments.pose(this);
        if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player && ApeLook.is(player)) {
            // Thickened ape limbs need wider joints, not merely a wider swing around human-sized hips.
            float growth = com.dbzenith.transform.ApeChange.growth(ApeTransformation.age(player,
                    net.minecraft.util.Mth.clamp(age - entity.tickCount, 0, 1)));
            apeShoulders = 3f * growth;
            apeHips = 1.6f * growth;
            rightArm.x -= apeShoulders;
            leftArm.x += apeShoulders;
            rightLeg.x -= apeHips;
            leftLeg.x += apeHips;
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
            rightPants.copyFrom(rightLeg);
            leftPants.copyFrom(leftLeg);
        }
    }
}
