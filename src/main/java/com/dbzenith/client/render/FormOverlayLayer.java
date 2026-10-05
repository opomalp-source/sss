package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.FormLooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** What a form grows over the body whatever skin is under it: Super Saiyan 4's fur (transform.FormLooks). */
public class FormOverlayLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    public FormOverlayLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        String overlay = FormLooks.of(FormHairLayer.flicker(player, state, ageInTicks).id()).overlay();
        if (overlay == null) return;
        ResourceLocation tex = TEXTURES.computeIfAbsent(overlay, o -> new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form/" + o + ".png"));
        renderColoredCutoutModel(getParentModel(), tex, pose, buffers, light, player, 1f, 1f, 1f);
    }
}
