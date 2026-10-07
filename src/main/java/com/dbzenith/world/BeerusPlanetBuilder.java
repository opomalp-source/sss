package com.dbzenith.world;

import com.dbzenith.npc.ModNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Builds Beerus's Planet the first time anyone lands (CX-17b): the God of Destruction's temple, a stepped violet
 * pyramid with a stair cut into its face and a domed pavilion on top where Beerus lounges; Whis waits at the foot of
 * the stairs. Around it: a road from the landing site, the pond of the Oracle Fish, tall round trees, mushroom-capped
 * rock pillars and rocks floating in the sky.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.dbzenith.DBZenith.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class BeerusPlanetBuilder {
    /** The temple's centre (the landing site is the origin, south of it). */
    public static final int TEMPLE_Z = -44;
    static final int GROUND = 1, TIERS = 5, TIER_H = 3, BASE_HALF = 18;
    public static final int TOP = GROUND + TIERS * TIER_H;              // where you stand on the pyramid

    private BeerusPlanetBuilder() {}

    static final class Built extends SavedData {
        boolean done;

        static Built load(CompoundTag tag) {
            Built b = new Built();
            b.done = tag.getBoolean("done");
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("done", done);
            return tag;
        }
    }

    /** However someone arrives (a pod, a command), the temple is there. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onArrive(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo() == Planet.BEERUS_PLANET.dimension() && event.getEntity().level() instanceof ServerLevel level) ensureBuilt(level);
    }

    public static void ensureBuilt(ServerLevel level) {
        if (level.dimension() != Planet.BEERUS_PLANET.dimension()) return;
        Built b = level.getDataStorage().computeIfAbsent(Built::load, Built::new, "dbzenith_beerus_planet_built");
        if (b.done) return;
        RandomSource rnd = RandomSource.create(1717);
        road(level);
        temple(level);
        pond(level, 24, -14);
        for (int[] t : new int[][]{{-26, -20}, {-30, -52}, {-14, -76}, {20, -74}, {30, -40}, {-38, 6}, {34, 10}}) tree(level, t[0], t[1], rnd);
        for (int[] m : new int[][]{{-52, -30}, {48, -60}, {-20, 34}}) mushroomRock(level, m[0], m[1], rnd);
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 2 * i / 9 + rnd.nextDouble() * 0.4, r = 46 + rnd.nextInt(40);
            floatingRock(level, (int) (Math.cos(a) * r), 28 + rnd.nextInt(34), TEMPLE_Z / 2 + (int) (Math.sin(a) * r), 3 + rnd.nextInt(4), rnd);
        }
        spawn(level, ModNpcs.BEERUS.get(), 0.5, TOP, TEMPLE_Z + 0.5, 0f);
        spawn(level, ModNpcs.WHIS.get(), 3.5, GROUND, TEMPLE_Z + BASE_HALF + 3.5, 0f);
        b.done = true;
        b.setDirty();
    }

    // ------------------------------------------------------------------ helpers

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block b) {
        set(level, x, y, z, b.defaultBlockState());
    }

    private static BlockState leaves(Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static <T extends Mob> void spawn(ServerLevel level, EntityType<T> type, double x, double y, double z, float yaw) {
        T mob = type.create(level);
        if (mob == null) return;
        mob.moveTo(x, y, z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    // ------------------------------------------------------------------ the temple

    static void road(ServerLevel level) {
        for (int z = TEMPLE_Z + BASE_HALF + 1; z <= 4; z++) for (int x = -2; x <= 2; x++) {
            set(level, x, GROUND - 1, z, Math.abs(x) == 2 ? Blocks.POLISHED_DEEPSLATE : Blocks.POLISHED_ANDESITE);
        }
    }

    static void temple(ServerLevel level) {
        int zc = TEMPLE_Z;
        for (int i = 0; i < TIERS; i++) {                                    // the stepped pyramid
            int half = BASE_HALF - (int) Math.round(i * 3.5), y0 = GROUND + i * TIER_H;
            for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) for (int y = y0; y < y0 + TIER_H; y++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                boolean corner = Math.abs(x) == half && Math.abs(z) == half;
                Block b = corner ? Blocks.QUARTZ_PILLAR : edge && y == y0 + TIER_H - 1 ? Blocks.GOLD_BLOCK
                        : edge ? ((x + z + y) % 5 == 0 ? Blocks.PURPUR_PILLAR : Blocks.PURPUR_BLOCK) : Blocks.SMOOTH_STONE;
                set(level, x, y, zc + z, b);
            }
        }
        int topHalf = BASE_HALF - (int) Math.round((TIERS - 1) * 3.5);
        for (int x = -topHalf; x <= topHalf; x++) for (int z = -topHalf; z <= topHalf; z++) {   // the top floor
            set(level, x, TOP - 1, zc + z, (x + z) % 2 == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.PURPUR_BLOCK);
        }
        for (int k = 0; k < TIERS * TIER_H; k++) {                           // the stair, cut into the south face
            int y = GROUND + k, z = zc + BASE_HALF - k;
            for (int x = -2; x <= 2; x++) {
                set(level, x, y, z, Math.abs(x) == 2 ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : Blocks.PURPUR_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                for (int above = y + 1; above < TOP; above++) set(level, x, above, z, Blocks.AIR);
                for (int below = GROUND; below < y; below++) set(level, x, below, z, Blocks.PURPUR_BLOCK);
            }
        }
        for (int sx : new int[]{-5, 5}) {                                    // braziers at the foot of the stair
            int z = zc + BASE_HALF + 2;
            for (int y = GROUND; y < GROUND + 3; y++) set(level, sx, y, z, Blocks.POLISHED_BLACKSTONE);
            set(level, sx, GROUND + 3, z, Blocks.SOUL_LANTERN);
        }
        // the pavilion: four pillars, a flat roof and a dome
        for (int px : new int[]{-3, 3}) for (int pz : new int[]{-3, 3}) {
            for (int y = TOP; y < TOP + 5; y++) set(level, px, y, zc + pz, Blocks.QUARTZ_PILLAR);
        }
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            set(level, x, TOP + 5, zc + z, Math.abs(x) == 4 || Math.abs(z) == 4 ? Blocks.GOLD_BLOCK : Blocks.PURPUR_BLOCK);
        }
        for (int x = -4; x <= 4; x++) for (int y = 1; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            double d = Math.sqrt(x * x + y * y * 1.3 + z * z);
            if (d <= 4.3 && d > 3.2) set(level, x, TOP + 5 + y, zc + z, y == 4 ? Blocks.GOLD_BLOCK : Blocks.PURPUR_BLOCK);
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {      // a cushion to lounge on
            set(level, x, TOP, zc + z + 1, Blocks.PURPLE_CARPET);
        }
    }

    // ------------------------------------------------------------------ the grounds

    /** The pond of the Oracle Fish. */
    static void pond(ServerLevel level, int cx, int cz) {
        for (int x = -7; x <= 7; x++) for (int z = -6; z <= 6; z++) {
            double d = Math.hypot(x / 7.0, z / 6.0);
            if (d > 1.12) continue;
            if (d > 1.0) {
                set(level, cx + x, GROUND - 1, cz + z, Blocks.SMOOTH_STONE);
                continue;
            }
            set(level, cx + x, GROUND - 1, cz + z, Blocks.WATER);
            set(level, cx + x, GROUND - 2, cz + z, d < 0.6 ? Blocks.WATER : Blocks.SAND);
            set(level, cx + x, GROUND - 3, cz + z, Blocks.SAND);
        }
        for (int i = 0; i < 3; i++) spawn(level, EntityType.TROPICAL_FISH, cx + 0.5 + i - 1, GROUND - 1.5, cz + 0.5, 0f);
    }

    /** A tall tree with a round crown. */
    static void tree(ServerLevel level, int x, int z, RandomSource rnd) {
        int h = 9 + rnd.nextInt(5);
        for (int y = GROUND; y < GROUND + h; y++) set(level, x, y, z, Blocks.DARK_OAK_LOG);
        int r = 4 + rnd.nextInt(2), cy = GROUND + h;
        for (int dx = -r; dx <= r; dx++) for (int dy = -r + 1; dy <= r - 1; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy * 1.6 + dz * dz > r * r) continue;
            set(level, x + dx, cy + dy, z + dz, leaves(rnd.nextInt(5) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
        }
    }

    /** A pillar of rock with a wide cap. */
    static void mushroomRock(ServerLevel level, int x, int z, RandomSource rnd) {
        int h = 16 + rnd.nextInt(8);
        for (int y = GROUND; y < GROUND + h; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (dx * dx + dz * dz <= 5) set(level, x + dx, y, z + dz, (y + dx) % 4 == 0 ? Blocks.ANDESITE : Blocks.STONE);
        }
        int r = 6 + rnd.nextInt(3);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = 0; dy <= 3; dy++) {
            double d = Math.hypot(dx, dz);
            if (d > r - dy * 1.2) continue;
            set(level, x + dx, GROUND + h + dy, z + dz, dy == 3 || d > r - dy * 1.2 - 1.2 && dy > 0 ? Blocks.MOSS_BLOCK : Blocks.STONE);
        }
    }

    /** A rock hanging in the sky, grass on top. */
    static void floatingRock(ServerLevel level, int x, int y, int z, int r, RandomSource rnd) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -r * 2; dy <= 0; dy++) {
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 0.3);
            if (d > r + rnd.nextDouble() * 0.6) continue;
            set(level, x + dx, y + dy, z + dz, dy == 0 ? Blocks.GRASS_BLOCK : dy > -2 ? Blocks.DIRT : Blocks.STONE);
        }
    }
}
