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

    /** The race's skin, or null if it has none (they look like the player's own skin). */
    public static ResourceLocation texture(Race race) {
        return switch (race) {
            case NAMEKIAN, FROST_DEMON, MAJIN -> new ResourceLocation(DBZenith.MOD_ID, "textures/entity/race/" + race.id() + ".png");
            default -> null;
        };
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
            if (state == null || !state.raceLook() || texture(state.raceEnum()) == null) return;
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
        ResourceLocation tex = texture(state.raceEnum());
        if (tex != null) renderColoredCutoutModel(getParentModel(), tex, pose, buffers, light, player, 1f, 1f, 1f);
    }
}
