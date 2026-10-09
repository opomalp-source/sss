package com.dbzenith.world.landmark;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

/**
 * The great landmarks of Earth (CX-33) and where they may stand. Each sits at most once in every {@code spacing}
 * square of the world (a random spot in the cell, from the world seed), only where the biome at its centre fits and
 * the ground suits it (each plan decides), and never overlapping an earlier landmark in this list. {@code radius} is
 * how far from its centre it builds (blocks), so the chunk builder knows which chunks it reaches.
 */
public enum Landmark {
    TOURNAMENT("tournament", 1024, 160, 0x7a11L, Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA,
            Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST)),
    LOOKOUT("lookout", 2048, 90, 0x10c0L, Set.of(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.PLAINS, Biomes.SAVANNA,
            Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.MEADOW, Biomes.TAIGA, Biomes.DARK_FOREST)),
    CELL_GAMES("cell_games", 1792, 130, 0xce11L, Set.of(Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.DESERT,
            Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA)),
    FRYPAN("frypan_mountains", 2048, 240, 0xf7a9L, Set.of(Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.DESERT)),
    WEST_CITY("west_city", 1792, 200, 0x3e57L, Set.of(Biomes.BEACH));

    public static final Landmark[] ALL = values();

    public final String id;
    public final int spacing, radius;
    public final long salt;
    public final Set<ResourceKey<Biome>> biomes;

    Landmark(String id, int spacing, int radius, long salt, Set<ResourceKey<Biome>> biomes) {
        this.id = id;
        this.spacing = spacing;
        this.radius = radius;
        this.salt = salt;
        this.biomes = biomes;
    }

    public static Landmark byId(String id) {
        for (Landmark l : ALL) if (l.id.equals(id)) return l;
        return null;
    }
}
