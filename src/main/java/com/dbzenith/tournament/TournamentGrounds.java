package com.dbzenith.tournament;

import com.dbzenith.DBZenith;
import com.dbzenith.npc.ModNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The grounds of the World Martial Arts Tournament (CX-17c), built once per world, 56 blocks north of spawn: a stone
 * plaza, the raised square ring of light tiles, stands with striped awnings on both sides, the fighters' hall to the
 * north with its orange roof, the great gate to the south, and the Announcer by the ring steps.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TournamentGrounds extends SavedData {
    public static final int RING = 7, PLAZA = 22;
    private static final String KEY = "dbzenith_tournament_grounds";

    /** The ring's centre, at the height you stand on it. */
    BlockPos ring;

    private TournamentGrounds() {}

    static TournamentGrounds load(CompoundTag tag) {
        TournamentGrounds g = new TournamentGrounds();
        if (tag.contains("ring")) g.ring = NbtUtils.readBlockPos(tag.getCompound("ring"));
        return g;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (ring != null) tag.put("ring", NbtUtils.writeBlockPos(ring));
        return tag;
    }

    static TournamentGrounds of(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TournamentGrounds::load, TournamentGrounds::new, KEY);
    }

    /** The ring's centre (standing height) in this world, or null before the grounds are built. */
    public static BlockPos ring(ServerLevel overworld) {
        return of(overworld).ring;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) placeIfNeeded(level.getServer().overworld());
    }

    public static void placeIfNeeded(ServerLevel overworld) {
        TournamentGrounds g = of(overworld);
        if (g.ring != null) return;
        build(overworld, overworld.getSharedSpawnPos().offset(0, 0, -56));
    }

    /** Builds the grounds around {@code center} (on the surface there) and remembers the ring. Returns the ring centre. */
    public static BlockPos build(ServerLevel level, BlockPos center) {
        level.getChunk(center.getX() >> 4, center.getZ() >> 4);
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()) - 1;
        int cx = center.getX(), cz = center.getZ();
        for (int x = -PLAZA; x <= PLAZA; x++) for (int z = -PLAZA; z <= PLAZA; z++) {
            for (int y = 1; y <= 18; y++) set(level, cx + x, y0 + y, cz + z, Blocks.AIR);        // clear the trees and the hills
            boolean border = Math.abs(x) == PLAZA || Math.abs(z) == PLAZA;
            set(level, cx + x, y0, cz + z, border ? Blocks.POLISHED_ANDESITE : (x + z) % 2 == 0 ? Blocks.SMOOTH_STONE : Blocks.STONE);
            for (int y = 1; y <= 20; y++) {                                                      // solid down to the ground
                BlockPos p = new BlockPos(cx + x, y0 - y, cz + z);
                if (level.getBlockState(p).isSolidRender(level, p)) break;
                set(level, p.getX(), p.getY(), p.getZ(), Blocks.STONE_BRICKS);
            }
        }
        ring(level, cx, y0, cz);
        stands(level, cx, y0, cz, 1);
        stands(level, cx, y0, cz, -1);
        hall(level, cx, y0, cz - 19);
        gate(level, cx, y0, cz + PLAZA - 1);
        Mob announcer = ModNpcs.TOURNAMENT_ANNOUNCER.get().create(level);
        if (announcer != null) {
            announcer.moveTo(cx - 3.5, y0 + 1, cz + RING + 3.5, 180f, 0);
            announcer.setYHeadRot(180f);
            announcer.setYBodyRot(180f);
            announcer.finalizeSpawn(level, level.getCurrentDifficultyAt(announcer.blockPosition()), MobSpawnType.STRUCTURE, null, null);
            announcer.setPersistenceRequired();
            level.addFreshEntity(announcer);
        }
        TournamentGrounds g = of(level);
        g.ring = new BlockPos(cx, y0 + 2, cz);
        g.setDirty();
        return g.ring;
    }

    /** Where fighter {@code side} (0 west, 1 east) starts a match, facing the other. */
    public static Vec3 corner(BlockPos ring, int side) {
        return new Vec3(ring.getX() + 0.5 + (side == 0 ? -5 : 5), ring.getY(), ring.getZ() + 0.5);
    }

    /** Where fighters wait between matches and losers are set down: the plaza south of the ring. */
    public static Vec3 sideline(BlockPos ring) {
        return new Vec3(ring.getX() + 0.5, ring.getY() - 1, ring.getZ() + RING + 5.5);
    }

    /** Out of the ring: down on anything lower than the tiles (or in water), or far from the ring altogether. */
    public static boolean isOut(net.minecraft.world.entity.LivingEntity e, BlockPos ring) {
        if (e.position().distanceToSqr(Vec3.atBottomCenterOf(ring)) > 40 * 40) return true;
        return (e.onGround() || e.isInWater()) && e.getY() < ring.getY() - 0.5;
    }

    // ------------------------------------------------------------------ the buildings

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block b) {
        set(level, x, y, z, b.defaultBlockState());
    }

    /** The square ring of light tiles, a step up from the plaza, with steps on the south side and lanterns at the corners. */
    static void ring(ServerLevel level, int cx, int y0, int cz) {
        for (int x = -RING; x <= RING; x++) for (int z = -RING; z <= RING; z++) {
            boolean edge = Math.abs(x) == RING || Math.abs(z) == RING;
            boolean seam = (x + RING) % 3 == 0 || (z + RING) % 3 == 0;
            set(level, cx + x, y0 + 1, cz + z, edge ? Blocks.CHISELED_STONE_BRICKS : seam ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_STONE);
        }
        for (int x = -1; x <= 1; x++) {
            set(level, cx + x, y0 + 1, cz + RING + 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        }
        for (int sx : new int[]{-RING - 2, RING + 2}) for (int sz : new int[]{-RING - 2, RING + 2}) {
            for (int y = 1; y <= 3; y++) set(level, cx + sx, y0 + y, cz + sz, Blocks.RED_NETHER_BRICK_WALL);
            set(level, cx + sx, y0 + 4, cz + sz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }

    /** Stepped stands along one side ({@code dir} +1 east, -1 west) under a red and white awning. */
    static void stands(ServerLevel level, int cx, int y0, int cz, int dir) {
        for (int k = 0; k < 6; k++) {
            int x = cx + dir * (11 + k);
            for (int z = -14; z <= 14; z++) {
                for (int y = 1; y <= k + 1; y++) set(level, x, y0 + y, cz + z, y == k + 1 ? Blocks.SPRUCE_PLANKS : Blocks.STONE_BRICKS);
            }
        }
        for (int z = -15; z <= 15; z++) for (int k = 0; k <= 7; k++) {
            int x = cx + dir * (10 + k);
            set(level, x, y0 + 10 + (k > 5 ? 0 : 0), cz + z, ((z + 15) / 3) % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
        }
        for (int z = -15; z <= 15; z += 6) for (int y = 7; y <= 9; y++) set(level, cx + dir * 17, y0 + y, cz + z, Blocks.QUARTZ_PILLAR);
    }

    /** The fighters' hall: white walls, an orange roof with upturned eaves, the door facing the ring. */
    static void hall(ServerLevel level, int cx, int y0, int cz) {
        for (int x = -8; x <= 8; x++) for (int z = -3; z <= 3; z++) for (int y = 1; y <= 5; y++) {
            boolean wall = Math.abs(x) == 8 || Math.abs(z) == 3;
            boolean corner = Math.abs(x) == 8 && Math.abs(z) == 3;
            if (!wall) set(level, cx + x, y0 + y, cz + z, Blocks.AIR);
            else set(level, cx + x, y0 + y, cz + z, corner ? Blocks.RED_TERRACOTTA : y == 3 && x % 3 == 0 ? Blocks.GLASS_PANE : Blocks.SMOOTH_QUARTZ);
        }
        for (int x = -1; x <= 0; x++) for (int y = 1; y <= 3; y++) set(level, cx + x, y0 + y, cz + 3, Blocks.AIR);
        for (int x = -10; x <= 10; x++) for (int z = -5; z <= 5; z++) {                         // the roof, rising to a ridge
            int lift = 5 - Math.abs(z) / 2;
            boolean eave = Math.abs(x) == 10 || Math.abs(z) == 5;
            set(level, cx + x, y0 + 6 + lift - 3, cz + z, eave ? Blocks.ORANGE_GLAZED_TERRACOTTA : Blocks.ORANGE_TERRACOTTA);
        }
    }

    /** The great gate: two red pillars, a lintel and a two-tier orange roof. */
    static void gate(ServerLevel level, int cx, int y0, int cz) {
        for (int sx : new int[]{-5, 5}) for (int y = 1; y <= 7; y++) {
            set(level, cx + sx, y0 + y, cz, Blocks.RED_CONCRETE);
            set(level, cx + sx, y0 + y, cz - 1, Blocks.RED_CONCRETE);
        }
        for (int x = -6; x <= 6; x++) {
            set(level, cx + x, y0 + 8, cz, Blocks.RED_TERRACOTTA);
            set(level, cx + x, y0 + 8, cz - 1, Blocks.RED_TERRACOTTA);
        }
        for (int x = -8; x <= 8; x++) for (int z = -2; z <= 1; z++) set(level, cx + x, y0 + 9, cz + z, Blocks.ORANGE_TERRACOTTA);
        for (int x = -6; x <= 6; x++) for (int z = -1; z <= 0; z++) set(level, cx + x, y0 + 10, cz + z, Blocks.ORANGE_GLAZED_TERRACOTTA);
        for (int sx : new int[]{-3, 3}) set(level, cx + sx, y0 + 7, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }
}
