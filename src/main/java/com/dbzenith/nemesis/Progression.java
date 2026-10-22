package com.dbzenith.nemesis;

import com.dbzenith.npc.ai.AiProfile;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.NpcForms;
import com.dbzenith.npc.ai.Power;
import com.dbzenith.race.Variant;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * How named characters grow, using the mod's own scales: the fighter level ({@code KiFighter.levelFor}), the forms of
 * their race and variant ({@link NpcForms}, unlocked by the character level they fight like) and the technique table
 * ({@code Techniques}, by race and unlock level). Experience from battles, victories, defeats (Saiyans get more from a
 * defeat: their zenkai) and training, at a rate set by their traits and temperament. Bounded:
 * <ul>
 *   <li>at most {@code nemesis.growthPerDay} levels an in-game day;</li>
 *   <li>never more than a quarter above the strongest player it has measured itself against (a weak fighter does not
 *       overtake you after one defeat);</li>
 *   <li>never past {@code enemies.maxLevel}.</li>
 * </ul>
 */
public final class Progression {
    private Progression() {}

    public static double xpFor(int level) {
        return 100 * (1 + level * 0.15);
    }

    /** How fast it grows, 0.5..1.5: discipline and ambition up, recklessness down. */
    public static double growthOf(NamedFighter c) {
        var t = c.temper();
        double g = 0.75 + 0.4 * t.discipline - 0.2 * t.recklessness + (c.has(Trait.AMBITIOUS) ? 0.25 : 0) + (c.has(Trait.VENGEFUL) ? 0.1 : 0);
        return Math.max(0.5, Math.min(1.5, g));
    }

    /** Experience it gets from one in-game hour of training (more when chasing a rival, obsessively after defeats). */
    public static double trainingXp(NamedFighter c) {
        var t = c.temper();
        double rate = 0.5 + 0.5 * t.discipline + (c.has(Trait.AMBITIOUS) ? 0.4 : 0);
        double rivalry = 0;
        for (Bond b : c.players.values()) rivalry = Math.max(rivalry, b.rivalry);
        rate += rivalry / 200.0;
        if (c.has(Trait.VENGEFUL) && c.goal == NamedFighter.Goal.REVENGE) rate += 0.6;           // obsessive
        return 6 * rate;
    }

    /** The highest level it may reach now. */
    public static int cap(NamedFighter c) {
        int maxLevel;
        try {
            maxLevel = com.dbzenith.config.DBZConfig.SERVER.enemyMaxLevel.get();
        } catch (IllegalStateException e) {
            maxLevel = 200;
        }
        int best = 0;
        for (Bond b : c.players.values()) best = Math.max(best, b.playerLevelSeen);
        int fair = best > 0 ? (int) Math.ceil(best * 1.25) : c.level + 2;
        return Math.max(c.level, Math.min(maxLevel, fair));
    }

    /**
     * Gives experience; levels come as it fills, within the caps. Returns the levels gained (new forms and techniques
     * are recorded in its history).
     */
    public static int gain(NamedFighter c, double xp, long now) {
        if (xp <= 0 || !c.active()) return 0;
        long day = Math.floorDiv(now, 24000L);
        if (day != c.day) {
            c.day = day;
            c.dayStartLevel = c.level;
        }
        c.xp += xp * c.growth;
        int gained = 0, cap = cap(c);
        double perDay = NemesisConfig.growthPerDay();
        while (c.xp >= xpFor(c.level) && c.level < cap && c.level - c.dayStartLevel < perDay) {
            c.xp -= xpFor(c.level);
            c.level++;
            gained++;
        }
        if (c.level >= cap || c.level - c.dayStartLevel >= perDay) c.xp = Math.min(c.xp, xpFor(c.level) * 0.95);
        if (gained > 0) unlocks(c, now);
        return gained;
    }

    /** New forms and techniques its level now allows, from the existing tables. */
    static void unlocks(NamedFighter c, long now) {
        AiProfile p = profile(c);
        int eq = Power.equivalentLevel(c.level);
        for (Form f : NpcForms.ladder(p, eq)) {
            if (c.forms.add(f.id())) {
                c.remember(new Memory(Memory.Kind.UNLOCKED_FORM, now, null, f.id()));
                c.reputation += 15;
            }
        }
        if (c.learned.size() >= 4) return;
        List<Technique> can = learnable(c, eq);
        if (can.isEmpty()) return;
        Technique t = can.get(AiRandom.pick(c.id.getLeastSignificantBits(), c.level, 9, can.size()));
        c.learned.add(t.id());
        c.remember(new Memory(Memory.Kind.LEARNED_TECHNIQUE, now, null, t.id()));
    }

    /** Techniques it could learn now: existing ones an NPC can throw (not self effects), of its race or anyone's, unlocked. */
    static List<Technique> learnable(NamedFighter c, int equivalentLevel) {
        Race race = race(c);
        List<Technique> out = new ArrayList<>();
        java.util.Set<String> have = new java.util.HashSet<>(c.learned);
        for (Technique t : kindTechniques(c)) have.add(t.id());
        for (Technique t : Techniques.all()) {
            if (t.isCustom() || t.style() == Technique.Style.SELF || have.contains(t.id())) continue;
            if (t.effect() == Technique.Effect.SPIRIT_BOMB || t.id().equals("hakai")) continue;      // taught by gods and Kais, not learned alone
            if (t.isRacial() && (race == null || !t.races().contains(race))) continue;
            if (t.unlockLevel() > equivalentLevel) continue;
            out.add(t);
        }
        return out;
    }

    static List<Technique> kindTechniques(NamedFighter c) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(c.type));
        if (type == null) return List.of();
        var probe = com.dbzenith.npc.ModNpcs.profileOf(type);
        return probe == null ? List.of() : probe.techniques();
    }

    static AiProfile profile(NamedFighter c) {
        if (c.resident()) return com.dbzenith.living.Residents.profile(c);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(c.type));
        return type == null ? AiProfile.of(net.minecraft.world.entity.EntityType.PIG) : AiProfile.of(type);
    }

    static Race race(NamedFighter c) {
        try {
            return c.race.isEmpty() ? null : Race.valueOf(c.race.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Variant variant(NamedFighter c) {
        try {
            return c.variant.isEmpty() ? null : Variant.valueOf(c.variant.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Its strongest form now (the top of its ladder), for the readout: "base" if none. */
    public static String topForm(NamedFighter c) {
        String top = "base";
        for (String f : c.forms) top = f;
        return top;
    }

    /** Unlock level of a form, scaled (for tests and the readout). */
    static int unlockOf(Form f) {
        return FormMath.unlockLevel(f);
    }
}
