package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws form hair (colored, glowing) and eye color on any player whose public state says they are transformed. */
public class FormHairLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form_hair.png");

    private final FormHairModel model;

    public FormHairLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new FormHairModel(models.bakeLayer(FormHairModel.LAYER));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        Form form = Forms.byId(state.form());
        Form.HairStyle[] styles = Form.HairStyle.values();
        Form.HairStyle custom = state.hairStyle() >= 0 && state.hairStyle() < styles.length ? styles[state.hairStyle()] : Form.HairStyle.NONE;
        // A form with its own hair overrides the chosen hairstyle; otherwise the character's own look shows.
        boolean formHair = form.hairStyle() != Form.HairStyle.NONE && form.hairColor() >= 0;
        Form.HairStyle style = formHair ? form.hairStyle() : custom;
        int hairColor = formHair ? form.hairColor() : state.hairColor();
        int eyeColor = form.eyeColor() >= 0 ? form.eyeColor() : state.eyeColor();
        if (style == Form.HairStyle.NONE && eyeColor < 0) return;

        model.copyHead(getParentModel().head);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int glow = form.isBase() ? light : LightTexture.FULL_BRIGHT; // transformed hair and eyes glow
        if (style != Form.HairStyle.NONE) {
            int c = hairColor;
            model.renderHair(pose, vc, formHair ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, style,
                    ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
        if (eyeColor >= 0) {
            int c = eyeColor;
            model.renderEyes(pose, vc, glow, OverlayTexture.NO_OVERLAY,
                    ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
