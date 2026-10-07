package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.PvpRules;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.KiCombat;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.duel.Duel;
import com.dbzenith.duel.DuelLadder;
import com.dbzenith.duel.Duels;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.npc.TrainingDummy;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-19 phase 9: duels (knockout, ring out, arena rules, the ladder) and the training dummy. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DuelTests {
    private static final String EMPTY = "empty";
    private static final Moves.Input JAB = new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true);

    private DuelTests() {}

    private static ServerPlayer[] pair(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            p.setGameMode(GameType.SURVIVAL);
            ModCapabilities.getOrThrow(p).refill();
        }
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        a.teleportTo(at.x, at.y, at.z);
        b.teleportTo(at.x + 4, at.y, at.z);
        return new ServerPlayer[]{a, b};
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aDuelEndsInAKnockout(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1];
        Duel d = Duels.start(a, b, 1, Duel.Rules.FULL);
        helper.assertTrue(Duels.inDuel(a) && Duels.opponents(a, b), "they are duelling");
        float before = b.getHealth();
        b.hurt(b.damageSources().playerAttack(a), 4f);
        helper.assertTrue(b.getHealth() == before, "nothing hurts before the bell");
        Duels.skipCountdown(d);
        b.kill();
        helper.assertTrue(b.isAlive() && !b.isDeadOrDying(), "a knockout, not a death");
        helper.assertTrue(!Duels.inDuel(a) && !Duels.inDuel(b), "best of one: over");
        DuelLadder ladder = DuelLadder.get(helper.getLevel().getServer());
        helper.assertTrue(ladder.peek(a.getUUID()).wins == 1 && ladder.peek(b.getUUID()).losses == 1, "the ladder has it");
        helper.assertTrue(ladder.peek(a.getUUID()).rating > DuelLadder.START, "the winner's rating goes up");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void leavingTheArenaLosesTheRound(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1];
        Duel d = Duels.start(a, b, 1, Duel.Rules.FULL);
        Duels.skipCountdown(d);
        Vec3 c = d.center();
        b.teleportTo(c.x, c.y + 60, c.z + 40);                                  // well out (and up, clear of other tests)
        helper.runAfterDelay(5 * 20 + 10, () -> {
            helper.assertTrue(!Duels.inDuel(a), "over");
            helper.assertTrue(d.wins(0) == 1, "a ring out for the one who stayed: " + d.wins(0) + "-" + d.wins(1));
            TestPlayers.remove(helper, a);
            TestPlayers.remove(helper, b);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void arenaRules(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1], c = TestPlayers.create(helper);
        Duel d = Duels.start(a, b, 3, Duel.Rules.MELEE);
        Duels.skipCountdown(d);
        helper.assertTrue(PvpRules.judge(a, b) == PvpRules.Verdict.ALLOW, "duelists fight each other");
        helper.assertTrue(PvpRules.judge(c, a) == PvpRules.Verdict.BLOCK_DUEL && PvpRules.judge(a, c) == PvpRules.Verdict.BLOCK_DUEL, "nobody else is in it");
        helper.assertTrue(!Duels.kiAllowed(a) && !KiCombat.fireForTest(a, -1), "melee rules: no ki");
        Duels.forfeit(b);
        helper.assertTrue(!Duels.inDuel(a), "a forfeit ends it");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        TestPlayers.remove(helper, c);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void theTrainingDummy(GameTestHelper helper) {
        TrainingDummy dummy = ModNpcs.TRAINING_DUMMY.get().create(helper.getLevel());
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 1)));
        dummy.moveTo(at.x, at.y, at.z, 90, 0);
        helper.getLevel().addFreshEntity(dummy);
        ServerPlayer a = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        Vec3 me = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        a.teleportTo(me.x, me.y, me.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
        dummy.lookAt(EntityAnchorArgument.Anchor.EYES, a.getEyePosition());
        dummy.setMode(TrainingDummy.Mode.STAND);
        float full = dummy.getMaxHealth();
        helper.assertTrue(CombatEngine.press(a, JAB), "a jab");
        helper.runAfterDelay(8, () -> {
            float open = full - dummy.getHealth();
            helper.assertTrue(open > 0 && dummy.string()[0] == 1, "standing, it takes it and counts it: " + open + " hits " + dummy.string()[0] + " dmg " + dummy.string()[1] + " hp " + dummy.getHealth() + "/" + full);
            dummy.setHealth(full);
            dummy.setMode(TrainingDummy.Mode.GUARD);
            dummy.lookAt(EntityAnchorArgument.Anchor.EYES, a.getEyePosition());
            CombatEngine.press(a, new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true));
            helper.runAfterDelay(20, () -> {
                float blocked = full - dummy.getHealth();
                helper.assertTrue(blocked < open * 0.5f, "guarding, only chip damage: " + blocked + " vs " + open);
                dummy.hurt(dummy.damageSources().genericKill(), Float.MAX_VALUE);
                helper.assertTrue(dummy.isAlive() && dummy.getHealth() == dummy.getMaxHealth(), "it never dies");
                dummy.discard();
                TestPlayers.remove(helper, a);
                helper.succeed();
            });
        });
    }
}
