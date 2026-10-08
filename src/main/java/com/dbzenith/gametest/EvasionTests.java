package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.GuardRules;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Evasion;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
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

/** CX-19 phase 3: vanish and counter, perfect guard and counter, guard only in front, the super dash. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EvasionTests {
    private static final String EMPTY = "empty";

    private EvasionTests() {}

    private static final Moves.Input JAB = new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true);

    /** An attacker (creative) and a defender (survival, full pools) facing each other two blocks apart. */
    private static ServerPlayer[] pair(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        b.setGameMode(GameType.SURVIVAL);
        ModCapabilities.getOrThrow(b).refill();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        a.teleportTo(at.x, at.y, at.z);
        b.teleportTo(at.x + 2, at.y, at.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, b.getEyePosition());
        b.lookAt(EntityAnchorArgument.Anchor.EYES, a.getEyePosition());
        return new ServerPlayer[]{a, b};
    }

    private static void done(GameTestHelper helper, ServerPlayer... ps) {
        for (ServerPlayer p : ps) TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void aVanishDodgesAndCounters(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1];
        PlayerData bd = ModCapabilities.getOrThrow(b);
        helper.assertTrue(!Evasion.dashKey(b, bd, 0, 0), "an ordinary dash (it opens the vanish window)");
        helper.assertTrue(CombatEngine.press(a, JAB), "the jab comes");
        float health = b.getHealth();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(b.getHealth() == health, "it misses");
            Vec3 toB = b.position().subtract(a.position());
            helper.assertTrue(toB.dot(a.getLookAngle()) < 0 && toB.length() < 2.5, "the defender is behind the attacker: " + toB);
            helper.assertTrue(CombatEngine.press(b, JAB) && CombatEngine.peek(b).move().id.equals("counter_strike"), "and counters: " + CombatEngine.describe(b));
            done(helper, a, b);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void aPerfectGuardCounters(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1];
        PlayerData bd = ModCapabilities.getOrThrow(b);
        GuardRules.raise(bd, helper.getLevel().getGameTime());
        CombatEngine.press(a, JAB);
        helper.runAfterDelay(4, () -> {
            // the parry shows in the counter it opens (health can be touched by other tests' stray explosions)
            helper.assertTrue(com.dbzenith.combat.engine.Evasion.counterReady(b, helper.getLevel().getGameTime()), "parried: the counter is open");
            GuardRules.lower(bd);
            helper.assertTrue(CombatEngine.press(b, JAB) && CombatEngine.peek(b).move().id.equals("counter_strike"), "a counter: " + CombatEngine.describe(b));
            done(helper, a, b);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void guardOnlyCoversTheFront(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1];
        PlayerData bd = ModCapabilities.getOrThrow(b);
        helper.assertTrue(Evasion.guardCovers(b, a), "facing the attacker: covered");
        b.lookAt(EntityAnchorArgument.Anchor.EYES, b.getEyePosition().add(b.position().subtract(a.position())));   // turn away
        helper.assertTrue(!Evasion.guardCovers(b, a), "back turned: not covered");
        done(helper, a, b);
    }

    @GameTest(template = EMPTY)
    public static void theSuperDashGoesForTheFoe(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 14));
        pig.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        ServerPlayer a = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        a.teleportTo(at.x, at.y, at.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        PlayerData ad = ModCapabilities.getOrThrow(a);
        helper.assertTrue(Evasion.dashKey(a, ad, 1, 0), "Dash forward at a foe is a super dash");
        Vec3 v = a.getDeltaMovement(), to = pig.position().subtract(a.position());
        helper.assertTrue(v.length() > 1.0 && v.normalize().dot(to.normalize()) > 0.95, "rushing straight at it: " + v);
        pig.discard();
        done(helper, a);
    }
}
