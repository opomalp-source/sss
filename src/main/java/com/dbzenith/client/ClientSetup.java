package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.render.DragonSpiritRenderer;
import com.dbzenith.client.render.FalseMoonRenderer;
import com.dbzenith.client.render.FormHairLayer;
import com.dbzenith.client.render.FormHairModel;
import com.dbzenith.client.render.KiBeamRenderer;
import com.dbzenith.client.render.RaceFeatureLayer;
import com.dbzenith.client.render.RaceFeatureModel;
import com.dbzenith.client.render.KiBlastRenderer;
import com.dbzenith.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
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
        event.registerAboveAll("dragon_radar", new RadarOverlay());
        event.registerAboveAll("debug_stats", new DebugStatsOverlay());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ModKeys.ALL) event.register(key);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FormHairModel.LAYER, FormHairModel::createLayer);
        event.registerLayerDefinition(RaceFeatureModel.LAYER, RaceFeatureModel::createLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new FormHairLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new RaceFeatureLayer(renderer, event.getEntityModels()));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KI_BLAST.get(), KiBlastRenderer::new);
        event.registerEntityRenderer(ModEntities.KI_BEAM.get(), KiBeamRenderer::new);
        event.registerEntityRenderer(ModEntities.FALSE_MOON.get(), FalseMoonRenderer::new);
        event.registerEntityRenderer(ModEntities.DRAGON_SPIRIT.get(), DragonSpiritRenderer::new);
    }
}
