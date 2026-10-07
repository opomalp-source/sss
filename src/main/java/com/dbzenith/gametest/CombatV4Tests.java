package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-19 phase 2: moves from data, the light chain, combo scaling, launches, no vanilla punch, NPCs on the engine. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatV4Tests {
    private static final String EMPTY = "empty";

    private CombatV4Tests() {}

    private static Moves.Input in(Move.Button b, Move.Dir push, boolean up, boolean down, boolean ground) {
        return new Moves.Input(b, push, up, down, ground);
    }

    /** A tough, slow pig to hit, and a fighter facing it. */
    private static Pig dummy(GameTestHelper helper, BlockPos at) {
        Pig p = helper.spawn(EntityType.PIG, at);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        p.setHealth(1000);
        p.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return p;
    }

    private static ServerPlayer fighter(GameTestHelper helper, Vec3 at, Pig target) {
        ServerPlayer a = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        a.teleportTo(at.x, at.y, at.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        return a;
    }

    @GameTest(template = EMPTY)
    public static void movesComeFromData(GameTestHelper helper) {
        helper.assertTrue(Moves.loaded() && Moves.get("light_1") != null, "the moves are loaded");
        helper.assertTrue(Moves.select(in(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true), "start").id.equals("light_1"), "an opener");
        helper.assertTrue(Moves.select(in(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true), "light_4").id.equals("light_5"), "the chain goes on");
        helper.assertTrue(Moves.select(in(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true), "light_5").id.equals("light_1"), "and starts again after its end");
        helper.assertTrue(Moves.select(in(Move.Button.HEAVY, Move.Dir.BACK, false, false, true), "light_2").id.equals("heavy_sweep"), "heavy back: the sweep");
        helper.assertTrue(Moves.select(in(Move.Button.HEAVY, Move.Dir.FORWARD, false, false, true), "start").id.equals("heavy_rush"), "heavy forward: the rush");
        helper.assertTrue(Moves.select(in(Move.Button.HEAVY, Move.Dir.NEUTRAL, true, false, true), "light_3").id.equals("heavy_uppercut"), "looking up: the uppercut");
        helper.assertTrue(Moves.select(in(Move.Button.HEAVY, Move.Dir.NEUTRAL, false, true, false), "start").id.equals("heavy_spike"), "looking down in the air: the spike");
        helper.assertTrue(Moves.select(in(Move.Button.HEAVY, Move.Dir.NEUTRAL, false, false, true), "start").id.equals("heavy_smash"), "otherwise the smash");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void aChainLandsAndScales(GameTestHelper helper) {
        Pig pig = dummy(helper, new BlockPos(3, 2, 1));
        ServerPlayer a = fighter(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))), pig);
        helper.assertTrue(CombatEngine.press(a, in(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true)), "the jab starts");
        helper.assertTrue(CombatEngine.peek(a).move().id.equals("light_1"), "light_1");
        helper.runAfterDelay(6, () -> {
            float after1 = pig.getHealth();
            helper.assertTrue(after1 < 1000, "the jab lands: " + after1);
            a.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getEyePosition());
            helper.assertTrue(CombatEngine.press(a, in(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true)), "the next press");
            helper.assertTrue(CombatEngine.peek(a).move().id.equals("light_2"), "continues the chain: " + CombatEngine.describe(a));
            helper.runAfterDelay(6, () -> {
                Fighter victim = CombatEngine.peek(pig);
                helper.assertTrue(victim != null && victim.comboHits() == 2, "two hits of one combo: " + (victim == null ? -1 : victim.comboHits()));
                pig.discard();
                TestPlayers.remove(helper, a);
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void theUppercutLaunches(GameTestHelper helper) {
        Pig pig = dummy(helper, new BlockPos(3, 2, 1));
        ServerPlayer a = fighter(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))), pig);
        double y0 = pig.getY();
        helper.assertTrue(CombatEngine.press(a, in(Move.Button.HEAVY, Move.Dir.NEUTRAL, true, false, true)), "the uppercut starts");
        helper.assertTrue(CombatEngine.peek(a).move().id.equals("heavy_uppercut"), "heavy_uppercut");
        helper.runAfterDelay(14, () -> {                                     // the hitstop holds the launch a few ticks (phase 5)
            helper.assertTrue(pig.getY() > y0 + 1.0, "launched: " + (pig.getY() - y0));
            pig.discard();
            TestPlayers.remove(helper, a);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void noVanillaPunchAndNpcsUseTheEngine(GameTestHelper helper) {
        Pig pig = dummy(helper, new BlockPos(3, 2, 1));
        ServerPlayer a = fighter(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))), pig);
        a.setGameMode(GameType.SURVIVAL);
        a.attack(pig);
        helper.assertTrue(pig.getHealth() == 1000, "a bare-handed vanilla punch does nothing");
        var soldier = com.dbzenith.npc.ModNpcs.KI_SOLDIER.get().create(helper.getLevel());
        soldier.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
        helper.getLevel().addFreshEntity(soldier);
        soldier.doHurtTarget(pig);
        helper.assertTrue(CombatEngine.peek(soldier) != null && CombatEngine.peek(soldier).move() != null, "an NPC's swing is a move: " + CombatEngine.describe(soldier));
        soldier.discard();
        pig.discard();
        TestPlayers.remove(helper, a);
        helper.succeed();
    }
}
