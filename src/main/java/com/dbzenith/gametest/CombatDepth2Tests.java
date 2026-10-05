package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.GuardRules;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.skill.CustomTechniques;
import com.dbzenith.skill.CustomTechniques.Kind;
import com.dbzenith.skill.CustomTechniques.Mod;
import com.dbzenith.skill.CustomTechniques.Spec;
import com.dbzenith.skill.KiBeamEntity;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** V2-E: guard meter, parry and deflect, beam struggles, the Ki Creator. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatDepth2Tests {
    private static final String EMPTY = "empty";

    private CombatDepth2Tests() {}

    @GameTest(template = EMPTY)
    public static void guardMeterBreaksAndRefills(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(GuardRules.raise(d, now - 50), "guard goes up");
        double max = d.getDerived().maxBody();
        GuardRules.Outcome o = GuardRules.onHit(p, d, null, max * 0.05, true, false, now);
        helper.assertTrue(o == GuardRules.Outcome.BLOCK && d.getGuardMeter() < 100, "a late block costs meter: " + d.getGuardMeter());
        o = GuardRules.onHit(p, d, null, max * 0.6, true, false, now);
        helper.assertTrue(o == GuardRules.Outcome.BREAK && !d.isGuarding(), "blocking too much breaks the guard");
        helper.assertTrue(ModEffects.isStunned(p), "a guard break stuns");
        helper.assertTrue(!GuardRules.raise(d, now + 1), "...and the guard can't go straight back up");
        d.setGuardMeter(0);
        for (int i = 0; i < 200; i++) GuardRules.tick(d, now + 100 + i);
        helper.assertTrue(d.getGuardMeter() >= 100, "the meter refills when you stop guarding");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void justInTimeGuardParries(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        Zombie foe = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        long now = helper.getLevel().getGameTime();
        GuardRules.raise(d, now);
        GuardRules.Outcome o = GuardRules.onHit(p, d, foe, 10, true, false, now + 2);
        helper.assertTrue(o == GuardRules.Outcome.PARRY, "a guard raised just in time parries");
        helper.assertTrue(ModEffects.isStunned(foe), "the attacker staggers");
        o = GuardRules.onHit(p, d, foe, 10, true, false, now + 3);
        helper.assertTrue(o == GuardRules.Outcome.BLOCK, "one parry per raise");
        GuardRules.lower(d);
        GuardRules.raise(d, now + 10);
        helper.assertTrue(GuardRules.onHit(p, d, foe, 10, false, true, now + 11) == GuardRules.Outcome.BLOCK, "ki is never parried, only deflected");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void timedGuardSendsKiBack(GameTestHelper helper) {
        ServerPlayer defender = TestPlayers.create(helper);
        defender.setGameMode(GameType.SURVIVAL);
        defender.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        Zombie thrower = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        PlayerData d = ModCapabilities.getOrThrow(defender);
        GuardRules.raise(d, helper.getLevel().getGameTime());
        KiBlastEntity blast = KiBlastEntity.create(helper.getLevel(), thrower, Techniques.KI_BLAST, 5);
        Vec3 start = helper.absoluteVec(new Vec3(1.5, 3, 3));
        blast.moveTo(start.x, start.y, start.z);
        blast.setDeltaMovement(0, 0, 1.2);
        helper.getLevel().addFreshEntity(blast);
        double body = d.getBody();
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(blast.getOwner() == defender, "the blast now belongs to the defender");
            helper.assertTrue(d.getBody() >= body, "and did them no harm");
            TestPlayers.remove(helper, defender);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void strongerBeamWinsTheStruggle(GameTestHelper helper) {
        Zombie strong = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        Zombie weak = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 12));
        strong.setNoAi(true);
        weak.setNoAi(true);
        strong.setYRot(0);
        strong.setYHeadRot(0);
        weak.setYRot(180);
        weak.setYHeadRot(180);
        weak.setHealth(weak.getMaxHealth());
        float before = weak.getHealth();
        TechniqueHandler.spawn(helper.getLevel(), strong, Techniques.WAVE_BEAM, 400);
        TechniqueHandler.spawn(helper.getLevel(), weak, Techniques.WAVE_BEAM, 40);
        helper.runAfterDelay(8, () -> {
            boolean locked = !helper.getLevel().getEntitiesOfClass(KiBeamEntity.class, strong.getBoundingBox().inflate(20)).isEmpty()
                    && com.dbzenith.skill.BeamStruggle.isStruggling(strong);
            helper.assertTrue(locked, "two beams meeting head-on lock into a struggle");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!com.dbzenith.skill.BeamStruggle.isStruggling(strong), "struggle still going");
            helper.assertTrue(!weak.isAlive() || weak.getHealth() < before, "the weaker caster is blasted by the winner");
        });
    }

    @GameTest(template = EMPTY)
    public static void kiCreatorDesignsStayInBalance(GameTestHelper helper) {
        PlayerData d = BalanceReport.reference(1000);
        List<Double> builtIn = new ArrayList<>();
        for (BalanceReport2.TechStats s : BalanceReport2.damaging(d)) if (s.cost() > 0) builtIn.add(s.perKi());
        double mid = BalanceReport2.median(builtIn);
        double punch = BalanceReport2.punchDps(d);
        int designs = 0;
        java.util.List<Spec> specs = new ArrayList<>();
        for (Kind k : Kind.values()) for (int p = 1; p <= 5; p++) for (int mods = 0; mods < 1 << Mod.values().length; mods++) {
            if (Integer.bitCount(mods) > (p == 3 ? CustomTechniques.MAX_MODS_LATE : CustomTechniques.MAX_MODS)) continue;
            specs.add(new Spec("t", k, p, mods, 0xFFFFFF, CustomTechniques.Method.FIRED,
                    k == Kind.NOVA ? CustomTechniques.Origin.BODY : CustomTechniques.Origin.HAND, Technique.KiType.PURE));
        }
        int[] sample = {0, 1 << Mod.RAPID.ordinal(), 1 << Mod.EFFICIENT.ordinal(), 1 << Mod.RAPID.ordinal() | 1 << Mod.EFFICIENT.ordinal(),
                1 << Mod.RAPID.ordinal() | 1 << Mod.EFFICIENT.ordinal() | 1 << Mod.FAST.ordinal(), 1 << Mod.CHAIN.ordinal() | 1 << Mod.STUN.ordinal()};
        for (Kind k : Kind.values()) for (CustomTechniques.Method m : CustomTechniques.Method.values()) for (CustomTechniques.Origin o : CustomTechniques.Origin.values())
            for (Technique.KiType ty : Technique.KiType.values()) for (int p : new int[]{1, 5}) for (int mods : sample) {
                if (!m.fits(k) || !o.fits(k)) continue;
                specs.add(new Spec("t", k, p, mods, 0xFFFFFF, m, o, ty));
            }
        for (Spec spec : specs) {
            Kind k = spec.kind();
            int p = spec.power(), mods = spec.mods();
            boolean fits = true;
            for (Mod m : spec.modSet()) fits &= m.fits(k);
            if (!fits) continue;
            Technique t = CustomTechniques.build(0, spec);
            BalanceReport2.TechStats s = BalanceReport2.tech(d, t);
            helper.assertTrue(s != null, k + " p" + p + " deals damage");
            helper.assertTrue(s.perKi() >= mid * 0.6 && s.perKi() <= mid * 1.6,
                    spec + ": damage per ki " + s.perKi() + " vs median " + mid);
            helper.assertTrue(s.dps() / punch <= 4.0, spec + ": spammed it beats punching " + s.dps() / punch + "x");
            designs++;
        }
        helper.assertTrue(designs > 100, "every design was checked: " + designs);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void kiCreatorRules(GameTestHelper helper) {
        PlayerData d = BalanceReport.reference(20);
        d.setTrainingPoints(100000);
        Spec ok = new Spec("  Burning§c  Attack!!  ", Kind.BEAM, 3, 1 << Mod.LARGE.ordinal(), 0xFF8000);
        helper.assertTrue(CustomTechniques.create(d, 0, ok) == null, "a valid design is created");
        helper.assertTrue(d.getCustomSpec(0).name().equals("Burningc Attack!!"), "formatting codes are stripped: " + d.getCustomSpec(0).name());
        helper.assertTrue(d.knows("custom_0") && d.deckView().contains("custom_0"), "it is learned and slotted");
        helper.assertTrue(Techniques.resolve(d, "custom_0").style() == Technique.Style.BEAM, "the id resolves to the design");
        helper.assertTrue(d.getTrainingPoints() == 100000 - CustomTechniques.tpCost(ok), "TP was spent");
        helper.assertTrue(CustomTechniques.create(d, 0, new Spec("x", Kind.BEAM, 3, 1 << Mod.HOMING.ordinal(), 0)) != null, "homing beams are refused");
        int three = 1 << Mod.FAST.ordinal() | 1 << Mod.LARGE.ordinal() | 1 << Mod.PIERCING.ordinal();
        helper.assertTrue(CustomTechniques.create(d, 1, new Spec("x", Kind.BLAST, 3, three, 0)) != null, "three modifiers are refused (before level 800)");
        helper.assertTrue(CustomTechniques.create(d, 1, new Spec("x", Kind.BLAST, 3, 0, 0, CustomTechniques.Method.FIRED, CustomTechniques.Origin.HAND, Technique.KiType.DIVINE)) != null, "divine ki needs god ki");
        helper.assertTrue(CustomTechniques.create(d, 1, new Spec("x", Kind.NOVA, 3, 0, 0, CustomTechniques.Method.PLACED, CustomTechniques.Origin.BODY, Technique.KiType.PURE)) != null, "a nova cannot be placed");
        Spec v2 = new Spec("Frost Rain", Kind.RAIN, 2, 1 << Mod.CHAIN.ordinal(), 0x90E0FF, CustomTechniques.Method.FIRED, CustomTechniques.Origin.HAND, Technique.KiType.FREEZING);
        helper.assertTrue(CustomTechniques.create(d, 1, v2) == null && Techniques.resolve(d, "custom_1").kiType() == Technique.KiType.FREEZING
                && Techniques.resolve(d, "custom_1").has(Technique.RAIN) && Techniques.resolve(d, "custom_1").has(Technique.CHAIN), "v2 designs build their traits");
        helper.assertTrue(CustomTechniques.create(d, 1, new Spec("x", Kind.BLAST, 9, 0, 0)) != null, "power above 5 is refused");
        helper.assertTrue(CustomTechniques.create(d, CustomTechniques.slots(d), ok) != null, "slots beyond your level are refused");
        PlayerData copy = new PlayerData();
        copy.load(d.save());
        helper.assertTrue(copy.customTechnique("custom_0") != null && copy.getCustomSpec(0).equals(d.getCustomSpec(0)), "designs persist");
        CustomTechniques.delete(d, 0);
        helper.assertTrue(d.getCustomSpec(0) == null && !d.knows("custom_0") && Techniques.resolve(d, "custom_0") == null, "deleting forgets it");
        helper.succeed();
    }
}
