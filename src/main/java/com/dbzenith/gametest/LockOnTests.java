package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.Evasion;
import com.dbzenith.combat.engine.KiCombat;
import com.dbzenith.combat.engine.Targeting;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.skill.KiBlastEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** CX-19 phase 6: the server side of lock-on. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LockOnTests {
    private static final String EMPTY = "empty";

    private LockOnTests() {}

    private static Pig pig(GameTestHelper helper, BlockPos at) {
        Pig pig = helper.spawn(EntityType.PIG, at);
        pig.setNoAi(true);
        return pig;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.CREATIVE);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        p.teleportTo(at.x, at.y, at.z);
        return p;
    }

    @GameTest(template = EMPTY)
    public static void theServerChecksTheLock(GameTestHelper helper) {
        Pig pig = pig(helper, new BlockPos(1, 2, 8));
        ServerPlayer p = player(helper);
        helper.assertTrue(Targeting.set(p, pig.getId()) && Targeting.target(p) == pig, "locks onto the pig");
        helper.assertTrue(!Targeting.set(p, p.getId()) && Targeting.target(p) == null, "not onto yourself");
        Targeting.set(p, pig.getId());
        pig.discard();
        helper.assertTrue(Targeting.target(p) == null, "a lock on something gone lets go");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theSuperDashGoesForTheLockedFoe(GameTestHelper helper) {
        Pig pig = pig(helper, new BlockPos(1, 2, 12));
        ServerPlayer p = player(helper);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition().add(1, 0, 0));    // looking away, 90 degrees off
        Targeting.set(p, pig.getId());
        helper.assertTrue(Evasion.dashKey(p, ModCapabilities.getOrThrow(p), 1, 0), "Dash forward while locked is a super dash");
        Vec3 v = p.getDeltaMovement(), to = pig.position().subtract(p.position());
        helper.assertTrue(v.length() > 1.0 && v.normalize().dot(to.normalize()) > 0.9, "straight at the locked pig: " + v);
        pig.discard();
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void blastsCurveTowardTheLock(GameTestHelper helper) {
        Pig pig = pig(helper, new BlockPos(6, 14, 13));                                 // up and off to the side: the blast flies into the sky, clear of other tests
        ServerPlayer p = player(helper);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition().add(0, 1, 1));    // up ahead; the pig is off to the side
        Targeting.set(p, pig.getId());
        helper.assertTrue(KiCombat.fireForTest(p, -1), "a quick blast");
        List<KiBlastEntity> out = helper.getLevel().getEntitiesOfClass(KiBlastEntity.class, new AABB(p.blockPosition()).inflate(8), b -> b.getOwner() == p);
        helper.assertTrue(out.size() == 1, "one blast");
        KiBlastEntity blast = out.get(0);
        double before = blast.getDeltaMovement().x;                              // the pig is off to +x
        helper.runAfterDelay(4, () -> {
            double after = blast.getDeltaMovement().x;
            helper.assertTrue(after > before + 0.03, "it bends toward the pig: " + before + " -> " + after);
            blast.discard();
            pig.discard();
            TestPlayers.remove(helper, p);
            helper.succeed();
        });
    }
}
