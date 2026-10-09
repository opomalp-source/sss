package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.network.PublicStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Your own arm in first person, as your character's arm (CX-18): the same body, race skin, form markings and glows the
 * third-person model wears, at the same thickness, instead of the bare Minecraft skin. The skin layers are run again
 * with every other part of the model hidden. Also gives the afterimages the character's textures.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonBody {
    private static final Map<PlayerRenderer, List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>> SKIN_LAYERS = new IdentityHashMap<>();

    private FirstPersonBody() {}

    /** The layers that paint the body itself (not hair, gear or held items), kept per player renderer. */
    public static void register(PlayerRenderer renderer, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer) {
        SKIN_LAYERS.computeIfAbsent(renderer, r -> new ArrayList<>()).add(layer);
    }

    /** Whether the character has a body of its own (a chosen skin tone or a race look) rather than the Minecraft skin. */
    public static boolean hasOwnBody(PublicStatePacket state) {
        return state != null && (BodySkinLayer.active(state) || (state.raceLook() && RaceSkinLayer.texture(state) != null));
    }

    /** The character's main body texture (for afterimages), or null for the Minecraft skin. */
    public static ResourceLocation bodyTexture(PublicStatePacket state) {
        if (state == null) return null;
        if (BodySkinLayer.active(state)) return BodySkinLayer.bodyTexture(state);
        return state.raceLook() ? RaceSkinLayer.texture(state) : null;
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        PublicStatePacket state = ClientPublicStates.get(player.getId());
        if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer pr) {
            // your own fist and forearm in 3D (CX-27); a vanilla armour chestplate hides the forearm muscles only
            Physique.show(pr.getModel(), BodyShape.build(state), !BodyShape.armour(player, net.minecraft.world.entity.EquipmentSlot.CHEST), true, true);
            Physique.spread = 0f;
        }
        if (!hasOwnBody(state)) return;
        EntityRenderer<?> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(r instanceof PlayerRenderer renderer)) return;
        List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers = SKIN_LAYERS.get(renderer);
        if (layers == null) return;
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart arm = right ? model.rightArm : model.leftArm, sleeve = right ? model.rightSleeve : model.leftSleeve;

        // pose the arm as vanilla does for the first-person hand
        if (model instanceof dev.kosmx.playerAnim.impl.IPlayerModel p) p.playerAnimator_prepForFirstPersonRender();
        model.attackTime = 0f;
        model.crouching = false;
        model.swimAmount = 0f;
        model.setupAnim(player, 0f, 0f, 0f, 0f, 0f);
        arm.xRot = 0f;
        sleeve.xRot = 0f;
        BodyShape.shape(model, FormShape.bulk(state.form()));

        boolean[] shown = visibility(model);
        model.setAllVisible(false);
        arm.visible = true;
        sleeve.visible = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE) && !state.raceLook();
        // the Minecraft skin underneath (covered by the character's body), then the body's own layers on top
        arm.render(pose, buffers.getBuffer(RenderType.entitySolid(player.getSkinTextureLocation())), light, OverlayTexture.NO_OVERLAY);
        float pt = Minecraft.getInstance().getFrameTime();
        for (RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer : layers) {
            layer.render(pose, buffers, light, player, 0f, 0f, pt, player.tickCount + pt, 0f, 0f);
        }
        restore(model, shown);
        BodyShape.shape(model, 1f, 1f, 1f, 1f);
    }

    private static boolean[] visibility(PlayerModel<?> m) {
        return new boolean[]{m.head.visible, m.hat.visible, m.body.visible, m.jacket.visible, m.rightArm.visible, m.leftArm.visible,
                m.rightSleeve.visible, m.leftSleeve.visible, m.rightLeg.visible, m.leftLeg.visible, m.rightPants.visible, m.leftPants.visible};
    }

    private static void restore(PlayerModel<?> m, boolean[] v) {
        m.head.visible = v[0];
        m.hat.visible = v[1];
        m.body.visible = v[2];
        m.jacket.visible = v[3];
        m.rightArm.visible = v[4];
        m.leftArm.visible = v[5];
        m.rightSleeve.visible = v[6];
        m.leftSleeve.visible = v[7];
        m.rightLeg.visible = v[8];
        m.leftLeg.visible = v[9];
        m.rightPants.visible = v[10];
        m.leftPants.visible = v[11];
    }
}
