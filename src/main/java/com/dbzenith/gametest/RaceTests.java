package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.RacePassives;
import com.dbzenith.race.Races;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Forms;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 3: racial traits, passives and character creation. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RaceTests {
    private static final String EMPTY = "empty";
    private static final int SPAWN_PROTECTION = 62;

    private RaceTests() {}

    private static ServerPlayer as(GameTestHelper helper, Race race) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(d, race);
        d.refill();
        return p;
    }

    @GameTest(template = EMPTY)
    public static void tpCostFollowsRace(GameTestHelper helper) {
        PlayerData human = new PlayerData();
        human.initDefaultsIfNeeded();
        PlayerData saiyan = new PlayerData();
        saiyan.initDefaultsIfNeeded();
        saiyan.setRace(Race.SAIYAN);
        helper.assertTrue(StatCalculator.tpCost(saiyan, Attribute.STRENGTH) < StatCalculator.tpCost(human, Attribute.STRENGTH),
                "Saiyans train STR more cheaply");
        helper.assertTrue(StatCalculator.tpCost(saiyan, Attribute.MIND) > StatCalculator.tpCost(human, Attribute.MIND),
                "Saiyans train MIND more slowly than humans");
        helper.assertTrue(StatCalculator.scaleTpGain(human, 100) > StatCalculator.scaleTpGain(saiyan, 100), "humans learn faster");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void androidNeverTiresAndAbsorbsKi(GameTestHelper helper) {
        ServerPlayer android = as(helper, Race.ANDROID);
        PlayerData d = ModCapabilities.getOrThrow(android);
        d.setCharging(true);
        for (int i = 0; i < 40; i++) KiTicker.tick(android, d);
        helper.assertTrue(d.getStamina() == d.getDerived().maxStamina(), "androids do not tire");
        d.setKi(0);
        double through = RacePassives.absorbKiHit(d, 100, 0);
        helper.assertTrue(through < 1.0 && d.getKi() > 0, "androids absorb part of a ki hit as ki");
        d.setAbsorbUntil(1000);
        helper.assertTrue(RacePassives.absorbKiHit(d, 100, 10) == 0.0, "energy absorb window swallows the whole hit");
        TestPlayers.remove(helper, android);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void namekiansRegenerateFaster(GameTestHelper helper) {
        ServerPlayer human = as(helper, Race.HUMAN);
        ServerPlayer namek = as(helper, Race.NAMEKIAN);
        PlayerData h = ModCapabilities.getOrThrow(human);
        PlayerData n = ModCapabilities.getOrThrow(namek);
        for (PlayerData d : new PlayerData[]{h, n}) {
            d.setBody(d.getDerived().maxBody() * 0.3);
            d.setLastDamagedTick(Long.MIN_VALUE / 4);
        }
        for (int i = 0; i < 40; i++) {
            KiTicker.tick(human, h);
            KiTicker.tick(namek, n);
        }
        double hr = h.getBody() / h.getDerived().maxBody();
        double nr = n.getBody() / n.getDerived().maxBody();
        helper.assertTrue(nr - 0.3 > 2.5 * (hr - 0.3), "Namekians should regenerate ~3x faster: " + nr + " vs " + hr);
        TestPlayers.remove(helper, human);
        TestPlayers.remove(helper, namek);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void zenkaiAfterNearDeath(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.SAIYAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAttribute(Attribute.STRENGTH, 500);
        d.recomputeIfStale();
        long now = helper.getLevel().getGameTime();
        d.setBody(d.getDerived().maxBody() * 0.05);
        RacePassives.tick(p, d, now);
        helper.assertTrue(!d.isZenkaiArmed(), "hazards like gravity strain do not arm Zenkai");
        d.setLastFoeHitTick(now); // beaten down by an enemy
        RacePassives.tick(p, d, now);
        helper.assertTrue(d.isZenkaiArmed(), "near death in a fight arms Zenkai");
        d.refill(); // e.g. a Senzu Bean
        RacePassives.tick(p, d, now + 1);
        helper.assertTrue(d.getAttribute(Attribute.STRENGTH) == 500 + RacePassives.zenkaiGain(500, 0.25), "recovering grants a permanent boost: " + d.getAttribute(Attribute.STRENGTH));
        int after = d.getAttribute(Attribute.STRENGTH);
        d.setBody(d.getDerived().maxBody() * 0.05);
        d.setLastFoeHitTick(now + 2);
        RacePassives.tick(p, d, now + 2);
        d.refill();
        RacePassives.tick(p, d, now + 3);
        helper.assertTrue(d.getAttribute(Attribute.STRENGTH) == after, "Zenkai has a cooldown");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void majinHealsOnKills(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.MAJIN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setBody(d.getDerived().maxBody() * 0.4);
        double before = d.getBody();
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        z.hurt(p.damageSources().playerAttack(p), 10_000f);
        helper.assertTrue(!z.isAlive(), "zombie should die");
        helper.assertTrue(d.getBody() > before, "Majin heal by absorbing the fallen");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void frostDemonsNeedNoAir(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.FROST_DEMON);
        p.setAirSupply(0);
        KiTicker.tick(p, ModCapabilities.getOrThrow(p));
        helper.assertTrue(p.getAirSupply() == p.getMaxAirSupply(), "Frost Demons are breathless");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void creationAppliesChoicesOnce(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        int spi = d.getAttribute(Attribute.SPIRIT);
        int con = d.getAttribute(Attribute.CONSTITUTION);
        CharacterCreation.Choices c = new CharacterCreation.Choices(Race.NAMEKIAN, FightingPath.SPIRITUALIST,
                PlayerData.BodyType.BULKY, 1, 0x40A040, 0xFF0000, -30);
        helper.assertTrue(CharacterCreation.create(d, c), "first creation succeeds");
        helper.assertTrue(d.getRace() == Race.NAMEKIAN && d.getPath() == FightingPath.SPIRITUALIST && d.isCharacterCreated(), "choices applied");
        helper.assertTrue(d.getAttribute(Attribute.SPIRIT) == spi + Races.of(Race.NAMEKIAN).startBonus(Attribute.SPIRIT), "racial start bonus");
        helper.assertTrue(d.getAttribute(Attribute.CONSTITUTION) == con + 3, "bulky body bonus");
        helper.assertTrue(!d.hasTail() && d.getAlignment() == -30 && d.getEyeColor() == 0xFF0000, "tail, alignment, eyes");
        helper.assertTrue(d.knows("regenerate"), "racial technique learned");
        helper.assertTrue(!CharacterCreation.create(d, c), "creation cannot be repeated");
        CharacterCreation.applyRace(d, Race.MAJIN);
        helper.assertTrue(!d.knows("regenerate") && d.knows("candy_beam"), "changing race swaps racial techniques");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyRaceHasItsOwnFormLine(GameTestHelper helper) {
        for (Race r : Race.values()) {
            helper.assertTrue(!Forms.children("base", r).isEmpty(), r.id() + " should have at least one form");
        }
        helper.assertTrue(Forms.children("base", Race.HUMAN).stream().noneMatch(f -> f.id().startsWith("super_saiyan")), "no SSJ for humans");
        helper.assertTrue(Forms.GIANT_NAMEKIAN.scale() > 2f, "Giant Namekian is giant");
        helper.succeed();
    }
}
