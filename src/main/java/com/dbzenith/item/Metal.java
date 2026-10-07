package com.dbzenith.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Metals and alloys (12e), ranked into ten tiers from the common metals of Earth to the hardest metal there is.
 * Natural metals come from ores (Earth's crust, Namek, the Kai's little planet, Limbo); alloys are blended at a
 * crafting table and smelted in a blast furnace. A metal's tier is what it is worth: plating a gi piece with it
 * ({@link GiPlating}) adds damage reduction by tier, and the heaviest training weights are forged from the top tiers.
 */
public enum Metal {
    COPPER(1, Kind.VANILLA, 0xE07850),
    TIN(1, Kind.ORE, 0xC8CCD4),
    ZINC(1, Kind.ORE, 0x98A6B2),
    IRON(2, Kind.VANILLA, 0xD8D8D8),
    GOLD(2, Kind.VANILLA, 0xF0C840),
    SILVER(2, Kind.ORE, 0xE6EAF2),
    BRONZE(2, Kind.ALLOY, 0xC88A3A),
    BRASS(2, Kind.ALLOY, 0xD8B850),
    STEEL(3, Kind.ALLOY, 0x7E848E),
    ELECTRUM(3, Kind.ALLOY, 0xE8D890),
    TITANIUM(4, Kind.ORE, 0x8C9CB4),
    TUNGSTEN(4, Kind.ORE, 0x5C6068),
    DURASTEEL(5, Kind.ALLOY, 0x5A6C80),
    TUNGSTEN_CARBIDE(5, Kind.ALLOY, 0x3C4048),
    MITHRIL(6, Kind.ORE, 0x9AE0F0),
    ADAMANTIUM(6, Kind.ORE, 0x7A5AA0),
    ORICHALCUM(7, Kind.ALLOY, 0xE8803A),
    KATCHIN(8, Kind.ORE, 0x4A8A7C),
    CELESTIAL_BRONZE(9, Kind.ALLOY, 0xF0CC70),
    KACHI_KATCHIN(10, Kind.ALLOY, 0x3A4C8A);

    /** Where a metal comes from. */
    public enum Kind { VANILLA, ORE, ALLOY }

    private final int tier;
    private final Kind kind;
    private final int color;

    Metal(int tier, Kind kind, int color) {
        this.tier = tier;
        this.kind = kind;
        this.color = color;
    }

    public int tier() { return tier; }
    public Kind kind() { return kind; }
    public int color() { return color; }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "metal.dbzenith." + id();
    }

    /** Damage reduction one plated gi piece adds: 0.3% per tier, so three pieces of the hardest add 9%. */
    public double platingReduction() {
        return tier * 0.003;
    }

    /** The ingot item (vanilla's for copper, iron and gold). */
    public Item ingot() {
        return switch (this) {
            case COPPER -> Items.COPPER_INGOT;
            case IRON -> Items.IRON_INGOT;
            case GOLD -> Items.GOLD_INGOT;
            default -> com.dbzenith.registry.ModItems.INGOTS.get(this).get();
        };
    }

    /** The metal an ingot is, or null. */
    public static Metal of(Item item) {
        for (Metal m : values()) if (m.ingot() == item) return m;
        return null;
    }

    public static Metal byId(String id) {
        for (Metal m : values()) if (m.id().equals(id)) return m;
        return null;
    }
}
