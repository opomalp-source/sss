package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.KiCharge;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** CX-23: charging techniques by holding the key, and holding the key to transform. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChargeTests {
    private static final String EMPTY = "empty";

    private ChargeTests() {}

    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (Attribute a : Attribute.values()) d.setAttribute(a, 400);
        d.recomputeIfStale();
        d.refill();
        d.learn("ki_blast");
        d.setDeck(List.of("ki_blast"));
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        p.teleportTo(at.x, at.y, at.z);
        p.setXRot(-90);                                                          // fire into the sky, clear of other tests
        return p;
    }

    private static KiBlastEntity newest(ServerPlayer p) {
        List<KiBlastEntity> blasts = p.level().getEntitiesOfClass(KiBlastEntity.class, p.getBoundingBox().inflate(4), b -> b.getOwner() == p);
        KiBlastEntity best = null;
        for (KiBlastEntity b : blasts) if (best == null || b.tickCount < best.tickCount) best = b;
        return best;
    }

    @GameTest(template = EMPTY)
    public static void chargingMakesItBiggerAndStronger(GameTestHelper helper) {
        Technique t = Techniques.byId("ki_blast");
        helper.assertTrue(t.chargeTicks() > 0, "a ki blast can be charged");
        helper.assertTrue(TechniqueHandler.chargeDamage(t, 0) == 1 && Math.abs(TechniqueHandler.chargeDamage(t, 1) - t.chargePower()) < 1e-9, "from x1 to its full power");
        Technique beam = Techniques.byId("wave_beam");
        helper.assertTrue(beam.chargeTicks() >= 600 && beam.chargePower() >= 5, "a beam charges for up to 30 s, five times as strong");
        helper.assertTrue(TechniqueHandler.chargeScale(beam, 1) > 2f, "and over twice as wide");
        helper.assertTrue(Techniques.byId("supernova_orb").chargeTicks() >= 900, "the biggest charge for 45 s or more");

        ServerPlayer p = caster(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        KiCharge.press(p, "ki_blast");                                           // a tap: as before
        KiCharge.release(p);
        KiBlastEntity tap = newest(p);
        helper.assertTrue(tap != null && Math.abs(tap.getSize() - t.size()) < 1e-4, "a tap fires the plain blast");
        tap.discard();
        d.setCooldown("ki_blast", 0);

        KiCharge.press(p, "ki_blast");
        helper.assertTrue(KiCharge.isCharging(p), "held: it charges");
        double kiBefore = d.getKi();
        for (int i = 1; i <= 40; i++) KiCharge.tick(p, d, i);
        helper.assertTrue(d.getKi() < kiBefore, "charging drains ki");
        int max = KiCharge.maxTicks(t);
        for (int i = 41; i <= max + 20; i++) {
            if (i % 20 == 0) d.setKi(d.getDerived().maxKi());
            KiCharge.tick(p, d, i);
        }
        helper.assertTrue(KiCharge.fraction(p) >= 0.999, "it reaches a full charge: " + KiCharge.fraction(p));
        KiCharge.release(p);
        KiBlastEntity full = newest(p);
        helper.assertTrue(!KiCharge.isCharging(p) && full != null && full.getSize() > t.size() * 1.9f, "let go: a far bigger blast: " + (full == null ? "none" : full.getSize()));
        full.discard();
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aChargeGoesOffWhenTheKiRunsShort(GameTestHelper helper) {
        ServerPlayer p = caster(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        Technique t = Techniques.byId("ki_blast");
        KiCharge.press(p, "ki_blast");
        for (int i = 1; i <= 10; i++) KiCharge.tick(p, d, i);
        d.setKi(TechniqueHandler.baseCost(d, t) * 1.001);                       // only enough left to fire
        KiCharge.tick(p, d, 11);
        helper.assertTrue(!KiCharge.isCharging(p) && newest(p) != null, "out of ki to give: it goes off on its own");
        newest(p).discard();

        d.refill();
        d.setCooldown("ki_blast", 0);
        KiCharge.press(p, "ki_blast");
        p.addEffect(new MobEffectInstance(com.dbzenith.registry.ModEffects.STUN.get(), 20, 0));
        KiCharge.tick(p, d, 12);
        helper.assertTrue(!KiCharge.isCharging(p) && newest(p) == null, "stunned: the charge fizzles");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void transformationsAreHeld(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.SAIYAN);
        d.setPvp(false);
        for (Attribute a : Attribute.values()) d.setAttribute(a, 3000);
        d.recomputeIfStale();
        d.refill();
        helper.assertTrue(FormHandler.holdUp(p) && d.isTransforming() && d.getTransformTotal() >= 10, "held: the power-up starts");
        for (int i = 1; i <= 8; i++) FormHandler.tick(p, d, i);
        int reached = d.getTransformTicks();
        d.setTransformHeld(false);                                               // let go
        for (int i = 9; i <= 12; i++) FormHandler.tick(p, d, i);
        helper.assertTrue(d.getTransformTicks() < reached && d.isTransforming(), "let go: the bar falls back slowly: " + reached + " to " + d.getTransformTicks());
        double ki = d.getKi();
        helper.assertTrue(FormHandler.holdUp(p) && d.isTransformHeld(), "held again: it picks up where it was");
        for (int i = 13; i <= 13 + d.getTransformTotal() + 5 && d.isTransforming(); i++) FormHandler.tick(p, d, i);
        helper.assertTrue(d.getFormId().equals(Forms.SUPER_SAIYAN.id()), "held to the end: transformed (" + d.getFormId() + ")");
        helper.assertTrue(d.getKi() < ki, "paid for when it took hold");

        FormHandler.revertToBase(p, d);
        d.refill();
        FormHandler.holdUp(p);
        for (int i = 100; i <= 103; i++) FormHandler.tick(p, d, i);
        d.setTransformHeld(false);
        double kiLet = d.getKi();
        for (int i = 104; i <= 140 && d.isTransforming(); i++) FormHandler.tick(p, d, i);
        helper.assertTrue(!d.isTransforming() && d.getFormId().equals(Forms.BASE.id()), "let go long enough: it fades to nothing");
        helper.assertTrue(d.getKi() >= kiLet - 1e-6, "and costs nothing");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
