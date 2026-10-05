package com.dbzenith.race;

import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that makes a race mechanically distinct. Pure data; behavior lives in {@link RacePassives},
 * {@code StatCalculator} (TP weights) and the combat/ki tickers. Defined in {@link Races}.
 */
public final class RaceTraits {
    /** Head/body feature drawn for the race (placeholder geometry). */
    public enum Feature { NONE, ANTENNAE, HORNS, TENTACLE, EARS, WINGS, DEMON_HORNS }

    private final Race race;
    private final Map<Attribute, Double> costWeights;
    private final Map<Attribute, Integer> startBonus;
    private final int auraColor;
    private final double tpGainBonus;
    private final double kiCostReduction;
    private final double regenMultiplier;
    private final double regenDelayFactor;
    private final double zenkaiPercent;
    private final boolean tail;
    private final boolean breathless;
    private final boolean noHunger;
    private final boolean slowHunger;
    private final boolean staminaless;
    private final double kiAbsorb;
    private final double kiRegenMultiplier;
    private final double staminaRegenMultiplier;
    private final double killHeal;
    private final Feature feature;
    private final List<String> racialTechniques;

    private RaceTraits(Builder b) {
        race = b.race;
        costWeights = b.costWeights;
        startBonus = b.startBonus;
        auraColor = b.auraColor;
        tpGainBonus = b.tpGainBonus;
        kiCostReduction = b.kiCostReduction;
        regenMultiplier = b.regenMultiplier;
        regenDelayFactor = b.regenDelayFactor;
        zenkaiPercent = b.zenkaiPercent;
        tail = b.tail;
        breathless = b.breathless;
        noHunger = b.noHunger;
        slowHunger = b.slowHunger;
        staminaless = b.staminaless;
        kiAbsorb = b.kiAbsorb;
        kiRegenMultiplier = b.kiRegenMultiplier;
        staminaRegenMultiplier = b.staminaRegenMultiplier;
        killHeal = b.killHeal;
        feature = b.feature;
        racialTechniques = b.racialTechniques;
    }

    public static Builder builder(Race race) {
        return new Builder(race);
    }

    public Race race() { return race; }
    /** Multiplier on the TP cost of raising an attribute (below 1 = cheaper). */
    public double costWeight(Attribute a) { return costWeights.getOrDefault(a, 1.0); }
    public int startBonus(Attribute a) { return startBonus.getOrDefault(a, 0); }
    public int auraColor() { return auraColor; }
    public double tpGainBonus() { return tpGainBonus; }
    public double kiCostReduction() { return kiCostReduction; }
    public double regenMultiplier() { return regenMultiplier; }
    public double regenDelayFactor() { return regenDelayFactor; }
    public double zenkaiPercent() { return zenkaiPercent; }
    public boolean tail() { return tail; }
    public boolean breathless() { return breathless; }
    public boolean noHunger() { return noHunger; }
    public boolean slowHunger() { return slowHunger; }
    public boolean staminaless() { return staminaless; }
    public double kiAbsorb() { return kiAbsorb; }
    public double kiRegenMultiplier() { return kiRegenMultiplier; }
    public double staminaRegenMultiplier() { return staminaRegenMultiplier; }
    public double killHeal() { return killHeal; }
    public Feature feature() { return feature; }
    public List<String> racialTechniques() { return racialTechniques; }

    public String descriptionKey() {
        return "race.dbzenith." + race.id() + ".desc";
    }

    public static final class Builder {
        private final Race race;
        private final Map<Attribute, Double> costWeights = new EnumMap<>(Attribute.class);
        private final Map<Attribute, Integer> startBonus = new EnumMap<>(Attribute.class);
        private int auraColor = 0xD9F2FF;
        private double tpGainBonus;
        private double kiCostReduction;
        private double regenMultiplier = 1;
        private double regenDelayFactor = 1;
        private double zenkaiPercent;
        private boolean tail;
        private boolean breathless;
        private boolean noHunger;
        private boolean slowHunger;
        private boolean staminaless;
        private double kiAbsorb;
        private double kiRegenMultiplier = 1;
        private double staminaRegenMultiplier = 1;
        private double killHeal;
        private Feature feature = Feature.NONE;
        private List<String> racialTechniques = List.of();

        private Builder(Race race) {
            this.race = race;
        }

        /** Weights in attribute order: STR, DEX, CON, KI_POWER, WIL, MND, SPI. */
        public Builder costs(double str, double dex, double con, double kip, double wil, double mnd, double spi) {
            double[] w = {str, dex, con, kip, wil, mnd, spi};
            for (Attribute a : Attribute.values()) costWeights.put(a, w[a.ordinal()]);
            return this;
        }

        public Builder bonus(Attribute a, int points) { startBonus.put(a, points); return this; }
        public Builder aura(int color) { auraColor = color; return this; }
        public Builder tpGain(double bonus) { tpGainBonus = bonus; return this; }
        public Builder kiCostReduction(double r) { kiCostReduction = r; return this; }
        public Builder regen(double multiplier, double delayFactor) { regenMultiplier = multiplier; regenDelayFactor = delayFactor; return this; }
        public Builder zenkai(double percent) { zenkaiPercent = percent; return this; }
        public Builder tail() { tail = true; return this; }
        public Builder breathless() { breathless = true; return this; }
        public Builder noHunger() { noHunger = true; return this; }
        public Builder slowHunger() { slowHunger = true; return this; }
        public Builder staminaless() { staminaless = true; return this; }
        public Builder kiAbsorb(double fraction) { kiAbsorb = fraction; return this; }
        public Builder kiRegen(double multiplier) { kiRegenMultiplier = multiplier; return this; }
        public Builder staminaRegen(double multiplier) { staminaRegenMultiplier = multiplier; return this; }
        public Builder killHeal(double fraction) { killHeal = fraction; return this; }
        public Builder feature(Feature f) { feature = f; return this; }
        public Builder racial(String... techniqueIds) { racialTechniques = List.of(techniqueIds); return this; }

        public RaceTraits build() {
            return new RaceTraits(this);
        }
    }
}
