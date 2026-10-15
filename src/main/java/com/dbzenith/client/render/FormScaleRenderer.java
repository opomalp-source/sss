package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.dbzenith.transform.GreatApe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Scales the player model for giant forms (Great Ape). Pushes in Pre (lowest priority, so only when nothing
 * cancelled the render) and pops in Post (lowest priority, after the aura and other Post renderers).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FormScaleRenderer {
    private FormScaleRenderer() {}

    private static GreatApeModel apeModel;
    /** When each player was first seen as an ape (their age in ticks), for the roar. */
    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Float> APE_SINCE = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private static final float ROAR_TICKS = 90;

    /** A Great Ape is drawn with its own model instead of a scaled-up player. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void greatApe(RenderPlayerEvent.Pre event) {
        net.minecraft.world.entity.player.Player player = event.getEntity();
        com.dbzenith.network.PublicStatePacket state = com.dbzenith.client.ClientPublicStates.get(player.getId());
        if (state == null || !GreatApe.isApeForm(state.form()) || player.isInvisible()) {
            if (state != null && !GreatApe.isApeForm(state.form())) APE_SINCE.remove(player.getId());
            return;
        }
        if (apeModel == null) {
            apeModel = new GreatApeModel(net.minecraft.client.Minecraft.getInstance().getEntityModels().bakeLayer(GreatApeModel.LAYER));
        }
        event.setCanceled(true);
        float pt = event.getPartialTick();
        com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
        float bodyYaw = net.minecraft.util.Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        float headYaw = net.minecraft.util.Mth.wrapDegrees(net.minecraft.util.Mth.rotLerp(pt, player.yHeadRotO, player.yHeadRot) - bodyYaw);
        float pitch = net.minecraft.util.Mth.lerp(pt, player.xRotO, player.getXRot());
        float s = GreatApe.scaleOf(player);
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - bodyYaw));
        pose.scale(s, s, s);
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        apeModel.attackTime = player.getAttackAnim(pt);
        float age = player.tickCount + pt;
        Float since = APE_SINCE.get(player.getId());                            // the roar that comes with the change
        if (since == null || age < since) APE_SINCE.put(player.getId(), since = age);
        apeModel.setRoar(Math.min(1f, (age - since) / ROAR_TICKS));
        apeModel.setAirborne(player.onGround() ? 0f : Math.min(1f, (float) Math.abs(player.getDeltaMovement().y) * 2.5f + 0.3f));
        apeModel.setupAnim(player, player.walkAnimation.position(pt), Math.min(1f, player.walkAnimation.speed(pt)),
                age, headYaw, pitch);
        int overlay = net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(player, 0f);
        apeModel.renderToBuffer(pose, event.getMultiBufferSource().getBuffer(apeModel.renderType(GreatApeModel.texture(state.form()))),
                event.getPackedLight(), overlay, 1f, 1f, 1f, 1f);
        apeModel.renderToBuffer(pose, event.getMultiBufferSource().getBuffer(net.minecraft.client.renderer.RenderType.eyes(GreatApeModel.EYES)),   // the red eyes glow
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 1f, 1f, 1f, 1f);
        pose.popPose();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderPlayerEvent.Pre event) {
        float s = GreatApe.scaleOf(event.getEntity());
        float width = bodyWidth(event.getEntity().getId());
        float height = com.dbzenith.appearance.Stature.heightScale(event.getEntity());
        event.getPoseStack().pushPose();
        if (s != 1f || width != 1f || height != 1f) event.getPoseStack().scale(s * width, s * height, s * width);
    }

    /** Body type: slim and bulky characters are drawn narrower or wider (looks only; hitbox unchanged). */
    private static float bodyWidth(int entityId) {
        com.dbzenith.network.PublicStatePacket state = com.dbzenith.client.ClientPublicStates.get(entityId);
        if (state == null) return 1f;
        return switch (state.bodyType()) {
            case 0 -> 0.92f;
            case 2 -> 1.12f;
            default -> 1f;
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(RenderPlayerEvent.Post event) {
        event.getPoseStack().popPose();
    }
}
