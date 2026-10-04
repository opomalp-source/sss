package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.world.Cosmetics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Scars and tattoos: skin-layout overlay textures (mostly transparent) drawn on the player model, the way vanilla
 * draws glowing eyes.
 */
public class CosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CosmeticsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static ResourceLocation scarTexture(int scar) {
        return new ResourceLocation(DBZenith.MOD_ID, "textures/entity/cosmetics/scar_" + Cosmetics.SCARS.get(scar) + ".png");
    }

    public static ResourceLocation tattooTexture(int tattoo) {
        return new ResourceLocation(DBZenith.MOD_ID, "textures/entity/cosmetics/tattoo_" + Cosmetics.TATTOOS.get(tattoo) + ".png");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        int scar = state.scar(), tattoo = state.tattoo();
        if (tattoo > 0 && tattoo < Cosmetics.TATTOOS.size()) {
            renderColoredCutoutModel(getParentModel(), tattooTexture(tattoo), pose, buffers, light, player, 1f, 1f, 1f);
        }
        if (scar > 0 && scar < Cosmetics.SCARS.size()) {
            renderColoredCutoutModel(getParentModel(), scarTexture(scar), pose, buffers, light, player, 1f, 1f, 1f);
        }
    }
}
