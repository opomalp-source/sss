package com.dbzenith.client.motion;

import com.dbzenith.DBZenith;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev automation for the motion engine (CX-18): a devshot named with "drivewalk", "drivesprint", "driveback" or
 * "driveleft" holds that movement key (and "driveturn" turns as it goes) until the shot is taken, so screenshots show
 * real walking, running and flight.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class MotionDev {
    private static int ticks;
    private static boolean forward, back, left, sprint, turn, up, down;

    private MotionDev() {}

    /** Reads the drive flags from a devshot name. */
    public static void fromShot(String name, int delay) {
        forward = name.contains("drivewalk") || name.contains("drivesprint");
        sprint = name.contains("drivesprint");
        back = name.contains("driveback");
        left = name.contains("driveleft");
        turn = name.contains("driveturn");
        up = name.contains("driveup");
        down = name.contains("drivedown");
        ticks = forward || back || left || turn || up || down ? delay + (up || down ? 6 : 60) : 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ticks <= 0) return;
        ticks--;
        boolean on = ticks > 0;
        mc.options.keyUp.setDown(on && forward);
        mc.options.keyDown.setDown(on && back);
        mc.options.keyLeft.setDown(on && left);
        mc.options.keySprint.setDown(on && sprint);
        mc.options.keyJump.setDown(on && up);
        mc.options.keyShift.setDown(on && down);
        if (on) mc.player.setSprinting(sprint);
        if (on && turn) mc.player.setYRot(mc.player.getYRot() + 4f);
    }
}
