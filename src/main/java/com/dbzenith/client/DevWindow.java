package com.dbzenith.client;

import com.dbzenith.DBZenith;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Dev automation only (the scripted test client, {@code -Ddbzenith.devAutomation=true}; never in a normal game): the
 * window moves itself off-screen once it is up, so background visual checks keep rendering (a minimised window draws
 * nothing) without sitting on the user's desktop.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class DevWindow {
    private DevWindow() {}

    private static boolean moved;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (moved || e.phase != TickEvent.Phase.END || !Boolean.getBoolean("dbzenith.devAutomation") || Boolean.getBoolean("dbzenith.devWindowVisible")) return;
        moved = true;
        long handle = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) != 0) GLFW.glfwRestoreWindow(handle);
        GLFW.glfwSetWindowPos(handle, -6000, 80);
    }
}
