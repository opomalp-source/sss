package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.appearance.HairCode;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hair and eyes. The character's own hair code is drawn strand by strand ({@link HairMesh}); a form with hair of its
 * own grows it from that code ({@link HairCode#forForm}) and makes it glow in the form's colour. With the player's
 * own Minecraft skin a cap covers their painted hair first.
 */
public class FormHairLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form_hair.png");
    private static final Map<String, String> FORM_HAIR = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > 64;
        }
    };

    private final FormHairModel model;

    public FormHairLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new FormHairModel(models.bakeLayer(FormHairModel.LAYER));
    }

    /** The hair code shown for a character in a form (cached: deriving it decodes and re-encodes the strands). */
    public static String hairFor(String baseCode, Form form) {
        if (form.hairStyle() == Form.HairStyle.NONE) return baseCode;
        return FORM_HAIR.computeIfAbsent(form.id() + "|" + baseCode, k -> HairCode.forForm(baseCode, form));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        Form form = Forms.byId(state.form());
        boolean formHair = form.hairStyle() != Form.HairStyle.NONE && form.hairColor() >= 0;
        String code = hairFor(state.hairCode(), form);
        int hairColor = form.hairColor() >= 0 ? form.hairColor() : state.hairColor();
        int eyeColor = form.eyeColor() >= 0 ? form.eyeColor() : state.eyeColor();
        if (code.isEmpty() && eyeColor < 0) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int hairLight = formHair ? LightTexture.FULL_BRIGHT : light;              // transformed hair glows
        float r = ((hairColor >> 16) & 0xFF) / 255f, g = ((hairColor >> 8) & 0xFF) / 255f, b = (hairColor & 0xFF) / 255f;
        if (!code.isEmpty()) {
            model.copyHead(getParentModel().head);
            if (state.skinTone() < 0 && !state.raceLook()) {                       // hide the skin's own painted hair
                model.renderCap(pose, vc, hairLight, OverlayTexture.NO_OVERLAY, r, g, b);
            }
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            HairWind.State wind = HairWind.of(player, getParentModel().head, state, formHair, partialTick);
            HairMesh.render(pose, vc, HairMesh.of(code), wind, hairLight, OverlayTexture.NO_OVERLAY, r, g, b);
            pose.popPose();
        }
        if (eyeColor >= 0) {
            int c = eyeColor;
            model.copyHead(getParentModel().head);
            model.renderEyes(pose, vc, form.isBase() ? light : LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
