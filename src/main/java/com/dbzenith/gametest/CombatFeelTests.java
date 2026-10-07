package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.network.ImpactPacket;
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

/** CX-19 phase 5: hitstop on both fighters, criticals, the fighter states clients are told. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatFeelTests {
    private static final String EMPTY = "empty";
    private static final Moves.Input JAB = new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true);
    private static final Moves.Input SMASH = new Moves.Input(Move.Button.HEAVY, Move.Dir.NEUTRAL, false, false, true);

    private CombatFeelTests() {}

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

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aHeavyFreezesBothThenThrows(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1];
        helper.assertTrue(CombatEngine.press(a, SMASH), "the smash comes");
        boolean[] froze = {false};
        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            Fighter g = CombatEngine.peek(b);
            if (!froze[0]) {
                helper.assertTrue(g != null && g.frozen(now), "waiting for the blow to land");
                helper.assertTrue(CombatEngine.peek(a).frozen(now), "the attacker hangs in the blow too");
                helper.assertTrue(b.getDeltaMovement().horizontalDistance() < 0.01, "no knockback yet: " + b.getDeltaMovement());
                froze[0] = true;
                throw new net.minecraft.gametest.framework.GameTestAssertException("frozen; now waiting for the throw");
            }
            helper.assertTrue(!g.frozen(now), "the freeze ends");
            helper.assertTrue(b.getDeltaMovement().horizontalDistance() > 0.1, "then the throw: " + b.getDeltaMovement());
            TestPlayers.remove(helper, a);
            TestPlayers.remove(helper, b);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aBlowFromBehindIsCritical(GameTestHelper helper) {
        Pig front = pig(helper, new BlockPos(3, 2, 1)), back = pig(helper, new BlockPos(3, 2, 7));
        ServerPlayer a1 = attacker(helper, new BlockPos(1, 2, 1), front), a2 = attacker(helper, new BlockPos(1, 2, 7), back);
        face(front, a1.position());
        face(back, back.position().add(back.position().subtract(a2.position())));     // turned away
        float hf = front.getHealth(), hb = back.getHealth();
        helper.assertTrue(CombatEngine.press(a1, JAB) && CombatEngine.press(a2, JAB), "two jabs");
        helper.runAfterDelay(10, () -> {
            float lf = hf - front.getHealth(), lb = hb - back.getHealth();
            helper.assertTrue(lf > 0 && lb > 0, "both land: " + lf + " / " + lb);
            helper.assertTrue(lb > lf * 1.1f, "from behind it is a critical: " + lf + " / " + lb);
            front.discard();
            back.discard();
            TestPlayers.remove(helper, a1);
            TestPlayers.remove(helper, a2);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void clientsAreToldTheStance(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper);
        ServerPlayer a = p[0], b = p[1];
        helper.assertTrue(CombatEngine.press(a, JAB), "a jab");
        helper.succeedWhen(() -> {
            Fighter g = CombatEngine.peek(b);
            helper.assertTrue(g != null && (g.sentState() == Fighter.State.STUNNED || g.sentState() == Fighter.State.LAUNCHED),
                    "the victim shows as stunned (or launched: test players never land)");
            TestPlayers.remove(helper, a);
            TestPlayers.remove(helper, b);
        });
    }

    @GameTest(template = EMPTY)
    public static void hitstopByKind(GameTestHelper helper) {
        helper.assertTrue(ImpactPacket.hitstopFor(ImpactPacket.PUNCH, 0) < ImpactPacket.hitstopFor(ImpactPacket.HEAVY, 0), "heavier, longer");
        helper.assertTrue(ImpactPacket.hitstopFor(ImpactPacket.PUNCH, ImpactPacket.COUNTER) > ImpactPacket.hitstopFor(ImpactPacket.PUNCH, 0), "counters hang longer");
        helper.assertTrue(ImpactPacket.hitstopFor(ImpactPacket.KI_HIT, 0) == 0, "no freeze for ki");
        helper.succeed();
    }

    private static Pig pig(GameTestHelper helper, BlockPos at) {
        Pig pig = helper.spawn(EntityType.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        return pig;
    }

    private static ServerPlayer attacker(GameTestHelper helper, BlockPos at, Pig foe) {
        ServerPlayer a = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        Vec3 p = Vec3.atBottomCenterOf(helper.absolutePos(at));
        a.teleportTo(p.x, p.y, p.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getBoundingBox().getCenter());
        return a;
    }

    private static void face(Pig pig, Vec3 toward) {
        Vec3 d = toward.subtract(pig.position());
        float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
        pig.setYRot(yaw);
        pig.setYHeadRot(yaw);
        pig.yBodyRot = yaw;
        pig.setXRot(0);
    }
}
