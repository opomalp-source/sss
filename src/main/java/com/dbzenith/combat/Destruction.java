package com.dbzenith.combat;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.ImpactPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Map;

/**
 * Craters (CX-20): fighters blown into walls and the ground, and ki attacks, tear the land up, more the harder the
 * impact. One routine for both, by a {@code force} (about 0..3):
 * <ul>
 *   <li>under {@link #CRACK_FORCE}: nothing;</li>
 *   <li>up to {@link #DIG_FORCE}: the surface cracks (grass to coarse dirt, stone to cobblestone, bricks crack...);</li>
 *   <li>above: a bowl dug into the surface along its normal (wider than deep), its size growing with the force, cracked
 *       blocks round its rim, debris and dust.</li>
 * </ul>
 * Never inside safe zones (PvP zones, the spawn's safe radius, the tournament grounds), never blocks holding something
 * (chests, furnaces...), fluids, unbreakable blocks, or blocks harder than the force allows (up to {@code maxHardness}).
 * A player's crater asks the protection mods first (a block break event for each block). NPCs follow the
 * {@code mobGriefing} rule. Server config {@code [destruction]}.
 */
public final class Destruction {
    public static final double CRACK_FORCE = 0.35, DIG_FORCE = 0.8;

    private static final Map<Block, Block> CRACKED = Map.ofEntries(
            Map.entry(Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT), Map.entry(Blocks.DIRT, Blocks.COARSE_DIRT), Map.entry(Blocks.PODZOL, Blocks.COARSE_DIRT),
            Map.entry(Blocks.MYCELIUM, Blocks.COARSE_DIRT), Map.entry(Blocks.DIRT_PATH, Blocks.COARSE_DIRT), Map.entry(Blocks.STONE, Blocks.COBBLESTONE),
            Map.entry(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS), Map.entry(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE),
            Map.entry(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS), Map.entry(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES),
            Map.entry(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), Map.entry(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS),
            Map.entry(Blocks.SANDSTONE, Blocks.SAND), Map.entry(Blocks.RED_SANDSTONE, Blocks.RED_SAND), Map.entry(Blocks.SNOW_BLOCK, Blocks.SNOW),
            Map.entry(Blocks.ICE, Blocks.AIR), Map.entry(Blocks.GRAVEL, Blocks.GRAVEL));

    private Destruction() {}

    /** The bowl's radius (blocks) for a force: about 1 at the digging threshold, 6 at full force. */
    public static double radius(double force) {
        if (force < DIG_FORCE) return 0;
        return Math.min(DBZConfig.SERVER.destructionMaxRadius.get(), 0.9 + (force - DIG_FORCE) * 2.3);
    }

    /** The hardest block a force can break (stone 1.5, deepslate 3, iron 5, obsidian 50). */
    public static double hardnessLimit(double force) {
        return Math.min(DBZConfig.SERVER.destructionMaxHardness.get(), 2 + force * 12);
    }

    /** A fighter blown away slamming into something. */
    public static int slam(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity victim, Entity by) {
        if (!DBZConfig.SERVER.destructionKnockback.get()) return 0;
        return crater(level, at, normal, force, by);
    }

    /** A ki attack striking something. */
    public static int ki(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        if (!DBZConfig.SERVER.destructionKi.get()) return 0;
        return crater(level, at, normal, force, by);
    }

    /**
     * Digs a crater at {@code at} into the surface facing {@code normal} (unit, pointing out of it). Returns the blocks
     * removed. {@code by} is whoever caused it (a player is checked against protection; an NPC against mobGriefing).
     */
    public static int crater(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!c.destructionEnabled.get() || force < CRACK_FORCE || isProtected(level, at)) return 0;
        if (!(by instanceof Player) && !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return 0;
        ServerPlayer blame = by instanceof ServerPlayer sp ? sp : null;
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        double r = radius(force), depth = r * 0.7, rim = r > 0 ? r * 1.45 + 0.6 : 1.3 + force;
        double hard = hardnessLimit(force);
        Vec3 centre = at.subtract(n.scale(r * 0.3));
        int maxBlocks = c.destructionMaxBlocks.get(), removed = 0, effects = 0;
        double drops = c.destructionDrops.get();
        int reach = (int) Math.ceil(rim) + 1;
        BlockPos base = BlockPos.containing(centre);
        long seed = base.asLong() * 31 + level.getGameTime();
        for (int dx = -reach; dx <= reach && removed < maxBlocks; dx++) for (int dy = -reach; dy <= reach && removed < maxBlocks; dy++) for (int dz = -reach; dz <= reach && removed < maxBlocks; dz++) {
            BlockPos pos = base.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) continue;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || hardness > hard) continue;
            Vec3 v = Vec3.atCenterOf(pos).subtract(centre);
            double along = v.dot(n);
            double lateral = v.subtract(n.scale(along)).length();
            double jitter = 1 + 0.18 * (Mth.sin((float) (seed + pos.asLong() * 7919 % 1000)) );   // a ragged edge
            boolean dig = r > 0 && Math.pow(lateral / r, 2) + Math.pow(along / depth, 2) <= jitter;
            boolean crack = !dig && Math.pow(lateral / rim, 2) + Math.pow(along / Math.max(1, depth * 1.4), 2) <= jitter
                    && (Math.floorMod(pos.asLong() * 2654435761L + seed, 100) < 55);
            if (!dig && !crack) continue;
            if (blame != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, blame))) continue;
            if (dig || state.canBeReplaced()) {
                if (drops > 0 && Math.floorMod(pos.asLong() * 40503L + seed, 1000) < drops * 1000) {
                    Block.dropResources(state, level, pos, null, by, ItemStack.EMPTY);
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
                if (effects < 48 && (removed % 3 == 0 || r < 2)) {
                    level.levelEvent(2001, pos, Block.getId(state));                                       // break particles and sound
                    effects++;
                }
            } else {
                Block to = CRACKED.get(state.getBlock());
                if (to != null && to != state.getBlock()) level.setBlock(pos, to.defaultBlockState(), 3);
            }
        }
        float size = (float) Math.max(0.6, r);
        ImpactPacket.at(at, n, ImpactPacket.EXPLOSION, size, 0xA08A6A, by == null ? -1 : by.getId()).send(level);
        if (r > 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, (float) Math.min(2.5, 0.4 + r * 0.35),
                (float) Math.max(0.5, 1.3 - r * 0.12));
        return removed;
    }

    /** Places nothing is torn up: PvP safe zones, the world spawn's safe radius, the tournament grounds, the other world. */
    public static boolean isProtected(ServerLevel level, Vec3 at) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (level.dimension() == com.dbzenith.world.Otherworld.OTHERWORLD) return true;
        int r = c.pvpSpawnSafeRadius.get();
        if (r > 0 && level.dimension() == Level.OVERWORLD) {
            BlockPos s = level.getSharedSpawnPos();
            double dx = at.x - s.getX() - 0.5, dz = at.z - s.getZ() - 0.5;
            if (dx * dx + dz * dz < (double) r * r) return true;
        }
        if (level.dimension() == Level.OVERWORLD) {
            BlockPos ring = com.dbzenith.tournament.TournamentGrounds.ring(level);
            int half = com.dbzenith.tournament.TournamentGrounds.PLAZA + 4;
            if (ring != null && Math.abs(at.x - ring.getX() - 0.5) <= half && Math.abs(at.z - ring.getZ() - 0.5) <= half && Math.abs(at.y - ring.getY()) < 32) return true;
        }
        return PvpZones.of(level.getServer()).at(level.dimension().location().toString(), at) != null;
    }
}
