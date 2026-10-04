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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderPlayerEvent.Pre event) {
        float s = GreatApe.scaleOf(event.getEntity());
        float width = bodyWidth(event.getEntity().getId());
        event.getPoseStack().pushPose();
        if (s != 1f || width != 1f) event.getPoseStack().scale(s * width, s, s * width);
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
