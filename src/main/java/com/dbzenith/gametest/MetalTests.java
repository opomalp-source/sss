package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.item.GiArmorItem;
import com.dbzenith.item.GiPlating;
import com.dbzenith.item.Metal;
import com.dbzenith.item.TrainingWeightsItem;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 12e: metals and alloys, plating, the dense weights and the hardest blocks. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MetalTests {
    private static final String EMPTY = "empty";

    private MetalTests() {}

    private static TransientCraftingContainer grid(ItemStack... items) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player p) { return true; }
        };
        TransientCraftingContainer g = new TransientCraftingContainer(menu, 3, 3);
        for (int i = 0; i < items.length; i++) g.setItem(i, items[i]);
        return g;
    }

    @GameTest(template = EMPTY)
    public static void platingAGiAddsReductionByTier(GameTestHelper helper) {
        Level level = helper.getLevel();
        ItemStack top = new ItemStack(ModItems.GI.get(0).get());                          // the Turtle gi top
        var g = grid(top, new ItemStack(Metal.STEEL.ingot()));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, g, level);
        helper.assertTrue(recipe.isPresent(), "a gi and an ingot make a plating recipe");
        ItemStack plated = recipe.get().assemble(g, level.registryAccess());
        helper.assertTrue(GiPlating.of(plated) == Metal.STEEL && plated.getItem() == top.getItem(), "the same gi, plated with steel");
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(top, new ItemStack(Metal.STEEL.ingot()), new ItemStack(Metal.TIN.ingot())), level).isEmpty(), "one ingot only");

        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        p.setItemSlot(EquipmentSlot.CHEST, GiPlating.plate(top, Metal.KACHI_KATCHIN));
        GiArmorItem.updateBonus(p, d);
        helper.assertTrue(Math.abs(d.getGearReduction() - Metal.KACHI_KATCHIN.platingReduction()) < 1e-9,
                "one plated piece counts on its own: " + d.getGearReduction());
        helper.assertTrue(Metal.KACHI_KATCHIN.platingReduction() > Metal.STEEL.platingReduction() && Metal.STEEL.platingReduction() > Metal.TIN.platingReduction(),
                "harder metal, more protection");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyAlloyCanBeMadeAndEveryOreSmelted(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (Metal m : Metal.values()) {
            if (m.kind() == Metal.Kind.ALLOY) {
                helper.assertTrue(recipes.byKey(new ResourceLocation(DBZenith.MOD_ID, m.id() + "_blend")).isPresent(), "a blend recipe for " + m.id());
                helper.assertTrue(recipes.byKey(new ResourceLocation(DBZenith.MOD_ID, m.id() + "_ingot_from_blasting")).isPresent(), "blasting for " + m.id());
            }
            if (m.kind() == Metal.Kind.ORE) {
                helper.assertTrue(ModBlocks.ORES.containsKey(m), "an ore for " + m.id());
                helper.assertTrue(recipes.byKey(new ResourceLocation(DBZenith.MOD_ID, m.id() + "_ingot_from_smelting")).isPresent(), "smelting for " + m.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void katchinShrugsOffExplosions(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, ModBlocks.KATCHIN_BLOCK.get());
        helper.setBlock(at.east(), ModBlocks.KACHI_KATCHIN_BLOCK.get());
        BlockPos abs = helper.absolutePos(at.above());
        helper.getLevel().explode(null, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 6f, Level.ExplosionInteraction.TNT);
        helper.assertBlockPresent(ModBlocks.KATCHIN_BLOCK.get(), at);
        helper.assertBlockPresent(ModBlocks.KACHI_KATCHIN_BLOCK.get(), at.east());
        helper.assertTrue(ModBlocks.KATCHIN_BLOCK.get().defaultBlockState().is(BlockTags.NEEDS_DIAMOND_TOOL), "only diamond picks mine it");
        helper.assertTrue(ModBlocks.ORES.get(Metal.TIN).get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "ores are mined with a pickaxe");
        double heavy = ((TrainingWeightsItem) ModItems.HEAVY_TRAINING_WEIGHTS.get()).trainingFactor();
        double tung = ((TrainingWeightsItem) ModItems.TUNGSTEN_TRAINING_WEIGHTS.get()).trainingFactor();
        double kat = ((TrainingWeightsItem) ModItems.KATCHIN_TRAINING_WEIGHTS.get()).trainingFactor();
        helper.assertTrue(heavy < tung && tung < kat, "denser metal, heavier weights");
        helper.succeed();
    }
}
