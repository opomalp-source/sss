package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.npc.KiFighter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Humanoid renderer for enemy fighters with a per-type placeholder skin (64x64 humanoid layout) and size. */
public class FighterRenderer<T extends KiFighter> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public FighterRenderer(EntityRendererProvider.Context ctx, String skin, float scale) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f * scale);
        this.texture = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/fighter/" + skin + ".png");
        this.scale = scale;
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
