package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.data.PlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Powering up roots you to the spot (CX-24): while you charge ki or hold a transformation, the movement keys do
 * nothing (walking, strafing, jumping, flying up or down) and whatever speed you had dies away quickly, so you stand
 * and roar instead of sliding across the ground.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ChargeLock {
    private ChargeLock() {}

    /** Are you charging or powering up right now? */
    public static boolean locked() {
        if (!ClientPlayerData.hasData()) return false;
        PlayerData d = ClientPlayerData.get();
        return d.isCharging() || d.isTransforming() && d.isTransformHeld();
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!locked()) return;
        event.getInput().forwardImpulse = 0;
        event.getInput().leftImpulse = 0;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
        event.getInput().up = event.getInput().down = event.getInput().left = event.getInput().right = false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !locked()) return;
        Vec3 v = p.getDeltaMovement();
        boolean flying = p.getAbilities().flying;
        p.setDeltaMovement(v.x * 0.5, flying ? v.y * 0.5 : v.y, v.z * 0.5);   // stop quickly; fall if you were jumping
        p.setSprinting(false);
    }
}
