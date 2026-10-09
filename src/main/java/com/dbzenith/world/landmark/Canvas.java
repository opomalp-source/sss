package com.dbzenith.world.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where a landmark draws while one chunk generates (CX-33): every write is clipped to that chunk, so a structure
 * hundreds of blocks across is built a chunk at a time with no seams and nothing written into chunks that are not
 * being generated. Builders write in absolute block coordinates and ask {@link #intersects} to skip whole parts that
 * miss the chunk.
 */
public final class Canvas {
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();

    final WorldGenLevel level;
    /** The chunk being generated, inclusive block bounds. */
    public final int minX, minZ, maxX, maxZ;
    final int minY, maxY;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    Canvas(WorldGenLevel level, int chunkX, int chunkZ) {
        this.level = level;
        this.minX = chunkX << 4;
        this.minZ = chunkZ << 4;
        this.maxX = minX + 15;
        this.maxZ = minZ + 15;
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight() - 1;
    }

    /** Whether the horizontal rectangle x0..x1, z0..z1 (inclusive, any order) touches this chunk. */
    public boolean intersects(int x0, int z0, int x1, int z1) {
        return Math.max(x0, x1) >= minX && Math.min(x0, x1) <= maxX && Math.max(z0, z1) >= minZ && Math.min(z0, z1) <= maxZ;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public void set(int x, int y, int z, BlockState state) {
        if (x < minX || x > maxX || z < minZ || z > maxZ || y < minY || y > maxY) return;
        level.setBlock(pos.set(x, y, z), state, 2);
    }

    public BlockState get(int x, int y, int z) {
        if (!contains(x, z)) return AIR;
        return level.getBlockState(pos.set(x, y, z));
    }

    /** Fills the box between the two corners (inclusive, any order), clipped to the chunk. */
    public void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        int ax = Math.max(Math.min(x0, x1), minX), bx = Math.min(Math.max(x0, x1), maxX);
        int az = Math.max(Math.min(z0, z1), minZ), bz = Math.min(Math.max(z0, z1), maxZ);
        int ay = Math.max(Math.min(y0, y1), minY), by = Math.min(Math.max(y0, y1), maxY);
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++)
                for (int y = ay; y <= by; y++) level.setBlock(pos.set(x, y, z), state, 2);
    }

    /** A vertical run in one column. */
    public void column(int x, int z, int y0, int y1, BlockState state) {
        if (!contains(x, z)) return;
        for (int y = Math.max(Math.min(y0, y1), minY); y <= Math.min(Math.max(y0, y1), maxY); y++) level.setBlock(pos.set(x, y, z), state, 2);
    }

    /** The ground's top in this column as generated so far (the first solid block from the top), only inside the chunk. */
    public int surface(int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    /**
     * Shapes the ground in one column to {@code y}: cuts away everything above it (hills, trees) up to {@code clear}
     * blocks over it, and fills down from it to the ground below with {@code fill} under a {@code top} skin, so the
     * landmark neither floats nor sits in a pit.
     */
    public void ground(int x, int z, int y, BlockState top, BlockState fill, int clear) {
        if (!contains(x, z)) return;
        int s = surface(x, z);
        for (int k = y + 1; k <= Math.max(s, y) + clear && k <= maxY; k++) level.setBlock(pos.set(x, k, z), AIR, 2);
        if (s < y) for (int k = s + 1; k < y; k++) level.setBlock(pos.set(x, k, z), fill, 2);
        level.setBlock(pos.set(x, y, z), top, 2);
    }

    /** Spawns a persistent mob standing at x, y, z (if that spot is in this chunk), facing {@code yaw}. */
    public void spawn(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, double x, double y, double z, float yaw) {
        if (!contains((int) Math.floor(x), (int) Math.floor(z))) return;
        net.minecraft.world.entity.Mob mob = type.create(level.getLevel());
        if (mob == null) return;
        mob.moveTo(x, y, z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    // ------------------------------------------------------------------ states

    public static BlockState stairs(BlockState stairs, Direction facing, boolean upsideDown) {
        return stairs.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
    }

    public static BlockState slab(BlockState slab, boolean top) {
        return slab.setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    /** A cheap, stable hash of a position (for picking texture variants and scatter that agree across chunks). */
    public static int hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) h & 0x7fffffff;
    }
}
