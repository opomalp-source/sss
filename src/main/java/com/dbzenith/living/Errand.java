package com.dbzenith.living;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

/**
 * One errand a resident needs done (docs/LIVING_WORLD.md, {@link Errands}): what, for whom, about whom or what, how
 * far along, who took it. Kept in {@link LivingData} until it is turned in (its reward is given once, then it is gone),
 * given up, or it runs out.
 */
public final class Errand {
    public enum Kind {
        /** A master: spar with some of my students. */
        SPAR_STUDENTS,
        /** A student: train with me. */
        SPAR_ME,
        /** A guard: deal with a real threat (a named fighter about), or the monsters prowling round town. */
        DEFEAT_THREAT,
        /** Anyone: take a message to someone. */
        DELIVER,
        /** A cook or merchant: bring goods. */
        BRING_ITEMS,
        /** Someone at odds with a neighbour: talk to them for me. */
        RESOLVE_RIVALRY,
        /** The curious: go and see a landmark, then tell me about it. */
        VISIT_LANDMARK;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final UUID id;
    public Kind kind = Kind.DELIVER;
    /** Who asked, and where. */
    public UUID giver, settlement;
    /** About whom (a character), or null; the name it goes by; an item or landmark id; a place (block pos as long). */
    public UUID target;
    public String targetName = "", item = "";
    public long place;
    public int count = 1, progress, baseline;
    /** Who took it (null: still offered). */
    public UUID taker;
    public long created, expires;
    public int rewardTp;

    public Errand(UUID id) {
        this.id = id;
    }

    public boolean done() {
        return progress >= count;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("kind", kind.id());
        if (giver != null) t.putUUID("giver", giver);
        if (settlement != null) t.putUUID("town", settlement);
        if (target != null) t.putUUID("target", target);
        t.putString("tname", targetName);
        t.putString("item", item);
        t.putLong("place", place);
        t.putIntArray("n", new int[]{count, progress, baseline, rewardTp});
        if (taker != null) t.putUUID("taker", taker);
        t.putLongArray("times", new long[]{created, expires});
        return t;
    }

    static Errand load(CompoundTag t) {
        Errand e = new Errand(t.getUUID("id"));
        for (Kind k : Kind.values()) if (k.id().equals(t.getString("kind"))) e.kind = k;
        e.giver = t.hasUUID("giver") ? t.getUUID("giver") : null;
        e.settlement = t.hasUUID("town") ? t.getUUID("town") : null;
        e.target = t.hasUUID("target") ? t.getUUID("target") : null;
        e.targetName = t.getString("tname");
        e.item = t.getString("item");
        e.place = t.getLong("place");
        int[] n = java.util.Arrays.copyOf(t.getIntArray("n"), 4);
        e.count = Math.max(1, n[0]);
        e.progress = n[1];
        e.baseline = n[2];
        e.rewardTp = n[3];
        e.taker = t.hasUUID("taker") ? t.getUUID("taker") : null;
        long[] times = java.util.Arrays.copyOf(t.getLongArray("times"), 2);
        e.created = times[0];
        e.expires = times[1];
        return e;
    }
}
