package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.npc.ai.Action;
import com.dbzenith.npc.ai.AiMemory;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.Faction;
import com.dbzenith.npc.ai.NpcBrain;
import com.dbzenith.npc.ai.NpcGuard;
import com.dbzenith.npc.ai.OpponentProfile;
import com.dbzenith.npc.ai.Personality;
import com.dbzenith.npc.ai.Situation;
import com.dbzenith.npc.ai.UtilityScorer;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.stats.Attribute;
import com.dbzenith.transform.Forms;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The NPC combat AI (docs/COMBAT_AI.md): deterministic utility choices, personality, transformations by need with
 * cost and hysteresis, low ki, NPC against NPC by side, being outnumbered, the guard, friendly fire, gradual
 * adaptation, saving and reloading, and flight that never enters unloaded chunks or walls. Fighters that think are
 * confined to their own arena (other tests run alongside).
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AiTests {
    private static final String EMPTY = "empty";

    private AiTests() {}

    private static <T extends KiFighter> T fighter(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T k = h.spawn(type, at);
        k.brain().arena = new AABB(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(-14, -4, -14))), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(26, 24, 32))));
        return k;
    }

    /** Whatever happens (a failed test times out), these leave before the test ends: later batches reuse the spot. */
    private static void cleanup(GameTestHelper h, int timeout, net.minecraft.world.entity.Entity... es) {
        Delays.after(h, Math.max(1, timeout - 3), () -> {
            for (var e : es) if (e != null && !e.isRemoved()) e.discard();
        });
    }

    private static PlayerTeam team(GameTestHelper h, String name, LivingEntity... members) {
        Scoreboard sb = h.getLevel().getScoreboard();
        String id = name + "_" + UUID.randomUUID().toString().substring(0, 6);
        PlayerTeam t = sb.addPlayerTeam(id);
        for (LivingEntity e : members) sb.addPlayerToTeam(e.getStringUUID(), t);
        return t;
    }

    private static void dropTeams(GameTestHelper h, PlayerTeam... teams) {
        for (PlayerTeam t : teams) h.getLevel().getScoreboard().removePlayerTeam(t);
    }

    // ------------------------------------------------------------------ the decision itself (pure)

    @GameTest(template = EMPTY)
    public static void choicesAreDeterministicAndShapedByPersonality(GameTestHelper h) {
        Situation s = new Situation();
        s.distance = 2.5;
        s.ratio = 1.0;
        UtilityScorer.Result a = UtilityScorer.score(s), b = UtilityScorer.score(s);
        for (Action x : Action.values()) h.assertTrue(a.of(x) == b.of(x), "the same situation scores the same: " + x);
        h.assertTrue(a.best() == Action.PRESSURE, "close in on even terms: pressure, got " + a.best() + " because " + a.why(a.best()));
        h.assertTrue(AiRandom.unit(42, 7, 3) == AiRandom.unit(42, 7, 3) && AiRandom.unit(42, 7, 3) != AiRandom.unit(42, 8, 3), "variation is a pure hash");

        Situation far = new Situation();                                          // a ki fighter far off, a foe that melees hard
        far.distance = 16;
        far.quickReady = true;
        far.personality = Personality.TACTICAL;
        far.style = Personality.TACTICAL.style();
        far.profile = meleeHeavyProfile();
        UtilityScorer.Result r = UtilityScorer.score(far);
        h.assertTrue(r.of(Action.KI_VOLLEY) > r.of(Action.APPROACH), "against a brawler, from range: fire rather than walk in");

        Situation hurt = new Situation();                                         // badly hurt, outmatched
        hurt.health = 0.15;
        hurt.ratio = 0.3;
        hurt.distance = 6;
        hurt.personality = Personality.COWARDLY;
        hurt.style = Personality.COWARDLY.style();
        Situation brave = copy(hurt);
        brave.personality = Personality.RECKLESS;
        brave.style = Personality.RECKLESS.style();
        h.assertTrue(UtilityScorer.score(hurt).best() == Action.RETREAT, "a coward runs: " + UtilityScorer.score(hurt).best());
        h.assertTrue(UtilityScorer.score(brave).best() != Action.RETREAT, "the reckless do not");

        Situation low = new Situation();                                          // no ki to spend, far off
        low.distance = 18;
        low.ki = 0.05;
        low.quickReady = low.bigReady = false;
        UtilityScorer.Result lr = UtilityScorer.score(low);
        h.assertTrue(lr.of(Action.KI_VOLLEY) == UtilityScorer.NO && lr.of(Action.BIG_TECHNIQUE) == UtilityScorer.NO, "nothing to fire with");
        h.assertTrue(lr.of(Action.CHARGE_KI) > 0.5, "so it charges: " + lr.of(Action.CHARGE_KI));
        low.distance = 3;
        h.assertTrue(UtilityScorer.score(low).of(Action.CHARGE_KI) < UtilityScorer.score(low).of(Action.PRESSURE), "but not with a foe in its face");
        h.succeed();
    }

    private static OpponentProfile meleeHeavyProfile() {
        OpponentProfile p = new OpponentProfile();
        for (int i = 0; i < 200; i++) {
            for (int k = 0; k < 3; k++) p.observeMeleeForTest();
            p.sampleForTest(6, false, false, false, 3);
        }
        return p;
    }

    private static Situation copy(Situation s) {
        Situation c = new Situation();
        c.health = s.health;
        c.ratio = s.ratio;
        c.distance = s.distance;
        return c;
    }

    @GameTest(template = EMPTY)
    public static void transformationsWeighNeedPrideAndCost(GameTestHelper h) {
        Situation s = new Situation();
        s.canTransformUp = true;
        s.ratio = 0.6;
        s.health = 0.9;
        s.distance = 8;
        s.nextFormFactor = 1.3;
        s.kiAfterForm = 0.8;
        s.personality = Personality.TACTICAL;
        double cautious = UtilityScorer.score(s).of(Action.TRANSFORM);
        s.personality = Personality.ARROGANT;
        double proud = UtilityScorer.score(s).of(Action.TRANSFORM);
        h.assertTrue(cautious > proud + 0.3, "the arrogant hold back their forms: " + cautious + " vs " + proud);
        s.health = 0.25;
        double proudHurt = UtilityScorer.score(s).of(Action.TRANSFORM);
        h.assertTrue(proudHurt > proud + 0.3, "until they are hurt: " + proudHurt);
        s.kiAfterForm = 0.05;
        h.assertTrue(UtilityScorer.score(s).of(Action.TRANSFORM) < proudHurt - 0.8, "an exhausted fighter avoids a costly form");
        s.kiAfterForm = 0.8;
        s.ratio = 1.6;
        s.health = 0.9;
        s.personality = Personality.TACTICAL;
        h.assertTrue(UtilityScorer.score(s).of(Action.TRANSFORM) == UtilityScorer.NO, "no need, no form (escalate only when needed)");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void adaptationIsGradualAndObservable(GameTestHelper h) {
        OpponentProfile p = new OpponentProfile();
        for (int k = 0; k < 4; k++) p.observeMeleeForTest();
        p.sampleForTest(6, false, false, false, 3);
        double first = p.meleeHeavy();
        h.assertTrue(first < 0.1, "one flurry proves little: " + first);
        for (int i = 0; i < 120; i++) {
            for (int k = 0; k < 2; k++) p.observeMeleeForTest();
            p.sampleForTest(6, i % 3 == 0, false, false, 3);
        }
        h.assertTrue(p.meleeHeavy() > 0.35 && p.blastSpam() < 0.05, "a minute of brawling reads as a brawler: " + p.summary());
        h.assertTrue(p.turtle() > 0.15, "and one that guards now and then: " + p.summary());
        Situation s = new Situation();
        s.distance = 3;
        s.quickReady = true;
        double before = UtilityScorer.score(s).of(Action.KEEP_DISTANCE);
        s.profile = p;
        h.assertTrue(UtilityScorer.score(s).of(Action.KEEP_DISTANCE) > before + 0.1, "so a ki fighter keeps away from it");
        h.succeed();
    }

    // ------------------------------------------------------------------ in the world

    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "npc_ai_1")
    public static void friezaTransformsWhenOutmatchedAndHolds(GameTestHelper h) {
        KiFighter boss = fighter(h, ModNpcs.TYRANT_LORD.get(), new BlockPos(1, 1, 1));
        boss.setFighterLevel(30);
        boss.setHealth(boss.getMaxHealth() * 0.4f);
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setAttribute(Attribute.CONSTITUTION, d.getAttribute(Attribute.CONSTITUTION) + 50_000);
        d.setAttribute(Attribute.STRENGTH, d.getAttribute(Attribute.STRENGTH) + 50_000);
        d.recomputeIfStale();
        d.refill();
        p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 7))));
        boss.setTarget(p);
        cleanup(h, 400, boss);
        NpcBrain b = boss.brain();
        h.assertTrue(b.personality() == Personality.ARROGANT && Forms.BASE.id().equals(boss.aiFormId()), "Frieza starts arrogant, in base");
        Delays.after(h, 150, () -> {
            String form = boss.aiFormId();
            h.assertTrue(!Forms.BASE.id().equals(form), "outmatched and hurt, he transforms: " + String.join(" / ", b.describe()));
            h.assertTrue(form.equals(b.forms().current().id()), "the form clients see is the one he is in");
            h.assertTrue(Forms.byId(form).races().contains(com.dbzenith.stats.Race.FROST_DEMON), "a Frost Demon form, his own line: " + form);
            h.assertTrue(b.resources().kiShare() < 0.97, "and paid for it in ki");
            h.assertTrue(boss.formFactor() > 1.0 && boss.kiDamage() > 0, "it strengthens his blows");
            Delays.after(h, 60, () -> {
                h.assertTrue(!Forms.BASE.id().equals(boss.aiFormId()), "no flip-flopping straight back to base");
                TestPlayers.remove(h, p);
                boss.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "npc_ai_2")
    public static void lowKiFighterChargesInsteadOfFiring(GameTestHelper h) {
        KiFighter soldier = fighter(h, ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        soldier.brain().setPersonality(Personality.TACTICAL);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 14));
        cleanup(h, 200, soldier, pig);
        soldier.setTarget(pig);
        soldier.brain().resources().set(0.01, 1);
        double start = soldier.brain().resources().ki();
        Delays.after(h, 25, () -> {
            boolean blasts = !h.getLevel().getEntitiesOfClass(KiBlastEntity.class, soldier.getBoundingBox().inflate(20), e -> e.getOwner() == soldier).isEmpty();
            h.assertTrue(!blasts, "no ki to fire with: nothing fired");
            h.assertTrue(soldier.brain().action() == Action.CHARGE_KI && soldier.hasAiFlag(KiFighter.AI_CHARGING),
                    "it charges, visibly: " + String.join(" / ", soldier.brain().describe()));
            Delays.after(h, 20, () -> {
                h.assertTrue(soldier.brain().resources().ki() > start + 40, "and its ki comes back");
                soldier.discard();
                pig.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "npc_ai_3")
    public static void npcsOfEnemySidesFightEachOther(GameTestHelper h) {
        pen(h, true);                                                                    // a closed pen, wherever the test is placed (it may be open sea)
        Delays.after(h, 597, () -> pen(h, false));
        KiFighter namek = fighter(h, ModNpcs.NAMEKIAN_WARRIOR.get(), new BlockPos(1, 1, 1));
        KiFighter soldier = fighter(h, ModNpcs.SPROUTLING.get(), new BlockPos(1, 1, 4));   // a Saibaman: Frieza Force, melee only, no flight
        namek.brain().setPersonality(Personality.HONORABLE);
        AABB pair = new AABB(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(-3, 0, -3))), Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(6, 6, 9))));
        namek.brain().arena = pair;                                                      // each other only, in the pen: other tests run alongside
        soldier.brain().arena = pair;
        cleanup(h, 600, namek, soldier);
        PlayerTeam a = team(h, "aiA", namek), b = team(h, "aiB", soldier);
        h.assertTrue(Faction.enemies(namek, soldier) && !Faction.allied(namek, soldier), "different teams: enemies");
        h.assertTrue(Faction.allied(namek, namek), "everyone is their own ally");
        soldier.setTarget(namek);
        namek.setTarget(soldier);
        float nm = namek.getHealth(), sm = soldier.getHealth();
        h.succeedWhen(() -> {
            h.assertTrue(namek.getHealth() < nm, "the soldier hurts the Namekian: " + String.join(" / ", soldier.brain().describe()) + " at " + soldier.blockPosition().subtract(namek.blockPosition()));
            h.assertTrue(soldier.getHealth() < sm, "and the Namekian fights back: " + String.join(" / ", namek.brain().describe()));
            dropTeams(h, a, b);
            namek.discard();
            soldier.discard();
            pen(h, false);
        });
    }

    /** A closed glass pen on a stone floor (or, {@code false}, its blocks cleared again: later batches reuse the spot). */
    private static void pen(GameTestHelper h, boolean build) {
        for (int x = -3; x <= 5; x++)
            for (int z = -3; z <= 8; z++)
                for (int y = 0; y <= 5; y++) {
                    boolean wall = x == -3 || x == 5 || z == -3 || z == 8 || y == 0 || y == 5;
                    h.setBlock(new BlockPos(x, y, z), !build || !wall ? net.minecraft.world.level.block.Blocks.AIR
                            : y == 0 ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.GLASS);
                }
    }

    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "npc_ai_4")
    public static void outnumberedCowardPicksAThreatAndRuns(GameTestHelper h) {
        KiFighter coward = fighter(h, ModNpcs.KI_SOLDIER.get(), new BlockPos(2, 1, 2));
        coward.brain().setPersonality(Personality.COWARDLY);
        coward.setFighterLevel(20);
        coward.setHealth(coward.getMaxHealth() * 0.15f);
        KiFighter x = fighter(h, ModNpcs.KI_SOLDIER.get(), new BlockPos(2, 1, 9));
        KiFighter y = fighter(h, ModNpcs.KI_SOLDIER.get(), new BlockPos(6, 1, 9));
        KiFighter z = fighter(h, ModNpcs.SPROUTLING.get(), new BlockPos(0, 1, 9));
        PlayerTeam a = team(h, "aiC", coward), b = team(h, "aiD", x, y, z);
        cleanup(h, 200, coward, x, y, z);
        for (KiFighter k : new KiFighter[]{x, y, z}) k.setTarget(coward);
        coward.setLastHurtByMob(x);
        h.succeedWhen(() -> {
            LivingEntity t = coward.getTarget();
            h.assertTrue(t == x || t == y || t == z, "it faces one of them: " + (t == null ? "none" : t.getName().getString()));
            h.assertTrue(coward.brain().action() == Action.RETREAT, "and runs: " + String.join(" / ", coward.brain().describe()));
            dropTeams(h, a, b);
            for (KiFighter k : new KiFighter[]{coward, x, y, z}) k.discard();
        });
    }

    @GameTest(template = EMPTY, batch = "npc_ai_5")
    public static void guardBlocksParriesAndBreaksByThePlayersRules(GameTestHelper h) {
        KiFighter k = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(2, 1, 2));
        k.setYRot(0);
        k.setYHeadRot(0);
        k.yBodyRot = 0;
        Pig front = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 5));
        Pig back = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 0));
        NpcBrain b = k.brain();
        long now = h.getLevel().getGameTime();
        b.testRaiseGuard(now);
        h.assertTrue(k.hasAiFlag(KiFighter.AI_GUARDING), "the guard shows");
        h.assertTrue(NpcGuard.onHit(k, b.resources(), front, 0.05, true, false, false, now) == NpcGuard.Outcome.PARRY, "raised just in time: a parry");
        h.assertTrue(front.hasEffect(ModEffects.STUN.get()), "the attacker staggers");
        double g0 = b.resources().guardShare();
        h.assertTrue(NpcGuard.onHit(k, b.resources(), front, 0.05, true, false, false, now) == NpcGuard.Outcome.BLOCK, "then blocks");
        h.assertTrue(b.resources().guardShare() < g0, "which wears the guard");
        h.assertTrue(NpcGuard.onHit(k, b.resources(), back, 0.05, true, false, false, now) == NpcGuard.Outcome.NONE, "not from behind");
        h.assertTrue(NpcGuard.onHit(k, b.resources(), front, 0.05, true, false, true, now) == NpcGuard.Outcome.NONE, "not against a sweep");
        NpcGuard.Outcome o = NpcGuard.Outcome.BLOCK;
        for (int i = 0; i < 20 && o != NpcGuard.Outcome.BREAK; i++) o = NpcGuard.onHit(k, b.resources(), front, 0.2, true, true, false, now);
        h.assertTrue(o == NpcGuard.Outcome.BREAK && k.hasEffect(ModEffects.STUN.get()), "enough and it breaks, stunning it");
        b.testRaiseGuard(now + 1);
        h.assertTrue(!b.resources().guarding(), "and it can't guard again for a while");

        KiFighter open = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(4, 1, 2));    // a blow through the real damage path
        KiFighter guarded = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(0, 1, 2));
        for (KiFighter f : new KiFighter[]{open, guarded}) {
            f.setYRot(0);
            f.setYHeadRot(0);
        }
        Pig hitter = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 6));
        guarded.brain().testRaiseGuard(now - 20);
        float o0 = open.getHealth(), g1 = guarded.getHealth();
        open.invulnerableTime = guarded.invulnerableTime = 0;
        open.hurt(ModDamageTypes.strike(h.getLevel(), hitter), 6);
        guarded.hurt(ModDamageTypes.strike(h.getLevel(), hitter), 6);
        h.assertTrue(o0 - open.getHealth() > (g1 - guarded.getHealth()) * 3, "a guarded blow does a fraction: " + (o0 - open.getHealth()) + " vs " + (g1 - guarded.getHealth()));
        for (var e : new net.minecraft.world.entity.Entity[]{k, front, back, open, guarded, hitter}) e.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void holdsFireWhenAnAllyIsInTheWay(GameTestHelper h) {
        KiFighter shooter = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        KiFighter ally = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 4));
        Pig foe = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 8));
        h.assertTrue(Faction.allied(shooter, ally), "two Frieza Force soldiers are allies");
        h.assertTrue(shooter.isAlliedTo(ally), "and the engine knows it (no blows between them)");
        h.assertTrue(shooter.brain().testAllyInLine(foe), "an ally in the line of fire");
        ally.moveTo(ally.getX() + 4, ally.getY(), ally.getZ());
        h.assertTrue(!shooter.brain().testAllyInLine(foe), "stepped aside: clear");
        for (var e : new net.minecraft.world.entity.Entity[]{shooter, ally, foe}) e.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "npc_ai_6")
    public static void flightNeverEntersUnloadedChunksOrWalls(GameTestHelper h) {
        KiFighter k = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(2, 2, 2));
        var move = k.brain().locomotion();
        Vec3 far = move.safe(new Vec3(4000, 0, 0), k.position().add(40000, 0, 0));
        h.assertTrue(far.horizontalDistance() < 1e-6, "toward unloaded chunks it stops: " + far);
        for (int y = 1; y <= 6; y++) for (int z = 0; z <= 4; z++) h.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        Vec3 into = new Vec3(0.6, 0, 0);
        Vec3 made = move.safe(into, k.position().add(10, 0, 0));
        AABB box = k.getBoundingBox();
        h.assertTrue(h.getLevel().noCollision(k, box.expandTowards(made.scale(4)).deflate(0.02)), "no flying into a wall: " + made);
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) h.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
        Vec3 up = move.safe(new Vec3(0, 0.6, 0), k.position().add(0, 10, 0));
        h.assertTrue(h.getLevel().noCollision(k, box.expandTowards(up.scale(4)).deflate(0.02)), "nor into a ceiling: " + up);
        k.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mindAndMemorySurviveAReload(GameTestHelper h) {
        KiFighter android = h.spawnWithNoFreeWill(ModNpcs.ANDROID_UNIT.get(), new BlockPos(1, 1, 1));
        android.setFighterLevel(6);
        NpcBrain b = android.brain();
        b.setPersonality(Personality.PATIENT);
        b.resources().set(0.4, 0.6);
        b.testEnterForm(Forms.UPGRADE_MK2);
        CompoundTag tag = new CompoundTag();
        android.saveWithoutId(tag);
        KiFighter copy = ModNpcs.ANDROID_UNIT.get().create(h.getLevel());
        copy.load(tag);
        h.assertTrue(copy.brain().personality() == Personality.PATIENT, "its temperament");
        h.assertTrue(Math.abs(copy.brain().resources().kiShare() - 0.4) < 0.01, "its ki: " + copy.brain().resources().kiShare());
        h.assertTrue("upgrade_mk2".equals(copy.brain().forms().current().id()) && "upgrade_mk2".equals(copy.aiFormId()), "and its form, shown again");
        copy.discard();
        android.discard();

        AiMemory mem = new AiMemory();
        UUID npc = UUID.randomUUID(), foe = UUID.randomUUID();
        AiMemory.Npc n = mem.npc(npc, "dbzenith:tyrant_lord", 100);
        AiMemory.Opponent o = n.opponent(foe, "Goku", 100);
        o.fights = 3;
        o.losses = 1;
        o.payoff[Action.PRESSURE.ordinal()] = -0.2f;
        o.tries[Action.PRESSURE.ordinal()] = 5;
        o.payoff[Action.BIG_TECHNIQUE.ordinal()] = 0.3f;
        o.tries[Action.BIG_TECHNIQUE.ordinal()] = 2;
        for (int i = 0; i < 20; i++) n.opponent(UUID.randomUUID(), "x" + i, 200 + i);
        AiMemory back = AiMemory.load(mem.save(new CompoundTag()));
        AiMemory.Npc n2 = back.peek(npc);
        h.assertTrue(n2 != null && n2.opponents.size() == 16, "remembered, bounded to 16 opponents: " + (n2 == null ? -1 : n2.opponents.size()));
        h.assertTrue(!n2.opponents.containsKey(foe), "the longest unseen forgotten first");
        AiMemory.Npc n3 = AiMemory.load(saveOne(npc, foe)).peek(npc);
        AiMemory.Opponent o3 = n3.opponents.get(foe);
        h.assertTrue(o3.fights == 3 && o3.losses == 1 && "Goku".equals(o3.name), "the record of the fights");
        h.assertTrue(o3.payoff[Action.PRESSURE.ordinal()] < 0 && o3.tries[Action.BIG_TECHNIQUE.ordinal()] == 2, "and what worked and what failed");
        h.succeed();
    }

    private static CompoundTag saveOne(UUID npc, UUID foe) {
        AiMemory mem = new AiMemory();
        AiMemory.Opponent o = mem.npc(npc, "dbzenith:tyrant_lord", 100).opponent(foe, "Goku", 100);
        o.fights = 3;
        o.losses = 1;
        o.payoff[Action.PRESSURE.ordinal()] = -0.2f;
        o.tries[Action.PRESSURE.ordinal()] = 5;
        o.tries[Action.BIG_TECHNIQUE.ordinal()] = 2;
        return mem.save(new CompoundTag());
    }
}
