package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.DashHandler;
import com.dbzenith.ki.FlightHandler;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.skill.GrabThrow;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 5 slice 1: status effects, ki transfer, grab and throw, ball-drop, aerial combat. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatDepthTests {
    private static final String EMPTY = "empty";

    private CombatDepthTests() {}

    /** A survival player standing in the test area at (x, 1, z), facing +X. */
    private static ServerPlayer fighter(GameTestHelper helper, double x, double z) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(x, 1, z));
        p.teleportTo(pos.x, pos.y, pos.z);
        p.setYRot(-90f);
        p.setXRot(0f);
        p.setOnGround(true);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAttribute(Attribute.KI_POWER, 100);
        d.setAttribute(Attribute.STRENGTH, 100);
        d.setAttribute(Attribute.SPIRIT, 100);
        d.recomputeIfStale();
        d.setKi(d.getDerived().maxKi());
        d.setStamina(d.getDerived().maxStamina());
        return p;
    }

    @GameTest(template = EMPTY)
    public static void stunStopsEverything(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, 0.5, 0.5);
        p.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 100, 0));
        helper.assertTrue(TechniqueHandler.use(p, Techniques.KI_BLAST, true) == TechniqueHandler.Result.STUNNED, "no techniques while stunned");
        helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1e-6, "cannot move: " + p.getAttributeValue(Attributes.MOVEMENT_SPEED));
        ModCapabilities.getOrThrow(p).setKi(0);                         // a stunned dash is a Burst (CX-19), which needs ki
        DashHandler.dash(p, 1, 0);
        helper.assertTrue(ModEffects.isStunned(p) && p.getDeltaMovement().horizontalDistanceSqr() < 1e-6, "cannot dash");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        float start = zombie.getHealth();
        p.attack(zombie);
        helper.assertTrue(zombie.getHealth() == start, "stunned hits do nothing");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void kiSealBlocksKiUse(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, 0.5, 0.5);
        PlayerData d = ModCapabilities.getOrThrow(p);
        p.addEffect(new MobEffectInstance(ModEffects.KI_SEAL.get(), 100, 0));
        helper.assertTrue(TechniqueHandler.use(p, Techniques.KI_BLAST, true) == TechniqueHandler.Result.SEALED, "no techniques while sealed");
        helper.assertTrue(!FlightHandler.toggle(p), "no flight while sealed");
        d.setKi(1);
        com.dbzenith.ki.KiTicker.tick(p, d);
        helper.assertTrue(d.getKi() == 1, "no ki regen while sealed: " + d.getKi());
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sealOrbSealsWhatItHits(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Vec3 target = zombie.position().add(0, 1, 0);
        KiBlastEntity orb = KiBlastEntity.create(helper.getLevel(), null, Techniques.SEAL_ORB, 1);
        orb.moveTo(target.x - 1.5, target.y, target.z, 0, 0);
        orb.setDeltaMovement(0.5, 0, 0);
        helper.getLevel().addFreshEntity(orb);
        helper.succeedWhen(() -> helper.assertTrue(ModEffects.isKiSealed(zombie), "the zombie's ki is sealed"));
    }

    @GameTest(template = EMPTY)
    public static void kiTransferGivesKi(GameTestHelper helper) {
        ServerPlayer giver = fighter(helper, 0.5, 1.5);
        ServerPlayer taker = fighter(helper, 2.5, 1.5);
        PlayerData g = ModCapabilities.getOrThrow(giver);
        PlayerData t = ModCapabilities.getOrThrow(taker);
        t.setKi(0);
        double before = g.getKi();
        helper.assertTrue(TechniqueHandler.use(giver, Techniques.KI_TRANSFER, true) == TechniqueHandler.Result.FIRED, "transfer fires");
        helper.assertTrue(t.getKi() > 0, "the ally got ki");
        helper.assertTrue(Math.abs((before - g.getKi()) - t.getKi()) < 1e-6, "ki moves, it is not created");
        TestPlayers.remove(helper, giver);
        TestPlayers.remove(helper, taker);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void grabThenThrow(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, 0.5, 1.5);
        // a mob with AI: no-AI mobs ignore velocity, so they could never be thrown
        net.minecraft.world.entity.animal.Cow mob = helper.spawn(EntityType.COW, new BlockPos(2, 1, 1));
        mob.setPos(mob.getX(), mob.getY(), p.getZ());
        float start = mob.getHealth();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(TechniqueHandler.use(p, Techniques.GRAB_THROW, true) == TechniqueHandler.Result.FIRED, "grab");
            helper.assertTrue(GrabThrow.isHolding(p) && ModEffects.isStunned(mob), "holding a stunned mob");
            ModCapabilities.getOrThrow(p).setCooldown(Techniques.GRAB_THROW.id(), 0);
            p.setXRot(45f); // throw it into the floor
            helper.assertTrue(TechniqueHandler.use(p, Techniques.GRAB_THROW, true) == TechniqueHandler.Result.FIRED, "throw");
            helper.assertTrue(!GrabThrow.isHolding(p), "let go");
            helper.assertTrue(mob.getDeltaMovement().length() > 1, "flying: " + mob.getDeltaMovement());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(mob.getHealth() < start || !mob.isAlive(), "impact damage");
            TestPlayers.remove(helper, p);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void gatheringSphereFormsOverheadThenFlies(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, 1.5, 1.5);
        p.teleportTo(p.getX(), p.getY() + 30, p.getZ()); // open air
        PlayerData d = ModCapabilities.getOrThrow(p);
        p.setGameMode(GameType.CREATIVE); // no ki cost
        helper.assertTrue(TechniqueHandler.use(p, Techniques.GATHERING_SPHERE, true) == TechniqueHandler.Result.FIRED, "fires");
        KiBlastEntity[] sphere = new KiBlastEntity[1];
        helper.runAfterDelay(5, () -> {
            sphere[0] = helper.getLevel().getEntitiesOfClass(KiBlastEntity.class, p.getBoundingBox().inflate(8)).stream().findFirst().orElse(null);
            helper.assertTrue(sphere[0] != null && sphere[0].isHovering(), "hovering while it forms");
            helper.assertTrue(sphere[0].getY() > p.getEyeY() + 1, "above the head");
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!sphere[0].isHovering() && sphere[0].getDeltaMovement().length() > 0.5, "then hurled");
            sphere[0].discard();
            TestPlayers.remove(helper, p);
            helper.succeed();
        });
    }
}
