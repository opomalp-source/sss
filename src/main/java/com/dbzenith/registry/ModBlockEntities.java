package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.world.GravityChamberBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DBZenith.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // the data fixer type is unused for mod block entities
    public static final RegistryObject<BlockEntityType<GravityChamberBlockEntity>> GRAVITY_CHAMBER = BLOCK_ENTITIES.register("gravity_chamber",
            () -> BlockEntityType.Builder.of(GravityChamberBlockEntity::new, ModBlocks.GRAVITY_CHAMBER.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
