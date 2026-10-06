package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.render.DragonSpiritRenderer;
import com.dbzenith.client.render.FalseMoonRenderer;
import com.dbzenith.client.render.FighterRenderer;
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
    public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(com.dbzenith.client.anim.AnimController::registerLayers);
        event.enqueueWork(com.dbzenith.client.ui.SettingsEntry::registerConfigScreen);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("aura_edge", new com.dbzenith.client.fx.AuraEdgeOverlay());
        event.registerAboveAll("hud", new DbzHud());
        event.registerAboveAll("transform_cut_in", new com.dbzenith.client.ui.CutInOverlay());
        event.registerAboveAll("beam_struggle", new com.dbzenith.client.ui.StruggleOverlay());
        event.registerAboveAll("dragon_radar", new RadarOverlay());
        event.registerAboveAll("scouter", new ScouterOverlay());
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
        event.registerLayerDefinition(com.dbzenith.client.render.GearModel.LAYER, com.dbzenith.client.render.GearModel::createLayer);
        event.registerLayerDefinition(com.dbzenith.client.render.GreatApeModel.LAYER, com.dbzenith.client.render.GreatApeModel::createLayer);
        event.registerLayerDefinition(com.dbzenith.client.render.DragonModel.LAYER, com.dbzenith.client.render.DragonModel::createLayer);
        event.registerLayerDefinition(com.dbzenith.client.render.SpacePodRenderer.LAYER, com.dbzenith.client.render.SpacePodRenderer::createLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                com.dbzenith.client.render.BodyShape.attach(renderer.getModel());
                renderer.addLayer(new com.dbzenith.client.render.BodySkinLayer(renderer));
                renderer.addLayer(new com.dbzenith.client.render.RaceSkinLayer(renderer));
                renderer.addLayer(new com.dbzenith.client.render.FormOverlayLayer(renderer));
                renderer.addLayer(new com.dbzenith.client.render.FaceLayer(renderer));
                renderer.addLayer(new com.dbzenith.client.render.BodyFxLayer(renderer));
                renderer.addLayer(new com.dbzenith.client.render.CosmeticsLayer(renderer));
                renderer.addLayer(new com.dbzenith.client.render.GearLayer(renderer, event.getEntityModels()));
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
        event.registerEntityRenderer(ModEntities.SPACE_POD.get(), com.dbzenith.client.render.SpacePodRenderer::new);
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.SPROUTLING.get(), ctx -> new FighterRenderer<>(ctx, "sproutling", 0.7f));
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.KI_SOLDIER.get(), ctx -> new FighterRenderer<>(ctx, "ki_soldier", 1f));
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.ANDROID_UNIT.get(), ctx -> new FighterRenderer<>(ctx, "android_unit", 1f));
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.TYRANT_LORD.get(), ctx -> new FighterRenderer<>(ctx, "tyrant_lord", 1.1f));
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.RAMPAGE_BRUTE.get(), ctx -> new FighterRenderer<>(ctx, "rampage_brute", 1.6f));
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.MASTER.get(), ctx -> new FighterRenderer<>(ctx, "martial_arts_master", 0.95f));
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.PATROL_OFFICER.get(), ctx -> new FighterRenderer<>(ctx, "patrol_officer", 1f));
        event.registerEntityRenderer(com.dbzenith.npc.ModNpcs.NAMEKIAN_WARRIOR.get(), ctx -> new FighterRenderer<>(ctx, "namekian_warrior", 1.05f));
    }
}
