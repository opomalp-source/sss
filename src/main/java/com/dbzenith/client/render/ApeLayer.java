package com.dbzenith.client.render;

import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * The Great Ape's body ({@link ApeLook}): a coat of fur over the player's own figure (bare chest, face, hands and
 * feet), the red eyes glowing, and the ape's face on the head ({@link ApeHeadModel}) with its jaw working: wide open
 * in the roar that comes with the change, snarling now and then, opening with each blow.
 */
public class ApeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** The roar after growth, in ticks from the witnessed form change (matching {@code Anims.APE_ROAR}). */
    public static final float ROAR_FROM = com.dbzenith.transform.ApeChange.ROAR, ROAR_TO = com.dbzenith.transform.ApeChange.ROAR_END;

    private final ApeHeadModel head;

    public ApeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        head = new ApeHeadModel(models.bakeLayer(ApeHeadModel.LAYER));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float age, float netHeadYaw, float headPitch) {
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (!ApeLook.is(state)) {
            return;
        }
        if (player.isInvisible()) return;
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        float t = ApeTransformation.age(player, partialTick);
        var before = ApeTransformation.before(player);
        if (t < com.dbzenith.transform.ApeChange.GROWN && BodySkinLayer.active(before)) {
            int c = before.skinTone();
            renderColoredCutoutModel(model, BodySkinLayer.bodyTexture(before), pose, buffers, light, player,
                    ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
        }
        if (t >= com.dbzenith.transform.ApeChange.GROWN) {
            renderColoredCutoutModel(model, ApeLook.body(state.form()), pose, buffers, light, player, 1f, 1f, 1f);
        } else {
            // Fur takes the shoulders first, then the trunk and legs, finally the face.
            pose.pushPose();
            pose.scale(1.002f, 1.002f, 1.002f);
            var fur = buffers.getBuffer(RenderType.entityTranslucent(ApeLook.body(state.form())));
            coat(model.rightArm, pose, fur, light, overlay, t, 14, 42);
            coat(model.leftArm, pose, fur, light, overlay, t, 16, 44);
            coat(model.body, pose, fur, light, overlay, t, 20, 50);
            coat(model.rightLeg, pose, fur, light, overlay, t, 24, 56);
            coat(model.leftLeg, pose, fur, light, overlay, t, 26, 58);
            coat(model.head, pose, fur, light, overlay, t, 32, 64);
            pose.popPose();
        }
        if (!model.head.visible) return;                                         // first person: the arm alone
        float face = com.dbzenith.transform.ApeChange.face(t);
        if (face > 0.75f) model.head.render(pose, buffers.getBuffer(RenderType.eyes(ApeLook.EYES)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, com.dbzenith.transform.ApeChange.phase(t, 54, 68));

        float open = 0.08f + 0.25f * (float) Math.pow(Math.max(0, Mth.sin(age * 0.045f)), 8);   // a snarl now and then
        if (t >= ROAR_FROM - 4 && t < ROAR_TO + 6) {                             // the roar: the jaw thrown wide, shaking
            float in = Mth.clamp((t - ROAR_FROM + 4) / 5f, 0, 1), out = Mth.clamp((ROAR_TO + 6 - t) / 6f, 0, 1);
            open = Math.max(open, in * out * (0.9f + 0.1f * Mth.sin(age * 1.9f)));
        }
        float swing = player.getAttackAnim(partialTick);
        if (swing > 0) open = Math.max(open, Mth.sin(Mth.sqrt(swing) * Mth.PI) * 0.6f);
        head.pose(open, age);
        pose.pushPose();
        model.head.translateAndRotate(pose);
        // The muzzle and ears grow out of the head rather than appearing at full size.
        if (face > 0.001f) {
            pose.scale(face, face, face);
            head.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ApeLook.parts(state.form()))), light, overlay);
        }
        pose.popPose();
    }

    private static void coat(net.minecraft.client.model.geom.ModelPart part, PoseStack pose,
                             com.mojang.blaze3d.vertex.VertexConsumer fur, int light, int overlay,
                             float tick, float from, float to) {
        float alpha = com.dbzenith.transform.ApeChange.phase(tick, from, to);
        if (alpha > 0) part.render(pose, fur, light, overlay, 1f, 1f, 1f, alpha);
    }
}
