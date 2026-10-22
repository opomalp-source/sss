package com.dbzenith.living;

import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.Progression;
import com.dbzenith.nemesis.Trait;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.Temper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Townsfolk among themselves (docs/LIVING_WORLD.md), every two seconds for the residents in the world:
 * <ul>
 *   <li>two idle residents near each other may stop to chat (more likely the friendlier, less so the shy, never
 *       angry or worried ones): they face each other a while; how it goes moves what they think of each other (alike
 *       temperaments get on; a greedy one grates), each passes on the newest news the other hasn't heard, and a player
 *       close by overhears a line of it;</li>
 *   <li>residents training at the dojo pair off for drills (strikes and guards, no harm done) and both grow a little;
 *       with the master watching, students are the keener for it;</li>
 *   <li>residents greet a famous player who comes close (once a day each), and warn off one their town wants.</li>
 * </ul>
 */
final class Social {
    /** When each player last overheard townsfolk, and when each resident last greeted each player. */
    private static final Map<UUID, Long> OVERHEARD = new HashMap<>();
    private static final Map<Long, Long> GREETED = new HashMap<>();

    private Social() {}

    static void tick(MinecraftServer server, LivingData ld, long now) {
        if (!LivingConfig.social()) return;
        List<Resident> idle = new ArrayList<>(), training = new ArrayList<>();
        for (KiFighter k : Nemesis.bodies()) {
            if (!(k instanceof Resident r) || !r.isAlive() || r.getTarget() != null || r.talkingTo != null || FleeGoal.fleeing(r)) continue;
            if (r.chatWith != null && now >= r.chatUntil) r.chatWith = null;
            if (r.activity == Schedules.Activity.TRAIN && now >= r.drillUntil) training.add(r);
            else if (r.chatWith == null && sociable(r.activity)) idle.add(r);
            greet(server, ld, r, now);
        }
        idle.sort((a, b) -> Double.compare(AiRandom.unit(a.getUUID().getMostSignificantBits(), now, 40), AiRandom.unit(b.getUUID().getMostSignificantBits(), now, 40)));
        int started = 0;
        for (Resident a : idle) {
            if (started >= LivingConfig.maxChats()) break;
            if (a.chatWith != null || now - a.lastChat < 600) continue;
            NamedFighter ca = a.character();
            if (ca == null || !Mood.of(ca, a.level()).sociable() || !AiRandom.chance(ca.id.getMostSignificantBits(), now / 40, 41, chatChance(ca))) continue;
            Resident b = nearest(a, idle, 5);
            if (b == null) continue;
            NamedFighter cb = b.character();
            if (cb == null || !Mood.of(cb, b.level()).sociable()) continue;
            chat(server, ld, a, ca, b, cb, now);
            started++;
        }
        for (Resident a : training) {
            if (now < a.drillUntil) continue;
            Resident b = null;
            double bd = 4 * 4;
            for (Resident o : training) if (o != a && now >= o.drillUntil && o.level() == a.level() && o.distanceToSqr(a) < bd) {
                bd = o.distanceToSqr(a);
                b = o;
            }
            if (b != null) drill(server, ld, a, b, now);
        }
    }

    static boolean sociable(Schedules.Activity a) {
        return a == Schedules.Activity.SOCIAL || a == Schedules.Activity.WANDER || a == Schedules.Activity.EAT || a == Schedules.Activity.SHOP;
    }

    /** How keen it is to stop and talk, from its nature. */
    static double chatChance(NamedFighter c) {
        double p = 0.35;
        if (c.has(Trait.FRIENDLY)) p += 0.25;
        if (c.has(Trait.CURIOUS)) p += 0.15;
        if (c.has(Trait.SHY)) p -= 0.25;
        if (c.has(Trait.CALM)) p -= 0.05;
        return Math.max(0.05, p);
    }

    static Resident nearest(Resident a, List<Resident> among, double range) {
        Resident best = null;
        double bd = range * range;
        for (Resident o : among) {
            if (o == a || o.chatWith != null || o.level() != a.level()) continue;
            double d = o.distanceToSqr(a);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** Two residents chat: what they make of each other, news passed on, a line overheard. */
    static void chat(MinecraftServer server, LivingData ld, Resident a, NamedFighter ca, Resident b, NamedFighter cb, long now) {
        long len = 100 + AiRandom.pick(ca.id.getLeastSignificantBits(), now / 40, 42, 100);
        a.chatWith = b.getUUID();
        b.chatWith = a.getUUID();
        a.chatUntil = b.chatUntil = now + len;
        a.lastChat = b.lastChat = now;
        a.getNavigation().stop();
        b.getNavigation().stop();
        double feel = compatibility(ca, cb);
        Bond ab = ca.character(cb), ba = cb.character(ca);
        ab.encounters++;
        ba.encounters++;
        ab.shift(feel, 0);
        ba.shift(feel, 0);
        ab.state = com.dbzenith.nemesis.Relationship.next(ab.state, ab.affinity, ab.rivalry, ab.encounters, ab.spars);
        ba.state = com.dbzenith.nemesis.Relationship.next(ba.state, ba.affinity, ba.rivalry, ba.encounters, ba.spars);
        if (feel > 1) {
            Mood.set(ca, Mood.HAPPY, now, 2400);
            Mood.set(cb, Mood.HAPPY, now, 2400);
        } else if (feel < -1) {
            Mood.set(ca, Mood.ANNOYED, now, 1200);
        }
        Rumor told = Rumors.exchange(ld, ca, cb);
        overheard(server, ld, a, ca, b, cb, told, now);
        Nemesis.data().setDirty();
    }

    /** How well two temperaments get on (-3..3): alike ones do, the friendly with anyone, the greedy and the proud less. */
    static double compatibility(NamedFighter a, NamedFighter b) {
        Temper ta = a.temper(), tb = b.temper();
        double alike = 1 - (Math.abs(ta.aggression - tb.aggression) + Math.abs(ta.honor - tb.honor) + Math.abs(ta.caution - tb.caution)) / 1.5;
        double f = alike * 2;
        if (a.has(Trait.FRIENDLY) || b.has(Trait.FRIENDLY)) f += 1;
        if (a.has(Trait.GREEDY) || b.has(Trait.GREEDY)) f -= 1;
        if (ta.pride > 0.8 && tb.pride > 0.8) f -= 1;
        return Math.max(-3, Math.min(3, f));
    }

    /** A player near a chat hears a line of it (once every half minute at most). */
    static void overheard(MinecraftServer server, LivingData ld, Resident a, NamedFighter ca, Resident b, NamedFighter cb, Rumor told, long now) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != a.level() || p.distanceTo(a) > 10) continue;
            Long last = OVERHEARD.get(p.getUUID());
            if (last != null && now - last < 600) continue;
            String text = told != null ? Rumors.line(told, b.getUUID(), cb.name, ld, now, ca.id.getMostSignificantBits())
                    : Conversations.bark("smalltalk", a, ca, p);
            if (text == null) continue;
            OVERHEARD.put(p.getUUID(), now);
            p.sendSystemMessage(Component.literal(ca.name + " → " + cb.name + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
        }
    }

    /** Two residents drill at the dojo: strikes and guards, a little stronger for it. */
    static void drill(MinecraftServer server, LivingData ld, Resident a, Resident b, long now) {
        NamedFighter ca = a.character(), cb = b.character();
        if (ca == null || cb == null) return;
        a.drillUntil = b.drillUntil = now + 200;
        a.getLookControl().setLookAt(b, 30f, 30f);
        b.getLookControl().setLookAt(a, 30f, 30f);
        boolean master = false;
        for (KiFighter k : Nemesis.bodies()) if (k instanceof Resident m && m != a && m != b && m.occupation() == Occupation.MASTER && m.distanceToSqr(a) < 12 * 12) master = true;
        double xp = master ? 14 : 8;
        Progression.gain(ca, xp, now);
        Progression.gain(cb, xp, now);
        Bond ab = ca.character(cb), ba = cb.character(ca);
        ab.spars++;
        ba.spars++;
        ab.shift(2, 1);
        ba.shift(2, 1);
        ab.state = com.dbzenith.nemesis.Relationship.next(ab.state, ab.affinity, ab.rivalry, ab.encounters, ab.spars);
        ba.state = com.dbzenith.nemesis.Relationship.next(ba.state, ba.affinity, ba.rivalry, ba.encounters, ba.spars);
        if (master && ca.has(Trait.AMBITIOUS) && AiRandom.chance(ca.id.getMostSignificantBits(), now / 200, 43, 0.1)) {
            Rumors.add(ld, Rumor.Kind.TRAINING_HARD, ca.id, ca.name, null, "", "", ca.settlement, now,
                    Residents.of(ld.settlement(ca.settlement) == null ? new Settlement(UUID.randomUUID()) : ld.settlement(ca.settlement)));
        }
        Nemesis.data().setDirty();
    }

    /** A resident sees a player come close: a famous one is greeted (once a day each); a wanted one is warned off. */
    static void greet(MinecraftServer server, LivingData ld, Resident r, long now) {
        if (r.chatWith != null || r.isSleeping()) return;
        var p = r.level().getNearestPlayer(r, 5);
        if (!(p instanceof ServerPlayer sp) || sp.isSpectator()) return;
        NamedFighter c = r.character();
        if (c == null) return;
        long key = r.getUUID().getMostSignificantBits() ^ sp.getUUID().getLeastSignificantBits();
        Long last = GREETED.get(key);
        if (last != null && now - last < 24000) return;
        Settlement s = ld.settlement(c.settlement);
        String kind = s != null && s.wanted.contains(sp.getUUID()) ? "wanted"
                : Fame.Tier.of(ld.fame(sp.getUUID())).ordinal() >= Fame.Tier.KNOWN.ordinal() ? "famous"
                : c.players.containsKey(sp.getUUID()) && c.players.get(sp.getUUID()).state.friendly() ? "friend" : null;
        if (kind == null) return;
        String text = Conversations.bark(kind, r, c, sp);
        GREETED.put(key, now);
        if (text == null) return;
        r.getLookControl().setLookAt(sp, 30f, 30f);
        sp.sendSystemMessage(Component.literal(c.name + ": ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
        if (kind.equals("famous")) Mood.set(c, Mood.EXCITED, now, 1200);
    }

    static void forget(UUID player) {
        OVERHEARD.remove(player);
    }
}
