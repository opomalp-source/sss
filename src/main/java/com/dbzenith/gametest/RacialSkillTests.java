package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.RacialSkill;
import com.dbzenith.race.RacialSkill.Stat;
import com.dbzenith.race.RacialSkillEffects;
import com.dbzenith.race.RacialSkills;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** CX-4: racial skills, passive and active. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RacialSkillTests {
    private static final String EMPTY = "empty";

    private RacialSkillTests() {}

    private static ServerPlayer fighter(GameTestHelper helper, Race race, Variant variant, int levels) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(d, race);
        d.setVariant(variant);
        d.setAttribute(Attribute.STRENGTH, d.getAttribute(Attribute.STRENGTH) + levels);
        d.recomputeIfStale();
        d.refill();
        return p;
    }

    @GameTest(template = EMPTY)
    public static void everyRaceHasAFullKit(GameTestHelper helper) {
        Set<String> ids = new HashSet<>();
        for (RacialSkill s : RacialSkills.all()) {
            helper.assertTrue(ids.add(s.id()), "unique id " + s.id());
            helper.assertTrue(!s.isActive() || s.cooldownTicks() > 0, s.id() + " has a cooldown");
            helper.assertTrue(s.isActive() || s.durationTicks() == 0, s.id() + ": only actives last");
            for (RacialSkill.Mod m : s.mods()) {
                helper.assertTrue(s.isActive() || m.when() != RacialSkill.When.ACTIVE && m.when() != RacialSkill.When.AFTER,
                        s.id() + ": a passive cannot have an active-only modifier");
            }
        }
        helper.assertTrue(ids.size() >= 70, "around seventy racial skills, got " + ids.size());
        for (Variant v : Variant.values()) {
            var kit = RacialSkills.forCharacter(v.race(), v);
            helper.assertTrue(kit.size() >= 3, v + " has at least three skills, got " + kit.size());
            helper.assertTrue(kit.stream().anyMatch(RacialSkill::isActive), v + " has an active");
            helper.assertTrue(kit.stream().anyMatch(s -> s.unlockLevel() == 0) || kit.get(0).unlockLevel() <= 50, v + " starts with something early");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void passivesFollowTheirConditions(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, Race.SAIYAN, Variant.SAIYAN, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        long now = helper.getLevel().getGameTime();
        RacialSkillEffects.tick(p, d, now - now % 5);
        d.recomputeIfStale();
        double calm = d.getDerived().meleeDamage();
        d.markCombat(now);
        d.setCombatTicks(40);
        RacialSkillEffects.tick(p, d, now - now % 5 + 5);
        d.recomputeIfStale();
        helper.assertTrue(Math.abs(d.getDerived().meleeDamage() / calm - 1.08) < 1e-6, "Warrior Race: +8% while fighting, got x" + d.getDerived().meleeDamage() / calm);
        helper.assertTrue(RacialSkills.blowFactor(d, null, 1.0, true, false) > RacialSkills.blowFactor(d, null, 1.0, false, false),
                "Prideful: harder against the stronger");

        ServerPlayer h = fighter(helper, Race.HUMAN, Variant.HUMAN, 100);
        PlayerData hd = ModCapabilities.getOrThrow(h);
        double full = RacialSkills.blowFactor(null, hd, 1, false, false);
        hd.setBody(hd.getDerived().maxBody() * 0.2);
        RacialSkillEffects.tick(h, hd, now - now % 5 + 10);
        helper.assertTrue(Math.abs(RacialSkills.blowFactor(null, hd, 1, false, false) / full - 0.85) < 1e-6, "Persistence: -15% taken when beaten down");

        ServerPlayer m = fighter(helper, Race.MAJIN, Variant.MAJIN, 0);
        PlayerData md = ModCapabilities.getOrThrow(m);
        helper.assertTrue(RacialSkills.blowFactor(null, md, 1, false, false) < RacialSkills.blowFactor(null, md, 1, false, true),
                "Gum Body softens blows, not ki");
        helper.assertTrue(!RacialSkills.has(md, "death_regeneration"), "Death Regeneration waits for level 400");
        TestPlayers.remove(helper, p);
        TestPlayers.remove(helper, h);
        TestPlayers.remove(helper, m);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void activesCostCooldownAndBuff(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, Race.SAIYAN, Variant.SAIYAN, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRacialSelected("saiyans_resolve");
        double ki = d.getKi();
        double taken = RacialSkills.factor(d, Stat.DAMAGE_TAKEN);
        helper.assertTrue(RacialSkillEffects.use(p), "Saiyan's Resolve fires");
        helper.assertTrue(d.getKi() < ki, "and costs ki");
        helper.assertTrue(Math.abs(RacialSkills.factor(d, Stat.DAMAGE_TAKEN) / taken - 0.7) < 1e-6, "30% less damage while it lasts");
        helper.assertTrue(RacialSkills.sum(d, Stat.KNOCKBACK_RESIST) >= 1, "and no knockback");
        helper.assertTrue(!RacialSkillEffects.use(p), "then it recharges");
        long now = helper.getLevel().getGameTime();
        RacialSkillEffects.tick(p, d, now + 15 * 20 + 5 - (now + 15 * 20 + 5) % 5);
        helper.assertTrue(RacialSkills.factor(d, Stat.DAMAGE_TAKEN) == taken, "and wears off");

        ServerPlayer q = fighter(helper, Race.SAIYAN, Variant.PRIMAL, 900);
        PlayerData qd = ModCapabilities.getOrThrow(q);
        double melee = qd.getDerived().meleeDamage();
        helper.assertTrue(RacialSkillEffects.use(q, qd, RacialSkills.byId("shattering_the_limit")), "Shattering the Limit fires");
        qd.recomputeIfStale();
        helper.assertTrue(qd.getDerived().meleeDamage() > melee * 1.2, "+25% while it lasts");
        long t = helper.getLevel().getGameTime() + 12 * 20 + 5;
        RacialSkillEffects.tick(q, qd, t - t % 5);
        qd.recomputeIfStale();
        helper.assertTrue(qd.getDerived().meleeDamage() < melee, "then the backlash");
        TestPlayers.remove(helper, p);
        TestPlayers.remove(helper, q);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void majinAndNamekianCheatDeath(GameTestHelper helper) {
        ServerPlayer m = fighter(helper, Race.MAJIN, Variant.MAJIN, 400);
        PlayerData md = ModCapabilities.getOrThrow(m);
        LivingDeathEvent death = new LivingDeathEvent(m, m.damageSources().generic());
        RacialSkillEffects.onDeath(death);
        helper.assertTrue(death.isCanceled() && md.getBody() > 0, "Death Regeneration pulls a Majin back together");
        LivingDeathEvent again = new LivingDeathEvent(m, m.damageSources().generic());
        RacialSkillEffects.onDeath(again);
        helper.assertTrue(!again.isCanceled(), "but not twice in ten minutes");

        PlayerData copy = new PlayerData();
        md.setRacialSelected("remote_absorb");
        md.setRacialCooldown("remote_absorb", 1234);
        copy.load(md.save());
        helper.assertTrue(copy.getRacialSelected().equals("remote_absorb") && copy.getRacialCooldown("remote_absorb") == 1234
                && copy.getRacialOnce("death_regeneration") == md.getRacialOnce("death_regeneration"), "racial state is saved");
        TestPlayers.remove(helper, m);
        helper.succeed();
    }
}
