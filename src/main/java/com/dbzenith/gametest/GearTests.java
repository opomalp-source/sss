package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.item.CapsuleItem;
import com.dbzenith.item.GiArmorItem;
import com.dbzenith.item.ScouterItem;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.registry.ModItems;
import com.dbzenith.stats.Attribute;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 4 slice 3: gi set bonus, scouter readings and overload, capsule storage. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GearTests {
    private static final String EMPTY = "empty";

    private GearTests() {}

    @GameTest(template = EMPTY)
    public static void fullGiSetBoostsCombatStats(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        KiTicker.tick(p, d);
        double melee = d.getDerived().meleeDamage();
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.GI.get(0).get()));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.GI.get(1).get()));
        KiTicker.tick(p, d);
        helper.assertTrue(d.getDerived().meleeDamage() == melee, "two pieces give no set bonus");
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.GI.get(2).get()));
        KiTicker.tick(p, d);
        d.recomputeIfStale();
        double expected = melee * GiArmorItem.Set.TURTLE.strMult();
        helper.assertTrue(Math.abs(d.getDerived().meleeDamage() - expected) < 1e-6, "full Turtle set: +10% melee, got "
                + d.getDerived().meleeDamage() + " expected " + expected);
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.GI.get(5).get())); // demon boots: mixed set
        KiTicker.tick(p, d);
        d.recomputeIfStale();
        helper.assertTrue(d.getDerived().meleeDamage() == melee, "mixed sets give no bonus");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void scouterReadsAndOverloads(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        helper.assertTrue(ScouterItem.powerOf(z) == ScouterItem.mobPower(z) && ScouterItem.powerOf(z) > 0, "creatures read from health and armor");
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAttribute(Attribute.STRENGTH, 5000);
        d.recomputeIfStale();
        helper.assertTrue(ScouterItem.powerOf(p) > 10_000, "players read from their stats");

        // look at the zombie from 3 blocks away with a scouter on
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        p.teleportTo(at.getX() + 0.5 - 3, at.getY(), at.getZ() + 0.5);
        p.setYRot(-90f);
        p.setXRot(10f);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SCOUTER.get()));
        helper.assertTrue(ScouterItem.target(p) == z, "the scouter locks onto what you look at");
        helper.assertTrue(!ScouterItem.checkOverload(p), "a zombie does not overload it");
        long limit = DBZConfig.SERVER.scouterLimit.get();
        DBZConfig.SERVER.scouterLimit.set(10L); // any reading overloads it now
        boolean broke = ScouterItem.checkOverload(p);
        DBZConfig.SERVER.scouterLimit.set(limit);
        helper.assertTrue(broke && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "a reading above the limit shatters the scouter");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void capsuleKeepsItemsAndRefusesCapsules(GameTestHelper helper) {
        ItemStack capsule = new ItemStack(ModItems.CAPSULE.get());
        SimpleContainer c = CapsuleItem.load(capsule);
        c.setItem(0, new ItemStack(Items.DIAMOND, 5));
        c.setItem(26, new ItemStack(ModItems.SENZU_BEAN.get(), 3));
        CapsuleItem.save(capsule, c);
        SimpleContainer reopened = CapsuleItem.load(capsule);
        helper.assertTrue(reopened.getItem(0).is(Items.DIAMOND) && reopened.getItem(0).getCount() == 5, "items survive in the capsule");
        helper.assertTrue(reopened.getItem(26).getCount() == 3, "last slot too");

        ServerPlayer p = TestPlayers.create(helper);
        reopened.setItem(5, new ItemStack(ModItems.CAPSULE.get()));
        CapsuleItem.ejectNested(reopened, p);
        helper.assertTrue(reopened.getItem(5).isEmpty() && p.getInventory().contains(new ItemStack(ModItems.CAPSULE.get())),
                "a nested capsule is handed back");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
