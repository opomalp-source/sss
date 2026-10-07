package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.FoeFocus;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.combat.engine.Targeting;
import com.dbzenith.data.ModCapabilities;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-19 phase 8: who the enemy panel shows. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatHudTests {
    private static final String EMPTY = "empty";

    private CombatHudTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void theFoeIsWhoYouFight(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        b.setGameMode(GameType.SURVIVAL);
        ModCapabilities.getOrThrow(b).refill();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        a.teleportTo(at.x, at.y, at.z);
        b.teleportTo(at.x + 2, at.y, at.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, b.getEyePosition());
        helper.assertTrue(FoeFocus.foe(a) == null, "no foe before a fight");
        helper.assertTrue(CombatEngine.press(a, new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true)), "a jab");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(FoeFocus.foe(a) == b, "the attacker faces the one they hit");
            helper.assertTrue(FoeFocus.foe(b) == a, "and the victim the one who hit them");
            Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 8));
            Targeting.set(a, pig.getId());
            helper.assertTrue(FoeFocus.foe(a) == pig, "a lock comes first");
            pig.discard();
            TestPlayers.remove(helper, a);
            TestPlayers.remove(helper, b);
            helper.succeed();
        });
    }
}
