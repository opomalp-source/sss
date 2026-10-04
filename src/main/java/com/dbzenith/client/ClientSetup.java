package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.render.KiBeamRenderer;
import com.dbzenith.client.render.KiBlastRenderer;
import com.dbzenith.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registration on the mod event bus. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hud", new DbzHud());
        event.registerAboveAll("debug_stats", new DebugStatsOverlay());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ModKeys.ALL) event.register(key);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KI_BLAST.get(), KiBlastRenderer::new);
        event.registerEntityRenderer(ModEntities.KI_BEAM.get(), KiBeamRenderer::new);
    }
}
