package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.race.RaceTraits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * The 3D look of an NPC (CX-12): chunky hair on its mass, race parts and a tail, the pieces of its clothes and the
 * things it wears ({@link NpcLooks}), built from the same models players use.
 */
public class NpcPartsLayer<T extends Mob> extends RenderLayer<T, PlayerModel<T>> {
    private static final ResourceLocation HAIR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/form_hair_hd.png");
    private static final ResourceLocation PARTS = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/race_parts.png");
    private static final ResourceLocation GEAR = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/gear_parts.png");

    private final NpcLooks.Look look;
    private final float featureLength;
    private final FormHairModel hair;
    private final RaceFeatureModel features;
    private final GearModel gear;
    private final NpcExtrasModel extras;

    public NpcPartsLayer(RenderLayerParent<T, PlayerModel<T>> parent, EntityModelSet models, String skin) {
        super(parent);
        this.look = NpcLooks.of(skin);
        this.featureLength = NpcLooks.featureLength(skin);
        this.hair = new FormHairModel(models.bakeLayer(FormHairModel.LAYER));
        this.features = new RaceFeatureModel(models.bakeLayer(RaceFeatureModel.LAYER));
        this.gear = new GearModel(models.bakeLayer(GearModel.LAYER));
        this.extras = new NpcExtrasModel(models.bakeLayer(NpcExtrasModel.LAYER));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T npc, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (npc.isInvisible()) return;
        PlayerModel<T> m = getParentModel();
        int overlay = OverlayTexture.NO_OVERLAY;
        String code = look.hairCode();
        boolean hat = look.extras().contains(NpcLooks.Extra.HAT), cap = look.extras().contains(NpcLooks.Extra.CAP);
        if (!code.isEmpty() && !hat) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(HAIR));
            int c = look.hairColor();
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            if (com.dbzenith.appearance.HairCode.hasVolume(code)) {
                hair.copyHead(m.head);
                hair.renderMass(pose, vc, light, overlay, r * 0.85f + 0.03f, g * 0.85f + 0.03f, b * 0.85f + 0.03f);
            }
            if (!cap) {                                                                  // a cap flattens the hair to its mass
                pose.pushPose();
                m.head.translateAndRotate(pose);
                HairMesh.render(pose, vc, HairMesh.of(code), light, overlay, r, g, b);
                pose.popPose();
            }
        }
        if (look.feature() != RaceTraits.Feature.NONE || look.ears() || look.tailColor() >= 0) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
            features.follow(m.head, m.body);
            int c = look.featureColor();
            if (look.feature() != RaceTraits.Feature.NONE || look.ears()) {
                features.renderFeature(pose, vc, light, overlay, look.feature(), look.ears(), 1f, featureLength,
                        ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
                if (look.feature() == RaceTraits.Feature.HORNS) {                 // the ear plates in the shell purple
                    features.renderHornPlates(pose, vc, light, overlay, 0x8A / 255f, 0x4A / 255f, 0xC8 / 255f);
                }
            }
            if (look.tailColor() >= 0) {
                int t = look.tailColor();
                float lift = (float) Math.min(1, npc.getDeltaMovement().horizontalDistance() * 4);
                features.renderTail(pose, vc, light, overlay, ageInTicks, lift, look.wrapped(),
                        ((t >> 16) & 255) / 255f, ((t >> 8) & 255) / 255f, (t & 255) / 255f);
            }
        }
        if (look.extras().isEmpty()) return;
        VertexConsumer vg = buffers.getBuffer(RenderType.entityCutoutNoCull(GEAR));
        gear.follow(m);
        extras.follow(m.head, m.body);
        for (NpcLooks.Extra e : look.extras()) {
            switch (e) {
                case PADS -> {
                    gear.render(pose, vg, light, overlay, GearModel.Piece.PADS, 0, 0.93f, 0.94f, 0.95f);
                    gear.render(pose, vg, light, overlay, GearModel.Piece.PAD_TRIM, 0, 0.85f, 0.69f, 0.25f);
                }
                case FROST_PADS -> {
                    gear.render(pose, vg, light, overlay, GearModel.Piece.PADS, 0, 0.54f, 0.29f, 0.78f);
                    gear.render(pose, vg, light, overlay, GearModel.Piece.PAD_TRIM, 0, 0.95f, 0.94f, 0.96f);
                }
                case NAMEK_PADS -> gear.render(pose, vg, light, overlay, GearModel.Piece.NAMEK_PADS, 0, 0.96f, 0.95f, 0.93f);
                case CAPE -> gear.render(pose, vg, light, overlay, GearModel.Piece.CAPE,
                        (float) Math.min(1, npc.getDeltaMovement().horizontalDistance() * 4), 0.96f, 0.95f, 0.93f);
                case SHELL -> {
                    extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.SHELL, -1);
                    extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.SHELL_RIM, -1);
                }
                case CAP -> {
                    extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.CAP, -1);
                    extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.CAP_BADGE, -1);
                }
                case HAT -> {
                    extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.HAT, -1);
                    extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.HAT_PLATE, -1);
                }
                case SCOUTER -> {
                    extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.SCOUTER, -1);
                    extras.render(pose, vg, LightTexture.FULL_BRIGHT, overlay, NpcExtrasModel.Piece.SCOUTER_LENS, -1);
                }
                case EARRINGS -> extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.EARRINGS, -1);
                case DOME -> extras.render(pose, vg, light, overlay, NpcExtrasModel.Piece.DOME, -1);
                case HALO -> {
                    extras.spinHalo(ageInTicks * 0.04f);
                    extras.render(pose, vg, LightTexture.FULL_BRIGHT, overlay, NpcExtrasModel.Piece.HALO, -1);
                }
            }
        }
    }
}
