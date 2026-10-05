package com.dbzenith.appearance;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A character's height (85-115% of the usual 1.8 blocks), chosen at creation: the model, the hitbox and the eyes all
 * follow it, so a tall fighter really does see over walls and a short one fits through gaps. Width is unchanged.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Stature {
    private Stature() {}

    /** Height as a factor: from capability data on the server, from public state on clients. */
    public static float heightScale(Player player) {
        int percent = 100;
        if (player.level().isClientSide) {
            PublicStatePacket state = ClientPublicStates.get(player.getId());
            if (state != null && state.height() > 0) percent = state.height();
        } else {
            PlayerData d = ModCapabilities.get(player).orElse(null);
            if (d != null) percent = d.getHeightPercent();
        }
        return Math.max(PlayerData.MIN_HEIGHT, Math.min(PlayerData.MAX_HEIGHT, percent)) / 100f;
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        float h = heightScale(player);
        if (h == 1f) return;
        float eye = event.getNewEyeHeight();
        event.setNewSize(event.getNewSize().scale(1f, h));
        event.setNewEyeHeight(eye * h);
    }
}
