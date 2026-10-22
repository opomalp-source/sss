package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.KnockOut;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.Dialogue;
import com.dbzenith.nemesis.Encounters;
import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.nemesis.NemesisPackets;
import com.dbzenith.nemesis.Progression;
import com.dbzenith.nemesis.Relationship;
import com.dbzenith.nemesis.Trait;
import com.dbzenith.nemesis.WorldSim;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.npc.ai.Personality;
import com.dbzenith.stats.Attribute;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Nemesis system (docs/NEMESIS.md): identities and memories that survive saving, relationships that move both
 * ways, dialogue that only says what happened and doesn't repeat, growth within its caps using the existing forms,
 * promotion and duplicate bodies, what knock-outs, sparing and death leave behind, encounters, the world simulation,
 * separate bonds per player, and the history screen's data.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NemesisTests {
    private static final String EMPTY = "empty";

    private NemesisTests() {}

    private static NamedFighter saiyan(String name) {
        NamedFighter c = new NamedFighter(UUID.randomUUID());
        c.name = name;
        c.type = "dbzenith:saiyan_warrior";
        c.race = "saiyan";
        c.variant = "saiyan";
        c.personality = Personality.ARROGANT;
        c.level = 1;
        return c;
    }

    /** Whatever happens (a failed test times out), these leave before the test ends: later batches reuse the spot. */
    private static void cleanup(GameTestHelper h, int timeout, net.minecraft.world.entity.Entity... es) {
        Delays.after(h, Math.max(1, timeout - 3), () -> {
            for (var e : es) {
                if (e instanceof ServerPlayer sp && !sp.isRemoved()) TestPlayers.remove(h, sp);
                else if (e != null && !e.isRemoved()) e.discard();
            }
        });
    }

    /** A fighter that thinks but never picks a fight in a test (its arena is far away). */
    private static KiFighter calm(GameTestHelper h, net.minecraft.world.entity.EntityType<? extends KiFighter> type, BlockPos at) {
        KiFighter k = h.spawn(type, at);
        k.brain().arena = new AABB(0, -500, 0, 1, -499, 1);
        return k;
    }

    // ------------------------------------------------------------------ data

    @GameTest(template = EMPTY)
    public static void identitiesAndMemoriesSurviveSaving(GameTestHelper h) {
        NemesisData d = new NemesisData();
        NamedFighter c = saiyan("Kabura");
        c.traits.add(Trait.VENGEFUL);
        c.level = 7;
        c.reputation = 120;
        c.forms.add("super_saiyan");
        c.learned.add("ki_blast");
        c.status = NamedFighter.Status.RECOVERING;
        c.returnAt = 99_000;
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        Bond b = c.player(player, "Dev");
        b.encounters = 3;
        b.wins = 2;
        b.losses = 1;
        b.shift(-30, 40);
        b.habits = new double[]{1, 0.2, 0.1, 0, 0, 0.3, 0, 0, 0};
        b.formsShown.add("super_saiyan");
        c.remember(new Memory(Memory.Kind.LOST, 500, player, "humiliated"));
        NamedFighter rival = saiyan("Gobou");
        c.character(rival).shift(-10, 30);
        d.add(c);
        d.add(rival);
        NemesisData back = NemesisData.load(d.save(new CompoundTag()));
        NamedFighter c2 = back.get(c.id);
        h.assertTrue(c2 != null && c2.name.equals("Kabura") && c2.level == 7 && c2.reputation == 120, "name, level and fame");
        h.assertTrue(c2.traits.contains(Trait.VENGEFUL) && c2.personality == Personality.ARROGANT, "temperament");
        h.assertTrue(c2.forms.contains("super_saiyan") && c2.learned.contains("ki_blast"), "forms and techniques");
        h.assertTrue(c2.status == NamedFighter.Status.RECOVERING && c2.returnAt == 99_000, "status and return");
        Bond b2 = c2.players.get(player);
        h.assertTrue(b2.wins == 2 && b2.losses == 1 && b2.encounters == 3 && b2.state == b.state, "the record and the bond: " + b2.state);
        h.assertTrue(b2.habits != null && b2.habits[0] == 1 && b2.formsShown.contains("super_saiyan"), "what each learned of the other");
        h.assertTrue(c2.last(Memory.Kind.LOST, player) != null && "humiliated".equals(c2.last(Memory.Kind.LOST, player).detail()), "the memory");
        h.assertTrue(c2.characters.containsKey(rival.id), "its bond with another character");
        h.assertTrue(back.knowing(player).size() == 1 && back.knowing(other).isEmpty(), "indexed by the player who met it");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void relationshipsMoveBothWaysWithoutFlicker(GameTestHelper h) {
        Bond b = new Bond();
        b.encounters = 1;
        h.assertTrue(Relationship.next(Relationship.STRANGER, 0, 0, 0, 0) == Relationship.STRANGER, "unmet: a stranger");
        b.shift(0, 0);
        h.assertTrue(b.state == Relationship.NEUTRAL, "met: neutral, got " + b.state);
        b.shift(-40, 50);
        h.assertTrue(b.state == Relationship.BITTER_RIVAL, "hated and contested: a bitter rival, got " + b.state);
        b.shift(-40, 10);
        h.assertTrue(b.state == Relationship.SWORN_ENEMY, "worse: a sworn enemy, got " + b.state);
        b.shift(120, 0);
        h.assertTrue(b.state == Relationship.FRIENDLY_RIVAL, "a rival can become a friend: " + b.state);
        b.spars = 2;
        b.shift(30, -40);
        h.assertTrue(b.state == Relationship.TRAINING_PARTNER, "sparring partners: " + b.state);
        b.shift(-150, 40);
        h.assertTrue(!b.state.friendly(), "and a friend an enemy: " + b.state);
        Bond edge = new Bond();
        edge.encounters = 1;
        edge.shift(0, 28);
        Relationship before = edge.state;
        edge.shift(0, 3);
        h.assertTrue(edge.state == before, "a nudge across a boundary doesn't flip it: " + before + " -> " + edge.state);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void dialogueSaysOnlyWhatHappenedAndVaries(GameTestHelper h) {
        h.assertTrue(Dialogue.loaded(), "the dialogue data loaded");
        NamedFighter c = saiyan("Rapinn");
        c.personality = Personality.BALANCED;
        Bond b = c.player(UUID.randomUUID(), "Dev");
        b.encounters = 2;
        b.wins = 1;
        b.lastResult = 1;                                                          // it won last time
        Dialogue.Context ctx = new Dialogue.Context();
        ctx.npc = c;
        ctx.bond = b;
        ctx.player = "Dev";
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            Dialogue.Line line = Dialogue.pick(Dialogue.On.GREET_AGAIN, ctx, i);
            h.assertTrue(line != null, "a line every time");
            h.assertTrue(!line.id().startsWith("again_lost_last") && !line.id().startsWith("again_spared"), "never claims what didn't happen: " + line.id());
            h.assertTrue(!b.recentLines.contains(line.id()), "not one said lately: " + line.id());
            b.said(line.id());
            seen.add(line.id());
        }
        h.assertTrue(seen.size() == 6, "varied: " + seen.size() + " different lines");
        b.wins = 3;
        b.losses = 2;
        String text = Dialogue.format("{wins} to {losses}, {player}", ctx).getString();
        h.assertTrue(text.equals("3 to 2, Dev"), "placeholders filled from the record: " + text);
        b.lastResult = -1;                                                         // now it lost last
        b.recentLines.clear();
        boolean grudge = false;
        for (int i = 0; i < 30 && !grudge; i++) {
            Dialogue.Line line = Dialogue.pick(Dialogue.On.GREET_AGAIN, ctx, i * 7L);
            grudge = line.id().startsWith("again_lost_last");
        }
        h.assertTrue(grudge, "a loss it remembers comes up");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void growthIsCappedAndUsesExistingForms(GameTestHelper h) {
        NamedFighter c = saiyan("Okran");
        c.growth = 1;
        Bond b = c.player(UUID.randomUUID(), "Dev");
        b.playerLevelSeen = 20;
        long day = 10 * 24000L;
        int gained = Progression.gain(c, 100_000, day);
        h.assertTrue(gained <= 2 && c.level <= 3, "a day's training is bounded: +" + gained + " to " + c.level);
        for (int d = 11; d < 80; d++) Progression.gain(c, 100_000, d * 24000L);
        h.assertTrue(c.level <= Progression.cap(c) && c.level <= 25, "never far past the strongest player it measured: " + c.level);
        h.assertTrue(c.forms.contains("super_saiyan"), "a Saiyan reaches Super Saiyan, the existing form: " + c.forms);
        for (String f : c.forms) {
            var form = com.dbzenith.transform.Forms.byId(f);
            h.assertTrue(form.races().contains(com.dbzenith.stats.Race.SAIYAN) && form.trigger() == com.dbzenith.transform.Form.Trigger.MANUAL && form.scale() < 2,
                    "only Saiyan forms it can take: " + f);
        }
        h.assertTrue(c.last(Memory.Kind.UNLOCKED_FORM, null) != null, "and remembers reaching it");
        for (String t : c.learned) h.assertTrue(com.dbzenith.skill.Techniques.byId(t) != null, "learned only existing techniques: " + t);
        h.succeed();
    }

    // ------------------------------------------------------------------ in the world

    @GameTest(template = EMPTY, batch = "nemesis_1")
    public static void promotionBindsOneBodyAndRefusesCopies(GameTestHelper h) {
        NemesisData data = Nemesis.data();                                              // earlier tests' leftovers (bodies discarded, characters kept) don't crowd it out
        for (NamedFighter x : data.all()) {
            if (!x.active() || x.resident()) continue;
            boolean stale = x.status != NamedFighter.Status.PRESENT || x.body == null;
            if (!stale) {
                stale = true;
                for (var l : h.getLevel().getServer().getAllLevels()) if (l.getEntity(x.body) != null) stale = false;
            }
            if (stale) x.status = NamedFighter.Status.RETIRED;
        }
        KiFighter k = calm(h, ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        NamedFighter c = Nemesis.promote(k, "test");
        h.assertTrue(c != null && c.id.equals(k.nemesisId()), "an ordinary soldier becomes a named character");
        h.assertTrue(k.hasCustomName() && k.isCustomNameVisible() && k.getCustomName().getString().startsWith(c.name), "its name over its head: " + k.getCustomName().getString());
        h.assertTrue(k.getUUID().equals(c.body), "this body is its body");
        CompoundTag tag = new CompoundTag();
        k.saveWithoutId(tag);
        tag.remove("UUID");
        KiFighter copy = ModNpcs.KI_SOLDIER.get().create(h.getLevel());
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        copy.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 1, 2))));
        h.getLevel().addFreshEntity(copy);
        h.assertTrue(!copy.isAddedToWorld() && h.getLevel().getEntity(copy.getUUID()) == null, "a second body of the same character is refused");
        c.status = NamedFighter.Status.DEAD;
        KiFighter ghost = ModNpcs.KI_SOLDIER.get().create(h.getLevel());
        ghost.load(tag);
        ghost.setUUID(UUID.randomUUID());
        c.body = null;
        h.getLevel().addFreshEntity(ghost);
        h.assertTrue(h.getLevel().getEntity(ghost.getUUID()) == null || !ghost.isAddedToWorld(), "nor a body of the dead");
        k.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "nemesis_2", timeoutTicks = 100)
    public static void knockoutsSparingAndEscapesAreRemembered(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 6))));
        KiFighter k = calm(h, ModNpcs.SAIYAN_WARRIOR.get(), new BlockPos(1, 1, 1));
        cleanup(h, 100, k, p);
        NamedFighter c = Nemesis.identify(k);
        h.assertTrue(c != null && c.race.equals("saiyan"), "a Saiyan Warrior is named on sight");
        Nemesis.talk(k, p);                                                        // a word first (right-click)
        h.assertTrue(c.last(Memory.Kind.MET, p.getUUID()) != null && !c.players.get(p.getUUID()).recentLines.isEmpty(), "talking to it: it answers, and you've met");
        c.personality = Personality.HONORABLE;
        k.brain().setPersonality(Personality.HONORABLE);
        k.setLastHurtByMob(p);                                                     // as the blow that downed it would
        KnockOut.knockOut(k, p.damageSources().playerAttack(p));
        Bond b = c.players.get(p.getUUID());
        h.assertTrue(b != null && b.losses == 1 && b.lastResult == -1 && b.rivalry > 0, "it lost to the player, and remembers");
        h.assertTrue(c.last(Memory.Kind.LOST, p.getUUID()) != null, "the memory");
        int level = c.level;
        double affinity = b.affinity;
        boolean handled = Nemesis.onBledOut(k, false);
        h.assertTrue(handled && k.isRemoved(), "left alive, it gets away");
        h.assertTrue(c.status == NamedFighter.Status.RECOVERING && c.returnAt > h.getLevel().getGameTime(), "recovering, back later");
        h.assertTrue(b.sparedThem == 1 && b.affinity > affinity, "an honorable fighter respects being spared: " + affinity + " -> " + b.affinity);
        h.assertTrue(c.level >= level, "and grew from the fight");
        KnockOut.standUp(k);

        KiFighter brute = calm(h, ModNpcs.KI_SOLDIER.get(), new BlockPos(3, 1, 1));   // an ordinary soldier who beats a player becomes someone
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setBody(1);
        KnockOut.knockOut(p, brute.damageSources().mobAttack(brute));
        NamedFighter winner = Nemesis.of(brute);
        h.assertTrue(winner != null && winner.players.get(p.getUUID()).wins == 1, "the soldier who beat the player is now named");
        if (winner.temper().honor >= 0.65) h.assertTrue(Nemesis.spares(brute, p), "an honorable winner spares");
        KnockOut.standUp(p);
        brute.discard();
        TestPlayers.remove(h, p);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "nemesis_3")
    public static void deathIsFinalOrSurvivedByChance(GameTestHelper h) {
        NemesisData d = Nemesis.data();
        NamedFighter lucky = saiyan("Leekan"), doomed = saiyan("Parsnov");
        d.add(lucky);
        d.add(doomed);
        long now = h.getLevel().getGameTime();
        h.assertTrue(Nemesis.resolveDeath(lucky, null, now, 2.0), "at a certain chance it survives");
        h.assertTrue(lucky.status == NamedFighter.Status.RECOVERING && lucky.survivedDeaths == 1 && lucky.goal == NamedFighter.Goal.REVENGE,
                "comes back for revenge, later");
        h.assertTrue(lucky.last(Memory.Kind.SURVIVED_DEATH, null) != null, "and remembers dying");
        h.assertTrue(!Nemesis.resolveDeath(doomed, null, now, 0.0), "at none it doesn't");
        h.assertTrue(doomed.status == NamedFighter.Status.DEAD && !doomed.active(), "dead and gone");
        lucky.survivedDeaths = 3;
        h.assertTrue(!Nemesis.resolveDeath(lucky, null, now, 2.0), "nobody cheats death forever");
        lucky.status = NamedFighter.Status.RETIRED;
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "nemesis_4", timeoutTicks = 120)
    public static void returningRivalsArriveFairlyAndLeave(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        PlayerData pd = ModCapabilities.getOrThrow(p);
        cleanup(h, 120, p);
        pd.setAttribute(Attribute.STRENGTH, pd.getAttribute(Attribute.STRENGTH) + 2000);
        pd.recomputeIfStale();
        NemesisData d = Nemesis.data();
        NamedFighter c = saiyan("Endivo");
        c.traits.add(Trait.VENGEFUL);
        c.level = 500;                                                             // far ahead (from training): the arrival must stay fair
        c.status = NamedFighter.Status.AWAY;
        c.returnAt = 0;
        Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
        b.encounters = 2;
        b.losses = 1;
        b.lastResult = -1;
        b.shift(-30, 40);
        d.add(c);
        long now = h.getLevel().getGameTime();
        Encounters.Choice ch = Encounters.choose(p, now, false);
        h.assertTrue(ch != null && ch.c() == c && ch.kind() == Encounters.Kind.REVENGE, "a vengeful loser comes for revenge: " + (ch == null ? "none" : ch.kind()));
        KiFighter body = Encounters.start(ch, p, now);
        if (body == null) {                                                         // no safe ground here: the rest needs a body
            c.status = NamedFighter.Status.RETIRED;
            TestPlayers.remove(h, p);
            h.succeed();
            return;
        }
        double dist = body.distanceTo(p);
        h.assertTrue(dist >= 8 && dist <= 24, "at a distance, not in your face: " + dist);
        h.assertTrue(body.level().hasChunkAt(body.blockPosition()), "on loaded ground");
        int fair = KiFighter.levelFor(com.dbzenith.stats.StatCalculator.fullPower(pd));
        h.assertTrue(body.fighterLevel() <= Math.ceil(fair * 1.3) + 1, "never unfairly far ahead: " + body.fighterLevel() + " vs " + fair);
        h.assertTrue(body.getTarget() == p, "it came for them");
        h.assertTrue(Encounters.choose(p, now, false) == null, "and only one of it at a time");
        body.brain().arena = new AABB(0, -500, 0, 1, -499, 1);
        body.setTarget(null);
        body.leaveAt = now + 1;
        Delays.after(h, 30, () -> {
            h.assertTrue(body.isRemoved() && c.body == null && c.status == NamedFighter.Status.AWAY, "its business done, it leaves again");
            c.status = NamedFighter.Status.RETIRED;
            TestPlayers.remove(h, p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void worldSimulationIsDeterministicAndBounded(GameTestHelper h) {
        NemesisData a = new NemesisData();
        for (int i = 0; i < 6; i++) {
            NamedFighter c = saiyan("Sim" + i);
            c.type = i % 2 == 0 ? "dbzenith:ki_soldier" : "dbzenith:namekian_warrior";   // enemies by faction
            c.race = i % 2 == 0 ? "" : "namekian";
            c.variant = i % 2 == 0 ? "" : "warrior_clan";
            c.status = NamedFighter.Status.AWAY;
            c.level = 2 + i;
            c.player(UUID.nameUUIDFromBytes(("p" + i).getBytes()), "P").playerLevelSeen = 10;
            a.add(c);
        }
        NemesisData b = NemesisData.load(a.save(new CompoundTag()));
        for (int t = 1; t <= 200; t++) {
            WorldSim.step(a, t * 1000L);
            WorldSim.step(b, t * 1000L);
        }
        h.assertTrue(a.save(new CompoundTag()).equals(b.save(new CompoundTag())), "the same world plays out the same way");
        int duels = 0;
        for (NamedFighter c : a.all()) {
            h.assertTrue(c.level <= Progression.cap(c), "bounded growth: " + c.name + " " + c.level);
            for (Memory m : c.history) if (m.kind() == Memory.Kind.NPC_WON || m.kind() == Memory.Kind.NPC_LOST) duels++;
            h.assertTrue(c.history.size() <= 32, "bounded history");
        }
        h.assertTrue(duels > 0, "characters fought each other without the player: " + duels);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "nemesis_5")
    public static void eachPlayerHasTheirOwnBondAndSeesOnlyTheirStory(GameTestHelper h) {
        ServerPlayer a = TestPlayers.create(h), b = TestPlayers.create(h);
        NemesisData d = Nemesis.data();
        NamedFighter c = saiyan("Chardon");
        c.status = NamedFighter.Status.AWAY;
        Bond ba = c.player(a.getUUID(), a.getGameProfile().getName()), bb = c.player(b.getUUID(), b.getGameProfile().getName());
        ba.encounters = bb.encounters = 2;
        ba.shift(70, 10);
        bb.shift(-70, 60);
        ba.formsShown.add("super_saiyan");
        c.forms.add("super_saiyan");
        c.forms.add("super_saiyan_2");
        c.remember(new Memory(Memory.Kind.SPARED_BY, 10, a.getUUID(), ""));
        c.remember(new Memory(Memory.Kind.WON, 20, b.getUUID(), "secret"));
        d.add(c);
        h.assertTrue(ba.state.friendly() || ba.state == Relationship.RESPECTFUL, "one player's friend: " + ba.state);
        h.assertTrue(bb.state == Relationship.SWORN_ENEMY, "another's sworn enemy: " + bb.state);
        NemesisPackets.Entry ea = null;
        for (NemesisPackets.Entry e : NemesisPackets.entries(a)) if (e.id().equals(c.id)) ea = e;
        h.assertTrue(ea != null, "on the first player's list");
        for (String m : ea.history()) h.assertTrue(!m.contains("secret"), "never another player's story: " + m);
        h.assertTrue(ea.forms().equals(List.of("super_saiyan")), "only the forms they saw it use: " + ea.forms());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NemesisPackets.Data.encode(new NemesisPackets.Data(List.of(ea)), buf);
        NemesisPackets.Entry back = NemesisPackets.Data.decode(buf).entries().get(0);
        h.assertTrue(back.equals(ea), "the screen's data survives the wire");
        c.status = NamedFighter.Status.RETIRED;
        TestPlayers.remove(h, a);
        TestPlayers.remove(h, b);
        h.succeed();
    }
}
