package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.world.Planet;
import com.dbzenith.world.SpacePodEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Remaining-art pass: Namek trees and dome houses generate, and the Space Pod launches and lands. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArtWorldTests {
    private static final String EMPTY = "empty";

    private ArtWorldTests() {}

    /** A flat grass pad far from the other tests, with open air above. */
    private static BlockPos pad(ServerLevel level, int x, int z) {
        BlockPos base = new BlockPos(x, 150, z);
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            level.setBlock(base.offset(dx, -2, dz), Blocks.DIRT.defaultBlockState(), 2);
            level.setBlock(base.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int dy = 0; dy < 14; dy++) level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        }
        return base;
    }

    private static boolean place(ServerLevel level, String feature, BlockPos at) {
        ConfiguredFeature<?, ?> f = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(new ResourceLocation(DBZenith.MOD_ID, feature));
        return f != null && f.place(level, level.getChunkSource().getGenerator(), level.random, at);
    }

    @GameTest(template = EMPTY)
    public static void namekTreesAndHousesGenerate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tree = pad(level, 40_000, 40_000);
        helper.assertTrue(place(level, "namek_tree", tree), "a Namek tree grows");
        helper.assertTrue(level.getBlockState(tree).is(ModBlocks.NAMEK_LOG.get()), "pale trunk");
        boolean leaves = false;
        for (int y = 4; y < 14 && !leaves; y++) leaves = level.getBlockState(tree.above(y)).is(ModBlocks.NAMEK_LEAVES.get());
        helper.assertTrue(leaves, "round canopy on top");
        BlockPos house = pad(level, 40_100, 40_000);
        helper.assertTrue(place(level, "namek_house", house), "a dome house is built");
        helper.assertTrue(level.getBlockState(house.above(5)).is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                || level.getBlockState(house.above(4)).is(Blocks.WHITE_CONCRETE)
                || level.getBlockState(house.above(5)).is(Blocks.WHITE_CONCRETE), "with a domed roof");
        helper.assertTrue(level.getBlockState(house.above(1)).isAir(), "and room inside");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void spacePodLaunchesAndLands(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        SpacePodEntity pod = SpacePodEntity.create(helper.getLevel(), at);
        helper.getLevel().addFreshEntity(pod);
        p.teleportTo(at.x, at.y, at.z);
        helper.assertTrue(p.startRiding(pod, true), "climb in");
        helper.assertTrue(pod.launch(p, Planet.NORTHERN_PLANET), "lift-off");
        helper.assertTrue(!pod.launch(p, Planet.NAMEK), "already flying");
        helper.succeedWhen(() -> {
            helper.assertTrue(Planet.of(p.level()) == Planet.NORTHERN_PLANET, "arrived on the Northern Planet");
            helper.assertTrue(p.getVehicle() instanceof SpacePodEntity landing && landing.state() != SpacePodEntity.LAUNCHING,
                    "riding the landing pod");
            p.stopRiding();
            TestPlayers.remove(helper, p);
        });
    }
}
