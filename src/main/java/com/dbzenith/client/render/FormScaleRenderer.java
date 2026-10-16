package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Scales the player model for giant forms (the Great Ape, drawn as the player's own figure grown giant under its fur,
 * {@link ApeLayer}). Pushes in Pre (lowest priority, so only when nothing cancelled the render) and pops in Post
 * (lowest priority, after the aura and other Post renderers).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FormScaleRenderer {
    private FormScaleRenderer() {}

    /** The Great Ape is broader than the figure it grew from: wide shoulders, a heavy trunk. */
    private static final float APE_WIDTH = 1.12f;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderPlayerEvent.Pre event) {
        float s = ApeTransformation.scale(event.getEntity(), event.getPartialTick());
        float width = bodyWidth(event.getEntity().getId());
        float height = com.dbzenith.appearance.Stature.heightScale(event.getEntity());
        event.getPoseStack().pushPose();
        if (s != 1f || width != 1f || height != 1f) event.getPoseStack().scale(s * width, s * height, s * width);
    }

    /** Body type: slim and bulky characters are drawn narrower or wider (looks only; hitbox unchanged). */
    private static float bodyWidth(int entityId) {
        com.dbzenith.network.PublicStatePacket state = com.dbzenith.client.ClientPublicStates.get(entityId);
        if (state == null) return 1f;
        if (ApeLook.is(state)) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(entityId) instanceof net.minecraft.world.entity.player.Player p) {
                var before = ApeTransformation.before(p);
                float original = before == null ? 1 : before.bodyType() == 0 ? 0.92f : before.bodyType() == 2 ? 1.12f : 1f;
                return net.minecraft.util.Mth.lerp(com.dbzenith.transform.ApeChange.growth(ApeTransformation.age(p, mc.getFrameTime())), original, APE_WIDTH);
            }
            return APE_WIDTH;
        }
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
