package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.living.Companions;
import com.dbzenith.living.Conversations;
import com.dbzenith.living.Fame;
import com.dbzenith.living.LivingData;
import com.dbzenith.living.Mood;
import com.dbzenith.living.Occupation;
import com.dbzenith.living.Poi;
import com.dbzenith.living.Resident;
import com.dbzenith.living.Residents;
import com.dbzenith.living.Rumor;
import com.dbzenith.living.Rumors;
import com.dbzenith.living.Schedules;
import com.dbzenith.living.Settlement;
import com.dbzenith.living.TownBuilder;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.nemesis.Trait;
import com.dbzenith.npc.ai.Faction;
import com.dbzenith.npc.ai.Personality;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The living world (docs/LIVING_WORLD.md): what it saves, schedules shaped by trade, temperament, mood and trouble,
 * the town builder's places, residents as townsfolk of the Nemesis system, routines that take them where their day
 * says, server-checked conversations, rumors made from real events and passed on, and a town's answer to an attack.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingTests {
    private static final String EMPTY = "empty";

    private LivingTests() {}

    /** A town on paper (no blocks): its places at test positions, registered with the world's living data. */
    private static Settlement town(GameTestHelper h, BlockPos... places) {
        Settlement s = new Settlement(UUID.randomUUID());
        s.name = "Testville";
        s.dimension = h.getLevel().dimension().location().toString();
        s.center = h.absolutePos(new BlockPos(1, 1, 1));
        s.radius = 24;
        Poi.Kind[] kinds = {Poi.Kind.HOME, Poi.Kind.PLAZA, Poi.Kind.MARKET, Poi.Kind.TAVERN, Poi.Kind.DOJO, Poi.Kind.SHOP, Poi.Kind.PATROL, Poi.Kind.GATE};
        BlockPos at = places.length == 0 ? new BlockPos(1, 1, 1) : places[0];
        for (Poi.Kind k : kinds) s.pois.add(new Poi(k, h.absolutePos(at), k.id()));
        LivingData.get(h.getLevel().getServer()).add(s);
        return s;
    }

    private static Resident resident(GameTestHelper h, Settlement s, Occupation o, BlockPos at, long seed) {
        NamedFighter c = Residents.create(Nemesis.data(), s, o, s.pois.get(0).pos(), h.getLevel().getGameTime(), seed);
        return Residents.spawnBody(h.getLevel(), c, h.absolutePos(at));
    }

    /** Whatever happens, the bodies, players and town are gone before the test ends (later batches reuse the spot). */
    private static void cleanup(GameTestHelper h, int timeout, Settlement s, Entity... es) {
        Delays.after(h, Math.max(1, timeout - 3), () -> forget(h, s, es));
    }

    private static void forget(GameTestHelper h, Settlement s, Entity... es) {
        for (Entity e : es) {
            if (e instanceof ServerPlayer sp && !sp.isRemoved()) TestPlayers.remove(h, sp);
            else if (e != null && !e.isRemoved()) e.discard();
        }
        if (s == null) return;
        for (NamedFighter c : Residents.of(s)) c.status = NamedFighter.Status.DEAD;
        LivingData.get(h.getLevel().getServer()).remove(s.id);
    }

    // ------------------------------------------------------------------ data

    @GameTest(template = EMPTY)
    public static void livingDataSurvivesSaving(GameTestHelper h) {
        LivingData d = new LivingData();
        Settlement s = new Settlement(UUID.randomUUID());
        s.name = "Ginger Town";
        s.center = new BlockPos(100, 64, -40);
        s.pois.add(new Poi(Poi.Kind.HOME, new BlockPos(101, 65, -39), "home 1"));
        s.pois.add(new Poi(Poi.Kind.DOJO, new BlockPos(110, 65, -30), "master"));
        s.unreachable.add(1);
        s.alarmUntil = 5000;
        d.add(s);
        UUID player = UUID.randomUUID();
        Rumor r = new Rumor(UUID.randomUUID(), Rumor.Kind.LOST_TO_PLAYER, UUID.randomUUID(), "Kabura", player, "Dev", "", s.id, 1234);
        d.add(r);
        d.addFame(player, 120);
        d.setFameTier(player, Fame.Tier.KNOWN.ordinal());
        d.townPlaced = true;
        LivingData back = LivingData.load(d.save(new CompoundTag()));
        Settlement s2 = back.settlement(s.id);
        h.assertTrue(s2 != null && s2.name.equals("Ginger Town") && s2.center.equals(s.center), "the town");
        h.assertTrue(s2.pois.size() == 2 && s2.pois.get(1).label().equals("master") && s2.pois.get(1).kind() == Poi.Kind.DOJO, "its places");
        h.assertTrue(s2.unreachable.contains(1) && s2.alarmUntil == 5000, "what it learned and its alert");
        h.assertTrue(back.rumor(r.id()) != null && back.rumor(r.id()).subjectName().equals("Kabura") && back.rumor(r.id()).tick() == 1234, "the rumor");
        h.assertTrue(back.fame(player) == 120 && back.fameTier(player) == Fame.Tier.KNOWN.ordinal() && back.townPlaced, "fame and the placed flag");

        NemesisData nd = new NemesisData();
        NamedFighter c = new NamedFighter(UUID.randomUUID());
        c.name = "Nutmeg";
        c.occupation = "merchant";
        c.settlement = s.id;
        c.home = new BlockPos(101, 65, -39).asLong();
        c.look = 5;
        c.mood = "happy";
        c.moodUntil = 777;
        c.knowledge.add(r.id());
        c.traits.add(Trait.GREEDY);
        Bond b = c.player(player, "Dev");
        b.talks = 4;
        b.told.add(r.id().toString());
        b.flags.add("asked_training");
        nd.add(c);
        NamedFighter c2 = NemesisData.load(nd.save(new CompoundTag())).get(c.id);
        h.assertTrue(c2.resident() && c2.occupation.equals("merchant") && s.id.equals(c2.settlement) && c2.home == c.home && c2.look == 5, "the resident's place");
        h.assertTrue(c2.mood.equals("happy") && c2.moodUntil == 777 && c2.knowledge.contains(r.id()) && c2.has(Trait.GREEDY), "mood, knowledge, traits");
        Bond b2 = c2.players.get(player);
        h.assertTrue(b2.talks == 4 && b2.told.contains(r.id().toString()) && b2.flags.contains("asked_training"), "what was said");
        h.succeed();
    }

    // ------------------------------------------------------------------ schedules

    /** The game's day time for an hour (sunrise is 6). */
    private static long at(int hour) {
        return Math.floorMod(hour - 6, 24) * 1000L + 500;
    }

    private static NamedFighter person(Occupation o, Personality p, Trait... traits) {
        NamedFighter c = new NamedFighter(new UUID(0x1111_2222L, 0x3333_4444L + o.ordinal()));
        c.occupation = o.id();
        c.personality = p;
        c.traits.addAll(List.of(traits));
        return c;
    }

    @GameTest(template = EMPTY)
    public static void schedulesShapeTheDay(GameTestHelper h) {
        h.assertTrue(Schedules.loaded(), "schedules load from data");
        NamedFighter citizen = person(Occupation.CITIZEN, Personality.BALANCED);
        NamedFighter guard = person(Occupation.GUARD, Personality.DEFENSIVE);
        NamedFighter student = person(Occupation.STUDENT, Personality.BALANCED);
        NamedFighter merchant = person(Occupation.MERCHANT, Personality.BALANCED);
        // a day that is no day off for these (one day in seven is)
        long day = 0;
        while (day < 30 && (offFor(citizen, day) || offFor(student, day) || offFor(merchant, day))) day++;
        long base = day * 24000;
        h.assertTrue(Schedules.activity(citizen, base + at(2), Mood.RELAXED, false) == Schedules.Activity.SLEEP, "asleep at 2 am");
        h.assertTrue(Schedules.activity(student, base + at(9), Mood.RELAXED, false) == Schedules.Activity.TRAIN, "a student trains in the morning");
        h.assertTrue(Schedules.activity(merchant, base + at(10), Mood.RELAXED, false) == Schedules.Activity.WORK, "a merchant minds the shop");
        h.assertTrue(Schedules.activity(guard, base + at(12), Mood.RELAXED, false) == Schedules.Activity.PATROL, "a guard is on duty by day");
        h.assertTrue(Schedules.activity(citizen, base + at(10), Mood.RELAXED, true) == Schedules.Activity.HOME, "trouble sends civilians home");
        h.assertTrue(Schedules.activity(guard, base + at(2), Mood.RELAXED, true) == Schedules.Activity.PATROL, "and fighters out, even at night");
        NamedFighter shy = person(Occupation.CITIZEN, Personality.BALANCED, Trait.SHY);
        long shyDay = 0;
        while (shyDay < 30 && offFor(shy, shyDay)) shyDay++;
        h.assertTrue(Schedules.activity(shy, shyDay * 24000 + at(15), Mood.RELAXED, false) == Schedules.Activity.HOME, "the shy skip the afternoon crowd");
        h.assertTrue(Schedules.activity(citizen, base + at(15), Mood.RELAXED, false) == Schedules.Activity.SOCIAL, "others mix in the afternoon");
        h.assertTrue(Schedules.activity(citizen, base + at(15), Mood.WORRIED, false) == Schedules.Activity.HOME, "the worried stay in");
        int off = 0;
        for (long d = 0; d < 70; d++) if (offFor(citizen, d)) off++;
        h.assertTrue(off >= 3 && off <= 20, "a day off now and then: " + off + " in 70");
        h.succeed();
    }

    private static boolean offFor(NamedFighter c, long day) {
        return Schedules.dayOff(c, day);
    }

    // ------------------------------------------------------------------ residents

    @GameTest(template = EMPTY, batch = "living_1", timeoutTicks = 60)
    public static void residentsAreTownsfolkOfTheNemesisSystem(GameTestHelper h) {
        Settlement s = town(h);
        Resident cook = resident(h, s, Occupation.COOK, new BlockPos(1, 1, 1), 101);
        Resident guard = resident(h, s, Occupation.GUARD, new BlockPos(2, 1, 1), 102);
        cleanup(h, 60, s, cook, guard);
        NamedFighter cc = cook.character(), cg = guard.character();
        h.assertTrue(cc != null && cg != null && cc.resident() && s.id.equals(cc.settlement), "named characters of the town");
        h.assertTrue(cook.aiProfile().faction() == Faction.TOWNSFOLK && Faction.allied(cook, guard), "townsfolk stand together");
        h.assertTrue(Faction.TOWNSFOLK.enemyOf(Faction.FRIEZA_FORCE) && !Faction.TOWNSFOLK.enemyOf(Faction.NAMEKIANS), "against invaders only");
        h.assertTrue(!cook.aiMayFight() && guard.aiMayFight(), "civilians don't fight; guards do");
        h.assertTrue(cook.skin().equals("townsfolk_cook") && guard.skin().equals("townsfolk_guard"), "dressed for their trade");
        h.assertTrue(cg.learned.contains("ki_blast") && cg.level > cc.level, "a guard knows its trade's technique and is stronger");
        NemesisData d = Nemesis.data();
        int before = d.activeCount();
        NamedFighter extra = Residents.create(d, s, Occupation.CITIZEN, s.pois.get(0).pos(), h.getLevel().getGameTime(), 103);
        h.assertTrue(d.activeCount() == before, "townsfolk don't take Nemesis rivals' places");
        extra.status = NamedFighter.Status.DEAD;
        h.assertTrue(!cook.isPreventingPlayerRest(null), "they don't keep you from sleeping");
        forget(h, s, cook, guard);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "living_2", timeoutTicks = 260)
    public static void residentsWalkWhereTheirDaySays(GameTestHelper h) {
        Settlement s = town(h, new BlockPos(1, 1, 7));
        for (int x = 0; x <= 2; x++)                                                     // a corridor to walk
            for (int z = 0; z <= 8; z++) {
                h.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
                h.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.AIR);
                h.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.AIR);
            }
        Resident r = resident(h, s, Occupation.CITIZEN, new BlockPos(1, 1, 1), 201);
        cleanup(h, 260, s, r);
        BlockPos dest = h.absolutePos(new BlockPos(1, 1, 7));
        h.succeedWhen(() -> {
            double d = Math.sqrt(r.distanceToSqr(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5));
            h.assertTrue(d < 2.2, "on its way: " + String.format("%.1f", d) + " blocks, " + r.activity.id());
            forget(h, s, r);
        });
    }

    // ------------------------------------------------------------------ conversations

    @GameTest(template = EMPTY, batch = "living_3", timeoutTicks = 80)
    public static void conversationsAreBranchingAndServerChecked(GameTestHelper h) {
        h.assertTrue(Conversations.loaded() && Conversations.has("root") && Conversations.has("news"), "conversations load from data");
        Settlement s = town(h);
        Resident r = resident(h, s, Occupation.CITIZEN, new BlockPos(1, 1, 1), 301);
        ServerPlayer p = TestPlayers.create(h);
        cleanup(h, 80, s, r, p);
        BlockPos near = h.absolutePos(new BlockPos(2, 1, 1));
        p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        Conversations.open(r, p);
        NamedFighter c = r.character();
        Bond b = c.players.get(p.getUUID());
        h.assertTrue(Conversations.talking(p, r) && r.talkingTo != null, "a conversation opens and it stops for it");
        h.assertTrue(b != null && b.talks == 1 && c.last(Memory.Kind.MET, p.getUUID()) != null, "they have met");
        List<String> opts = Conversations.offered(p);
        h.assertTrue(opts.contains("who") && opts.contains("work") && opts.contains("bye"), "root options: " + opts);
        h.assertTrue(!opts.contains("spar") && !opts.contains("master") && !opts.contains("apologize"), "only what applies: " + opts);
        Conversations.choose(p, r.getId(), 99);
        h.assertTrue(Conversations.offered(p).equals(opts), "an option never offered changes nothing");
        Conversations.choose(p, r.getId() + 1, opts.indexOf("who"));
        h.assertTrue(Conversations.offered(p).equals(opts), "nor one for another body");
        Conversations.choose(p, r.getId(), opts.indexOf("who"));
        h.assertTrue(Conversations.offered(p).contains("back"), "a choice leads on: " + Conversations.offered(p));
        Conversations.choose(p, r.getId(), Conversations.offered(p).indexOf("back"));
        h.assertTrue(Conversations.offered(p).contains("who"), "and back");
        BlockPos far = h.absolutePos(new BlockPos(1, 1, 30));
        p.teleportTo(far.getX(), far.getY(), far.getZ());
        Conversations.choose(p, r.getId(), 0);
        h.assertTrue(!Conversations.talking(p, r) && r.talkingTo == null, "walking away ends it");
        p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        s.wanted.add(p.getUUID());
        s.alarmUntil = h.getLevel().getGameTime() + 1000;
        Conversations.open(r, p);
        List<String> angry = Conversations.offered(p);
        h.assertTrue(angry.contains("apologize") && !angry.contains("news"), "a wanted player gets another welcome: " + angry);
        Conversations.choose(p, r.getId(), angry.indexOf("apologize"));
        h.assertTrue(b.flags.contains("apologized"), "the choice is remembered");
        forget(h, s, r, p);
        h.succeed();
    }

    // ------------------------------------------------------------------ rumors

    @GameTest(template = EMPTY, batch = "living_4", timeoutTicks = 60)
    public static void rumorsComeFromRealEventsAndSpread(GameTestHelper h) {
        Settlement s = town(h);
        Resident a = resident(h, s, Occupation.MERCHANT, new BlockPos(1, 1, 1), 401);
        Resident b = resident(h, s, Occupation.CITIZEN, new BlockPos(2, 1, 1), 402);
        cleanup(h, 60, s, a, b);
        LivingData ld = LivingData.get(h.getLevel().getServer());
        NamedFighter fighter = new NamedFighter(UUID.randomUUID());
        fighter.name = "Kabura";
        fighter.type = "dbzenith:saiyan_warrior";
        fighter.dimension = s.dimension;
        fighter.pos = s.center.asLong();
        UUID player = UUID.randomUUID();
        fighter.player(player, "Dev");
        int fame = ld.fame(player);
        long now = h.getLevel().getGameTime();
        fighter.remember(new Memory(Memory.Kind.LOST, now, player, ""));
        Rumor r = null;
        for (Rumor x : ld.rumors()) if (fighter.id.equals(x.subject()) && x.kind() == Rumor.Kind.LOST_TO_PLAYER) r = x;
        h.assertTrue(r != null && player.equals(r.object()) && s.id.equals(r.origin()), "a defeat becomes news where it happened");
        NamedFighter ca = a.character(), cb = b.character();
        h.assertTrue(ca.knowledge.contains(r.id()) && cb.knowledge.contains(r.id()), "the town hears first");
        h.assertTrue(ld.fame(player) > fame, "beating a known fighter makes a name");
        String told = Rumors.line(r, UUID.randomUUID(), "Someone", ld, now, 1);
        String you = Rumors.line(r, player, "Dev", ld, now, 1);
        h.assertTrue(told.contains("Kabura") && you.contains("Kabura") && !told.equals(you), "told about them, or to them: " + you);
        Rumor news = Rumors.add(ld, Rumor.Kind.TRAINING_HARD, ca.id, ca.name, null, "", "", s.id, now + 1, List.of(ca));
        h.assertTrue(!cb.knowledge.contains(news.id()), "not everyone knows everything");
        h.assertTrue(Rumors.exchange(ld, ca, cb) == news && cb.knowledge.contains(news.id()), "it passes on in a chat");
        forget(h, s, a, b);
        h.succeed();
    }

    // ------------------------------------------------------------------ trouble

    @GameTest(template = EMPTY, batch = "living_5", timeoutTicks = 60)
    public static void anAttackAlarmsTheTown(GameTestHelper h) {
        Settlement s = town(h);
        Resident victim = resident(h, s, Occupation.CITIZEN, new BlockPos(1, 1, 1), 501);
        Resident guard = resident(h, s, Occupation.GUARD, new BlockPos(2, 1, 2), 502);
        ServerPlayer p = TestPlayers.create(h);
        cleanup(h, 60, s, victim, guard, p);
        BlockPos near = h.absolutePos(new BlockPos(1, 1, 3));
        p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        long now = h.getLevel().getGameTime();
        victim.hurt(h.getLevel().damageSources().playerAttack(p), 1f);
        LivingData ld = LivingData.get(h.getLevel().getServer());
        h.assertTrue(s.alarmed(now + 1), "the town is on alert");
        h.assertTrue(s.wanted.contains(p.getUUID()), "and wants the one who did it");
        h.assertTrue(victim.fleeing() && !guard.fleeing(), "the civilian runs");
        boolean news = false;
        for (Rumor r : ld.rumors()) news |= r.kind() == Rumor.Kind.TOWN_ATTACKED && p.getUUID().equals(r.subject());
        h.assertTrue(news, "it is news");
        h.assertTrue(com.dbzenith.living.Living.wanted(guard, p), "the guard goes for them");
        Bond seen = guard.character().players.get(p.getUUID());
        h.assertTrue(seen != null && seen.affinity < 0, "those who saw it think less of them");
        h.assertTrue(Mood.of(victim.character(), h.getLevel()) == Mood.WORRIED && Mood.of(guard.character(), h.getLevel()) == Mood.ANGRY, "fear and anger");
        forget(h, s, victim, guard, p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the town builder

    @GameTest(template = EMPTY, batch = "living_town", timeoutTicks = 200)
    public static void theTownBuilderMakesReachablePlaces(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int x = 40_000, z = 40_000;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        Settlement s = TownBuilder.build(level, new BlockPos(x, y, z), "Ginger Town");
        h.assertTrue(s.of(Poi.Kind.HOME).size() == 7 && s.of(Poi.Kind.DOJO).size() == 5 && s.of(Poi.Kind.GATE).size() == 2, "homes, the dojo, the gates");
        h.assertTrue(s.of(Poi.Kind.SHOP).size() == 1 && s.of(Poi.Kind.TAVERN).size() == 5 && s.of(Poi.Kind.PATROL).size() == 4
                && s.of(Poi.Kind.PLAZA).size() == 5 && s.of(Poi.Kind.MARKET).size() == 4, "the shop, the restaurant, the round, the plaza");
        List<String> bad = new ArrayList<>();
        for (Poi p : s.pois) {
            BlockPos at = p.pos();
            boolean floor = !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
            boolean room = level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
            if (!floor || !room) bad.add(p.kind().id() + " " + p.label() + " " + at.toShortString());
        }
        h.assertTrue(bad.isEmpty(), "every place has a floor and room to stand: " + bad);
        int beds = 0;
        for (Poi home : s.of(Poi.Kind.HOME))
            for (BlockPos q : BlockPos.betweenClosed(home.pos().offset(-3, -1, -3), home.pos().offset(3, 1, 3)))
                if (level.getBlockState(q).getBlock() instanceof net.minecraft.world.level.block.BedBlock) beds++;
        h.assertTrue(beds == 7 * 4, "two beds a home: " + beds / 2);
        h.succeed();
    }

    // ------------------------------------------------------------------ stage 2: companions, lessons, errands, travelers

    @GameTest(template = EMPTY, batch = "living_6", timeoutTicks = 200)
    public static void companionsDecideForThemselvesAndFollowOrders(GameTestHelper h) {
        Settlement s = town(h);
        Resident guard = resident(h, s, Occupation.GUARD, new BlockPos(1, 1, 1), 601);
        Resident cook = resident(h, s, Occupation.COOK, new BlockPos(2, 1, 1), 602);
        guard.brain().arena = new net.minecraft.world.phys.AABB(0, -500, 0, 1, -499, 1);         // no fights with other tests' fighters nearby
        ServerPlayer p = TestPlayers.create(h);
        cleanup(h, 200, s, guard, cook, p);
        BlockPos near = h.absolutePos(new BlockPos(1, 1, 3));
        p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        long now = h.getLevel().getGameTime();
        NamedFighter cg = guard.character(), cc = cook.character();
        Bond b = cg.player(p.getUUID(), "Dev");
        h.assertTrue(Companions.refusal(cc, cc.player(p.getUUID(), "Dev"), p, s, now).equals("no_fighter"), "a cook is no fighter");
        h.assertTrue(Companions.refusal(cg, b, p, s, now).equals("stranger"), "nobody follows a stranger");
        b.shift(40, 0);
        h.assertTrue(Companions.refusal(cg, b, p, s, now).equals("none"), "a guard who trusts you will");
        Conversations.open(guard, p);
        h.assertTrue(Conversations.offered(p).contains("recruit"), "you can ask: " + Conversations.offered(p));
        Conversations.choose(p, guard.getId(), Conversations.offered(p).indexOf("recruit"));
        Conversations.choose(p, guard.getId(), Conversations.offered(p).indexOf("join"));
        h.assertTrue(Companions.of(cg, p.getUUID()) && Companions.mode(cg) == Companions.Mode.FOLLOW, "it joins and follows");
        h.assertTrue(!guard.canAttack(p), "it never turns on you");
        Conversations.close(p, false);
        Conversations.open(guard, p);
        Conversations.choose(p, guard.getId(), Conversations.offered(p).indexOf("orders"));
        Conversations.choose(p, guard.getId(), Conversations.offered(p).indexOf("wait"));
        h.assertTrue(Companions.mode(cg) == Companions.Mode.WAIT && cg.waitPos == guard.blockPosition().asLong(), "it waits where told");
        Conversations.close(p, false);
        Companions.command(cg, guard, Companions.Mode.FOLLOW);
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 40; z++) h.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos far = h.absolutePos(new BlockPos(1, 1, 38));
        p.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        h.succeedWhen(() -> {
            h.assertTrue(guard.distanceTo(p) < 8, "it catches up: " + String.format("%.1f", guard.distanceTo(p)));
            TestPlayers.remove(h, p);
            h.assertTrue(Companions.mode(cg) == Companions.Mode.HOME, "you log out: it goes home");
            h.assertTrue(Companions.of(cg, p.getUUID()), "and is still your companion");
            Companions.leave(h.getLevel().getServer(), cg, "living.dbzenith.companion.parted", now);
            for (int x = 0; x <= 2; x++)
                for (int z = 0; z <= 40; z++) h.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.AIR);
            forget(h, s, guard, cook);
        });
    }

    @GameTest(template = EMPTY, batch = "living_7", timeoutTicks = 200)
    public static void mastersTeachExistingTechniquesStepByStep(GameTestHelper h) {
        Settlement s = town(h);
        Resident master = resident(h, s, Occupation.MASTER, new BlockPos(1, 1, 1), 701);
        ServerPlayer p = TestPlayers.create(h);
        cleanup(h, 200, s, master, p);
        BlockPos near = h.absolutePos(new BlockPos(2, 1, 2));
        p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        var d = com.dbzenith.data.ModCapabilities.getOrThrow(p);
        d.setRace(com.dbzenith.stats.Race.HUMAN);
        NamedFighter c = master.character();
        Bond b = c.player(p.getUUID(), "Dev");
        h.assertTrue(com.dbzenith.living.Lessons.refusal(c, b, p, s).equals("stranger"), "no lessons for a stranger");
        b.shift(30, 0);
        var next = com.dbzenith.living.Lessons.teachable(c, b, d);
        h.assertTrue(!next.isEmpty(), "something to teach");
        var t = next.get(0);
        h.assertTrue(com.dbzenith.living.Lessons.CURRICULUM.contains(t) && !d.knows(t.id()), "an existing technique you don't know: " + t.id());
        h.assertTrue(com.dbzenith.living.Lessons.start(master, c, b, p), "the lesson begins");
        h.assertTrue(com.dbzenith.living.Lessons.step(b) == com.dbzenith.living.Lessons.Step.PRACTICE, "after the demonstration: ki control");
        boolean[] sparred = {false};
        h.succeedWhen(() -> {
            d.setCharging(true);                                                       // holding ki near the master
            var step = com.dbzenith.living.Lessons.step(b);
            if (step == com.dbzenith.living.Lessons.Step.TEST && !sparred[0]) {
                h.assertTrue(com.dbzenith.living.Lessons.test(master, c, b, p) && p.getUUID().equals(master.sparWith), "the test is a spar");
                master.sparWith = null;
                master.setTarget(null);
                b.spars++;                                                             // the spar ends
                sparred[0] = true;
            }
            h.assertTrue(d.knows(t.id()), "learned " + t.id() + "? step " + step + ", practised " + com.dbzenith.living.Lessons.practiced(p.getUUID(), c.id));
            h.assertTrue(b.flags.contains("taught:" + t.id()) && !com.dbzenith.living.Lessons.teachable(c, b, d).contains(t), "never taught twice");
            d.setCharging(false);
            forget(h, s, master, p);
        });
    }

    @GameTest(template = EMPTY, batch = "living_8", timeoutTicks = 60)
    public static void errandsComeFromTheTownAndPayOnce(GameTestHelper h) {
        Settlement s = town(h);
        Resident cook = resident(h, s, Occupation.COOK, new BlockPos(1, 1, 1), 801);
        Resident friend = resident(h, s, Occupation.CITIZEN, new BlockPos(2, 1, 2), 802);
        ServerPlayer p = TestPlayers.create(h);
        cleanup(h, 60, s, cook, friend, p);
        BlockPos near = h.absolutePos(new BlockPos(1, 1, 3));
        p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        LivingData ld = LivingData.get(h.getLevel().getServer());
        long now = h.getLevel().getGameTime();
        NamedFighter cc = cook.character(), cf = friend.character();
        for (var e : new ArrayList<>(ld.errands())) if (s.id.equals(e.settlement)) ld.removeErrand(e.id);
        com.dbzenith.living.Errand e = com.dbzenith.living.ErrandsTestAccess.make(h.getLevel().getServer(), ld, s, cc, now);
        h.assertTrue(e != null && e.kind == com.dbzenith.living.Errand.Kind.BRING_ITEMS, "the cook needs ingredients");
        ld.add(e);
        h.assertTrue(com.dbzenith.living.Errands.accept(ld, cc, p, now) && p.getUUID().equals(e.taker), "taken");
        h.assertTrue(!com.dbzenith.living.Errands.turnIn(ld, cc, p, now), "not without the goods");
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(e.item));
        p.getInventory().add(new net.minecraft.world.item.ItemStack(item, e.count));
        var d = com.dbzenith.data.ModCapabilities.getOrThrow(p);
        long tp = d.getTrainingPoints();
        h.assertTrue(com.dbzenith.living.Errands.turnIn(ld, cc, p, now), "turned in");
        h.assertTrue(d.getTrainingPoints() == tp + e.rewardTp, "paid");
        h.assertTrue(!com.dbzenith.living.Errands.turnIn(ld, cc, p, now) && d.getTrainingPoints() == tp + e.rewardTp, "once");
        h.assertTrue(cc.players.get(p.getUUID()).flags.contains("helped"), "and remembered");
        com.dbzenith.living.Errand msg = new com.dbzenith.living.Errand(UUID.randomUUID());
        msg.kind = com.dbzenith.living.Errand.Kind.DELIVER;
        msg.giver = cc.id;
        msg.settlement = s.id;
        msg.target = cf.id;
        msg.targetName = cf.name;
        msg.expires = now + 24000;
        ld.add(msg);
        com.dbzenith.living.Errands.accept(ld, cc, p, now);
        Conversations.open(friend, p);
        h.assertTrue(msg.done(), "the message is delivered by talking to them");
        Conversations.close(p, false);
        h.assertTrue(com.dbzenith.living.Errands.turnIn(ld, cc, p, now), "and the errand turned in");
        forget(h, s, cook, friend, p);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "living_9", timeoutTicks = 60)
    public static void travelersCarryNewsBetweenTowns(GameTestHelper h) {
        Settlement a = town(h), b = town(h, new BlockPos(1, 1, 1));
        b.name = "Other Town";
        Resident local = resident(h, a, Occupation.CITIZEN, new BlockPos(1, 1, 1), 901);
        cleanup(h, 60, a, local);
        LivingData ld = LivingData.get(h.getLevel().getServer());
        long now = h.getLevel().getGameTime();
        NamedFighter t = Residents.create(Nemesis.data(), null, Occupation.TRAVELER, a.center, now, 902);
        t.status = NamedFighter.Status.AWAY;
        t.travelTo = a.id;
        t.travelUntil = now;
        Rumor news = Rumors.add(ld, Rumor.Kind.STRONG_FIGHTER, UUID.randomUUID(), "Kabura", null, "", "elite", null, now, List.of());
        Rumor home = Rumors.add(ld, Rumor.Kind.TRAINING_HARD, local.character().id, local.character().name, null, "", "", a.id, now, List.of(local.character()));
        com.dbzenith.living.Travelers.arriveNow(h.getLevel().getServer(), t, now);
        h.assertTrue(a.id.equals(t.settlement) && t.status == NamedFighter.Status.PRESENT && t.travelUntil > now, "it arrives and stays a while");
        h.assertTrue(local.character().knowledge.contains(news.id()), "it brings news from the road: traveler knows news " + t.knowledge.contains(news.id())
                + ", knows " + t.knowledge.size() + ", local knows " + local.character().knowledge.size() + ", rumors " + ld.rumors().size() + ", news tick " + news.tick() + " now " + now);
        h.assertTrue(t.knowledge.contains(home.id()), "and takes the town's");
        com.dbzenith.living.Travelers.leaveNow(h.getLevel().getServer(), t, now);
        h.assertTrue(t.status == NamedFighter.Status.AWAY && t.settlement == null && t.travelTo != null && !a.id.equals(t.travelTo) && t.travelUntil > now, "then on to another town");
        t.status = NamedFighter.Status.DEAD;
        LivingData.get(h.getLevel().getServer()).remove(b.id);
        forget(h, a, local);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "living_10", timeoutTicks = 400)
    public static void travelersWalkOutThroughAGate(GameTestHelper h) {
        corridor(h, true);                                                               // closed: the spot may be open sea
        Settlement s = town(h, new BlockPos(1, 1, 8));                                    // every place, the gate included, at the far end
        ServerPlayer p = TestPlayers.create(h);
        long now = h.getLevel().getGameTime();
        NamedFighter t = Residents.create(Nemesis.data(), s, Occupation.TRAVELER, s.pois.get(0).pos(), now, 1001);
        Resident body = Residents.spawnBody(h.getLevel(), t, h.absolutePos(new BlockPos(1, 1, 1)));
        body.brain().arena = new net.minecraft.world.phys.AABB(0, -500, 0, 1, -499, 1);
        t.travelUntil = now;                                                              // time to move on
        cleanup(h, 400, s, body, p);
        BlockPos near = h.absolutePos(new BlockPos(1, 4, 4));                            // on the roof, out of its way
        p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        h.succeedWhen(() -> {
            h.assertTrue(body.isRemoved(), "it walks out: " + body.leaving + " at " + body.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)));
            h.assertTrue(t.status == NamedFighter.Status.AWAY && t.settlement == null && t.body == null, "and is on the road: " + t.status + " " + t.settlement + " " + t.body + " " + t.travelUntil + " now " + h.getLevel().getGameTime());
            t.status = NamedFighter.Status.DEAD;
            corridor(h, false);
            forget(h, s, p);
        });
        Delays.after(h, 397, () -> corridor(h, false));
    }

    /** A glass corridor on stone, x 0..2, z 0..9 inside (or, false, cleared again). */
    private static void corridor(GameTestHelper h, boolean build) {
        for (int x = -1; x <= 3; x++)
            for (int z = -1; z <= 10; z++)
                for (int y = 0; y <= 3; y++) {
                    boolean wall = x == -1 || x == 3 || z == -1 || z == 10 || y == 0 || y == 3;
                    h.setBlock(new BlockPos(x, y, z), !build || !wall ? net.minecraft.world.level.block.Blocks.AIR
                            : y == 0 ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.GLASS);
                }
    }

    @GameTest(template = EMPTY, batch = "living_11", timeoutTicks = 60)
    public static void companionsCountYourPartyAsFriends(GameTestHelper h) {
        Settlement s = town(h);
        Resident guard = resident(h, s, Occupation.GUARD, new BlockPos(1, 1, 1), 1101);
        ServerPlayer a = TestPlayers.create(h), b = TestPlayers.create(h), outsider = TestPlayers.create(h);
        cleanup(h, 60, s, guard, a, b, outsider);
        for (ServerPlayer p : new ServerPlayer[]{a, b, outsider}) {
            BlockPos at = h.absolutePos(new BlockPos(1, 1, 3));
            p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
        var social = com.dbzenith.social.SocialData.get(h.getLevel().getServer());
        com.dbzenith.social.SocialService.act(a, "create", false, "");
        var party = social.membership(a.getUUID(), false);
        com.dbzenith.social.SocialService.act(a, "invite", false, b.getGameProfile().getName());
        com.dbzenith.social.SocialService.act(b, "accept", false, party.id.toString());
        h.assertTrue(social.membership(b.getUUID(), false) == party, "a party of two");
        long now = h.getLevel().getGameTime();
        NamedFighter c = guard.character();
        c.player(a.getUUID(), "A").shift(40, 0);
        Companions.join(c, a, now);
        h.assertTrue(guard.isFriendOf(a) && guard.isFriendOf(b) && !guard.isFriendOf(outsider), "its player and their party are friends; others aren't");
        h.assertTrue(!guard.canAttack(a) && !guard.canAttack(b) && guard.canAttack(outsider), "it never fights the party");
        guard.hurt(h.getLevel().damageSources().playerAttack(b), 1f);
        h.assertTrue(!s.alarmed(now + 1) && !s.wanted.contains(b.getUUID()), "a party-mate's stray blow is no attack on the town");
        var snap = com.dbzenith.social.SocialService.snapshot(b, "");
        var rows = snap.getList("Companions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        h.assertTrue(rows.size() == 1 && rows.getCompound(0).getUUID("Owner").equals(a.getUUID()) && rows.getCompound(0).getString("Name").equals(c.name),
                "the Party tab lists it under its player: " + rows);
        Companions.leave(h.getLevel().getServer(), c, "living.dbzenith.companion.parted", now);
        com.dbzenith.social.SocialService.act(a, "disband", false, "");
        forget(h, s, guard, a, b, outsider);
        h.succeed();
    }
}
