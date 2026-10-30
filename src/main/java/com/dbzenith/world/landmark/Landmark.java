package com.dbzenith.world.landmark;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

/**
 * The great landmarks (CX-33; Earth's, and Namek's since v0.166.0) and where they may stand. Each sits at most once in every {@code spacing}
 * square of the world (a random spot in the cell, from the world seed), only where the biome at its centre fits and
 * the ground suits it (each plan decides), and never overlapping an earlier landmark in this list. {@code radius} is
 * how far from its centre it builds (blocks), so the chunk builder knows which chunks it reaches.
 */
public enum Landmark {
    TOURNAMENT("tournament", 1024, 160, 0x7a11L, Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA,
            Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST)),
    LOOKOUT("lookout", 2048, 90, 0x10c0L, Set.of(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.PLAINS, Biomes.SAVANNA,
            Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.MEADOW, Biomes.TAIGA, Biomes.DARK_FOREST)),
    CELL_GAMES("cell_games", 1536, 130, 0xce11L, Set.of(Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.DESERT,
            Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.PLAINS)),
    FRYPAN("frypan_mountains", 1792, 240, 0xf7a9L, Set.of(Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.DESERT,
            Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA)),
    /** West City with Capsule Corporation (docs/WORLD_STRUCTURES.md): one per world, never on already explored ground. */
    WEST_CITY("west_city", 2048, 132, 0x3e57L, Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA, Biomes.FOREST,
            Biomes.BIRCH_FOREST, Biomes.BEACH), true, true),
    /** Kame House (canon): Master Roshi's house on its little island, out at sea. One per world. */
    KAME_HOUSE("kame_house", 2048, 44, 0x6a3eL, Set.of(Biomes.OCEAN, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN), true, true),
    /** The Red Ribbon Army's Muscle Tower (canon), in the snowy north. One per world. */
    MUSCLE_TOWER("muscle_tower", 2048, 36, 0x3c1eL, Set.of(Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.ICE_SPIKES, Biomes.GROVE,
            Biomes.SNOWY_SLOPES), true, true),
    /** A Red Ribbon Army field base (canon-inspired): a fenced compound with its garrison. */
    RIBBON_BASE("red_ribbon_base", 1536, 56, 0x8bbaL, Set.of(Biomes.DESERT, Biomes.BADLANDS, Biomes.SAVANNA, Biomes.PLAINS, Biomes.TAIGA,
            Biomes.SNOWY_PLAINS, Biomes.FOREST), false, true),
    /** A traditional village (canon-inspired): round houses, a well, fields, its people. */
    VILLAGE("village", 1024, 60, 0x7111L, Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA, Biomes.FOREST,
            Biomes.BIRCH_FOREST, Biomes.TAIGA, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.DESERT), false, true),
    /** The Grand Elder's house (canon) on Namek: his home on its rock spire, Nail on guard, Dende. One per world. */
    GRAND_ELDER("grand_elder", 1024, 40, 0x6e1dL, Set.of(Keys.NAMEK_BIOME), true, true, Keys.NAMEK, 400),        // far from the villages
    /** A Namekian village (canon-inspired) on Namek: white dome houses, the elder's house, a spring, an Ajisa grove. */
    NAMEK_VILLAGE("namek_village", 768, 56, 0x9a3bL, Set.of(Keys.NAMEK_BIOME), false, true, Keys.NAMEK);

    public static final Landmark[] ALL = values();

    public final String id;
    public final int spacing, radius;
    public final long salt;
    public final Set<ResourceKey<Biome>> biomes;
    /** Canon and one of a kind: built once per world (the first site that suits, spiralling out from the world's centre). */
    public final boolean unique;
    /** Added after worlds already existed: never built in regions that were on disk before it (see {@link LandmarkGuard}). */
    public final boolean guarded;
    /** The world it stands in (Earth unless said). */
    public final ResourceKey<Level> dimension;
    /** How much farther than their reaches later landmarks keep from it (the Grand Elder lives far from the villages). */
    public final int clearance;

    /** Keys of the mod's own worlds and biomes landmarks stand in. */
    public static final class Keys {
        public static final ResourceKey<Level> NAMEK = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("dbzenith", "namek"));
        public static final ResourceKey<Biome> NAMEK_BIOME = ResourceKey.create(Registries.BIOME, new ResourceLocation("dbzenith", "namek"));

        private Keys() {}
    }

    Landmark(String id, int spacing, int radius, long salt, Set<ResourceKey<Biome>> biomes) {
        this(id, spacing, radius, salt, biomes, false, false);
    }

    Landmark(String id, int spacing, int radius, long salt, Set<ResourceKey<Biome>> biomes, boolean unique, boolean guarded) {
        this(id, spacing, radius, salt, biomes, unique, guarded, Level.OVERWORLD);
    }

    Landmark(String id, int spacing, int radius, long salt, Set<ResourceKey<Biome>> biomes, boolean unique, boolean guarded, ResourceKey<Level> dimension) {
        this(id, spacing, radius, salt, biomes, unique, guarded, dimension, 0);
    }

    Landmark(String id, int spacing, int radius, long salt, Set<ResourceKey<Biome>> biomes, boolean unique, boolean guarded, ResourceKey<Level> dimension,
             int clearance) {
        this.dimension = dimension;
        this.clearance = clearance;
        this.id = id;
        this.spacing = spacing;
        this.radius = radius;
        this.salt = salt;
        this.biomes = biomes;
        this.unique = unique;
        this.guarded = guarded;
    }

    public static Landmark byId(String id) {
        for (Landmark l : ALL) if (l.id.equals(id)) return l;
        return null;
    }
}
