package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.KiCombat;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.combat.engine.SpecialMeter;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
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

/** CX-19 phase 4: quick and charged ki blasts, the special meter, supers and ultimates. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiCombatTests {
    private static final String EMPTY = "empty";

    private KiCombatTests() {}

    private static ServerPlayer fighter(GameTestHelper helper, GameType mode) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(mode);
        ModCapabilities.getOrThrow(p).refill();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        p.teleportTo(at.x, at.y, at.z);
        return p;
    }

    private static List<KiBlastEntity> blasts(GameTestHelper helper, ServerPlayer owner) {
        return helper.getLevel().getEntitiesOfClass(KiBlastEntity.class, new AABB(owner.blockPosition()).inflate(40), b -> b.getOwner() == owner);
    }

    @GameTest(template = EMPTY)
    public static void quickAndChargedBlasts(GameTestHelper helper) {
        helper.assertTrue(KiCombat.loaded(), "ki blast data loaded");
        ServerPlayer p = fighter(helper, GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        double ki = d.getKi();
        helper.assertTrue(KiCombat.fireForTest(p, -1), "a quick blast");
        double quickCost = ki - d.getKi();
        helper.assertTrue(quickCost > 0, "costs ki");
        helper.assertTrue(!KiCombat.fireForTest(p, -1), "not again the same tick (a few ticks apart)");
        double before = d.getKi();
        helper.assertTrue(KiCombat.fireForTest(p, 1.0), "a charged blast");
        helper.assertTrue(before - d.getKi() > quickCost * 3, "a full charge costs far more");
        List<KiBlastEntity> out = blasts(helper, p);
        helper.assertTrue(out.size() == 2, "two blasts out: " + out.size());
        float small = Math.min(out.get(0).getSize(), out.get(1).getSize()), big = Math.max(out.get(0).getSize(), out.get(1).getSize());
        helper.assertTrue(big > small * 2, "the charged one is bigger: " + small + " / " + big);
        out.forEach(KiBlastEntity::discard);
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aChargedBlastStunsAndBuildsMeter(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 6));
        pig.setNoAi(true);
        ServerPlayer p = fighter(helper, GameType.CREATIVE);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setSpecial(0);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        float health = pig.getHealth();
        helper.assertTrue(KiCombat.fireForTest(p, 1.0), "fired");
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < health || !pig.isAlive(), "the pig is hit");
            if (pig.isAlive()) helper.assertTrue(pig.hasEffect(ModEffects.STUN.get()), "and stunned");
            helper.assertTrue(d.getSpecial() > 0, "the thrower's meter grows: " + d.getSpecial());
            pig.discard();
            TestPlayers.remove(helper, p);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void blowsFillTheMeter(GameTestHelper helper) {
        ServerPlayer a = fighter(helper, GameType.CREATIVE), b = fighter(helper, GameType.SURVIVAL);
        Vec3 at = a.position();
        b.teleportTo(at.x + 2, at.y, at.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, b.getEyePosition());
        PlayerData ad = ModCapabilities.getOrThrow(a), bd = ModCapabilities.getOrThrow(b);
        ad.setSpecial(0);
        bd.setSpecial(0);
        helper.assertTrue(CombatEngine.press(a, new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true)), "a jab");
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(ad.getSpecial() > 0, "landing it builds meter: " + ad.getSpecial());
            helper.assertTrue(bd.getSpecial() > 0, "taking it too: " + bd.getSpecial());
            helper.assertTrue(ad.getSpecial() > bd.getSpecial(), "more for landing it");
            TestPlayers.remove(helper, a);
            TestPlayers.remove(helper, b);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void supersAndUltimatesCostTheMeter(GameTestHelper helper) {
        helper.assertTrue(KiCombat.tier("ki_blast").meter() == 0, "a ki blast is basic");
        helper.assertTrue(KiCombat.tier("wave_beam").tier().equals("super") && KiCombat.tier("wave_beam").meter() == SpecialMeter.BAR, "the Kamehameha is a super");
        helper.assertTrue(KiCombat.tier("spirit_bomb").meter() == 3 * SpecialMeter.BAR && KiCombat.tier("spirit_bomb").cinematic(), "the Spirit Bomb is an ultimate");
        ServerPlayer p = fighter(helper, GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.learn("finger_beam");
        d.setDeck(List.of("finger_beam"));
        d.setSpecial(0);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.byId("finger_beam")) == TechniqueHandler.Result.NO_METER, "no bar, no Death Beam");
        d.setSpecial(150);
        p.setXRot(-90);                                                          // fire into the sky: a beam crosses other tests' ground
        TechniqueHandler.Result r = TechniqueHandler.use(p, Techniques.byId("finger_beam"));
        helper.assertTrue(r == TechniqueHandler.Result.FIRED, "with a bar it fires: " + r);
        helper.assertTrue(Math.abs(d.getSpecial() - 50) < 1e-6, "and takes the bar: " + d.getSpecial());
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
