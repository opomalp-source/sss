package com.dbzenith.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Render types for energy effects. Both are full-bright, double-sided and leave the depth buffer alone. */
public final class FxRenderTypes extends RenderType {
    /** Light adds onto what is behind it: cores, flashes, lightning. Glows against dark and bright skies alike. */
    private static final Function<ResourceLocation, RenderType> ADDITIVE = Util.memoize(tex -> create("dbzenith_fx_additive",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)   // SRC_ALPHA, ONE: additive that respects the texture's alpha
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false)));

    /** Ordinary blending, so a colour stays saturated in daylight: the body of an aura. */
    private static final Function<ResourceLocation, RenderType> SOFT = Util.memoize(tex -> create("dbzenith_fx_soft",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false)));

    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                          Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }

    public static RenderType soft(ResourceLocation texture) {
        return SOFT.apply(texture);
    }
}
