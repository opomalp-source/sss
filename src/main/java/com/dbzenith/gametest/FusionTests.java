package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.BodyHealth;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.race.Absorption;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 5 slice 2: Namekian fusion and Majin absorption. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FusionTests {
    private static final String EMPTY = "empty";

    private FusionTests() {}

    private static ServerPlayer as(GameTestHelper helper, Race race) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(d, race);
        d.setCharacterCreated(true);
        d.refill();
        return p;
    }

    @GameTest(template = EMPTY)
    public static void namekianFusesWithBeatenWarrior(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.NAMEKIAN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        KiFighter warrior = helper.spawnWithNoFreeWill(ModNpcs.NAMEKIAN_WARRIOR.get(), new BlockPos(1, 1, 1));
        warrior.setFighterLevel(5);
        int str = d.getAttribute(Attribute.STRENGTH);
        int expected = Absorption.fusionGain(str);
        helper.assertTrue(!Absorption.fuse(p, d, warrior), "a healthy warrior refuses");
        warrior.setHealth(warrior.getMaxHealth() * 0.2f);
        helper.assertTrue(Absorption.fuse(p, d, warrior), "a beaten warrior joins");
        helper.assertTrue(d.getAttribute(Attribute.STRENGTH) == str + expected, "a share of your own strength: " + (d.getAttribute(Attribute.STRENGTH) - str));
        helper.assertTrue(warrior.isRemoved() && d.getFusions() == 1, "the warrior is gone, one fusion used");
        d.addFusion();
        d.addFusion();
        KiFighter another = helper.spawnWithNoFreeWill(ModNpcs.NAMEKIAN_WARRIOR.get(), new BlockPos(1, 1, 1));
        another.setHealth(1);
        helper.assertTrue(!Absorption.fuse(p, d, another), "three fusions is the limit");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void playerFusionNeedsConsent(GameTestHelper helper) {
        ServerPlayer host = as(helper, Race.NAMEKIAN);
        ServerPlayer partner = as(helper, Race.NAMEKIAN);
        partner.teleportTo(host.getX() + 1, host.getY(), host.getZ()); // new players land anywhere within the spawn radius
        PlayerData hd = ModCapabilities.getOrThrow(host);
        PlayerData pd = ModCapabilities.getOrThrow(partner);
        pd.setAttribute(Attribute.KI_POWER, 400);
        TechniqueLibrary.learnFree(pd, Techniques.FINGER_BEAM);
        int ki = hd.getAttribute(Attribute.KI_POWER);
        helper.assertTrue(!Absorption.acceptFusion(partner), "nothing to accept yet");
        helper.assertTrue(Absorption.fuse(host, hd, partner), "request sent");
        helper.assertTrue(hd.getAttribute(Attribute.KI_POWER) == ki, "nothing happens before consent");
        helper.assertTrue(Absorption.acceptFusion(partner), "accepted");
        helper.assertTrue(hd.getAttribute(Attribute.KI_POWER) == ki + 100, "a quarter of the partner's KI POWER: " + (hd.getAttribute(Attribute.KI_POWER) - ki));
        helper.assertTrue(hd.knows(Techniques.FINGER_BEAM.id()), "and their techniques");
        helper.assertTrue(!pd.isCharacterCreated(), "the partner starts over");
        TestPlayers.remove(helper, host);
        TestPlayers.remove(helper, partner);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void majinAbsorbsBeatenFoes(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.MAJIN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAttribute(Attribute.STRENGTH, 200);
        d.recomputeIfStale();
        double melee = d.getDerived().meleeDamage();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        helper.assertTrue(!Absorption.absorb(p, d, zombie), "a healthy zombie resists");
        zombie.setHealth(2);
        helper.assertTrue(Absorption.absorb(p, d, zombie), "absorbed");
        helper.assertTrue(!zombie.isAlive(), "the zombie is gone");
        d.recomputeIfStale();
        helper.assertTrue(d.getMajinStacks() == 1 && d.getDerived().meleeDamage() > melee * 1.05, "stronger for a while");
        d.tickMajin(helper.getLevel().getGameTime() + 1_000_000);
        d.recomputeIfStale();
        helper.assertTrue(d.getMajinStacks() == 0 && Math.abs(d.getDerived().meleeDamage() - melee) < 1e-6, "and it wears off");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void majinStealsATechniqueFromAPlayer(GameTestHelper helper) {
        ServerPlayer majin = as(helper, Race.MAJIN);
        ServerPlayer victim = as(helper, Race.HUMAN);
        PlayerData md = ModCapabilities.getOrThrow(majin);
        helper.getLevel().getServer().setPvpAllowed(true);
        PlayerData vd = ModCapabilities.getOrThrow(victim);
        vd.learn(Techniques.FINGER_BEAM.id());
        vd.setBody(vd.getDerived().maxBody() * 0.1);
        BodyHealth.mirror(victim, vd);
        helper.assertTrue(Absorption.absorb(majin, md, victim), "absorbed a beaten player");
        helper.assertTrue(md.knows(Techniques.FINGER_BEAM.id()), "learned their technique");
        helper.assertTrue(victim.isDeadOrDying(), "the victim falls");
        TestPlayers.remove(helper, majin);
        TestPlayers.remove(helper, victim);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bossesCannotBeAbsorbed(GameTestHelper helper) {
        ServerPlayer p = as(helper, Race.MAJIN);
        PlayerData d = ModCapabilities.getOrThrow(p);
        var boss = helper.spawnWithNoFreeWill(ModNpcs.TYRANT_LORD.get(), new BlockPos(1, 1, 1));
        boss.setHealth(1);
        helper.assertTrue(!Absorption.absorb(p, d, boss), "bosses resist");
        helper.assertTrue(!Absorption.fuse(p, d, boss), "and only Namekians fuse");
        boss.discard();
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
