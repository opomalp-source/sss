package com.dbzenith.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * The player model with the fighter's physique (CX-27): vanilla's (and playerAnimator's) pose, then the arms hang a
 * little out from the body so they clear the lats. Swapped in for the player renderers' own model at start-up.
 */
public class PhysiqueModel<T extends LivingEntity> extends PlayerModel<T> {
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
            com.dbzenith.DBZenith.LOGGER.warn("Could not install the physique model; arms keep vanilla's hang", e);
        }
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch);
        Physique.spreadArms(this);
    }
}
