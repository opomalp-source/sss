package com.dbzenith.world.landmark;

import com.dbzenith.living.Occupation;
import com.dbzenith.living.Poi;
import com.dbzenith.living.Settlement;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.world.arch.CivilizationBlocks;
import com.dbzenith.world.settle.SettlementPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * The Grand Elder's house (canon; docs/WORLD_STRUCTURES.md): Grand Elder Guru's home, far from the villages, on top of
 * a lone spire of grey rock that flares out into a grassy cap. A great white dome between two small ones; inside, the
 * Grand Elder's wide seat at the back, Dende's bed in a side room, water. Nail keeps watch at the door. Its three
 * residents are the canon three: Grand Elder Guru (who never leaves his seat), Nail, Dende. Reached by flying. One per
 * world, on Namek.
 */
public final class GrandElderHouse extends SettlementPlan {
    /** How high the cap stands above the ground, the cap's radius. */
    static final int RISE = 42, CAP = 11;
    final int ground, top;

    private GrandElderHouse(LandmarkSites.Site site, int ground, int sea) {
        super(site);
        this.ground = ground;
        this.top = Math.max(ground, sea) + RISE;
        layOutPlaces();
    }

    /** The house at a chosen site and ground level (tests). */
    public static GrandElderHouse at(LandmarkSites.Site site, int ground, int sea) {
        return new GrandElderHouse(site, ground, sea);
    }

    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int h = t.height(s.x(), s.z());
        if (h < t.seaLevel() - 12) return null;                                                // not out in deep water
        return new GrandElderHouse(s, h, t.seaLevel());
    }

    @Override
    public String name() {
        return "the Grand Elder's House";
    }

    @Override
    public String style() {
        return "namek";
    }

    @Override
    public int reach() {
        return CAP + 6;
    }

    @Override
    public BlockPos centre() {
        return new BlockPos(x, top + 1, z + 9);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, top + 1, z + 10);
    }

    /** The canon three: the Grand Elder, Nail, Dende. */
    @Override
    public void settled(Settlement s, List<NamedFighter> residents) {
        for (NamedFighter c : residents) {
            Occupation o = Occupation.of(c);
            c.epithet = "";
            if (o == Occupation.ELDER) c.name = "Grand Elder Guru";
            else if (o == Occupation.GUARD) c.name = "Nail";
            else c.name = "Dende";
        }
    }

    static BlockState s(String id) {
        return CivilizationBlocks.get(id);
    }

    /** The spire's radius k blocks above its foot: a pillar a little waisted, flaring into the cap near the top. */
    double radius(int k, double angle) {
        int h = top - ground;
        double f = (double) k / h, r = 6.2 - 1.4 * Math.sin(Math.PI * Math.min(1, f * 1.1));
        if (k > h - 7) r += (k - (h - 7)) * 0.75;                                              // the flare under the cap
        return r + 0.8 * Math.sin(angle * 3 + k * 0.35 + (seed & 7)) + 0.5 * Math.sin(angle * 5 - k * 0.2);
    }

    @Override
    public void build(Canvas c) {
        if (!c.intersects(x - CAP - 4, z - CAP - 4, x + CAP + 4, z + CAP + 4)) return;
        spire(c);
        house(c);
        ajisa(c, x - 8, z + 7, 4);
        ajisa(c, x + 8, z - 7, 5);
    }

    void spire(Canvas c) {
        BlockState[] rock = {Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
                Blocks.STONE.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState()};
        for (int wx = Math.max(c.minX, x - CAP - 3); wx <= Math.min(c.maxX, x + CAP + 3); wx++)
            for (int wz = Math.max(c.minZ, z - CAP - 3); wz <= Math.min(c.maxZ, z + CAP + 3); wz++) {
                double d = Math.hypot(wx - x, wz - z), a = Math.atan2(wz - z, wx - x);
                int floor = c.surface(wx, wz);
                for (int y = Math.min(floor, ground) - 3; y <= top; y++) {
                    int k = y - ground;
                    double r = k < 0 ? radius(0, a) + 1 : radius(k, a);
                    if (d > r) continue;
                    BlockState st = y == top ? Blocks.GRASS_BLOCK.defaultBlockState() : y >= top - 2 ? Blocks.DIRT.defaultBlockState()
                            : rock[Math.floorMod(y / 3 + Canvas.hash(seed, wx, 0, wz) % 2, rock.length)];
                    c.set(wx, y, wz, st);
                }
            }
    }

    /** The house: a great dome between two small ones, round windows, the door south; the seat, Dende's room, water. */
    void house(Canvas c) {
        int y0 = top;
        dome(c, x, z, 6.6, 10.5, y0);
        dome(c, x - 7, z, 3.4, 5.5, y0);                                                 // (on the cap, not over its edge)
        dome(c, x + 7, z, 3.4, 5.5, y0);
        c.box(x - 6, y0 + 1, z, x + 6, y0 + 2, z, Canvas.AIR);
        c.box(x - 6, y0, z, x + 6, y0, z, s("namek_floor"));
        c.box(x, y0 + 1, z + 5, x, y0 + 2, z + 8, Canvas.AIR);
        BlockState d = s("namek_door").setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        c.set(x, y0 + 1, z + 6, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(x, y0 + 2, z + 6, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int dx = -2; dx <= 2; dx++) c.set(x + dx, y0 + 1, z - 4, Canvas.stairs(s("namek_temple_stairs"), Direction.NORTH, false));   // the Grand Elder's seat
        for (int dx = -2; dx <= 2; dx++) c.box(x + dx, y0 + 1, z - 5, x + dx, y0 + 3, z - 5, s(Math.abs(dx) == 2 ? "namek_pillar" : "namek_pattern"));
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) <= 4) c.set(x + dx, y0 + 1, z + dz, Blocks.CYAN_CARPET.defaultBlockState());
        c.set(x, y0 + 8, z, s("namek_lamp"));
        BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        c.set(x - 7, y0 + 1, z - 1, bed.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(x - 7, y0 + 1, z - 2, bed.setValue(BedBlock.PART, BedPart.HEAD));
        c.set(x - 8, y0 + 1, z + 1, Blocks.WATER_CAULDRON.defaultBlockState());
        c.set(x + 7, y0 + 1, z - 2, Blocks.WATER_CAULDRON.defaultBlockState());
        c.set(x + 8, y0 + 1, z, Blocks.CHEST.defaultBlockState());
    }

    void dome(Canvas c, int cx, int cz, double r, double h, int y0) {
        Shapes.dome(c, cx, cz, y0 + 1, r, h, 1.1, s("namek_shell"));
        Shapes.ring(c, cx, cz, y0 + 1, r - 0.7, r + 0.1, s("namek_band"));
        int R = (int) Math.ceil(r) + 1;
        double rk = r * Math.sqrt(1 - Math.pow(2.0 / h, 2));
        for (int wx = Math.max(c.minX, cx - R); wx <= Math.min(c.maxX, cx + R); wx++)
            for (int wz = Math.max(c.minZ, cz - R); wz <= Math.min(c.maxZ, cz + R); wz++) {
                double d = Math.hypot(wx - cx, wz - cz);
                if (d <= rk + 0.4 && d >= rk - 0.9 && Math.floorMod(Shapes.arc(wx - cx, wz - cz, rk), 3) == 0) c.set(wx, y0 + 3, wz, s("namek_window"));
            }
        Shapes.disc(c, cx, cz, y0, r - 0.4, s("namek_floor"));
    }

    void ajisa(Canvas c, int tx, int tz, int height) {
        if (!c.intersects(tx - 3, tz - 3, tx + 3, tz + 3)) return;
        BlockState log = ModBlocks.NAMEK_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState leaf = ModBlocks.NAMEK_LEAVES.get().defaultBlockState();
        if (leaf.hasProperty(LeavesBlock.PERSISTENT)) leaf = leaf.setValue(LeavesBlock.PERSISTENT, true);
        c.column(tx, tz, top + 1, top + height, log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++)
                    if (dx * dx + dz * dz + dy * dy * 1.6 <= 5.2) c.set(tx + dx, top + height + 1 + dy, tz + dz, leaf);
    }

    void layOutPlaces() {
        place(Poi.Kind.HOME, x, top + 1, z - 3, "the Grand Elder's seat");
        place(Poi.Kind.HOME, x - 7, top + 1, z + 1, "Dende's room");
        place(Poi.Kind.PLAZA, x, top + 1, z - 3, "elder");
        place(Poi.Kind.PATROL, x + 1, top + 1, z + 8, "the door");
        place(Poi.Kind.PATROL, x - 6, top + 1, z + 7, "the cap's edge");
        place(Poi.Kind.PATROL, x + 7, top + 1, z + 5, "the cap's edge");
        roundRoom(x, z, 9, top, top + 12, way(x, top + 1, z + 9));
        people.addAll(List.of("elder", "guard", "citizen"));
    }
}
