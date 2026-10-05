package com.dbzenith.race;

import com.dbzenith.stats.Race;

/**
 * Sub-races, clans, destinies and paths. Every race has a default variant; some offer a choice at creation (clans,
 * lineages), some are rare destinies rolled once at creation and revealed at the first milestone, and some are
 * paths chosen when that milestone is reached. Variants decide form lines (see Form#variants), racial skills, look.
 */
public enum Variant {
    // Saiyan lineages and destinies
    SAIYAN(Race.SAIYAN, Kind.DEFAULT, -1, null),
    PRIMAL(Race.SAIYAN, Kind.CLAN, 0xFFB070, null),
    LEGENDARY(Race.SAIYAN, Kind.RARE, 0x9CFF6A, null),
    LEGENDARY_PRIMAL(Race.SAIYAN, Kind.RARE, 0x7CFF4A, null),
    // Half-Saiyan paths
    HALF_SAIYAN(Race.HALF_SAIYAN, Kind.DEFAULT, -1, null),
    NEW_GENERATION(Race.HALF_SAIYAN, Kind.PATH, -1, null),
    FUTURE_LINEAGE(Race.HALF_SAIYAN, Kind.PATH, 0xFFE08A, null),
    AWAKENED_EVOLUTION(Race.HALF_SAIYAN, Kind.PATH, 0xFF8A6A, null),
    // Human paths
    HUMAN(Race.HUMAN, Kind.DEFAULT, -1, null),
    ANCIENT_HERMIT(Race.HUMAN, Kind.PATH, 0xF0E6C8, null),
    PEAK_HUMAN(Race.HUMAN, Kind.PATH, 0xE8F4FF, null),
    TRICLOPS(Race.HUMAN, Kind.PATH, 0xD8C8FF, null),
    // Namekian clans
    WARRIOR_CLAN(Race.NAMEKIAN, Kind.DEFAULT, -1, null),
    DRAGON_CLAN(Race.NAMEKIAN, Kind.CLAN, 0xE8FFF0, null),
    DEMON_CLAN(Race.NAMEKIAN, Kind.CLAN, 0xB070FF, null),
    // Frost Demon lineages and destinies
    FROST_DEMON(Race.FROST_DEMON, Kind.DEFAULT, -1, null),
    METAL(Race.FROST_DEMON, Kind.CLAN, 0xC8D8E8, null),
    MUTANT(Race.FROST_DEMON, Kind.RARE, 0xFF5AC8, null),
    // Majin
    MAJIN(Race.MAJIN, Kind.DEFAULT, -1, null),
    CORRUPTED(Race.MAJIN, Kind.RARE, 0x9A70C0, null),
    // Machines
    ANDROID(Race.ANDROID, Kind.DEFAULT, -1, null),
    CYBORG(Race.CYBORG, Kind.DEFAULT, -1, null),
    // Vampire bloodlines
    NOBLE_BLOOD(Race.VAMPIRE, Kind.DEFAULT, -1, null),
    FERAL_BLOOD(Race.VAMPIRE, Kind.CLAN, 0xFF3A3A, null),
    // Bio-Android strains
    APEX_STRAIN(Race.BIO_ANDROID, Kind.DEFAULT, -1, null),
    SWARM_STRAIN(Race.BIO_ANDROID, Kind.CLAN, 0x9AFF70, null),
    // Tuffle castes
    SCIENTIST_CASTE(Race.TUFFLE, Kind.DEFAULT, -1, null),
    PARASITE_CASTE(Race.TUFFLE, Kind.CLAN, 0xD0FF90, null),
    // Gen Alien adaptations
    HEAVYWORLDER(Race.GEN_ALIEN, Kind.DEFAULT, -1, null),
    VOIDBORN(Race.GEN_ALIEN, Kind.CLAN, 0x90A0FF, null),
    SWIFTKIN(Race.GEN_ALIEN, Kind.CLAN, 0x8AFFE0, null),
    // Core Person
    KAI(Race.CORE_PERSON, Kind.DEFAULT, -1, RaceTraits.Feature.EARS),
    DEMON(Race.CORE_PERSON, Kind.CLAN, 0xFF5050, RaceTraits.Feature.DEMON_HORNS);

    /** How a variant is obtained. */
    public enum Kind { DEFAULT, CLAN, RARE, PATH }

    private final Race race;
    private final Kind kind;
    private final int auraColor;
    private final RaceTraits.Feature feature;

    Variant(Race race, Kind kind, int auraColor, RaceTraits.Feature feature) {
        this.race = race;
        this.kind = kind;
        this.auraColor = auraColor;
        this.feature = feature;
    }

    public Race race() {
        return race;
    }

    public Kind kind() {
        return kind;
    }

    /** Aura override, or -1 to keep the race's. */
    public int auraColor() {
        return auraColor;
    }

    /** Head feature override, or null to keep the race's. */
    public RaceTraits.Feature feature() {
        return feature;
    }

    public String id() {
        return name().toLowerCase();
    }

    public String translationKey() {
        return "variant.dbzenith." + id();
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** The variant every new character of this race starts as. */
    public static Variant defaultFor(Race race) {
        for (Variant v : values()) if (v.race == race && v.kind == Kind.DEFAULT) return v;
        throw new IllegalStateException("no default variant for " + race);
    }

    public static Variant byId(String id, Race race) {
        for (Variant v : values()) if (v.id().equals(id) && v.race == race) return v;
        return defaultFor(race);
    }

    /** Variants offered at creation (the default plus clans and lineages). */
    public static java.util.List<Variant> creationChoices(Race race) {
        java.util.List<Variant> out = new java.util.ArrayList<>();
        for (Variant v : values()) if (v.race == race && (v.kind == Kind.DEFAULT || v.kind == Kind.CLAN)) out.add(v);
        return out;
    }

    /** Paths offered at the first milestone. */
    public static java.util.List<Variant> paths(Race race) {
        java.util.List<Variant> out = new java.util.ArrayList<>();
        for (Variant v : values()) if (v.race == race && v.kind == Kind.PATH) out.add(v);
        return out;
    }

    /** The rare destiny a creation choice can roll into, or null. */
    public static Variant rareFor(Variant chosen) {
        return switch (chosen) {
            case SAIYAN -> LEGENDARY;
            case PRIMAL -> LEGENDARY_PRIMAL;
            case FROST_DEMON -> MUTANT;
            case MAJIN -> CORRUPTED;
            default -> null;
        };
    }
}
