package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.motion.Bone;
import com.dbzenith.client.motion.MotionEngine;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Back accessories follow the final body pose independently of chest armor and art style. */
public final class CapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/gear_parts.png");
    private final GearModel model;
    public CapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        model = new GearModel(models.bakeLayer(GearModel.LAYER));
    }
    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float swing, float amount, float partial, float age, float yaw, float pitch) {
        var state = ClientPublicStates.get(player.getId());
        if (player.isInvisible() || state == null || !state.has(PublicStatePacket.CAPE)) return;
        model.follow(getParentModel());
        var motion = MotionEngine.peek(player);
        if (motion != null && MotionEngine.enabled() && state.has(PublicStatePacket.FLYING)) {
            var rig = MotionEngine.sample(motion, partial);
            model.capeForwardTilt(-rig.get(Bone.BODY, Bone.PITCH) * net.minecraft.util.Mth.DEG_TO_RAD * motion.engineWeight(partial));
        }
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.render(pose, vc, light, OverlayTexture.NO_OVERLAY, GearModel.Piece.CAPE, 0,
                Cloth.chain(player, "cape", Cloth.CAPE, age), .957f, .949f, .925f);
        model.render(pose, vc, light, OverlayTexture.NO_OVERLAY, GearModel.Piece.NAMEK_PADS, 0, .957f, .949f, .925f);
    }
}
