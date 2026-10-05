package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.stats.Race;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The full race look (Namekian, Frost Demon, Majin): a complete skin drawn over the player's own, like vanilla's
 * glowing-eyes layers. Players switch it off on the Life screen.
 */
public class RaceSkinLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public RaceSkinLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** The race's (or variant's) skin, or null if it has none (they look like the player's own skin). */
    public static ResourceLocation texture(Race race, com.dbzenith.race.Variant variant) {
        String name = skinName(race, variant);
        return name == null ? null : new ResourceLocation(DBZenith.MOD_ID, "textures/entity/race/" + name + ".png");
    }

    /** The race skin a race and lineage wear (before any transformation), or null for races drawn on other bodies. */
    public static String skinName(Race race, com.dbzenith.race.Variant variant) {
        return switch (variant) {
            case METAL -> "metal_frost_demon";
            case MUTANT -> "mutant_frost_demon";
            case CORRUPTED -> "corrupted_majin";
            case DEMON_CLAN -> "demon_namekian";
            case DEMON -> "core_demon";
            case KAI -> "kai";
            default -> switch (race) {
                case NAMEKIAN, FROST_DEMON, MAJIN, VAMPIRE, BIO_ANDROID, TUFFLE, GEN_ALIEN -> race.id();
                default -> null;
            };
        };
    }

    /** The skin a player wears now: their race skin, recoloured by their form if it changes the body (transform.FormLooks). */
    public static ResourceLocation texture(PublicStatePacket state) {
        String name = com.dbzenith.transform.FormLooks.skin(state.form(), skinName(state.raceEnum(), state.variantEnum()));
        return name == null ? null : new ResourceLocation(DBZenith.MOD_ID, "textures/entity/race/" + name + ".png");
    }

    /**
     * With a race look on, the player's own outer skin layer (hat, jacket, sleeves, pants) would show over the race
     * skin; hide it for that frame. The renderer sets these flags from the skin options again every frame.
     */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE,
            value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class HideOuterLayer {
        private HideOuterLayer() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void pre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
            PublicStatePacket state = ClientPublicStates.get(event.getEntity().getId());
            boolean raceSkin = state != null && state.raceLook() && texture(state) != null;
            if (!raceSkin && !BodySkinLayer.active(state)) return;
            PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
            m.hat.visible = false;
            m.jacket.visible = false;
            m.leftSleeve.visible = false;
            m.rightSleeve.visible = false;
            m.leftPants.visible = false;
            m.rightPants.visible = false;
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (state == null || !state.raceLook()) return;
        ResourceLocation tex = texture(state);
        if (tex != null) renderColoredCutoutModel(getParentModel(), tex, pose, buffers, light, player, 1f, 1f, 1f);
    }
}
