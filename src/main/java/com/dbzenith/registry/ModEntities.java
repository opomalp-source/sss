package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.KiBeamEntity;
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

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
