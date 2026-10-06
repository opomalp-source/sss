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

    public FighterRenderer(EntityRendererProvider.Context ctx, String skin, float scale) {
        // The player model: outer layers (capes, helmets, sleeves) and separately painted left limbs.
        super(ctx, new net.minecraft.client.model.PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f * scale);
        this.texture = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fighter/" + skin + ".png");
        this.scale = scale;
        addLayer(new NpcPartsLayer<>(this, ctx.getModelSet(), skin));
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
