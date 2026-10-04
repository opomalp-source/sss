package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.dragonball.DragonSpiritEntity;
import com.dbzenith.skill.KiBeamEntity;
import com.dbzenith.transform.FalseMoonEntity;
import com.dbzenith.skill.KiBlastEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DBZenith.MOD_ID);

    public static final RegistryObject<EntityType<KiBlastEntity>> KI_BLAST = ENTITIES.register("ki_blast",
            () -> EntityType.Builder.<KiBlastEntity>of(KiBlastEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("ki_blast"));

    public static final RegistryObject<EntityType<KiBeamEntity>> KI_BEAM = ENTITIES.register("ki_beam",
            () -> EntityType.Builder.<KiBeamEntity>of(KiBeamEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .build("ki_beam"));

    public static final RegistryObject<EntityType<FalseMoonEntity>> FALSE_MOON = ENTITIES.register("false_moon",
            () -> EntityType.Builder.<FalseMoonEntity>of(FalseMoonEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("false_moon"));

    public static final RegistryObject<EntityType<DragonSpiritEntity>> DRAGON_SPIRIT = ENTITIES.register("dragon_spirit",
            () -> EntityType.Builder.<DragonSpiritEntity>of(DragonSpiritEntity::new, MobCategory.MISC)
                    .sized(4f, 24f)
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .build("dragon_spirit"));

    public static final RegistryObject<EntityType<com.dbzenith.world.SpacePodEntity>> SPACE_POD = ENTITIES.register("space_pod",
            () -> EntityType.Builder.<com.dbzenith.world.SpacePodEntity>of(com.dbzenith.world.SpacePodEntity::new, MobCategory.MISC)
                    .sized(1.5f, 1.6f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("space_pod"));

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
