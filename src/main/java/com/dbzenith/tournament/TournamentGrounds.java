package com.dbzenith.tournament;

import com.dbzenith.DBZenith;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the World Martial Arts Tournament is held (CX-17c, rebuilt in CX-33): the generated tournament arena nearest
 * the world's spawn ({@link com.dbzenith.world.landmark.TournamentArena}, built by world generation like every
 * landmark). The ring's centre is worked out once and remembered with the world.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TournamentGrounds extends SavedData {
    public static final int RING = com.dbzenith.world.landmark.TournamentArena.RING;
    /** How far from the ring's centre the grounds reach (the stands' outer wall): PvP and destruction rules use it. */
    public static final int PLAZA = com.dbzenith.world.landmark.TournamentArena.WALL_OUT;
    private static final String KEY = "dbzenith_tournament_arena";

    /** The ring's centre, at the height you stand on it. */
    BlockPos ring;
    /** Set once the search has run and found nothing (not saved: a later version may place arenas this one could not). */
    private boolean searched;

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

    /** The ring's centre (standing height) in this world, or null if no arena could be found. */
    public static BlockPos ring(ServerLevel overworld) {
        placeIfNeeded(overworld);
        return of(overworld).ring;
    }

    /** Moves the tournament to a ring at {@code centre} (tests stage matches next to themselves, away from the arena). */
    public static void useRing(ServerLevel overworld, BlockPos centre) {
        TournamentGrounds g = of(overworld);
        g.ring = centre;
        g.searched = false;
        g.setDirty();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) placeIfNeeded(level.getServer().overworld());
    }

    /** Finds the arena nearest the spawn (once): it is built when its chunks generate. */
    public static void placeIfNeeded(ServerLevel overworld) {
        TournamentGrounds g = of(overworld);
        if (g.ring != null || g.searched) return;
        g.searched = true;
        var plan = com.dbzenith.world.landmark.LandmarkSites.nearest(com.dbzenith.world.landmark.Landmark.TOURNAMENT, overworld.getSeed(),
                com.dbzenith.world.landmark.LandmarkSites.Terrain.of(overworld), overworld.getSharedSpawnPos(), 16);
        if (plan instanceof com.dbzenith.world.landmark.TournamentArena arena) {
            g.ring = arena.ringCentre();
            g.setDirty();
        }
    }

    /** Where fighter {@code side} (0 west, 1 east) starts a match, facing the other. */
    public static Vec3 corner(BlockPos ring, int side) {
        return new Vec3(ring.getX() + 0.5 + (side == 0 ? -8 : 8), ring.getY(), ring.getZ() + 0.5);
    }

    /** Where fighters wait between matches and losers are set down: the plaza south of the ring. */
    public static Vec3 sideline(BlockPos ring) {
        return new Vec3(ring.getX() + 0.5, ring.getY() - 1, ring.getZ() + RING + 5.5);
    }

    /** Out of the ring: down on anything lower than the tiles (or in water), or far from the ring altogether. */
    public static boolean isOut(net.minecraft.world.entity.LivingEntity e, BlockPos ring) {
        if (e.position().distanceToSqr(Vec3.atBottomCenterOf(ring)) > 60 * 60) return true;
        return (e.onGround() || e.isInWater()) && e.getY() < ring.getY() - 0.5;
    }
}
