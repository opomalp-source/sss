package com.dbzenith.race;

import com.dbzenith.stats.Race;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A racial skill: a passive that is always on once unlocked (its modifiers apply whenever their condition holds),
 * or an active fired with the Racial key (a ki cost, a cooldown, and either a timed buff made of modifiers with
 * {@link When#ACTIVE} / {@link When#AFTER}, an instant effect in {@link RacialSkillEffects}, or both).
 * Unlocked by level; limited to races and optionally variants. Defined in {@link RacialSkills}.
 */
public final class RacialSkill {
    public enum Kind { PASSIVE, ACTIVE }

    /** What a modifier changes. Amounts are fractions: +0.2 = 20% more; for DAMAGE_TAKEN -0.2 = 20% less. */
    public enum Stat {
        /** Strength, dexterity and ki power. */
        POWER,
        MELEE,
        KI,
        SPEED,
        DAMAGE_DEALT,
        DAMAGE_TAKEN,
        BODY_REGEN,
        KI_REGEN,
        KI_COST,
        FORM_DRAIN,
        TP_GAIN,
        MASTERY_GAIN,
        /** 0..1 of knockback ignored. */
        KNOCKBACK_RESIST,
        /** Fraction of damage dealt returned as body. */
        LIFESTEAL,
        /** Fraction of max ki restored per hit landed. */
        KI_ON_HIT
    }

    /** When a modifier applies. The FOE_ and _HIT conditions are only known when a blow lands. */
    public enum When {
        ALWAYS, LOW_BODY, CRITICAL_BODY, FULL_BODY, IN_COMBAT, COMBAT_RAMP, TRANSFORMED, BASE_FORM, NIGHT, DAY, HIGH_KI,
        LOW_KI, HURT_RECENTLY, ACTIVE, AFTER, FOE_STRONGER, FOE_WEAK, MELEE_HIT, KI_HIT;

        /** Conditions that depend only on the fighter (and so can be folded into derived stats). */
        public boolean selfOnly() {
            return ordinal() <= AFTER.ordinal();
        }
    }

    public record Mod(Stat stat, double amount, When when) {}

    private final String id;
    private final Kind kind;
    private final Set<Race> races;
    private final Set<Variant> variants;
    private final int unlockLevel;
    private final List<Mod> mods;
    private final double kiCostPercent;
    private final int cooldownTicks;
    private final int durationTicks;
    private final int color;
    private final int maxLevel;
    private final int[] levelUnlocks;
    private final long tpBase;

    private RacialSkill(Builder b) {
        id = b.id;
        kind = b.kind;
        races = b.races;
        variants = b.variants;
        unlockLevel = b.unlockLevel;
        mods = List.copyOf(b.mods);
        kiCostPercent = b.kiCostPercent;
        cooldownTicks = b.cooldownTicks;
        durationTicks = b.durationTicks;
        color = b.color;
        maxLevel = b.maxLevel;
        levelUnlocks = b.levelUnlocks;
        tpBase = b.tpBase;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public boolean isActive() { return kind == Kind.ACTIVE; }
    public Set<Race> races() { return races; }
    /** Empty = every variant of its races. */
    public Set<Variant> variants() { return variants; }
    public int unlockLevel() { return unlockLevel; }
    public List<Mod> mods() { return mods; }
    public double kiCostPercent() { return kiCostPercent; }
    public int cooldownTicks() { return cooldownTicks; }
    /** Length of the timed buff ({@link When#ACTIVE}); the {@link When#AFTER} backlash lasts twice as long. */
    public int durationTicks() { return durationTicks; }
    public int color() { return color; }

    /** Universal skills are learned with TP (any race) and may have levels; racial skills come with the race. */
    public boolean learned() { return tpBase > 0; }
    public int maxLevel() { return maxLevel; }
    /** Character level needed to learn skill level {@code level} (1-based). */
    public int unlockLevelFor(int level) { return levelUnlocks.length == 0 ? unlockLevel : levelUnlocks[Math.min(levelUnlocks.length, Math.max(1, level)) - 1]; }
    /** TP to learn skill level {@code level}: the base times the level squared. */
    public long tpCost(int level) { return tpBase * level * level; }

    public boolean fits(Race race, Variant variant) {
        return races.contains(race) && (variants.isEmpty() || variants.contains(variant));
    }

    public String translationKey() { return "racial.dbzenith." + id; }
    public String descriptionKey() { return "racial.dbzenith." + id + ".desc"; }

    public static Builder passive(String id) { return new Builder(id, Kind.PASSIVE); }
    public static Builder active(String id) { return new Builder(id, Kind.ACTIVE); }

    public static final class Builder {
        private final String id;
        private final Kind kind;
        private Set<Race> races = EnumSet.noneOf(Race.class);
        private final Set<Variant> variants = EnumSet.noneOf(Variant.class);
        private int unlockLevel;
        private final List<Mod> mods = new ArrayList<>();
        private double kiCostPercent;
        private int cooldownTicks;
        private int durationTicks;
        private int color = 0xFFD8A040;
        private int maxLevel = 1;
        private int[] levelUnlocks = new int[0];
        private long tpBase;

        private Builder(String id, Kind kind) {
            this.id = id;
            this.kind = kind;
        }

        public Builder races(Race first, Race... rest) { races = EnumSet.of(first, rest); return this; }
        public Builder only(Variant... v) {
            variants.addAll(List.of(v));
            if (races.isEmpty()) for (Variant x : v) races.add(x.race());
            return this;
        }
        public Builder level(int l) { unlockLevel = l; return this; }
        public Builder mod(Stat s, double amount, When w) { mods.add(new Mod(s, amount, w)); return this; }
        public Builder mod(Stat s, double amount) { return mod(s, amount, When.ALWAYS); }
        /** Active: ki cost (% of max), cooldown and buff length, in seconds. */
        public Builder use(double kiPercent, int cooldownSeconds, int durationSeconds) {
            kiCostPercent = kiPercent;
            cooldownTicks = cooldownSeconds * 20;
            durationTicks = durationSeconds * 20;
            return this;
        }
        public Builder color(int rgb) { color = 0xFF000000 | rgb; return this; }
        /** Learned with TP by any race: the TP for level 1 and the character level each skill level needs. */
        public Builder learned(long tp, int... unlocks) {
            races = EnumSet.allOf(Race.class);
            tpBase = tp;
            levelUnlocks = unlocks.length == 0 ? new int[]{unlockLevel} : unlocks;
            maxLevel = Math.max(1, levelUnlocks.length);
            unlockLevel = levelUnlocks[0];
            return this;
        }

        public RacialSkill build() {
            if (races.isEmpty()) throw new IllegalStateException(id + " has no race");
            return new RacialSkill(this);
        }
    }
}
