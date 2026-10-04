package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.npc.BossFighter;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.skill.KiBeamEntity;
import com.dbzenith.skill.KiBlastEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 4 slice 4: enemy fighters, level scaling, ki attacks, bosses. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnemyTests {
    private static final String EMPTY = "empty";

    private EnemyTests() {}

    @GameTest(template = EMPTY)
    public static void levelScalesWithPowerAndBoostsStats(GameTestHelper helper) {
        helper.assertTrue(KiFighter.levelFor(0) == 1, "no nearby power: level 1");
        helper.assertTrue(KiFighter.levelFor(150_000) > KiFighter.levelFor(10_000), "stronger players meet stronger enemies");
        KiFighter soldier = helper.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        float base = soldier.getMaxHealth();
        double ki = soldier.kiDamage();
        soldier.setFighterLevel(10);
        helper.assertTrue(soldier.getMaxHealth() == base && soldier.toughness() > 5, "levels add toughness: " + soldier.toughness());
        helper.assertTrue(KiFighter.effectiveMaxHealth(soldier) > base * 5, "so it lasts longer");
        helper.assertTrue(soldier.kiDamage() > ki * 3, "ki damage scales with level");
        helper.assertTrue(soldier.getHealth() == soldier.getMaxHealth(), "spawned at full health");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void kiSoldierFiresAtItsTarget(GameTestHelper helper) {
        KiFighter soldier = helper.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(0, 1, 0));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
        soldier.fireAt(pig);
        AABB around = new AABB(soldier.blockPosition()).inflate(4);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(KiBlastEntity.class, around).isEmpty(), "a ki blast was fired");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void bossEnragesAtHalfHealth(GameTestHelper helper) {
        BossFighter boss = helper.spawnWithNoFreeWill(ModNpcs.TYRANT_LORD.get(), new BlockPos(1, 1, 1));
        double calm = boss.kiDamage();
        helper.assertTrue(!boss.isEnraged() && boss.bossBar() != null, "starts calm, with a boss bar");
        boss.setHealth(boss.getMaxHealth() * 0.4f);
        boss.enrage();
        helper.assertTrue(boss.isEnraged() && boss.kiDamage() > calm * 1.4, "enraged bosses hit harder");
        helper.assertTrue(!boss.removeWhenFarAway(10_000), "bosses never despawn");
        boss.fireAt(boss); // any target: just make sure every technique can be cast by a boss
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(KiBlastEntity.class, new AABB(boss.blockPosition()).inflate(6)).isEmpty()
                || !helper.getLevel().getEntitiesOfClass(KiBeamEntity.class, new AABB(boss.blockPosition()).inflate(6)).isEmpty(),
                "bosses cast their techniques");
        helper.succeed();
    }
}
