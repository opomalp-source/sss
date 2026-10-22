package com.dbzenith.living;

import com.dbzenith.npc.NpcStructures;
import com.dbzenith.world.landmark.LandmarkSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds Ginger Town (the small town of the series) from code, no structure files: the ground levelled, two roads
 * crossing at a plaza with a fountain and market stalls, gates where the roads leave, seven homes (two beds each), a
 * shop, a restaurant and a dojo ({@link NpcStructures}). Every place a resident goes is registered as a {@link Poi} on
 * the floor its building put there. Built on the overworld once for a new world, or anywhere by command
 * ({@code /dbz settlement build}); never over an existing world on its own.
 */
public final class TownBuilder {
    public static final String GINGER_TOWN = "Ginger Town";
    /** How far the levelled ground reaches from the centre (its last few blocks blend into the land). */
    public static final int RADIUS = 40;
    static final int BLEND = 7;

    private TownBuilder() {}

    // ------------------------------------------------------------------ choosing a site

    /** A dry, fairly flat spot 64-184 blocks from {@code near}, read from the terrain as generation makes it (no chunks made). */
    public static BlockPos findSite(ServerLevel level, BlockPos near) {
        LandmarkSites.Terrain t = LandmarkSites.Terrain.of(level);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int ring = 0; ring < 6; ring++) {
            int r = 64 + ring * 24;
            for (int a = 0; a < 12; a++) {
                double ang = a * Math.PI / 6 + ring * 0.37;
                int x = near.getX() + (int) Math.round(Math.cos(ang) * r), z = near.getZ() + (int) Math.round(Math.sin(ang) * r);
                double[] s = survey(t, x, z);
                if (s == null) continue;
                double score = s[0] + Math.abs(s[1] - t.seaLevel() - 4) * 0.15 + ring * 0.5;
                if (score < bestScore) {
                    bestScore = score;
                    best = new BlockPos(x, (int) s[1], z);
                }
            }
            if (best != null && bestScore < 8) break;
        }
        return best != null ? best : new BlockPos(near.getX(), t.height(near.getX(), near.getZ()), near.getZ());
    }

    /** {spread of heights, median height} over the town's square, or null if any of it is under water. */
    static double[] survey(LandmarkSites.Terrain t, int x, int z) {
        List<Integer> hs = new ArrayList<>();
        for (int dx = -32; dx <= 32; dx += 16)
            for (int dz = -32; dz <= 32; dz += 16) {
                if (t.wet(x + dx, z + dz)) return null;
                hs.add(t.height(x + dx, z + dz));
            }
        hs.sort(Integer::compare);
        return new double[]{hs.get(hs.size() - 1) - hs.get(0), hs.get(hs.size() / 2)};
    }

    // ------------------------------------------------------------------ building

    /** Builds a town centred on {@code site} (its y: the ground level to build at) and returns it, registered with its places. */
    public static Settlement build(ServerLevel level, BlockPos site, String name) {
        for (int cx = (site.getX() - RADIUS) >> 4; cx <= (site.getX() + RADIUS) >> 4; cx++)
            for (int cz = (site.getZ() - RADIUS) >> 4; cz <= (site.getZ() + RADIUS) >> 4; cz++) level.getChunk(cx, cz);
        int y0 = site.getY();
        BlockPos c = new BlockPos(site.getX(), y0, site.getZ());
        Settlement s = new Settlement(UUID.randomUUID());
        s.name = name;
        s.dimension = level.dimension().location().toString();
        s.center = c.above();
        s.radius = RADIUS + 4;
        level(level, c);
        roads(level, c, s);
        plaza(level, c, s);
        int[] roofs = {0, 1, 2, 3, 4, 5, 6};
        int[][] homes = {{12, -12}, {24, -12}, {-24, -12}, {24, 12}, {-24, 12}, {12, 26}, {-12, 26}};
        for (int i = 0; i < homes.length; i++) {
            boolean flip = homes[i][1] < 0;                                             // doors face the main road
            house(level, new Frame(c.offset(homes[i][0], 0, homes[i][1]), flip), roofs[i], s, i);
        }
        shop(level, new Frame(c.offset(-12, 0, 12), false), s);
        restaurant(level, new Frame(c.offset(-12, 0, -12), true), s);
        greenery(level, c);
        BlockPos dojo = NpcStructures.build(level, c.offset(12, 0, 12), NpcStructures.Kind.DOJO);   // open to the north: the road
        s.pois.add(new Poi(Poi.Kind.DOJO, dojo.offset(0, 0, 1), "master"));
        s.pois.add(new Poi(Poi.Kind.DOJO, dojo.offset(-1, 0, -1), "floor"));
        s.pois.add(new Poi(Poi.Kind.DOJO, dojo.offset(1, 0, -1), "floor"));
        s.pois.add(new Poi(Poi.Kind.DOJO, dojo.offset(-1, 0, 0), "floor"));
        s.pois.add(new Poi(Poi.Kind.DOJO, dojo.offset(1, 0, 0), "floor"));
        return s;
    }

    /** Levels the ground: flat inside, blending into the land at the edge; trees and hills cleared, holes and water filled. */
    static void level(ServerLevel level, BlockPos c) {
        int y0 = c.getY();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = c.getX() + dx, z = c.getZ() + dz;
                double d = Math.sqrt(dx * dx + dz * dz);                                      // round, not a square slab
                if (d > RADIUS) continue;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                double f = d <= RADIUS - BLEND ? 0 : (d - (RADIUS - BLEND)) / (double) BLEND;
                int target = (int) Math.round(y0 + (ground - y0) * f * f);
                int top = Math.max(ground, target) + (f == 0 ? 20 : 6);
                for (int y = target + 1; y <= top; y++) set(level, new BlockPos(x, y, z), air);
                set(level, new BlockPos(x, target, z), grass);
                for (int y = target - 1; y >= target - 3; y--) set(level, new BlockPos(x, y, z), dirt);
                for (int y = target - 4; y >= target - 16; y--) {                        // fill caves and water under it
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).isSolidRender(level, p)) break;
                    set(level, p, dirt);
                }
            }
    }

    static void roads(ServerLevel level, BlockPos c, Settlement s) {
        BlockState path = Blocks.DIRT_PATH.defaultBlockState(), stone = Blocks.STONE_BRICKS.defaultBlockState();
        int end = RADIUS - BLEND - 1;
        for (int i = -end; i <= end; i++)
            for (int w = -1; w <= 1; w++) {
                set(level, c.offset(i, 0, w), path);
                set(level, c.offset(w, 0, i), path);
            }
        for (int side : new int[]{-1, 1}) {                                              // gates on the east-west road
            for (int y = 1; y <= 4; y++) {
                set(level, c.offset(side * end, y, -2), stone);
                set(level, c.offset(side * end, y, 2), stone);
            }
            for (int w = -2; w <= 2; w++) set(level, c.offset(side * end, 5, w), Blocks.STONE_BRICK_SLAB.defaultBlockState());
            set(level, c.offset(side * end, 4, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            set(level, c.offset(side * end, 4, 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            s.pois.add(new Poi(Poi.Kind.GATE, c.offset(side * (end - 2), 1, 0), side < 0 ? "west" : "east"));
        }
        int p = end - 6;                                                                 // the guards' round
        s.pois.add(new Poi(Poi.Kind.PATROL, c.offset(p, 1, 0), "east road"));
        s.pois.add(new Poi(Poi.Kind.PATROL, c.offset(0, 1, p), "south road"));
        s.pois.add(new Poi(Poi.Kind.PATROL, c.offset(-p, 1, 0), "west road"));
        s.pois.add(new Poi(Poi.Kind.PATROL, c.offset(0, 1, -p), "north road"));
        for (int[] l : new int[][]{{p, 3}, {-p, -3}, {3, p}, {-3, -p}}) lamp(level, c.offset(l[0], 0, l[1]));
    }

    static void plaza(ServerLevel level, BlockPos c, Settlement s) {
        BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState(), border = Blocks.STONE_BRICKS.defaultBlockState();
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) set(level, c.offset(dx, 0, dz), Math.max(Math.abs(dx), Math.abs(dz)) == 7 ? border : floor);
        for (int dx = -2; dx <= 2; dx++)                                                 // the fountain
            for (int dz = -2; dz <= 2; dz++) {
                int e = Math.max(Math.abs(dx), Math.abs(dz));
                set(level, c.offset(dx, -1, dz), border);
                if (e == 2) set(level, c.offset(dx, 1, dz), Blocks.STONE_BRICK_WALL.defaultBlockState());
                else set(level, c.offset(dx, 0, dz), e == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.WATER.defaultBlockState());
            }
        set(level, c.offset(0, 1, 0), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        set(level, c.offset(0, 2, 0), Blocks.LANTERN.defaultBlockState());
        for (int[] l : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) lamp(level, c.offset(l[0], 0, l[1]));
        for (int[] b : new int[][]{{4, -5, 0}, {-4, 5, 1}}) {                            // benches facing the fountain
            Direction facing = b[2] == 0 ? Direction.SOUTH : Direction.NORTH;
            for (int w = -1; w <= 1; w++) set(level, c.offset(b[0] + w, 1, b[1]), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite()));
        }
        stall(level, c.offset(-5, 0, -4), Blocks.RED_WOOL.defaultBlockState());          // market stalls
        stall(level, c.offset(5, 0, 4), Blocks.YELLOW_WOOL.defaultBlockState());
        s.pois.add(new Poi(Poi.Kind.MARKET, c.offset(-5, 1, -2), "red stall"));
        s.pois.add(new Poi(Poi.Kind.MARKET, c.offset(5, 1, 2), "yellow stall"));
        s.pois.add(new Poi(Poi.Kind.PLAZA, c.offset(4, 1, -4), "bench"));
        s.pois.add(new Poi(Poi.Kind.PLAZA, c.offset(-4, 1, 4), "bench"));
        s.pois.add(new Poi(Poi.Kind.PLAZA, c.offset(3, 1, 3), "fountain"));
        s.pois.add(new Poi(Poi.Kind.PLAZA, c.offset(-3, 1, -3), "fountain"));
        s.pois.add(new Poi(Poi.Kind.PLAZA, c.offset(-3, 1, 3), "fountain"));
    }

    /** Trees round the edge of town (the game's own oak and birch) and a few hedges along the roads. */
    static void greenery(ServerLevel level, BlockPos c) {
        var features = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
        var gen = level.getChunkSource().getGenerator();
        int[][] trees = {{30, 24, 0}, {-30, 24, 1}, {30, -24, 1}, {-30, -24, 0}, {5, -31, 0}, {-5, 33, 1}, {34, -8, 1}, {-34, 8, 0}, {20, 35, 0}, {-20, -34, 1}};
        for (int[] t : trees) {
            var key = t[2] == 0 ? net.minecraft.data.worldgen.features.TreeFeatures.OAK : net.minecraft.data.worldgen.features.TreeFeatures.BIRCH;
            int x = c.getX() + t[0], z = c.getZ() + t[1];
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            features.getHolder(key).ifPresent(h -> h.value().place(level, gen, level.random, at));
        }
        BlockState hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int side : new int[]{-1, 1})                                                  // hedges lining the roads out of the plaza
            for (int i = 9; i <= 19; i += 2) {
                set(level, c.offset(i * side, 1, 3), hedge);
                set(level, c.offset(i * side, 1, -3), hedge);
                set(level, c.offset(3, 1, i * side), hedge);
                set(level, c.offset(-3, 1, i * side), hedge);
            }
    }

    /** A market stall: a counter of barrels under a wool awning on posts. */
    static void stall(ServerLevel level, BlockPos b, BlockState awning) {
        for (int w = -1; w <= 1; w++) set(level, b.offset(w, 1, 0), Blocks.BARREL.defaultBlockState());
        for (int[] p : new int[][]{{-1, -1}, {1, -1}}) for (int y = 1; y <= 2; y++) set(level, b.offset(p[0], y, p[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
        for (int w = -1; w <= 1; w++) for (int d = -1; d <= 0; d++) set(level, b.offset(w, 3, d), awning);
    }

    static void lamp(ServerLevel level, BlockPos b) {
        for (int y = 1; y <= 3; y++) set(level, b.offset(0, y, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(level, b.offset(0, 4, 0), Blocks.LANTERN.defaultBlockState());
    }

    // ------------------------------------------------------------------ buildings (a local frame: the door in the -z wall)

    /** A building's frame: its centre, and whether it is turned round (door to +z in the world). */
    record Frame(BlockPos origin, boolean flip) {
        BlockPos at(int x, int y, int z) {
            return flip ? origin.offset(-x, y, -z) : origin.offset(x, y, z);
        }

        Direction dir(Direction local) {
            return flip && local.getAxis().isHorizontal() ? local.getOpposite() : local;
        }
    }

    static final BlockState[] ROOFS = {Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.BLUE_TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.GREEN_TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.CYAN_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState()};

    /** Walls, floor and a rounded roof (the town's dome houses): {@code rx} by {@code rz} half-sizes, an open doorway {@code door} wide. */
    static void shell(ServerLevel level, Frame f, int rx, int rz, BlockState roof, int door) {
        BlockState wall = Blocks.WHITE_CONCRETE.defaultBlockState(), floor = Blocks.OAK_PLANKS.defaultBlockState(),
                trim = Blocks.SMOOTH_STONE.defaultBlockState(), glass = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
        for (int x = -rx; x <= rx; x++)
            for (int z = -rz; z <= rz; z++) {
                set(level, f.at(x, 0, z), Math.abs(x) == rx || Math.abs(z) == rz ? trim : floor);
                for (int y = 1; y <= 6; y++) set(level, f.at(x, y, z), Blocks.AIR.defaultBlockState());
            }
        for (int y = 1; y <= 3; y++)
            for (int x = -rx; x <= rx; x++)
                for (int z = -rz; z <= rz; z++) {
                    if (Math.abs(x) != rx && Math.abs(z) != rz) continue;
                    boolean doorway = z == -rz && Math.abs(x) <= door / 2 && y <= 2;
                    boolean window = y == 2 && !doorway && (Math.abs(x) == rx && Math.abs(z) <= rz - 2 && (z & 1) == 0
                            || Math.abs(z) == rz && Math.abs(x) >= 2 && Math.abs(x) <= rx - 1 && (x & 1) == 0);
                    set(level, f.at(x, y, z), doorway ? Blocks.AIR.defaultBlockState() : window ? glass : y == 3 ? trim : wall);
                }
        for (int y = 4; y <= 6; y++) {                                                  // the dome
            int in = y - 4;
            for (int x = -rx + in; x <= rx - in; x++)
                for (int z = -rz + in; z <= rz - in; z++) {
                    boolean edge = Math.abs(x) == rx - in || Math.abs(z) == rz - in;
                    if (y == 6 || edge || y == 4) set(level, f.at(x, y, z), y == 6 && edge ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState() : roof);
                }
        }
        set(level, f.at(0, 3, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** A home for two: two beds against the back wall, a table, a chest. */
    static void house(ServerLevel level, Frame f, int roof, Settlement s, int index) {
        shell(level, f, 3, 3, ROOFS[roof % ROOFS.length], 1);
        bed(level, f, -2, 1, Direction.SOUTH, Blocks.RED_BED.defaultBlockState());
        bed(level, f, 2, 1, Direction.SOUTH, Blocks.BLUE_BED.defaultBlockState());
        set(level, f.at(0, 1, 2), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, f.dir(Direction.NORTH)));
        set(level, f.at(-2, 1, -2), Blocks.OAK_FENCE.defaultBlockState());
        set(level, f.at(-2, 2, -2), Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        set(level, f.at(2, 1, -2), Blocks.POTTED_POPPY.defaultBlockState());
        BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.ALLIUM.defaultBlockState()};
        for (int x : new int[]{-3, -2, 2, 3}) {                                          // a flower bed either side of the door
            set(level, f.at(x, 0, -4), Blocks.GRASS_BLOCK.defaultBlockState());
            set(level, f.at(x, 1, -4), flowers[(index + x + 3) % flowers.length]);
        }
        s.pois.add(new Poi(Poi.Kind.HOME, f.at(0, 1, 0), "home " + (index + 1)));
    }

    static void bed(ServerLevel level, Frame f, int x, int z, Direction localHead, BlockState bed) {
        Direction d = f.dir(localHead);
        BlockPos foot = f.at(x, 1, z), head = foot.relative(d);
        set(level, foot, bed.setValue(BedBlock.FACING, d).setValue(BedBlock.PART, BedPart.FOOT));
        set(level, head, bed.setValue(BedBlock.FACING, d).setValue(BedBlock.PART, BedPart.HEAD));
    }

    /** The shop: a wide front, a counter across the room, shelves behind it. */
    static void shop(ServerLevel level, Frame f, Settlement s) {
        shell(level, f, 4, 3, Blocks.PURPLE_TERRACOTTA.defaultBlockState(), 3);
        for (int x = -3; x <= 2; x++) set(level, f.at(x, 1, 0), x == 0 ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE) : Blocks.BARREL.defaultBlockState());
        for (int x = -3; x <= 3; x++) for (int y = 1; y <= 2; y++) set(level, f.at(x, y, 2), y == 1 ? Blocks.BARREL.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
        s.pois.add(new Poi(Poi.Kind.SHOP, f.at(-1, 1, 1), "counter"));
        s.pois.add(new Poi(Poi.Kind.MARKET, f.at(-1, 1, -2), "shop floor"));
        s.pois.add(new Poi(Poi.Kind.MARKET, f.at(1, 1, -1), "shop floor"));
    }

    /** The restaurant: tables with chairs, a counter at the back with the kitchen behind it. */
    static void restaurant(ServerLevel level, Frame f, Settlement s) {
        shell(level, f, 4, 4, Blocks.BROWN_TERRACOTTA.defaultBlockState(), 1);
        for (int x = -3; x <= 1; x++) set(level, f.at(x, 1, 2), Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        set(level, f.at(-3, 1, 3), Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, f.dir(Direction.NORTH)));
        set(level, f.at(-2, 1, 3), Blocks.BARREL.defaultBlockState());
        for (int tx : new int[]{-2, 2}) {
            set(level, f.at(tx, 1, -1), Blocks.OAK_FENCE.defaultBlockState());
            set(level, f.at(tx, 2, -1), Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
            set(level, f.at(tx - 1, 1, -1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.dir(Direction.WEST)));
            set(level, f.at(tx + 1, 1, -1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.dir(Direction.EAST)));
            s.pois.add(new Poi(Poi.Kind.TAVERN, f.at(tx, 1, -2), "table"));
            s.pois.add(new Poi(Poi.Kind.TAVERN, f.at(tx, 1, 0), "table"));
        }
        s.pois.add(new Poi(Poi.Kind.TAVERN, f.at(-1, 1, 3), "kitchen"));
    }

    static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
