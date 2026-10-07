package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.BodyHealth;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.FlightHandler;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.AttributeTraining;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 1 checks: damage math, body pool, regen/charge, flight, TP spending, techniques, melee. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatTests {
    private static final String EMPTY = "empty";
    /** ServerPlayer ignores damage for its first 60 ticks after joining. */
    private static final int SPAWN_PROTECTION = 62;

    private CombatTests() {}

    private static PlayerData fresh() {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        return d;
    }

    @GameTest(template = EMPTY)
    public static void defenseHasFloorAndGuardReduces(GameTestHelper helper) {
        PlayerData def = fresh();
        def.setAttribute(Attribute.DEXTERITY, 100_000); // huge defense
        def.recomputeIfStale();
        RandomSource never = RandomSource.create(1);
        double raw = 100;
        double taken = DamageCalculator.againstPlayer(raw, def, false, never);
        helper.assertTrue(Math.abs(taken - raw * DBZConfig.SERVER.minDamageFraction.get()) < 1e-6,
                "defense must not reduce below the min fraction, got " + taken);

        PlayerData guard = fresh();
        double open = DamageCalculator.againstPlayer(raw, guard, false, never);
        guard.setGuarding(true);
        double guarded = DamageCalculator.againstPlayer(raw, guard, false, never);
        helper.assertTrue(guarded < open, "guard should reduce damage: " + guarded + " vs " + open);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void releaseScalesKiAndCost(GameTestHelper helper) {
        PlayerData d = fresh();
        d.setReleasePercent(50);
        double half = DamageCalculator.kiOutgoing(d, 1.0);
        double halfCost = DamageCalculator.kiCost(d, 100);
        d.setReleasePercent(100);
        helper.assertTrue(Math.abs(DamageCalculator.kiOutgoing(d, 1.0) - 2 * half) < 1e-6, "ki damage should scale linearly with release");
        helper.assertTrue(DamageCalculator.kiCost(d, 100) > halfCost, "higher release should cost more ki");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = SPAWN_PROTECTION + 40)
    public static void vanillaDamageHitsBodyAndMirrorsHealth(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        helper.runAfterDelay(SPAWN_PROTECTION, () -> {
        double max = d.getDerived().maxBody();
        player.hurt(player.damageSources().generic(), 2.0f);
        double expected = Math.max(DamageCalculator.fromVanilla(2.0) * DBZConfig.SERVER.minDamageFraction.get(),
                DamageCalculator.fromVanilla(2.0) - d.getDerived().defense());
        helper.assertTrue(Math.abs((max - d.getBody()) - expected) < 1e-3, "body loss " + (max - d.getBody()) + " expected " + expected);
        helper.assertTrue(Math.abs(player.getHealth() - BodyHealth.expectedHealth(player, d)) < 1e-3, "vanilla health must mirror body");
        helper.assertTrue(player.isAlive(), "should survive a small hit");
        TestPlayers.remove(helper, player);
        helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = SPAWN_PROTECTION + 40)
    public static void lethalBodyDamageKills(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        helper.runAfterDelay(SPAWN_PROTECTION, () -> {
        player.hurt(player.damageSources().generic(), 1000f); // 10,000 body damage
        helper.assertTrue(d.getBody() <= 0, "body should be empty");
        helper.assertTrue(player.isDeadOrDying(), "player should die when body hits 0");
        TestPlayers.remove(helper, player);
        helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void regenAndChargingTick(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(player);
        d.setKi(0);
        for (int i = 0; i < 20; i++) KiTicker.tick(player, d);
        double regen = d.getKi();
        helper.assertTrue(regen > 0, "ki should regenerate");

        d.setKi(0);
        d.setReleasePercent(50);
        double stamina = d.getStamina();
        d.setCharging(true);
        for (int i = 0; i < 20; i++) KiTicker.tick(player, d);
        helper.assertTrue(d.getKi() > regen, "charging should fill ki faster than regen: " + d.getKi() + " vs " + regen);
        helper.assertTrue(d.getStamina() < stamina, "charging should drain stamina");
        helper.assertTrue(d.getReleasePercent() > 50, "charging should raise release %");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void flightTogglesAndDrainsKi(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        helper.assertTrue(FlightHandler.toggle(player), "flight should turn on with ki");
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "abilities should allow flying");
        double ki = d.getKi();
        for (int i = 0; i < 20; i++) KiTicker.tick(player, d);
        helper.assertTrue(d.getKi() < ki, "flying should drain ki");
        d.setKi(0.0001);
        KiTicker.tick(player, d);
        helper.assertTrue(!d.isFlying() && !player.getAbilities().mayfly, "running out of ki should end flight");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tpUpgradeIsValidated(GameTestHelper helper) {
        PlayerData d = fresh();
        long cost = StatCalculator.tpCost(d, Attribute.STRENGTH);
        d.setTrainingPoints(cost - 1);
        helper.assertTrue(AttributeTraining.upgrade(d, Attribute.STRENGTH, 1) == 0, "must not upgrade without enough TP");
        d.setTrainingPoints(100_000);
        int start = d.getAttribute(Attribute.STRENGTH);
        int gained = AttributeTraining.upgrade(d, Attribute.STRENGTH, 5);
        helper.assertTrue(gained == 5 && d.getAttribute(Attribute.STRENGTH) == start + 5, "should gain 5 STR");
        helper.assertTrue(d.getTrainingPoints() < 100_000, "TP should be spent");
        helper.assertTrue(AttributeTraining.upgrade(d, Attribute.STRENGTH, 1_000_000) <= AttributeTraining.MAX_STEPS_PER_REQUEST,
                "requests are capped");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void techniqueSpendsKiAndCoolsDown(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        double ki = d.getKi();
        helper.assertTrue(TechniqueHandler.use(player, Techniques.KI_BLAST) == TechniqueHandler.Result.FIRED, "first use should fire");
        helper.assertTrue(d.getKi() < ki, "ki should be spent");
        helper.assertTrue(TechniqueHandler.use(player, Techniques.KI_BLAST) == TechniqueHandler.Result.COOLDOWN, "second use should be on cooldown");
        d.setKi(0);
        helper.assertTrue(TechniqueHandler.use(player, Techniques.WAVE_BEAM, true) == TechniqueHandler.Result.NOT_ENOUGH_KI, "no ki, no beam");
        int blasts = helper.getLevel().getEntitiesOfClass(KiBlastEntity.class, player.getBoundingBox().inflate(4), b -> b.getOwner() == player).size();
        helper.assertTrue(blasts == 1, "exactly one ki blast should spawn, found " + blasts);
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void kiBlastDamagesMob(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        float start = zombie.getHealth();
        Vec3 target = zombie.position().add(0, 1, 0);
        KiBlastEntity blast = KiBlastEntity.create(helper.getLevel(), null, Techniques.KI_BLAST, 50);
        blast.moveTo(target.x - 1.5, target.y, target.z, 0, 0); // stay inside this test's area
        blast.setDeltaMovement(0.5, 0, 0);
        helper.getLevel().addFreshEntity(blast);
        helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() < start, "zombie should be hurt by the blast"));
    }

    @GameTest(template = EMPTY)
    public static void meleeBuildsComboAndCostsStamina(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        d.setAttribute(Attribute.STRENGTH, 200);
        d.recomputeIfStale();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        float start = zombie.getHealth();
        double stamina = d.getStamina();
        player.attack(zombie);
        helper.assertTrue(zombie.getHealth() < start, "zombie should take melee damage");
        helper.assertTrue(start - zombie.getHealth() > 1.0f, "STR 200 should beat a bare fist, dealt " + (start - zombie.getHealth()));
        helper.assertTrue(d.getComboHits() == 1, "first hit starts a combo, got " + d.getComboHits());
        helper.assertTrue(d.getStamina() < stamina, "melee costs stamina");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }
}
