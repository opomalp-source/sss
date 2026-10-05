package com.dbzenith.skill;

import com.dbzenith.stats.Race;

import java.util.EnumSet;
import java.util.Set;

/**
 * A technique definition. Pure data; firing logic lives in {@link TechniqueHandler} (projectiles/beams)
 * and {@link TechniqueEffects} (self/area effects). Built with {@link #builder}.
 */
public final class Technique {
    public enum Style { BALL, DISK, BEAM, SELF }

    /** Special behavior. NONE = plain damage. */
    public enum Effect { NONE, HEAL_SELF, HEAL_ALLY, BLIND_AREA, EXPLOSIVE_WAVE, TELEPORT, KI_SENSE, ENERGY_ABSORB, CANDY, KI_TRANSFER, STUN_AREA, KI_SEAL, GRAB, FUSE, ABSORB }

    private final String id;
    private final double kiCost;
    private final double damageMult;
    private final float speed;
    private final float size;
    private final int cooldownTicks;
    private final int count;
    private final float spreadDegrees;
    private final int pierce;
    private final boolean homing;
    private final float explosionPower;
    private final int color;
    private final int lifeTicks;
    private final Style style;
    private final Effect effect;
    private final double effectPower;
    private final long learnCost;
    private final int unlockLevel;
    private final Set<Race> races;
    private final int holdTicks;
    private final String displayName;     // player-made techniques carry their own name
    private final String summary;         // ...and a generated description

    private Technique(Builder b) {
        id = b.id;
        kiCost = b.kiCost;
        damageMult = b.damageMult;
        speed = b.speed;
        size = b.size;
        cooldownTicks = b.cooldownTicks;
        count = b.count;
        spreadDegrees = b.spreadDegrees;
        pierce = b.pierce;
        homing = b.homing;
        explosionPower = b.explosionPower;
        color = b.color;
        lifeTicks = b.lifeTicks;
        style = b.style;
        effect = b.effect;
        effectPower = b.effectPower;
        learnCost = b.learnCost;
        unlockLevel = b.unlockLevel;
        races = b.races;
        holdTicks = b.holdTicks;
        displayName = b.displayName;
        summary = b.summary;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() { return id; }
    /** Base ki cost before ki control, release and racial scaling. */
    public double kiCost() { return kiCost; }
    /** Multiplier on {@code DamageCalculator.kiOutgoing}. */
    public double damageMult() { return damageMult; }
    /** Blocks per tick (beams: how fast the beam extends). */
    public float speed() { return speed; }
    /** Projectile diameter / beam width in blocks. */
    public float size() { return size; }
    public int cooldownTicks() { return cooldownTicks; }
    /** Projectiles per use (volleys). */
    public int count() { return count; }
    public float spreadDegrees() { return spreadDegrees; }
    /** Extra entities a projectile passes through. */
    public int pierce() { return pierce; }
    public boolean homing() { return homing; }
    public float explosionPower() { return explosionPower; }
    /** 0xRRGGBB tint. */
    public int color() { return color; }
    /** Max flight time / beam duration. */
    public int lifeTicks() { return lifeTicks; }
    public Style style() { return style; }
    public Effect effect() { return effect; }
    /** Effect strength (heal fraction, radius, distance, duration ticks...; meaning depends on the effect). */
    public double effectPower() { return effectPower; }
    /** TP to learn from the Techniques screen (0 = starting / racial). */
    public long learnCost() { return learnCost; }
    public int unlockLevel() { return unlockLevel; }
    /** Races that may learn it (all races unless restricted). */
    public Set<Race> races() { return races; }

    /** Ball-drop: the ball forms above the caster's head for this many ticks, then is hurled at the crosshair (0 = fired at once). */
    public int holdTicks() { return holdTicks; }

    public boolean isRacial() {
        return races.size() < Race.values().length;
    }

    public String translationKey() {
        return "technique.dbzenith." + id;
    }

    /** What to call it on screen: its translation, or the name its creator gave it. */
    public net.minecraft.network.chat.Component name() {
        return displayName != null ? net.minecraft.network.chat.Component.literal(displayName)
                : net.minecraft.network.chat.Component.translatable(translationKey());
    }

    public net.minecraft.network.chat.Component description() {
        return summary != null ? net.minecraft.network.chat.Component.literal(summary)
                : net.minecraft.network.chat.Component.translatable(translationKey() + ".desc");
    }

    /** Made in the Ki Creator. */
    public boolean isCustom() {
        return displayName != null;
    }

    public static final class Builder {
        private final String id;
        private double kiCost = 20;
        private double damageMult = 1;
        private float speed = 1.5f;
        private float size = 0.5f;
        private int cooldownTicks = 20;
        private int count = 1;
        private float spreadDegrees;
        private int pierce;
        private boolean homing;
        private float explosionPower;
        private int color = 0xFFFFFF;
        private int lifeTicks = 60;
        private Style style = Style.BALL;
        private Effect effect = Effect.NONE;
        private double effectPower;
        private long learnCost;
        private int unlockLevel;
        private Set<Race> races = EnumSet.allOf(Race.class);
        private int holdTicks;
        private String displayName;
        private String summary;

        private Builder(String id) {
            this.id = id;
        }

        public Builder cost(double ki) { kiCost = ki; return this; }
        public Builder damage(double mult) { damageMult = mult; return this; }
        public Builder speed(float s) { speed = s; return this; }
        public Builder size(float s) { size = s; return this; }
        public Builder cooldown(int ticks) { cooldownTicks = ticks; return this; }
        public Builder volley(int n, float spread) { count = n; spreadDegrees = spread; return this; }
        public Builder pierce(int n) { pierce = n; return this; }
        public Builder homing() { homing = true; return this; }
        public Builder explosion(float power) { explosionPower = power; return this; }
        public Builder color(int rgb) { color = rgb; return this; }
        public Builder life(int ticks) { lifeTicks = ticks; return this; }
        public Builder style(Style s) { style = s; return this; }
        public Builder effect(Effect e, double power) { effect = e; effectPower = power; return this; }
        public Builder learn(long tp, int level) { learnCost = tp; unlockLevel = level; return this; }
        public Builder drop(int ticksAboveHead) { holdTicks = ticksAboveHead; return this; }
        public Builder race(Race first, Race... rest) { races = EnumSet.of(first, rest); return this; }
        public Builder named(String name, String description) { displayName = name; summary = description; return this; }

        public Technique build() {
            return new Technique(this);
        }
    }
}
