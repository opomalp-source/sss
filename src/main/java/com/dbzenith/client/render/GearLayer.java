package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.item.GiArmorItem;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.HashMap;
import java.util.Map;

/**
 * Fighting clothes, painted (CX-14e): each gi piece a skin-layout texture drawn on the player model itself (so it
 * follows the chest shape and every animation), plus its 3D pieces from {@link GearModel}. Vanilla's flat armour
 * layers are switched off for these items on players, except in the Classic art style.
 */
public class GearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Renders nothing: replaces the vanilla armour model for gi pieces on players. */
    private static final Model EMPTY = new Model(RenderType::entityCutoutNoCull) {
        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {}
    };

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public Model getGenericArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            return entity instanceof Player && ArtStyle.get() != ArtStyle.CLASSIC ? EMPTY : original;
        }
    };

    private static final ResourceLocation PARTS = new ResourceLocation(DBZenith.MOD_ID, "textures/entity/gear_parts.png");
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private final GearModel model;

    public GearLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.model = new GearModel(models.bakeLayer(GearModel.LAYER));
    }

    static ResourceLocation texture(GiArmorItem.Set set, String piece) {
        return TEXTURES.computeIfAbsent(set.id() + "_" + piece, k -> new ResourceLocation(DBZenith.MOD_ID, "textures/entity/gear/" + k + ".png"));
    }

    /** Whether the player wears a turban (the Namekian garb), which hides the hair. */
    public static boolean turban(Player player) {
        return ArtStyle.get() != ArtStyle.CLASSIC && worn(player, EquipmentSlot.CHEST) == GiArmorItem.Set.NAMEKIAN;
    }

    static GiArmorItem.Set worn(Player player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).getItem() instanceof GiArmorItem gi ? gi.set() : null;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || ArtStyle.get() == ArtStyle.CLASSIC) return;
        GiArmorItem.Set top = worn(player, EquipmentSlot.CHEST), pants = worn(player, EquipmentSlot.LEGS), boots = worn(player, EquipmentSlot.FEET);
        PublicStatePacket fused = ClientPublicStates.get(player.getId());
        if (fused != null && fused.has(PublicStatePacket.FUSED_DANCE)) top = pants = boots = GiArmorItem.Set.FUSION;   // the dance's outfit (12c)
        if (top == null && pants == null && boots == null) return;
        if (pants != null) renderColoredCutoutModel(getParentModel(), texture(pants, "pants"), pose, buffers, light, player, 1f, 1f, 1f);
        if (boots != null) renderColoredCutoutModel(getParentModel(), texture(boots, "boots"), pose, buffers, light, player, 1f, 1f, 1f);
        if (top != null) renderColoredCutoutModel(getParentModel(), texture(top, "top"), pose, buffers, light, player, 1f, 1f, 1f);

        model.follow(getParentModel());
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        int o = OverlayTexture.NO_OVERLAY;
        if (pants == GiArmorItem.Set.MAJIN || pants == GiArmorItem.Set.FUSION) piece(pose, vc, light, o, GearModel.Piece.BAGGY, 0, 0xF2F0F4);
        if (top == null) return;
        switch (top) {
            case TURTLE -> piece(pose, vc, light, o, GearModel.Piece.SASH_TAILS, 0, 0x2852C8);
            case DEMON -> piece(pose, vc, light, o, GearModel.Piece.SASH_TAILS, 0, 0xC02838);
            case BATTLE_ARMOR -> {
                piece(pose, vc, light, o, GearModel.Piece.PADS, 0, 0xECEEF2);
                piece(pose, vc, light, o, GearModel.Piece.PAD_TRIM, 0, 0xD8B040);
            }
            case NAMEKIAN -> {
                PublicStatePacket state = ClientPublicStates.get(player.getId());
                float lift = state == null ? 0 : RaceFeatureLayer.lift(player, state);
                piece(pose, vc, light, o, GearModel.Piece.CAPE, lift, 0xF4F2EC);
                piece(pose, vc, light, o, GearModel.Piece.NAMEK_PADS, 0, 0xF4F2EC);
                piece(pose, vc, light, o, GearModel.Piece.SASH_TAILS, 0, 0x40B0E0);
                piece(pose, vc, light, o, GearModel.Piece.TURBAN, 0, 0xF4F2EC);
                piece(pose, vc, light, o, GearModel.Piece.TURBAN_BAND, 0, 0x6A3A9A);
            }
            case MAJIN -> { }
            case HOODIE -> piece(pose, vc, light, o, GearModel.Piece.HOOD, 0, 0xC8283A);
            case FROST_ARMOR -> {
                piece(pose, vc, light, o, GearModel.Piece.PADS, 0, 0x8A4AC8);
                piece(pose, vc, light, o, GearModel.Piece.PAD_TRIM, 0, 0xF2F0F6);
            }
            case FUSION -> {
                piece(pose, vc, light, o, GearModel.Piece.PADS, 0, 0xEAB830);
                piece(pose, vc, light, o, GearModel.Piece.PAD_TRIM, 0, 0x1F6F80);
            }
        }
    }

    private void piece(PoseStack pose, VertexConsumer vc, int light, int overlay, GearModel.Piece piece, float lift, int c) {
        model.render(pose, vc, light, overlay, piece, lift, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
    }
}
