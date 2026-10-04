package com.dbzenith.client;

import com.dbzenith.DBZenith;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Client-only listeners on the Forge event bus. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    /**
     * Dev automation, inert unless the JVM flags are set (they only are in the dev run configs):
     * {@code -Ddbzenith.devScreenshots=100,200} saves screenshots that many ticks after joining a world;
     * {@code -Ddbzenith.devQuitAfter=300} closes the client after that many in-world ticks.
     */
    private static final Set<Integer> DEV_SCREENSHOT_TICKS = parseTicks(System.getProperty("dbzenith.devScreenshots", ""));
    private static final int DEV_QUIT_AFTER = Integer.getInteger("dbzenith.devQuitAfter", -1);
    private static final boolean DEV_AUTOMATION = Boolean.getBoolean("dbzenith.devAutomation");
    private static int ticksInWorld;

    private ClientEvents() {}

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPlayerData.clear();
        ClientCombatState.clear();
        ticksInWorld = 0;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ticksInWorld++;
        if (DEV_SCREENSHOT_TICKS.contains(ticksInWorld)) {
            Screenshot.grab(mc.gameDirectory, "dbz_dev_" + ticksInWorld + ".png", mc.getMainRenderTarget(),
                    msg -> DBZenith.LOGGER.info("[dev] {}", msg.getString()));
        }
        if (DEV_QUIT_AFTER > 0 && ticksInWorld == DEV_QUIT_AFTER) {
            DBZenith.LOGGER.info("[dev] devQuitAfter reached, stopping client");
            mc.stop();
        }
    }

    /** Dev automation: save a named screenshot now (requested by /dbz devshot). */
    public static void devScreenshot(String name) {
        if (!DEV_AUTOMATION) return;
        Minecraft mc = Minecraft.getInstance();
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        Screenshot.grab(mc.gameDirectory, "dbz_" + safe + ".png", mc.getMainRenderTarget(),
                msg -> DBZenith.LOGGER.info("[dev] {}", msg.getString()));
    }

    private static Set<Integer> parseTicks(String csv) {
        if (csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Integer::parseInt).collect(Collectors.toUnmodifiableSet());
    }
}
