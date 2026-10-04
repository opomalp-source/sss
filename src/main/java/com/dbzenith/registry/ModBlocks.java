package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.dragonball.DragonBallBlock;
import com.dbzenith.world.TrainingBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DBZenith.MOD_ID);

    public static final RegistryObject<Block> GRAVITY_CHAMBER = register("gravity_chamber",
            () -> new TrainingBlocks.GravityChamber(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(s -> 7)));
    public static final RegistryObject<Block> PUNCHING_BAG = register("punching_bag",
            () -> new TrainingBlocks.PunchingBag(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
                    .strength(0.8f).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> TIME_CHAMBER_DOOR = register("time_chamber_door",
            () -> new TrainingBlocks.TimeChamberDoor(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(-1f, 3_600_000f).lightLevel(s -> 12).sound(SoundType.STONE)));

    /** One-star to seven-star Dragon Balls (index = star - 1). Explosion-proof, glowing. */
    public static final java.util.List<RegistryObject<Block>> DRAGON_BALLS = java.util.stream.IntStream.rangeClosed(1, 7)
            .mapToObj(star -> register("dragon_ball_" + star, () -> new DragonBallBlock(star, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(0.5f, 3_600_000f).lightLevel(s -> 10).noOcclusion()
                    .sound(SoundType.GLASS).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))))
            .toList();

    private ModBlocks() {}

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> obj = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
        return obj;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
