package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.GiantBody;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** The Great Ape as a giant (v0.134): its size, its body's rules, trampling, and the clothes it tears. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GiantTests {
    private static final String EMPTY = "empty";

    private GiantTests() {}

    private static ServerPlayer saiyan(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.SAIYAN);
        d.recomputeIfStale();
        d.refill();
        return p;
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(DBZenith.MOD_ID, id));
    }

    @GameTest(template = EMPTY)
    public static void greatApeTearsClothButBattleArmourStretches(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item("turban")));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(item("turtle_top")));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(item("battle_armor_pants")));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        FormHandler.enter(p, d, Forms.GREAT_APE);
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the turban tears");
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the gi top tears");
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(item("battle_armor_pants")), "battle armour stretches and stays on");
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty() && p.getInventory().contains(new ItemStack(Items.IRON_BOOTS)),
                "metal armour bursts off into the inventory");
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(item("frost_armor_top")));
        FormHandler.revertToBase(p, d);
        FormHandler.enter(p, d, Forms.GREAT_APE);
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(item("frost_armor_top")), "the Frieza Force armour stretches too");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item("headband")));
        FormHandler.enter(p, d, Forms.GREAT_APE);                                // already an ape: nothing more tears
        helper.assertTrue(!p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "only the change itself tears clothes");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void greatApeIsGiantAndShrinksBack(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        float height = p.getBbHeight();
        double step = p.getAttributeValue(ForgeMod.STEP_HEIGHT_ADDITION.get()), reach = p.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        FormHandler.enter(p, d, Forms.GREAT_APE);
        GiantBody.tick(p);
        helper.assertTrue(p.getBbHeight() > height * 9, "the Great Ape stands ten times as tall: " + p.getBbHeight());
        helper.assertTrue(p.getAttributeValue(ForgeMod.STEP_HEIGHT_ADDITION.get()) > step + 4, "it steps over what a person would climb");
        helper.assertTrue(p.getAttributeValue(ForgeMod.ENTITY_REACH.get()) > reach + 5, "its arms reach far");
        helper.assertTrue(!p.hurt(p.damageSources().inWall(), 4f), "its head in a cliff doesn't suffocate it");
        FormHandler.revertToBase(p, d);
        GiantBody.tick(p);
        helper.assertTrue(Math.abs(p.getBbHeight() - height) < 1e-3, "back to its own size");
        helper.assertTrue(Math.abs(p.getAttributeValue(ForgeMod.STEP_HEIGHT_ADDITION.get()) - step) < 1e-6
                && Math.abs(p.getAttributeValue(ForgeMod.ENTITY_REACH.get()) - reach) < 1e-6, "and to its own step and reach");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void giantsTrampleTreesAndPlantsOnly(GameTestHelper helper) {
        helper.assertTrue(GiantBody.trampled(Blocks.OAK_LEAVES.defaultBlockState()) == 1, "leaves are crushed");
        helper.assertTrue(GiantBody.trampled(Blocks.GRASS.defaultBlockState()) == 1 && GiantBody.trampled(Blocks.POPPY.defaultBlockState()) == 1,
                "plants are crushed");
        helper.assertTrue(GiantBody.trampled(Blocks.OAK_LOG.defaultBlockState()) == 2, "logs break and drop wood");
        helper.assertTrue(GiantBody.trampled(Blocks.STONE.defaultBlockState()) == 0 && GiantBody.trampled(Blocks.CHEST.defaultBlockState()) == 0
                && GiantBody.trampled(Blocks.WATER.defaultBlockState()) == 0 && GiantBody.trampled(Blocks.OAK_PLANKS.defaultBlockState()) == 0,
                "stone, chests, water and buildings stand");
        helper.succeed();
    }
}
