package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.living.LivingData;
import com.dbzenith.living.Poi;
import com.dbzenith.living.Residents;
import com.dbzenith.living.Settlement;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.world.arch.CivilizationBlockList;
import com.dbzenith.world.arch.CivilizationBlocks;
import com.dbzenith.world.landmark.Landmark;
import com.dbzenith.world.landmark.LandmarkGuard;
import com.dbzenith.world.landmark.LandmarkSites;
import com.dbzenith.world.landmark.WestCity;
import com.dbzenith.world.settle.SettlementSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** World structures (docs/WORLD_STRUCTURES.md): the civilizations' blocks, the existing-world guard, West City's plan, settlements from world generation. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureTests {
    private static final String EMPTY = "empty";

    private StructureTests() {}

    @GameTest(template = EMPTY)
    public static void civilizationBlocksAreRegisteredWithLoot(GameTestHelper h) {
        int n = 0;
        for (CivilizationBlockList.Spec s : CivilizationBlockList.ALL) {
            BlockState st = CivilizationBlocks.get(s.id());
            Block b = st.getBlock();
            h.assertTrue(b.asItem() != net.minecraft.world.item.Items.AIR, s.id() + " has an item");
            LootTable loot = h.getLevel().getServer().getLootData().getLootTable(b.getLootTable());
            h.assertTrue(loot != LootTable.EMPTY, s.id() + " drops something");
            if (s.stairs()) CivilizationBlocks.get(s.id() + "_stairs");
            if (s.slab()) CivilizationBlocks.get(s.id() + "_slab");
            if (s.wall()) CivilizationBlocks.get(s.id() + "_wall");
            n++;
        }
        h.assertTrue(n >= 60, "the five families: " + n);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void slidingDoorsOpenByHandAndCornersTurn(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        BlockState door = CivilizationBlocks.get("capsule_door").setValue(DoorBlock.FACING, Direction.SOUTH);
        h.getLevel().setBlock(at, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        h.getLevel().setBlock(at.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        h.assertTrue(DoorBlock.isWoodenDoor(h.getLevel(), at), "residents open it (a door anyone can open)");
        ((DoorBlock) door.getBlock()).setOpen(null, h.getLevel(), h.getLevel().getBlockState(at), at, true);
        h.assertTrue(h.getLevel().getBlockState(at).getValue(DoorBlock.OPEN), "it opens");
        BlockState north = CivilizationBlocks.get("capsule_corner").setValue(CivilizationBlocks.RoundCorner.FACING, Direction.NORTH);
        BlockState east = north.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        h.assertTrue(east.getValue(CivilizationBlocks.RoundCorner.FACING) == Direction.EAST, "a corner turns with its structure");
        var shapeN = north.getShape(h.getLevel(), at);
        var shapeE = east.getShape(h.getLevel(), at);
        h.assertTrue(!solidAt(shapeN, 0.05, 0.05) && solidAt(shapeN, 0.95, 0.95) && solidAt(shapeN, 0.95, 0.05),
                "a north corner is rounded off to the north-west");
        h.assertTrue(!solidAt(shapeE, 0.95, 0.05) && solidAt(shapeE, 0.05, 0.95) && solidAt(shapeE, 0.05, 0.05),
                "turned east, to the north-east");
        h.getLevel().setBlock(at.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    /** A resident whose every place lies beyond a closed sliding door walks there, opening it. */
    @GameTest(template = EMPTY, batch = "structures_door", timeoutTicks = 400)
    public static void residentsWalkThroughSlidingDoors(GameTestHelper h) {
        doorWalk(h, Direction.SOUTH, 1, 10, 4242);
    }

    /** The same from its other side (a door facing north, come at from the south). */
    @GameTest(template = EMPTY, batch = "structures_door2", timeoutTicks = 400)
    public static void residentsOpenDoorsFromEitherSide(GameTestHelper h) {
        doorWalk(h, Direction.NORTH, 10, 1, 4243);
    }

    /** Two residents going out by the same door one behind the other: the first must not shut it on the second. */
    @GameTest(template = EMPTY, batch = "structures_door3", timeoutTicks = 400)
    public static void residentsShareADoor(GameTestHelper h) {
        doorWalk(h, Direction.NORTH, 10, 1, 4244, 4245);
    }

    /**
     * Coming at a door on a slant from a room, from the side its leaf opens toward (a door facing south, hinged on the
     * left, opens to the east): the open leaf stood in the way of anyone who wasn't square with the doorway.
     */
    @GameTest(template = EMPTY, batch = "structures_door4", timeoutTicks = 400)
    public static void residentsLeaveRoomsOnASlant(GameTestHelper h) {
        doorWalk(h, Direction.SOUTH, 2, 1, 10, true, 4246);
    }

    static void doorWalk(GameTestHelper h, Direction facing, int fromZ, int toZ, long... seeds) {
        doorWalk(h, facing, 1, fromZ, toZ, false, seeds);
    }

    /** A glass corridor 1 wide and 12 long with a closed door half way (z 5); with {@code room}, 3 wide before the door. */
    static void doorWalk(GameTestHelper h, Direction facing, int fromX, int fromZ, int toZ, boolean room, long... seeds) {
        for (int x = -1; x <= 3; x++)
            for (int z = -1; z <= 12; z++)
                for (int y = 0; y <= 4; y++) {
                    boolean open = x == 1 || room && x >= 0 && x <= 2 && (fromZ < 5 ? z < 5 : z > 5);
                    boolean wall = !open || z == -1 || z == 12 || y == 0 || y == 4;
                    h.setBlock(new BlockPos(x, y, z), wall ? (y == 0 ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.GLASS)
                            : net.minecraft.world.level.block.Blocks.AIR);
                }
        BlockState door = CivilizationBlocks.get("capsule_door").setValue(DoorBlock.FACING, facing);
        h.setBlock(new BlockPos(1, 1, 5), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(1, 2, 5), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Settlement s = new Settlement(java.util.UUID.randomUUID());
        s.name = "Doorville";
        s.dimension = h.getLevel().dimension().location().toString();
        s.center = h.absolutePos(new BlockPos(1, 1, toZ));
        s.radius = 24;
        for (Poi.Kind k : Poi.Kind.values()) s.pois.add(new Poi(k, h.absolutePos(new BlockPos(1, 1, toZ)), k.id()));
        LivingData ld = LivingData.get(h.getLevel().getServer());
        ld.add(s);
        List<NamedFighter> cs = new java.util.ArrayList<>();
        List<com.dbzenith.living.Resident> rs = new java.util.ArrayList<>();
        int back = Integer.signum(fromZ - toZ);                                                // the second waits a step behind the first
        for (int i = 0; i < seeds.length; i++) {
            NamedFighter c = Residents.create(com.dbzenith.nemesis.Nemesis.data(), s, com.dbzenith.living.Occupation.CITIZEN, s.pois.get(0).pos(),
                    h.getLevel().getGameTime(), seeds[i]);
            var r = Residents.spawnBody(h.getLevel(), c, h.absolutePos(new BlockPos(fromX, 1, fromZ + i * back)));
            r.setInvulnerable(true);
            cs.add(c);
            rs.add(r);
        }
        Runnable cleanup = () -> {
            for (var r : rs) if (!r.isRemoved()) r.discard();
            for (NamedFighter c : cs) c.status = NamedFighter.Status.DEAD;
            ld.remove(s.id);
        };
        Delays.after(h, 396, cleanup);
        h.succeedWhen(() -> {
            BlockPos goal = h.absolutePos(new BlockPos(1, 1, toZ)), doorAt = h.absolutePos(new BlockPos(1, 1, 5));
            for (var r : rs)
                h.assertTrue(r.blockPosition().distSqr(goal) <= 9, r.getName().getString() + " through the door: " + Math.sqrt(r.blockPosition().distSqr(doorAt))
                        + " from it, " + Math.sqrt(r.blockPosition().distSqr(goal)) + " from where it is going");
            cleanup.run();
        });
    }

    static boolean solidAt(net.minecraft.world.phys.shapes.VoxelShape s, double x, double z) {
        return s.toAabbs().stream().anyMatch(b -> b.minX <= x && x <= b.maxX && b.minZ <= z && z <= b.maxZ);
    }

    /** Long walks go door to door: out of one building by its door, along the waypoints, in by the other's door (far off, unloaded ground). */
    @GameTest(template = EMPTY)
    public static void longWalksGoDoorToDoor(GameTestHelper h) {
        int x = 9_000_000, y = 70, z = 9_000_000;                                               // never loaded: open ground is taken on trust
        List<BlockPos> ways = List.of(new BlockPos(x + 8, y, z), new BlockPos(x + 40, y, z), new BlockPos(x + 72, y, z), new BlockPos(x + 80, y, z));
        var a = new com.dbzenith.living.Routes.Room(x - 6, y - 1, z - 6, x + 6, y + 8, z + 6, 0, true);
        var b = new com.dbzenith.living.Routes.Room(x + 82, y - 1, z - 6, x + 94, y + 8, z + 6, 3, false);
        var map = new com.dbzenith.living.Routes.Map(ways, List.of(a, b));
        BlockPos home = new BlockPos(x + 2, y, z + 3), work = new BlockPos(x + 90, y, z - 2);
        List<BlockPos> route = com.dbzenith.living.Routes.route(map, h.getLevel(), home, work);
        h.assertTrue(route != null && route.equals(List.of(ways.get(0), ways.get(1), ways.get(2), ways.get(3), work)), "door, street, door, place: " + route);
        h.assertTrue(com.dbzenith.living.Routes.next(map, h.getLevel(), home, work).equals(ways.get(0)), "first out of the door");
        BlockPos near = new BlockPos(x + 4, y, z - 3);
        h.assertTrue(com.dbzenith.living.Routes.next(map, h.getLevel(), home, near).equals(near), "about the same building: straight there");
        BlockPos outside = new BlockPos(x + 12, y, z + 2);
        h.assertTrue(com.dbzenith.living.Routes.next(map, h.getLevel(), home, outside).equals(ways.get(0)), "a step outside, even near: by the door");
        h.assertTrue(com.dbzenith.living.Routes.next(new com.dbzenith.living.Routes.Map(List.of(), List.of()), h.getLevel(), home, work).equals(work),
                "a town with no map is walked straight");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theGuardKeepsNewLandmarksOffOldRegions(GameTestHelper h) throws IOException {
        Path dir = Files.createTempDirectory("dbz_regions");
        Files.write(dir.resolve("r.1.-2.mca"), new byte[]{1});
        Files.write(dir.resolve("r.0.0.mca"), new byte[0]);                                      // empty: never written to
        Files.write(dir.resolve("notes.txt"), new byte[]{1});
        h.assertTrue(LandmarkGuard.regions(dir).equals(Set.of(LandmarkGuard.pack(1, -2))), "reads the region files: " + LandmarkGuard.regions(dir));
        Set<Long> before = LandmarkGuard.recorded(Landmark.WEST_CITY);
        try {
            LandmarkGuard.setForTest(Landmark.WEST_CITY, Set.of(LandmarkGuard.pack(1, -2)));
            h.assertTrue(LandmarkGuard.blocked(Landmark.WEST_CITY, 700, -800, 50), "inside an old region");
            h.assertTrue(LandmarkGuard.blocked(Landmark.WEST_CITY, 1100, -800, 136), "reaching into one");
            h.assertTrue(!LandmarkGuard.blocked(Landmark.WEST_CITY, 3000, 3000, 136), "new ground is free");
            h.assertTrue(!LandmarkGuard.blocked(Landmark.TOURNAMENT, 700, -800, 50), "older landmarks aren't guarded");
        } finally {
            LandmarkGuard.setForTest(Landmark.WEST_CITY, before);
        }
        h.succeed();
    }

    /** Every unique landmark (West City, Kame House, Muscle Tower, the Grand Elder's): one cell, the same every time, nothing round it. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void uniqueLandmarksAreUniqueAndStable(GameTestHelper h) {
        for (Landmark type : Landmark.ALL) {
            if (!type.unique) continue;
            var level = h.getLevel().getServer().getLevel(type.dimension);                     // each in its own world
            h.assertTrue(level != null, type.id + "'s world exists");
            LandmarkSites.Terrain t = LandmarkSites.Terrain.of(level);
            long[] cell = LandmarkSites.uniqueCell(type, level.getSeed(), t);
            if (cell == null) continue;
            int found = 0;
            for (int cx = -2; cx <= 2; cx++)
                for (int cz = -2; cz <= 2; cz++)
                    if (LandmarkSites.plan(type, level.getSeed(), (int) cell[0] + cx, (int) cell[1] + cz, t) != null) found++;
            h.assertTrue(found == 1, "one " + type.id + " round its cell: " + found);
            h.assertTrue(java.util.Arrays.equals(cell, LandmarkSites.uniqueCell(type, level.getSeed(), t)), type.id + ": the same cell every time");
        }
        h.succeed();
    }

    /** A Namekian village: on Namek, named for its elder; homes for all, the elder's seat, the grove; no shop or inn (water is enough). */
    @GameTest(template = EMPTY)
    public static void namekVillagesKeepTheirElders(GameTestHelper h) {
        h.assertTrue(Landmark.NAMEK_VILLAGE.dimension == Landmark.Keys.NAMEK && Landmark.GRAND_ELDER.dimension == Landmark.Keys.NAMEK, "on Namek");
        var v = com.dbzenith.world.landmark.NamekVillage.at(new LandmarkSites.Site(Landmark.NAMEK_VILLAGE, 0, 0, 30_000, 30_000, 0x9a3bL), 90);
        int[] kinds = new int[Poi.Kind.values().length];
        for (Poi p : v.places()) kinds[p.kind().ordinal()]++;
        h.assertTrue(v.name().endsWith("'s Village") && "namek".equals(v.style()), "named for its elder: " + v.name());
        h.assertTrue(kinds[Poi.Kind.HOME.ordinal()] * 2 >= v.people().size() && "elder".equals(v.people().get(0)), "an elder first, homes for all");
        h.assertTrue(v.places().stream().anyMatch(p -> p.kind() == Poi.Kind.PLAZA && "elder".equals(p.label())), "the elder's seat");
        h.assertTrue(kinds[Poi.Kind.FIELD.ordinal()] > 0 && v.people().contains("farmer"), "the grove and its farmers");
        h.assertTrue(kinds[Poi.Kind.SHOP.ordinal()] == 0 && kinds[Poi.Kind.TAVERN.ordinal()] == 0, "no shop, no inn");
        h.assertTrue(v.rooms().size() == 6, "five houses and the elder's");
        h.succeed();
    }

    /** Namek's people are Namekians, its elders and the Grand Elder's three named. */
    @GameTest(template = EMPTY)
    public static void namekiansLiveOnNamek(GameTestHelper h) {
        Settlement s = new Settlement(java.util.UUID.randomUUID());
        s.name = "Testnamek";
        s.style = "namek";
        s.pois.add(new Poi(Poi.Kind.HOME, h.absolutePos(new BlockPos(1, 1, 1)), "house"));
        var d = com.dbzenith.nemesis.Nemesis.data();
        NamedFighter elder = Residents.create(d, s, com.dbzenith.living.Occupation.ELDER, s.pois.get(0).pos(), h.getLevel().getGameTime(), 91);
        NamedFighter guard = Residents.create(d, s, com.dbzenith.living.Occupation.GUARD, s.pois.get(0).pos(), h.getLevel().getGameTime(), 92);
        NamedFighter folk = Residents.create(d, s, com.dbzenith.living.Occupation.CITIZEN, s.pois.get(0).pos(), h.getLevel().getGameTime(), 93);
        try {
            for (NamedFighter c : List.of(elder, guard, folk)) h.assertTrue("namekian".equals(c.race), c.name + " a Namekian");
            h.assertTrue("dragon_clan".equals(elder.variant) && "warrior_clan".equals(guard.variant), "the dragon clan, and the warrior clan to fight");
            h.assertTrue("namek_elder".equals(com.dbzenith.living.Resident.skinFor(elder)) && "namekian_warrior".equals(com.dbzenith.living.Resident.skinFor(guard))
                    && com.dbzenith.living.Resident.skinFor(folk).startsWith("namek_villager"), "dressed as Namekians");
            var ge = com.dbzenith.world.landmark.GrandElderHouse.at(new LandmarkSites.Site(Landmark.GRAND_ELDER, 0, 0, 0, 0, 1), 70, 63);
            h.assertTrue(ge.people().equals(List.of("elder", "guard", "citizen")), "three live there");
            ge.settled(s, List.of(elder, guard, folk));
            h.assertTrue("Grand Elder Guru".equals(elder.fullName()) && "Nail".equals(guard.fullName()) && "Dende".equals(folk.fullName()), "Guru, Nail, Dende");
        } finally {
            for (NamedFighter c : List.of(elder, guard, folk)) c.status = NamedFighter.Status.DEAD;
        }
        h.succeed();
    }

    /** A body never appears inside a wall: a home drawn into one, and the resident stands beside it. */
    @GameTest(template = EMPTY)
    public static void residentsNeverSpawnInWalls(GameTestHelper h) {
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) h.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(new BlockPos(2, 2, 2), net.minecraft.world.level.block.Blocks.STONE);
        Settlement s = new Settlement(java.util.UUID.randomUUID());
        s.name = "Wallville";
        s.pois.add(new Poi(Poi.Kind.HOME, h.absolutePos(new BlockPos(2, 1, 2)), "house"));
        NamedFighter c = Residents.create(com.dbzenith.nemesis.Nemesis.data(), s, com.dbzenith.living.Occupation.CITIZEN, s.pois.get(0).pos(), h.getLevel().getGameTime(), 94);
        var r = Residents.spawnBody(h.getLevel(), c, h.absolutePos(new BlockPos(2, 1, 2)));
        try {
            BlockPos at = r.blockPosition();
            h.assertTrue(h.getLevel().getBlockState(at).isAir() && h.getLevel().getBlockState(at.above()).isAir(), "standing in the open at " + at);
        } finally {
            r.discard();
            c.status = NamedFighter.Status.DEAD;
        }
        h.succeed();
    }

    /** A village: eight plots round the ring, homes for all, every place its people use, farmers where there are fields. */
    @GameTest(template = EMPTY)
    public static void villagesHaveAPlaceForEveryone(GameTestHelper h) {
        for (String style : List.of("earth", "snow", "desert")) {
            var v = com.dbzenith.world.landmark.Village.at(new LandmarkSites.Site(Landmark.VILLAGE, 0, 0, 40_000, -40_000, 0x7111L + style.length()), 70, style);
            int[] kinds = new int[Poi.Kind.values().length];
            for (Poi p : v.places()) {
                kinds[p.kind().ordinal()]++;
                h.assertTrue(Math.hypot(p.pos().getX() - v.x, p.pos().getZ() - v.z) <= v.reach(), style + ": " + p.label() + " inside the village");
            }
            h.assertTrue(kinds[Poi.Kind.HOME.ordinal()] * 2 >= v.people().size() && v.people().size() >= 8, style + ": homes for all " + v.people().size());
            for (Poi.Kind k : List.of(Poi.Kind.SHOP, Poi.Kind.MARKET, Poi.Kind.TAVERN, Poi.Kind.PLAZA, Poi.Kind.PATROL, Poi.Kind.GATE))
                h.assertTrue(kinds[k.ordinal()] > 0, style + ": a " + k.id());
            boolean farmers = v.people().contains("farmer");
            h.assertTrue(farmers == !style.equals("snow") && (kinds[Poi.Kind.FIELD.ordinal()] > 0) == farmers, style + ": farmers with fields");
            h.assertTrue(v.name().endsWith(" Village") && v.style().equals("village_" + style), style + ": named " + v.name());
            h.assertTrue(v.rooms().size() == 8 && v.ways().size() > 20, style + ": a building room each and a map");
        }
        h.succeed();
    }

    /** The farmer's trade: works the fields, up at dawn. */
    @GameTest(template = EMPTY)
    public static void farmersWorkTheFields(GameTestHelper h) {
        h.assertTrue(com.dbzenith.living.Occupation.FARMER.workplace == Poi.Kind.FIELD && !com.dbzenith.living.Occupation.FARMER.fighter, "fields, no fighting");
        Settlement s = new Settlement(java.util.UUID.randomUUID());
        s.name = "Testfield";
        s.pois.add(new Poi(Poi.Kind.HOME, h.absolutePos(new BlockPos(1, 1, 1)), "house"));
        NamedFighter c = Residents.create(com.dbzenith.nemesis.Nemesis.data(), s, com.dbzenith.living.Occupation.FARMER, s.pois.get(0).pos(), h.getLevel().getGameTime(), 77);
        try {
            h.assertTrue(com.dbzenith.living.Schedules.planned(c, 7) == com.dbzenith.living.Schedules.Activity.WORK, "at work at seven");
            h.assertTrue(com.dbzenith.living.Schedules.planned(c, 22) == com.dbzenith.living.Schedules.Activity.SLEEP, "asleep at ten");
            h.assertTrue("townsfolk_farmer".equals(com.dbzenith.living.Resident.skinFor(c)), "dressed for it");
        } finally {
            c.status = NamedFighter.Status.DEAD;
        }
        h.succeed();
    }

    /** The Red Ribbon Army: its soldiers are its own (enemies of townsfolk), and its chests are filled from its own loot tables. */
    @GameTest(template = EMPTY)
    public static void theRedRibbonArmyIsHostileAndSupplied(GameTestHelper h) {
        var soldier = h.spawnWithNoFreeWill(com.dbzenith.npc.ModNpcs.RED_RIBBON_SOLDIER.get(), new BlockPos(1, 2, 1));
        var faction = com.dbzenith.npc.ai.Faction.of(soldier);
        h.assertTrue(faction == com.dbzenith.npc.ai.Faction.RED_RIBBON && faction.enemyOf(com.dbzenith.npc.ai.Faction.TOWNSFOLK), "a Red Ribbon soldier: " + faction);
        soldier.discard();
        for (String table : List.of("dbzenith:chests/red_ribbon_base", "dbzenith:chests/red_ribbon_tower"))
            h.assertTrue(h.getLevel().getServer().getLootData().getLootTable(new net.minecraft.resources.ResourceLocation(table)) != LootTable.EMPTY, table + " loaded");
        h.succeed();
    }

    static WestCity city(int x, int z) {
        return WestCity.at(new LandmarkSites.Site(Landmark.WEST_CITY, 0, 0, x, z, 0x3e57L), 70);
    }

    @GameTest(template = EMPTY)
    public static void westCityHasAPlaceForEveryone(GameTestHelper h) {
        WestCity c = city(5000, -3000);
        h.assertTrue("West City".equals(c.name()) && c.people().size() == 20, "twenty people");
        int[] kinds = new int[Poi.Kind.values().length];
        Set<BlockPos> homes = new HashSet<>();
        for (Poi p : c.places()) {
            kinds[p.kind().ordinal()]++;
            double d = Math.hypot(p.pos().getX() - c.x, p.pos().getZ() - c.z);
            h.assertTrue(d <= c.reach(), p.label() + " inside the city: " + d);
            if (p.kind() == Poi.Kind.HOME) h.assertTrue(homes.add(p.pos()), "homes apart: " + p.pos());
        }
        h.assertTrue(kinds[Poi.Kind.HOME.ordinal()] * 2 >= c.people().size(), "beds for all");
        h.assertTrue(kinds[Poi.Kind.LAB.ordinal()] >= 3, "labs for the scientists");
        for (Poi.Kind k : List.of(Poi.Kind.SHOP, Poi.Kind.MARKET, Poi.Kind.TAVERN, Poi.Kind.DOJO, Poi.Kind.PLAZA, Poi.Kind.PATROL, Poi.Kind.GATE))
            h.assertTrue(kinds[k.ordinal()] > 0, "a " + k.id());
        h.assertTrue(c.places().get(0).label().startsWith("Capsule Corporation"), "the scientists live at Capsule Corporation");
        h.assertTrue(c.settlementId().equals(city(5000, -3000).settlementId()) && !c.settlementId().equals(city(5000, 3000).settlementId()),
                "a site's settlement is always the same one");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void settlementsRegisterOnceAndArePeopled(GameTestHelper h) {
        WestCity c = city(-7000 - (int) (h.getLevel().getGameTime() % 1000) * 300, 9000);
        LivingData ld = LivingData.get(h.getLevel().getServer());
        Settlement s = SettlementSites.register(h.getLevel(), c);
        try {
            h.assertTrue(s != null && ld.settlement(c.settlementId()) == s, "registered");
            h.assertTrue("capsule".equals(s.style) && s.pois.size() == c.places().size() && s.population.size() == 20, "with its places and plan");
            List<NamedFighter> people = Residents.of(s);
            h.assertTrue(people.size() == Residents.limitOf(s), "peopled by its plan: " + people.size());
            h.assertTrue(people.stream().allMatch(p -> p.body == null), "no bodies until their homes load");
            h.assertTrue(people.stream().filter(p -> com.dbzenith.living.Occupation.SCIENTIST.id().equals(p.occupation)).count() == Math.min(3, people.size()), "its scientists");
            h.assertTrue(SettlementSites.register(h.getLevel(), c) == null, "once");
        } finally {
            if (s != null) {
                for (NamedFighter p : Residents.of(s)) p.status = NamedFighter.Status.DEAD;
                ld.remove(s.id);
            }
        }
        h.assertTrue(SettlementSites.register(h.getLevel(), c) == null, "never again, even removed");
        h.succeed();
    }
}
