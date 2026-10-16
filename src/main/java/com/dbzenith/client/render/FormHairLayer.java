package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.hair.HairCodes;
import com.dbzenith.client.hair.HairRender;
import com.dbzenith.client.hair.HairShape;
import com.dbzenith.client.hair.HairSim;
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

import java.util.Map;

/**
 * Hair and eyes. The character's own hair code is drawn strand by strand ({@link HairMesh}); a form with hair of its
 * own grows it from that code ({@link HairCode#forForm}) and makes it glow in the form's colour. With the player's
 * own Minecraft skin a cap covers their painted hair first.
 */
public class FormHairLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Ultra Instinct (CX-24 phase 5): Mastered's silver hair and eyes, the Sign's grey-silver eyes. */
    public static final int UI_HAIR = 0xDCE2EC, UI_EYES = 0xC8D2E0, UI_SIGN_EYES = 0xA9B1BD;
    private static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form_hair.png");
    private static final ResourceLocation TEXTURE_HD = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form_hair_hd.png");

    private final FormHairModel model;

    public FormHairLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new FormHairModel(models.bakeLayer(FormHairModel.LAYER));
    }


    /** When each player began powering up (client ticks), for the flicker. */
    private static final Map<Integer, Float> POWERING_SINCE = new java.util.HashMap<>();

    /**
     * The form whose hair and eyes show: while powering up, the new form flashes through, faster and longer as the
     * power builds.
     */
    static Form flicker(AbstractClientPlayer player, PublicStatePacket state, float age) {
        Form current = Forms.byId(state.form());
        if (!state.has(PublicStatePacket.TRANSFORMING) || !Forms.exists(state.transformTarget())) {
            POWERING_SINCE.remove(player.getId());
            return current;
        }
        float since = POWERING_SINCE.computeIfAbsent(player.getId(), k -> age);
        float e = Math.max(0, age - since);
        double phase = Math.pow(e, 1.45) * 0.03;
        double on = Math.min(0.85, 0.15 + e / 120.0);                         // the new form holds a little longer each flash
        return phase % 1.0 < on ? Forms.byId(state.transformTarget()) : current;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        Form form = flicker(player, state, ageInTicks);
        boolean formHair = form.hairStyle() != Form.HairStyle.NONE && form.hairColor() >= 0;
        HairTransformation.Frame transition = HairTransformation.sample(player, state, partialTick);
        Form shape = transition.target().isEmpty() ? form : Forms.byId(state.form());
        HairShape hair;                                                          // the own hair in this form, or on its way to the next
        if (GearLayer.hairCovered(player)) hair = HairShape.BALD;                // a turban or a bandana covers the hair
        else if (transition.target().isEmpty()) hair = HairCodes.shape(state.hairCode(), shape);
        else {
            float p = transition.progress();
            hair = HairCodes.between(state.hairCode(), shape, Forms.byId(transition.target()), p * p * (3 - 2 * p));
        }
        int hairColor = form.hairColor() >= 0 ? form.hairColor() : state.hairColor();
        boolean goldCharge=state.has(PublicStatePacket.TRANSFORMING)&&HairTransformation.goldTarget(transition.target());
        if(goldCharge) hairColor=HairTransformation.chargeColor(state.hairColor(),Forms.SUPER_SAIYAN.hairColor(),transition.progress(),ageInTicks);
        int eyeColor = form.eyeColor() >= 0 ? form.eyeColor() : state.eyeColor();
        int ui = state.ultraInstinct();                                          // Ultra Instinct, over the form (CX-24 phase 5): silver eyes; Mastered, silver hair
        if (ui == 2 && !goldCharge) hairColor = UI_HAIR;
        if (ui > 0) eyeColor = ui == 2 ? UI_EYES : UI_SIGN_EYES;
        if (state.has(PublicStatePacket.MOON_EYES)) eyeColor = -1;
        boolean ape = ApeLook.is(state);                                          // the Great Ape: its own hairstyle grown wild in the fur's colour, its own red eyes
        if (ape) {
            float coat = com.dbzenith.transform.ApeChange.fur(ApeTransformation.age(player, partialTick));
            int from = state.hairColor(), to = ApeLook.hairColor(state.form());
            hairColor = (Math.round(net.minecraft.util.Mth.lerp(coat, (from >> 16) & 255, (to >> 16) & 255)) << 16)
                    | (Math.round(net.minecraft.util.Mth.lerp(coat, (from >> 8) & 255, (to >> 8) & 255)) << 8)
                    | Math.round(net.minecraft.util.Mth.lerp(coat, from & 255, to & 255));
            eyeColor = -1;
            goldCharge = false;
            ui = 0;
        }
        if (hair.isEmpty() && eyeColor < 0) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ArtStyle.hiRes() ? TEXTURE_HD : TEXTURE));
        int hairLight = goldCharge ? (hairColor==state.hairColor()?light:LightTexture.FULL_BRIGHT) : formHair || ui == 2 ? LightTexture.FULL_BRIGHT : light;              // transformed hair glows
        if (!hair.isEmpty()) {
            VertexConsumer hairBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(HairRender.MATERIAL));
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            if (ape) {                                                            // a wild mane, lifted off the brow so the eyes show
                float mane = com.dbzenith.transform.ApeChange.face(ApeTransformation.age(player, partialTick));
                pose.translate(0, -1.6f / 16f * mane, 1.4f / 16f * mane);
                float size = 1 + 0.12f * mane;
                pose.scale(size, size, size);
            }
            float aura = state.powering() ? 1f : formHair ? .3f : 0f;                // an aura lifts the hair
            float[] live = HairSim.step(player, getParentModel().head, hair, partialTick, aura);
            int tips = !ape && !goldCharge && ui < 2 && (form.isBase() || form.hairColor() < 0) ? state.highlight() : -1;   // dyed tips, until a form recolours the hair
            HairRender.render(pose, hairBuffer, hair, live, hairLight, OverlayTexture.NO_OVERLAY, hairColor, tips);
            pose.popPose();
        }
        if (eyeColor >= 0 && !FaceLayer.active(state)) {                         // drawn faces bring their own irises
            int c = eyeColor;
            model.copyHead(getParentModel().head);
            model.renderEyes(pose, vc, form.isBase() ? light : LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        }
    }
}
