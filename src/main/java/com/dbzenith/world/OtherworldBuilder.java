package com.dbzenith.world;

import com.dbzenith.npc.ModNpcs;
import com.dbzenith.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Builds the other world the first time anyone arrives (CX-12), in code so it fits the flat cloud sea exactly:
 * <ul>
 *   <li>the check-in station: a floating quartz and gold platform, a vermilion gate, a pillared hall, a red carpet
 *       running up to Enma's great desk and throne, his ogre clerks at their desks;</li>
 *   <li>Snake Way: a scaled road on the serpent's back, winding six hundred blocks from its head behind the station;</li>
 *   <li>the little planet of the Kai of the north floating at its tail: his domed house, a tree, his monkey and cricket;</li>
 *   <li>the Grand Kai's paradise: a floating island of blossoming trees, a temple and three springs of godly ki,
 *       reached by a white road from the station;</li>
 *   <li>and Limbo below: a crimson wasteland of jagged spires and lava pools round a black gate kept by ogres.</li>
 * </ul>
 */
public final class OtherworldBuilder {
    public static final int FLOOR = 65;
    public static final int SNAKE_START = 40;
    public static final int SNAKE_LENGTH = 640;
    public static final int SNAKE_END_X = (int) Math.round(pathX(SNAKE_LENGTH));
    public static final int PARADISE_X = -240, PARADISE_Y = 70, PARADISE_Z = 160;

    private OtherworldBuilder() {}

    /** The road's sideways wander at a distance along it. */
    static double pathX(double s) {
        return 34 * Math.sin(s / 58.0) + 14 * Math.sin(s / 21.0);
    }

    static int pathY(double s) {
        return 67 + (int) Math.round(5 * Math.sin(s / 120.0));
    }

    /** Built once per level (saved). */
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

    public static void ensureBuilt(ServerLevel level) {
        Built b = level.getDataStorage().computeIfAbsent(Built::load, Built::new, "dbzenith_otherworld_built");
        if (b.done) return;
        if (level.dimension() == Otherworld.OTHERWORLD) {
            station(level);
            snakeWay(level);
            kaiPlanet(level);
            paradise(level);
        } else if (level.dimension() == Otherworld.LIMBO) {
            limbo(level);
        } else return;
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

    private static void box(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(level, x, y, z, b);
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

    // ------------------------------------------------------------------ the check-in station

    static void station(ServerLevel level) {
        int r = 34;
        for (int x = -r; x <= r; x++) for (int z = -r - 12; z <= r; z++) {
            double d = Math.hypot(x, z > 0 ? z : z * 0.75);                                 // a long oval, the gate end stretched
            if (d > r) continue;
            boolean ring = Math.abs(d - 20) < 0.55 || Math.abs(d - (r - 1.5)) < 0.55;
            set(level, x, FLOOR, z, ring ? Blocks.GOLD_BLOCK : (x + z) % 2 == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.QUARTZ_BLOCK);
            set(level, x, FLOOR - 1, z, Blocks.SMOOTH_QUARTZ);
            if (d > r - 1) set(level, x, FLOOR + 1, z, Blocks.QUARTZ_SLAB);                 // a low rim
        }
        for (int z = -44; z <= 8; z++) for (int x = -3; x <= 3; x++) {                     // the red carpet, gold-edged
            set(level, x, FLOOR + 1, z, Math.abs(x) == 3 ? Blocks.YELLOW_CARPET : Blocks.RED_CARPET);
        }
        // the gate: two vermilion pillars, a dark top beam with upturned ends, a red tie beam
        int gz = -34;
        for (int side = -1; side <= 1; side += 2) {
            box(level, side * 8 - 1, FLOOR + 1, gz - 1, side * 8 + 1, FLOOR + 12, gz + 1, Blocks.RED_TERRACOTTA);
            box(level, side * 8 - 1, FLOOR + 1, gz - 1, side * 8 + 1, FLOOR + 1, gz + 1, Blocks.BLACKSTONE);
        }
        box(level, -12, FLOOR + 13, gz - 1, 12, FLOOR + 14, gz + 1, Blocks.BLACK_TERRACOTTA);
        set(level, -13, FLOOR + 15, gz, Blocks.BLACK_TERRACOTTA);
        set(level, 13, FLOOR + 15, gz, Blocks.BLACK_TERRACOTTA);
        box(level, -9, FLOOR + 10, gz, 9, FLOOR + 10, gz, Blocks.RED_TERRACOTTA);
        box(level, -1, FLOOR + 11, gz, 1, FLOOR + 12, gz, Blocks.GOLD_BLOCK);              // a gold plaque between the beams
        // the hall: quartz pillars under an orange roof trimmed in gold, lit from under the eaves
        for (int side = -1; side <= 1; side += 2) for (int z = -20; z <= 16; z += 9) {
            box(level, side * 14, FLOOR + 1, z, side * 14, FLOOR + 14, z, Blocks.QUARTZ_PILLAR);
            set(level, side * 14, FLOOR + 15, z, Blocks.GOLD_BLOCK);
        }
        for (int x = -17; x <= 17; x++) for (int z = -23; z <= 20; z++) {
            boolean edge = Math.abs(x) == 17 || z == -23 || z == 20;
            set(level, x, FLOOR + 16, z, edge ? Blocks.GOLD_BLOCK : Blocks.ORANGE_TERRACOTTA);
            if (edge && (x + z) % 4 == 0) set(level, x, FLOOR + 15, z, Blocks.SHROOMLIGHT);
        }
        for (int x = -15; x <= 15; x += 5) for (int z = -21; z <= 18; z += 5) {             // the hall is lit, softly, from nowhere in particular
            set(level, x, FLOOR + 10, z, Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15));
        }
        for (int x = -12; x <= 12; x++) for (int z = -18; z <= 15; z++) {                   // a raised second roof
            set(level, x, FLOOR + 17, z, (Math.abs(x) == 12 || z == -18 || z == 15) ? Blocks.RED_TERRACOTTA : Blocks.ORANGE_TERRACOTTA);
        }
        // Enma's desk: a great dark block banded in gold, his throne behind
        box(level, -9, FLOOR + 1, 9, 9, FLOOR + 3, 12, Blocks.DARK_OAK_PLANKS);
        box(level, -9, FLOOR + 3, 9, 9, FLOOR + 3, 12, Blocks.POLISHED_BLACKSTONE);
        box(level, -9, FLOOR + 1, 9, 9, FLOOR + 1, 9, Blocks.GOLD_BLOCK);
        box(level, -9, FLOOR + 3, 9, 9, FLOOR + 3, 9, Blocks.GOLD_BLOCK);
        for (int x = -6; x <= 6; x += 3) set(level, x, FLOOR + 4, 10, Blocks.WHITE_CARPET);   // papers
        box(level, -4, FLOOR + 1, 18, 4, FLOOR + 11, 19, Blocks.RED_WOOL);
        box(level, -4, FLOOR + 11, 18, 4, FLOOR + 12, 19, Blocks.GOLD_BLOCK);
        box(level, -4, FLOOR + 1, 16, 4, FLOOR + 3, 17, Blocks.RED_WOOL);
        // the clerks' desks either side of the carpet
        for (int side = -1; side <= 1; side += 2) {
            box(level, side * 8 - 1, FLOOR + 1, -8, side * 8 + 1, FLOOR + 2, -7, Blocks.SPRUCE_PLANKS);
            set(level, side * 8, FLOOR + 3, -8, Blocks.WHITE_CARPET);
        }
        spawn(level, ModNpcs.ENMA.get(), 0.5, FLOOR + 1, 14.5, 180f);
        RandomSource rnd = RandomSource.create(77);                                         // cloud puffs heaped on the sea, for depth
        BlockState puff = ModBlocks.OTHERWORLD_CLOUD.get().defaultBlockState();
        for (int k = 0; k < 420; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 42 + rnd.nextDouble() * 170;
            int px = (int) (Math.cos(a) * d), pz = (int) (Math.sin(a) * d) + 120, rx = 2 + rnd.nextInt(4), ry = 1 + rnd.nextInt(2);
            for (int x = -rx; x <= rx; x++) for (int y = 0; y <= ry; y++) for (int z = -rx; z <= rx; z++) {
                if ((x * x + z * z) / (double) (rx * rx) + y * y / (double) (ry * ry + 0.5) > 1) continue;
                BlockPos at = new BlockPos(px + x, 64 + y, pz + z);
                if (level.getBlockState(at).isAir()) level.setBlock(at, puff, Block.UPDATE_CLIENTS);
            }
        }
        spawn(level, ModNpcs.OGRE_CLERK_RED.get(), -7.5, FLOOR + 1, -5.5, 180f);
        spawn(level, ModNpcs.OGRE_CLERK_BLUE.get(), 8.5, FLOOR + 1, -5.5, 180f);
        spawn(level, ModNpcs.OGRE_CLERK_BLUE.get(), -5.5, FLOOR + 1, -26.5, 0f);
        spawn(level, ModNpcs.OGRE_CLERK_RED.get(), 6.5, FLOOR + 1, -26.5, 0f);
    }

    // ------------------------------------------------------------------ Snake Way

    static void snakeWay(ServerLevel level) {
        BlockState scale = ModBlocks.SNAKE_SCALE.get().defaultBlockState();
        BlockState belly = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        int prevX = (int) Math.round(pathX(0));
        for (int s = 0; s <= SNAKE_LENGTH; s++) {
            int cx = (int) Math.round(pathX(s)), y = pathY(s), z = SNAKE_START + s;
            int half = s > SNAKE_LENGTH - 30 ? Math.max(0, (SNAKE_LENGTH - s) / 15) : 1;        // the tail narrows
            for (int x = Math.min(prevX, cx) - half; x <= Math.max(prevX, cx) + half; x++) {
                set(level, x, y, z, scale);
                set(level, x, y - 1, z, scale);
                set(level, x, y - 2, z, belly);
            }
            prevX = cx;
        }
        // the serpent's head behind the station: a great scaled head, red-eyed, the road rising from its crown
        int hz = SNAKE_START, hy = pathY(0), hx = (int) Math.round(pathX(0));
        for (int x = -5; x <= 5; x++) for (int y = -4; y <= 3; y++) for (int z = -9; z <= 0; z++) {
            double e = Math.pow(x / 5.5, 2) + Math.pow((y + 0.5) / 4.2, 2) + Math.pow((z + 4.5) / 5.5, 2);
            if (e > 1) continue;
            boolean mouth = y == -2 && z < -5;
            set(level, hx + x, hy + y, hz + z, mouth ? Blocks.BLACK_CONCRETE.defaultBlockState() : y < -2 ? belly : scale);
        }
        for (int side = -1; side <= 1; side += 2) {
            set(level, hx + side * 3, hy + 1, hz - 9, Blocks.WHITE_CONCRETE);
            set(level, hx + side * 3, hy + 1, hz - 10, Blocks.REDSTONE_BLOCK);
            set(level, hx + side * 2, hy + 2, hz - 9, Blocks.ORANGE_TERRACOTTA);                // brow ridges
        }
    }

    // ------------------------------------------------------------------ the Kai of the north's planet

    static void kaiPlanet(ServerLevel level) {
        int cx = (int) Otherworld.KAI_PLANET.x, cy = (int) Otherworld.KAI_PLANET.y, cz = (int) Otherworld.KAI_PLANET.z, r = Otherworld.KAI_PLANET_RADIUS;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d > r) continue;
            Block b = d > r - 1 ? (y > -r / 3 ? Blocks.GRASS_BLOCK : Blocks.DIRT) : d > r - 4 ? Blocks.DIRT : Blocks.STONE;
            set(level, cx + x, cy + y, cz + z, b);
        }
        int top = cy + r;
        // his house: a white dome with a red cap, a doorway facing the road
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            double d = Math.sqrt(x * x + y * y * 1.3 + z * z);
            if (d > 4.3 || d < 3.3) continue;
            set(level, cx + x + 6, top + 1 + y, cz + z + 5, y >= 3 ? Blocks.RED_TERRACOTTA : Blocks.WHITE_CONCRETE);
        }
        box(level, cx + 6, top + 1, cz + 1, cx + 6, top + 2, cz + 1, Blocks.AIR);
        set(level, cx + 6, top + 4, cz + 1, Blocks.GLASS);
        // a tree
        box(level, cx - 6, top + 1, cz + 4, cx - 6, top + 5, cz + 4, Blocks.OAK_LOG);
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            if (x * x + y * y + z * z <= 5) set(level, cx - 6 + x, top + 5 + y, cz + 4 + z, Blocks.OAK_LEAVES);
        }
        spawn(level, ModNpcs.NORTH_KAI.get(), cx + 0.5, top + 1, cz - 3.5, 180f);
        spawn(level, ModNpcs.TRAINING_MONKEY.get(), cx + 3.5, top + 1, cz - 6.5, 0f);
        spawn(level, ModNpcs.TRAINING_CRICKET.get(), cx - 3.5, top + 1, cz - 5.5, 0f);
    }

    // ------------------------------------------------------------------ the Grand Kai's paradise

    static void paradise(ServerLevel level) {
        int px = PARADISE_X, py = PARADISE_Y, pz = PARADISE_Z, r = 38;
        RandomSource rnd = RandomSource.create(4242);
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double d = Math.hypot(x, z);
            if (d > r) continue;
            int depth = (int) (3 + (r - d) * 0.7 + rnd.nextInt(3));                         // a floating island, deep in the middle
            for (int y = 0; y < depth; y++) set(level, px + x, py - y, pz + z, y == 0 ? Blocks.GRASS_BLOCK : y < 4 ? Blocks.DIRT : Blocks.STONE);
        }
        // the white road from the station's west side
        for (int s = 0; s <= 200; s++) {
            double t = s / 200.0;
            int x = (int) Math.round(-36 + (px + 30 + 36) * t), z = (int) Math.round(pz * t * t), y = FLOOR + (int) Math.round((py - FLOOR) * t);
            for (int w = -2; w <= 2; w++) set(level, x, y, z + w, Math.abs(w) == 2 ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ);
        }
        // blossoming trees
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + 0.3;
            int tx = px + (int) (Math.cos(a) * 27), tz = pz + (int) (Math.sin(a) * 27);
            box(level, tx, py + 1, tz, tx, py + 5, tz, Blocks.CHERRY_LOG);
            for (int x = -3; x <= 3; x++) for (int y = 0; y <= 2; y++) for (int z = -3; z <= 3; z++) {
                if (x * x + y * y * 2 + z * z <= 10) set(level, tx + x, py + 5 + y, tz + z, Blocks.CHERRY_LEAVES);
            }
        }
        // the temple: a quartz floor, pillars, a purple roof edged in gold
        int tz0 = pz - 20;
        box(level, px - 7, py + 1, tz0 - 5, px + 7, py + 1, tz0 + 5, Blocks.SMOOTH_QUARTZ);
        for (int x = -6; x <= 6; x += 4) for (int z = -4; z <= 4; z += 8) box(level, px + x, py + 2, tz0 + z, px + x, py + 8, tz0 + z, Blocks.QUARTZ_PILLAR);
        for (int x = -8; x <= 8; x++) for (int z = -6; z <= 6; z++) {
            set(level, px + x, py + 9, tz0 + z, Math.abs(x) == 8 || Math.abs(z) == 6 ? Blocks.GOLD_BLOCK : Blocks.PURPLE_TERRACOTTA);
        }
        box(level, px - 5, py + 10, tz0 - 3, px + 5, py + 10, tz0 + 3, Blocks.PURPLE_TERRACOTTA);
        // three springs of godly ki
        BlockState spring = ModBlocks.SACRED_SPRING.get().defaultBlockState();
        int[][] pools = {{-13, 10}, {0, 16}, {13, 10}};
        for (int[] pool : pools) {
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                double d = Math.hypot(x, z);
                if (d > 5) continue;
                int bx = px + pool[0] + x, bz = pz + pool[1] + z;
                if (d > 4) {
                    set(level, bx, py, bz, Blocks.POLISHED_DIORITE);
                    set(level, bx, py + 1, bz, d > 4.6 ? Blocks.QUARTZ_SLAB : Blocks.AIR);
                } else {
                    set(level, bx, py - 1, bz, Blocks.PRISMARINE_BRICKS);
                    set(level, bx, py, bz, spring);
                }
            }
        }
        spawn(level, ModNpcs.GRAND_KAI.get(), px + 0.5, py + 2, tz0 + 0.5, 0f);
    }

    // ------------------------------------------------------------------ Limbo

    static void limbo(ServerLevel level) {
        int g = 60;
        RandomSource rnd = RandomSource.create(666);
        // the black gate at the arrival point
        for (int side = -1; side <= 1; side += 2) {
            box(level, side * 7 - 1, g + 1, -1, side * 7 + 1, g + 14, 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
            box(level, side * 7 - 1, g + 15, -1, side * 7 + 1, g + 15, 1, Blocks.RED_NETHER_BRICKS);
        }
        box(level, -9, g + 16, -1, 9, g + 17, 1, Blocks.RED_NETHER_BRICKS);
        box(level, -1, g + 13, 0, 1, g + 15, 0, Blocks.SHROOMLIGHT);
        // jagged spires and lava pools across the waste
        for (int k = 0; k < 70; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 14 + rnd.nextDouble() * 90;
            int sx = (int) (Math.cos(a) * d), sz = (int) (Math.sin(a) * d), h = 6 + rnd.nextInt(18);
            for (int y = 0; y < h; y++) {
                int w = (int) Math.max(0, (1 - y / (double) h) * 2.5);
                for (int x = -w; x <= w; x++) for (int z = -w; z <= w; z++) {
                    set(level, sx + x, g + 1 + y, sz + z, rnd.nextInt(4) == 0 ? Blocks.BASALT : Blocks.BLACKSTONE);
                }
            }
        }
        for (int k = 0; k < 10; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 20 + rnd.nextDouble() * 70;
            int lx = (int) (Math.cos(a) * d), lz = (int) (Math.sin(a) * d), lr = 2 + rnd.nextInt(3);
            for (int x = -lr; x <= lr; x++) for (int z = -lr; z <= lr; z++) {
                if (x * x + z * z <= lr * lr) set(level, lx + x, g, lz + z, Blocks.LAVA);
            }
        }
        spawn(level, ModNpcs.OGRE_GUARD.get(), -4.5, g + 1, 3.5, 180f);
        spawn(level, ModNpcs.OGRE_GUARD.get(), 5.5, g + 1, 3.5, 180f);
    }
}
