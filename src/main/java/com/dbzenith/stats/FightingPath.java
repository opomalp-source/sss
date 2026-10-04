package com.dbzenith.stats;

import java.util.Locale;

/**
 * Class/path choice. Weights the TP cost of raising each attribute (see {@link StatCalculator#tpCost}).
 */
public enum FightingPath {
    /** Cheaper physical attributes. */
    FIGHTER,
    /** Cheaper ki/mental attributes. */
    SPIRITUALIST,
    /** No discounts, no penalties. */
    HYBRID;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "path.dbzenith." + id();
    }

    /** Multiplier applied to the TP cost of raising {@code attribute}. Below 1 = cheaper. */
    public double costWeight(Attribute attribute) {
        return switch (this) {
            case FIGHTER -> switch (attribute) {
                case STRENGTH, DEXTERITY, CONSTITUTION -> 0.85;
                case KI_POWER, WILLPOWER, MIND, SPIRIT -> 1.15;
            };
            case SPIRITUALIST -> switch (attribute) {
                case STRENGTH, DEXTERITY, CONSTITUTION -> 1.15;
                case KI_POWER, WILLPOWER, MIND, SPIRIT -> 0.85;
            };
            case HYBRID -> 1.0;
        };
    }

    public static FightingPath byId(String id) {
        for (FightingPath p : values()) {
            if (p.id().equals(id)) return p;
        }
        return HYBRID;
    }
}
