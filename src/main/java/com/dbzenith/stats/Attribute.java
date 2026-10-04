package com.dbzenith.stats;

import java.util.Locale;

/**
 * The seven trainable base attributes. Every derived stat is computed from these in {@link StatCalculator}.
 * Named "Attribute" in the DBC sense; not to be confused with vanilla {@code net.minecraft.world.entity.ai.attributes.Attribute}.
 */
public enum Attribute {
    /** Melee damage. */
    STRENGTH("str"),
    /** Defense, evasion, attack speed, movement. */
    DEXTERITY("dex"),
    /** Body (health) and stamina. */
    CONSTITUTION("con"),
    /** Ki attack damage. */
    KI_POWER("kip"),
    /** Ki pool size and ki regeneration. */
    WILLPOWER("wil"),
    /** Ki control (spend efficiency) and training-point gain. */
    MIND("mnd"),
    /** Spirit modifier: mastery gain, ki transfer. */
    SPIRIT("spi");

    private final String shortName;

    Attribute(String shortName) {
        this.shortName = shortName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String shortName() {
        return shortName;
    }

    public String translationKey() {
        return "stat.dbzenith." + id();
    }

    public static Attribute byId(String id) {
        for (Attribute a : values()) {
            if (a.id().equals(id) || a.shortName.equals(id)) return a;
        }
        return null;
    }
}
