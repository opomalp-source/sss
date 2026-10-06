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
    public static final java.util.List<RegistryObject<Block>> DRAGON_BALLS = balls(com.dbzenith.dragonball.BallSet.EARTH, MapColor.COLOR_ORANGE);
    /** The Black Star Dragon Balls and the Super Dragon Balls (12d). */
    public static final java.util.List<RegistryObject<Block>> BLACK_STAR_BALLS = balls(com.dbzenith.dragonball.BallSet.BLACK_STAR, MapColor.COLOR_RED);
    public static final java.util.List<RegistryObject<Block>> SUPER_BALLS = balls(com.dbzenith.dragonball.BallSet.SUPER, MapColor.GOLD);

    private static java.util.List<RegistryObject<Block>> balls(com.dbzenith.dragonball.BallSet set, MapColor color) {
        return java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(star -> register(set.prefix() + "_" + star, () -> new DragonBallBlock(set, star, BlockBehaviour.Properties.of()
                        .mapColor(color).strength(0.5f, 3_600_000f).lightLevel(s -> set == com.dbzenith.dragonball.BallSet.SUPER ? 14 : 10).noOcclusion()
                        .sound(SoundType.GLASS).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))))
                .toList();
    }

    /** Namek trees: pale trunks and round blue-green canopies (generated on Namek). */
    public static final RegistryObject<Block> NAMEK_LOG = register("namek_log",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.BIRCH_LOG)));
    public static final RegistryObject<Block> NAMEK_LEAVES = register("namek_leaves",
            () -> new net.minecraft.world.level.block.LeavesBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_LEAVES)));

    /** The other world (CX-12): the golden cloud sea, the scales of Snake Way, the springs of paradise. */
    public static final RegistryObject<Block> OTHERWORLD_CLOUD = register("otherworld_cloud", () -> new com.dbzenith.world.OtherworldBlocks.Cloud(
            BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_YELLOW).strength(0.3f).noOcclusion()
                    .sound(net.minecraft.world.level.block.SoundType.WOOL).isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
    public static final RegistryObject<Block> SNAKE_SCALE = register("snake_scale",
            () -> new Block(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.ORANGE_TERRACOTTA)));
    public static final RegistryObject<Block> SACRED_SPRING = register("sacred_spring", () -> new com.dbzenith.world.OtherworldBlocks.Spring(
            BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN).strength(1.5f).noOcclusion()
                    .lightLevel(s -> 12).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));

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
