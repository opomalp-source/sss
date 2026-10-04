package com.dbzenith;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.registry.ModBlockEntities;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.registry.ModCreativeTabs;
import com.dbzenith.registry.ModEntities;
import com.dbzenith.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point. Forge-bus listeners register themselves via {@code @Mod.EventBusSubscriber};
 * mod-bus registration happens here.
 */
@Mod(DBZenith.MOD_ID)
public class DBZenith {
    public static final String MOD_ID = "dbzenith";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DBZenith() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        com.dbzenith.npc.ModNpcs.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModCapabilities::register);
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DBZConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, DBZConfig.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("Dragon Block Zenith common setup complete");
    }
}
