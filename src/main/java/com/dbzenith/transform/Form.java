package com.dbzenith.transform;

import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;

import java.util.EnumSet;
import java.util.Set;

/**
 * A transformation. Pure data; rules live in {@link FormHandler}, multipliers are applied in {@code StatCalculator}.
 * Multipliers apply to the combat attributes (STR, DEX, KI_POWER) only; pools (body/ki/stamina) are never multiplied.
 */
public final class Form {
    public enum HairStyle { NONE, SPIKY, SPIKY_TALL, LONG, SLIM }

    /** How a form can be entered. MANUAL = transform key; MOON = only by the moon trigger (Great Ape). */
    public enum Trigger { MANUAL, MOON }

    private final String id;
    private final String parent;
    private final int tier;
    private final Set<Race> races;
    private final double strMult;
    private final double dexMult;
    private final double kiMult;
    private final double kiDrainPercent;
    private final double staminaDrainPercent;
    private final double speedBonus;
    private final int auraColor;
    private final int hairColor;
    private final int eyeColor;
    private final HairStyle hairStyle;
    private final boolean lightning;
    private final int unlockLevel;
    private final double parentMasteryRequired;
    private final String requiredFlag;
    private final boolean allowsOverdrive;
    private final boolean allowsTechniques;
    private final boolean allowsFlight;
    private final float scale;
    private final Trigger trigger;

    private Form(Builder b) {
        id = b.id;
        parent = b.parent;
        tier = b.tier;
        races = b.races;
        strMult = b.strMult;
        dexMult = b.dexMult;
        kiMult = b.kiMult;
        kiDrainPercent = b.kiDrainPercent;
        staminaDrainPercent = b.staminaDrainPercent;
        speedBonus = b.speedBonus;
        auraColor = b.auraColor;
        hairColor = b.hairColor;
        eyeColor = b.eyeColor;
        hairStyle = b.hairStyle;
        lightning = b.lightning;
        unlockLevel = b.unlockLevel;
        parentMasteryRequired = b.parentMasteryRequired;
        requiredFlag = b.requiredFlag;
        allowsOverdrive = b.allowsOverdrive;
        allowsTechniques = b.allowsTechniques;
        allowsFlight = b.allowsFlight;
        scale = b.scale;
        trigger = b.trigger;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() { return id; }
    public String parent() { return parent; }
    public int tier() { return tier; }
    public Set<Race> races() { return races; }
    public double kiDrainPercent() { return kiDrainPercent; }
    public double staminaDrainPercent() { return staminaDrainPercent; }
    public double speedBonus() { return speedBonus; }
    public int auraColor() { return auraColor; }
    public int hairColor() { return hairColor; }
    public int eyeColor() { return eyeColor; }
    public HairStyle hairStyle() { return hairStyle; }
    public boolean lightning() { return lightning; }
    public int unlockLevel() { return unlockLevel; }
    public double parentMasteryRequired() { return parentMasteryRequired; }
    public String requiredFlag() { return requiredFlag; }
    public boolean allowsOverdrive() { return allowsOverdrive; }
    public boolean allowsTechniques() { return allowsTechniques; }
    public boolean allowsFlight() { return allowsFlight; }
    public float scale() { return scale; }
    public Trigger trigger() { return trigger; }

    public boolean isBase() {
        return parent == null;
    }

    /** Raw multiplier for an attribute (before mastery). 1 for non-combat attributes. */
    public double multiplier(Attribute attribute) {
        return switch (attribute) {
            case STRENGTH -> strMult;
            case DEXTERITY -> dexMult;
            case KI_POWER -> kiMult;
            default -> 1.0;
        };
    }

    public String translationKey() {
        return "form.dbzenith." + id;
    }

    public static final class Builder {
        private final String id;
        private String parent;
        private int tier;
        private Set<Race> races = EnumSet.allOf(Race.class);
        private double strMult = 1, dexMult = 1, kiMult = 1;
        private double kiDrainPercent;
        private double staminaDrainPercent;
        private double speedBonus;
        private int auraColor = 0xD9F2FF;
        private int hairColor = -1;
        private int eyeColor = -1;
        private HairStyle hairStyle = HairStyle.NONE;
        private boolean lightning;
        private int unlockLevel;
        private double parentMasteryRequired;
        private String requiredFlag;
        private boolean allowsOverdrive;
        private boolean allowsTechniques = true;
        private boolean allowsFlight = true;
        private float scale = 1f;
        private Trigger trigger = Trigger.MANUAL;

        private Builder(String id) {
            this.id = id;
        }

        public Builder parent(String parent, int tier) { this.parent = parent; this.tier = tier; return this; }
        public Builder races(Race first, Race... rest) { this.races = EnumSet.of(first, rest); return this; }
        public Builder multiplier(double all) { strMult = dexMult = kiMult = all; return this; }
        public Builder multipliers(double str, double dex, double ki) { strMult = str; dexMult = dex; kiMult = ki; return this; }
        public Builder drain(double kiPercentPerSecond, double staminaPercentPerSecond) {
            kiDrainPercent = kiPercentPerSecond;
            staminaDrainPercent = staminaPercentPerSecond;
            return this;
        }
        public Builder speedBonus(double bonus) { speedBonus = bonus; return this; }
        public Builder colors(int aura, int hair, int eyes) { auraColor = aura; hairColor = hair; eyeColor = eyes; return this; }
        public Builder hair(HairStyle style) { hairStyle = style; return this; }
        public Builder lightning() { lightning = true; return this; }
        public Builder unlock(int level, double parentMastery) { unlockLevel = level; parentMasteryRequired = parentMastery; return this; }
        public Builder requiresFlag(String flag) { requiredFlag = flag; return this; }
        public Builder allowsOverdrive() { allowsOverdrive = true; return this; }
        public Builder noTechniques() { allowsTechniques = false; return this; }
        public Builder noFlight() { allowsFlight = false; return this; }
        public Builder scale(float s) { scale = s; return this; }
        public Builder trigger(Trigger t) { trigger = t; return this; }

        public Form build() {
            return new Form(this);
        }
    }
}
