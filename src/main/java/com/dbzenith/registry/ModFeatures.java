package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.world.NamekHouseFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** World-generation features (placed through data/dbzenith/worldgen). */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, DBZenith.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> NAMEK_HOUSE =
            FEATURES.register("namek_house", () -> new NamekHouseFeature(NoneFeatureConfiguration.CODEC));

    /** The great landmarks (CX-33): one per chunk, last of the decoration steps; it builds the parts that reach the chunk. */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> LANDMARKS =
            FEATURES.register("landmarks", () -> new com.dbzenith.world.landmark.LandmarkFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
