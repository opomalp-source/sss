package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.GuardRules;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.KiCombat;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.DashHandler;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
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

/** CX-20 phase 2: PvP off is plain Minecraft (weak vanilla hits, no combat moves); PvP on is the combat system. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PvpModeTests {
    private static final String EMPTY = "empty";

    private PvpModeTests() {}

    @GameTest(template = EMPTY)
    public static void pvpOffIsPlainMinecraft(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.refill();
        d.setPvp(false);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        p.teleportTo(at.x, at.y, at.z);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());

        helper.assertTrue(!CombatEngine.press(p, new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true)), "no combos");
        p.attack(pig);
        float plain = 1000 - pig.getHealth();
        helper.assertTrue(plain > 0 && plain <= 2.5f, "a plain punch lands, weak (vanilla): " + plain);
        KiCombat.key(p, true);                                                  // the Ki Blast key, tapped
        KiCombat.key(p, false);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(com.dbzenith.skill.KiBlastEntity.class, p.getBoundingBox().inflate(6)).isEmpty(), "no ki blasts");
        helper.assertTrue(TechniqueHandler.use(p, Techniques.KI_BLAST) == TechniqueHandler.Result.INVALID, "no ki attacks");
        DashHandler.dash(p, 1, 0);
        helper.assertTrue(helper.getLevel().getGameTime() > d.getDashEvadeUntil(), "a dash is just a dash: no afterimage dodge");

        d.setPvp(true);                                                         // PvP on: the combat system
        pig.setHealth(1000);
        p.attack(pig);
        helper.assertTrue(pig.getHealth() == 1000, "a vanilla punch does nothing: blows are the engine's");
        helper.assertTrue(CombatEngine.press(p, new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true)), "combos are on");
        pig.discard();
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void turningPvpOffLowersTheGuard(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        GuardRules.raise(d, helper.getLevel().getGameTime());
        d.setGuarding(true);
        com.dbzenith.combat.PvpRules.set(p, false, false);
        helper.assertTrue(!d.isGuarding() && !d.isPvp(), "off: guard down");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
