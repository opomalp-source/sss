package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.race.Alignment;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.TailRules;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.world.LifeSim;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 5 slice 4: alignment, path, mental age, tail. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IdentityTests {
    private static final String EMPTY = "empty";
    private static final int SPAWN_PROTECTION = 62;

    private IdentityTests() {}

    @GameTest(template = EMPTY)
    public static void deedsMoveAlignment(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        var master = helper.spawnWithNoFreeWill(ModNpcs.MASTER.get(), new BlockPos(1, 1, 1));
        helper.assertTrue(Alignment.shiftFor(villager) == -5, "killing villagers is evil");
        helper.assertTrue(Alignment.shiftFor(master) == -20, "killing a master is very evil");
        helper.assertTrue(Alignment.shiftFor(zombie) > 0, "slaying monsters is good");
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAlignment(0);
        for (int i = 0; i < 8; i++) d.addAlignment(0.25);
        helper.assertTrue(d.getAlignment() == 2, "small good deeds add up: " + d.getAlignment());
        d.setAlignment(50);
        helper.assertTrue(Alignment.of(d) == Alignment.Standing.GOOD && Alignment.kiRegenMultiplier(d) > 1 && Alignment.damageMultiplier(d) == 1,
                "good: faster ki");
        d.setAlignment(-50);
        helper.assertTrue(Alignment.of(d) == Alignment.Standing.EVIL && Alignment.damageMultiplier(d) > 1 && Alignment.kiRegenMultiplier(d) == 1,
                "evil: harder hits");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pathsShapeTheFighter(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAttribute(Attribute.STRENGTH, 100);
        d.setAttribute(Attribute.KI_POWER, 100);
        d.setPath(FightingPath.FIGHTER);
        d.recomputeIfStale();
        double fMelee = d.getDerived().meleeDamage(), fKi = d.getDerived().maxKi(), fStamina = d.getDerived().maxStamina();
        d.setPath(FightingPath.SPIRITUALIST);
        d.recomputeIfStale();
        helper.assertTrue(fMelee > d.getDerived().meleeDamage(), "fighters hit harder");
        helper.assertTrue(fStamina > d.getDerived().maxStamina(), "and last longer");
        helper.assertTrue(d.getDerived().maxKi() > fKi, "spiritualists have more ki");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theMindAgesIntoWisdom(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(d, Race.ANDROID);
        double body = d.getPhysicalAge(), mind = d.getMentalAge();
        for (int i = 0; i < 50; i++) LifeSim.tick(p, d);
        helper.assertTrue(d.getPhysicalAge() == body && d.getMentalAge() > mind, "an android's mind still grows");
        d.setMentalAge(20);
        helper.assertTrue(LifeSim.wisdomMultiplier(d) == 1.0, "no wisdom yet at 20");
        d.setMentalAge(30);
        helper.assertTrue(Math.abs(LifeSim.wisdomMultiplier(d) - 1.1) < 1e-9, "+10% TP at 30");
        d.setMentalAge(500);
        helper.assertTrue(Math.abs(LifeSim.wisdomMultiplier(d) - 1.4) < 1e-9, "capped at +40%");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = SPAWN_PROTECTION + 40)
    public static void bladesCutTailsThatGrowBack(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(d, Race.SAIYAN);
        d.setTail(true);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        helper.runAfterDelay(SPAWN_PROTECTION, () -> {
            double chance = DBZConfig.SERVER.tailCutChance.get();
            DBZConfig.SERVER.tailCutChance.set(1.0);
            try {
                p.hurt(p.damageSources().mobAttack(zombie), 2f);
            } finally {
                DBZConfig.SERVER.tailCutChance.set(chance);
            }
            helper.assertTrue(!d.hasTail(), "the sword took the tail");
            TailRules.tick(p, d);
            helper.assertTrue(!d.hasTail(), "not back yet");
            d.setTailCutAt(helper.getLevel().getGameTime() - DBZConfig.SERVER.tailRegrowTicks.get());
            TailRules.tick(p, d);
            helper.assertTrue(d.hasTail(), "grown back after a few days");
            TestPlayers.remove(helper, p);
            helper.succeed();
        });
    }
}
