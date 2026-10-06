package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
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

/** The halo of the dead (CX-12): a slowly turning gold ring over the head of anyone whose soul is in the other world; and Potara earrings (12c). */
public class HaloLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation GEAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/gear_parts.png");
    private final NpcExtrasModel extras;

    public HaloLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.extras = new NpcExtrasModel(models.bakeLayer(NpcExtrasModel.LAYER));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        boolean halo = state != null && state.has(PublicStatePacket.DEAD), earrings = com.dbzenith.client.ClientFusion.earrings(player);
        if (!halo && !earrings) return;
        extras.follow(getParentModel().head, getParentModel().body);
        if (earrings) {                                                       // the Potara (12c)
            extras.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GEAR)), light, OverlayTexture.NO_OVERLAY,
                    NpcExtrasModel.Piece.POTARA_RING, -1);
            extras.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GEAR)), light, OverlayTexture.NO_OVERLAY,
                    NpcExtrasModel.Piece.POTARA_BEAD, -1);
        }
        if (!halo) return;
        extras.spinHalo(ageInTicks * 0.04f);
        extras.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GEAR)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                NpcExtrasModel.Piece.HALO, -1);
    }
}
