package com.dbzenith.client.render;

import com.dbzenith.npc.SkinVariant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws an NPC whose look varies by entity ({@link SkinVariant}: the town's residents) with a {@link FighterRenderer}
 * per skin, made the first time that skin is seen.
 */
public class VariantRenderer<T extends Mob & SkinVariant> extends EntityRenderer<T> {
    private final EntityRendererProvider.Context ctx;
    private final Map<String, FighterRenderer<T>> bySkin = new HashMap<>();

    public VariantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.ctx = ctx;
        this.shadowRadius = 0.5f;
    }

    FighterRenderer<T> of(T e) {
        return bySkin.computeIfAbsent(e.skin(), s -> new FighterRenderer<>(ctx, s, 1f));
    }

    @Override
    public void render(T e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        of(e).render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return of(e).getTextureLocation(e);
    }
}
