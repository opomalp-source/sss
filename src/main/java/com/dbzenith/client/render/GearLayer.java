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

    /** Whether the player wears Piccolo's turban (on the head; the clothing rework), which hides hair and antennae. */
    public static boolean turban(Player player) {
        return ArtStyle.get() != ArtStyle.CLASSIC && com.dbzenith.item.HeadwearItem.worn(player) == com.dbzenith.item.HeadwearItem.Kind.TURBAN;
    }

    /** Whether something on the head covers the hair (a turban or a bandana). */
    public static boolean hairCovered(Player player) {
        if(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof com.dbzenith.item.SpaceSuitItem)return true;
        if (ArtStyle.get() == ArtStyle.CLASSIC) return false;
        var k = com.dbzenith.item.HeadwearItem.worn(player);
        return k == com.dbzenith.item.HeadwearItem.Kind.TURBAN || k == com.dbzenith.item.HeadwearItem.Kind.BANDANA;
    }

    static GiArmorItem.Set worn(Player player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).getItem() instanceof GiArmorItem gi ? gi.set() : null;
    }

    /** The thick parts of a top (armour plates, gloves, wristbands, the sash), drawn standing off the body (the clothing rework). */
    static ResourceLocation shell(GiArmorItem.Set set) {
        return texture(set, "top_shell");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || ArtStyle.get() == ArtStyle.CLASSIC) return;
        GiArmorItem.Set top = worn(player, EquipmentSlot.CHEST), pants = worn(player, EquipmentSlot.LEGS), boots = worn(player, EquipmentSlot.FEET);
        com.dbzenith.item.HeadwearItem.Kind hat = com.dbzenith.item.HeadwearItem.worn(player);
        PublicStatePacket fused = ClientPublicStates.get(player.getId());
        if (fused != null && fused.has(PublicStatePacket.FUSED_DANCE)) top = pants = boots = GiArmorItem.Set.FUSION;   // the dance's outfit (12c)
        if (top == null && pants == null && boots == null && hat == null) return;
        int o = OverlayTexture.NO_OVERLAY;
        model.follow(getParentModel());
        float legWidthR = getParentModel().rightLeg.xScale, legWidthL = getParentModel().leftLeg.xScale;
        float legDepthR = getParentModel().rightLeg.zScale, legDepthL = getParentModel().leftLeg.zScale;
        var torsoChildren = BodyShape.childrenOf(getParentModel().body);
        boolean[] chestVisible = new boolean[BodyShape.NAMES.length];
        // Trousers cover the waist underside, but must not paint the upper chest's underside too.
        if (torsoChildren != null) for (int i = 0; i < chestVisible.length; i++) {
            var chest = torsoChildren.get(BodyShape.NAMES[i]);
            if (chest != null) { chestVisible[i] = chest.visible; chest.visible = false; }
        }
        try {
            if (pants != null && !pants.battleArmor()) {
                float volume = pants == GiArmorItem.Set.MAJIN || pants == GiArmorItem.Set.FUSION ? 1.15f : 1.07f;
                getParentModel().rightLeg.xScale *= volume; getParentModel().leftLeg.xScale *= volume;
                getParentModel().rightLeg.zScale *= volume; getParentModel().leftLeg.zScale *= volume;
            }
            if (pants != null) thick(getParentModel(), texture(pants, "pants"), pose, buffers, light, player, 1.008f, getParentModel().body, getParentModel().rightLeg, getParentModel().leftLeg);
        } finally {
            if (torsoChildren != null) for (int i = 0; i < chestVisible.length; i++) {
                var chest = torsoChildren.get(BodyShape.NAMES[i]);
                if (chest != null) chest.visible = chestVisible[i];
            }
            getParentModel().rightLeg.xScale = legWidthR; getParentModel().leftLeg.xScale = legWidthL;
            getParentModel().rightLeg.zScale = legDepthR; getParentModel().leftLeg.zScale = legDepthL;
        }
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        if (top != null) {
            thick(m, texture(top, "top"), pose, buffers, light, player, 1.008f, m.body, m.rightArm, m.leftArm);
            thick(m, shell(top), pose, buffers, light, player, 1.1f, m.body, m.rightArm, m.leftArm);   // plates, gloves, bands: standing off the body
        }
        if (boots != null) thick(m, texture(boots, "boots"), pose, buffers, light, player, 1.13f, m.rightLeg, m.leftLeg);   // boots, thick

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        // Use the existing segmented/bending limb geometry for cloth volume: no rigid extra thigh boxes.
        if (top != null && top.battleArmor()) {
            piece(pose, vc, light, o, GearModel.Piece.CHEST_PLATE, 0, 0xF2F1E8);
            piece(pose, vc, light, o, GearModel.Piece.ABDOMEN, 0, top == GiArmorItem.Set.FROST_ARMOR ? 0x8251A0 : 0xC7A665);
            piece(pose, vc, light, o, GearModel.Piece.STRAPS, 0, 0xF2F1E8);
        }
        if (pants == GiArmorItem.Set.MAJIN) {
            piece(pose, vc, light, o, GearModel.Piece.MAJIN_BUCKLE, 0, 0xD9B44B);
            piece(pose, vc, light, o, GearModel.Piece.MAJIN_MARK, 0, 0x24212B);
        }
        if (hat != null) {                                                         // head clothing (the clothing rework)
            switch (hat) {
                case TURBAN -> {
                    piece(pose, vc, light, o, GearModel.Piece.TURBAN, 0, hat.color);
                    piece(pose, vc, light, o, GearModel.Piece.TURBAN_BAND, 0, hat.accent);
                }
                case BANDANA -> {
                    piece(pose, vc, light, o, GearModel.Piece.BANDANA, 0, hat.color);
                    piece(pose, vc, light, o, GearModel.Piece.BANDANA_KNOT, 0, hat.accent);
                }
                case HEADBAND -> {
                    piece(pose, vc, light, o, GearModel.Piece.HEADBAND, 0, hat.color);
                    piece(pose, vc, light, o, GearModel.Piece.HEADBAND_TAILS, Cloth.chain(player, "headband", Cloth.SASH, ageInTicks), hat.color);
                }
            }
        }
        if (top == null) return;
        Cloth.Chain sash = Cloth.chain(player, "sash", Cloth.SASH, ageInTicks);   // CX-44: the sash ends and the cape swing on springs
        switch (top) {
            case TURTLE -> {
                piece(pose, vc, light, o, GearModel.Piece.GI_LAPELS, 0, 0xED7424);
                piece(pose, vc, light, o, GearModel.Piece.SASH_KNOT, 0, 0x243D7E);
                piece(pose, vc, light, o, GearModel.Piece.SASH_TAILS, sash, 0x243D7E);
            }
            case DEMON -> {
                piece(pose, vc, light, o, GearModel.Piece.GI_LAPELS, 0, 0x65419A);
                piece(pose, vc, light, o, GearModel.Piece.SASH_KNOT, 0, 0xAD2941);
                piece(pose, vc, light, o, GearModel.Piece.SASH_TAILS, sash, 0xAD2941);
            }
            case BATTLE_ARMOR -> {                                                 // Saiyan armour: layered pads flaring down and out
                piece(pose, vc, light, o, GearModel.Piece.PADS, 0, 0xC7A665);
                piece(pose, vc, light, o, GearModel.Piece.PAD_LIP, 0, 0xE2E4EA);
                piece(pose, vc, light, o, GearModel.Piece.HIP_GUARDS, 0, 0xC7A665);
                piece(pose, vc, light, o, GearModel.Piece.PAD_TRIM, 0, 0xF0EFE8);
            }
            case CELL_ARMOR -> { } // No shoulder or hip guards: this is a distinct, streamlined shell.
            case NAMEKIAN -> {
                piece(pose, vc, light, o, GearModel.Piece.SASH_KNOT, 0, 0x4CA4CC);
                piece(pose, vc, light, o, GearModel.Piece.SASH_TAILS, sash, 0x4CA4CC);
            }
            case MAJIN -> { } // The belt belongs to the trousers, including when pieces are mixed.
            case HOODIE -> {
                piece(pose, vc, light, o, GearModel.Piece.HOOD, 0, 0xB52C41);
                piece(pose, vc, light, o, GearModel.Piece.HOOD_TRIM, 0, 0xAE293B);
            }
            case FROST_ARMOR -> {                                                  // the Frieza Force: pads sweeping up like wings
                piece(pose, vc, light, o, GearModel.Piece.FROST_PADS, 0, 0x8A4AC8);
                piece(pose, vc, light, o, GearModel.Piece.FROST_PAD_TRIM, 0, 0xF2F0F6);
            }
            case FUSION -> {
                piece(pose, vc, light, o, GearModel.Piece.FUSION_COLLAR, 0, 0xE5B64B);
                piece(pose, vc, light, o, GearModel.Piece.SASH_KNOT, 0, 0x50A6C9);
                piece(pose, vc, light, o, GearModel.Piece.SASH_TAILS, sash, 0x50A6C9);
            }
        }
    }

    /**
     * Draws {@code tex} on the figure with {@code parts} made wider and deeper by {@code s} (the clothing rework): what is
     * thick (plates, gloves, boots) stands off the body, following every bend and segment of the limbs as the body
     * itself does. The texture holds only those parts.
     */
    private void thick(PlayerModel<AbstractClientPlayer> m, ResourceLocation tex, PoseStack pose, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float s, net.minecraft.client.model.geom.ModelPart... parts) {
        for (var p : parts) {
            p.xScale *= s;
            p.yScale *= 1.008f;                                                  // above the growing fur on every face, including shoulders and knees
            p.zScale *= s;
        }
        float previousShell = LimbSegments.clothingShell;
        try {
            LimbSegments.clothingShell = s > 1.05f ? .16f : .08f;
            renderColoredCutoutModel(m, tex, pose, buffers, light, player, 1f, 1f, 1f);
        } finally {
            LimbSegments.clothingShell = previousShell;
            for (var p : parts) {
                p.xScale /= s;
                p.yScale /= 1.008f;
                p.zScale /= s;
            }
        }
    }

    private void piece(PoseStack pose, VertexConsumer vc, int light, int overlay, GearModel.Piece piece, float lift, int c) {
        model.render(pose, vc, light, overlay, piece, lift, null, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
    }

    private void piece(PoseStack pose, VertexConsumer vc, int light, int overlay, GearModel.Piece piece, Cloth.Chain cloth, int c) {
        model.render(pose, vc, light, overlay, piece, 0, cloth, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
    }
}
