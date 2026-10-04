package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.race.RaceTraits;
import com.dbzenith.race.Races;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws racial features and the Saiyan tail on any player, from their public state. */
public class RaceFeatureLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form_hair.png");
    private static final int ANTENNAE = 0x5FA83A;
    private static final int HORNS = 0xEDE6D6;
    private static final int TENTACLE = 0xF07FB0;
    private static final int TAIL = 0x6B3E1E;

    private final RaceFeatureModel model;

    public RaceFeatureLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new RaceFeatureModel(models.bakeLayer(RaceFeatureModel.LAYER));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null) return;
        RaceTraits.Feature feature = Races.of(state.raceEnum()).feature();
        boolean tail = state.has(PublicStatePacket.TAIL);
        if (feature == RaceTraits.Feature.NONE && !tail) return;

        model.follow(getParentModel().head, getParentModel().body);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = OverlayTexture.NO_OVERLAY;
        if (feature != RaceTraits.Feature.NONE) {
            int c = switch (feature) {
                case ANTENNAE -> ANTENNAE;
                case HORNS -> HORNS;
                case TENTACLE -> TENTACLE;
                default -> 0xFFFFFF;
            };
            model.renderFeature(pose, vc, light, overlay, feature, r(c), g(c), b(c));
        }
        if (tail) model.renderTail(pose, vc, light, overlay, ageInTicks, r(TAIL), g(TAIL), b(TAIL));
    }

    private static float r(int c) { return ((c >> 16) & 0xFF) / 255f; }
    private static float g(int c) { return ((c >> 8) & 0xFF) / 255f; }
    private static float b(int c) { return (c & 0xFF) / 255f; }
}
