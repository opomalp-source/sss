package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Humanoid renderer for fighters and NPCs: a per-type 64x64 skin (player layout, both layers; see tools/ArtGen.java) and size. */
public class FighterRenderer<T extends net.minecraft.world.entity.Mob> extends HumanoidMobRenderer<T, net.minecraft.client.model.PlayerModel<T>> {
    private final ResourceLocation texture;
    private final float scale;
    /** The body's build (BodyShape: lean, athletic, bulky), from the skin (NpcLooks). */
    private final int build;

    public FighterRenderer(EntityRendererProvider.Context ctx, String skin, float scale) {
        // The player model: outer layers (capes, helmets, sleeves) and separately painted left limbs.
        super(ctx, new com.dbzenith.client.motion.MotionModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f * scale);
        this.texture = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fighter/" + skin + ".png");
        this.scale = scale;
        this.build = NpcLooks.build(skin);
        BodyShape.attach(getModel());                                              // anime proportions, as players have (CX-22)
        addLayer(new NpcPartsLayer<>(this, ctx.getModelSet(), skin));
    }

    @Override
    public void render(T fighter, float yaw, float partialTick, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        BodyShape.apply(getModel(), build, 0);                                       // a tapered torso and fuller limbs (CX-22)
        LimbSegments.show(getModel(), build, true, !BodyShape.armour(fighter, net.minecraft.world.entity.EquipmentSlot.LEGS),
                !BodyShape.armour(fighter, net.minecraft.world.entity.EquipmentSlot.FEET));      // segmented limbs, longer legs (CX-29)
        LimbSegments.proportions = true;
        LimbSegments.grounded = fighter.onGround() || fighter.isPassenger();
        super.render(fighter, yaw, partialTick, pose, buffers, light);
        LimbSegments.reset(getModel());
    }

    /** The motion engine tips and moves the whole figure (flight pitch, banking, leaning, crouching on landing), as playerAnimator does for players. */
    @Override
    protected void setupRotations(T fighter, PoseStack pose, float bob, float yaw, float partialTick) {
        super.setupRotations(fighter, pose, bob, yaw, partialTick);
        if (com.dbzenith.client.motion.MotionEngine.enabled()) com.dbzenith.client.motion.MotionEngine.applyBody(fighter, pose, partialTick, scale);
    }

    @Override
    protected void scale(T fighter, PoseStack pose, float partialTick) {
        if (scale != 1f) pose.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(T fighter) {
        return texture;
    }
}
