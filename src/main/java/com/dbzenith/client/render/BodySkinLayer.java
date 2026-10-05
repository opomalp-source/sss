package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The generated body: muscle-shaded flesh for the character's build, tinted to their skin tone, under an untinted
 * outfit (face, training pants, boots, wristbands). Shown when a skin tone is chosen, replacing the player's own
 * Minecraft skin; a full race look (Namekian, Frost Demon, Majin) still takes precedence.
 */
public class BodySkinLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation[] BODIES = {
            new ResourceLocation(DBZenith.MOD_ID, "textures/entity/body/lean.png"),
            new ResourceLocation(DBZenith.MOD_ID, "textures/entity/body/athletic.png"),
            new ResourceLocation(DBZenith.MOD_ID, "textures/entity/body/bulky.png")};
    private static final ResourceLocation OUTFIT = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/body/outfit.png");

    public BodySkinLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Whether this player is drawn with the generated body. */
    public static boolean active(PublicStatePacket state) {
        if (state == null || state.skinTone() < 0) return false;
        return !(state.raceLook() && RaceSkinLayer.texture(state) != null);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (!active(state)) return;
        int tint = com.dbzenith.transform.FormLooks.of(state.form()).tint();
        int c = tint >= 0 ? tint : state.skinTone();                     // some forms recolour the body
        ResourceLocation body = BODIES[Math.max(0, Math.min(BODIES.length - 1, state.bodyType()))];
        renderColoredCutoutModel(getParentModel(), body, pose, buffers, light, player,
                ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
        renderColoredCutoutModel(getParentModel(), OUTFIT, pose, buffers, light, player, 1f, 1f, 1f);
    }
}
