package com.dbzenith.combat;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.ImpactPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
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
@Mod.EventBusSubscriber(modid = com.dbzenith.DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

    /** A ki attack striking something: the same routine, but far bigger, with a scorched floor, a raised rim and debris. */
    public static int ki(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        if (!DBZConfig.SERVER.destructionKi.get()) return 0;
        return crater(level, at, normal, force, by, true, true);
    }

    /** A beam held on something, carving into it as it goes: as big, but no lip or flung debris (it would pile up). */
    public static int kiCarve(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        if (!DBZConfig.SERVER.destructionKi.get()) return 0;
        return crater(level, at, normal, force, by, true, false);
    }

    /** A ki crater's radius for a force (the ki attack rework, phase 6: "make craters very huge"). */
    public static double kiRadius(double force) {
        if (force < DIG_FORCE) return 0;
        return Math.min(DBZConfig.SERVER.kiCraterMaxRadius.get(), (0.9 + (force - DIG_FORCE) * 2.3) * DBZConfig.SERVER.kiCraterScale.get());
    }

    /**
     * Digs a crater at {@code at} into the surface facing {@code normal} (unit, pointing out of it). Returns the blocks
     * removed. {@code by} is whoever caused it (a player is checked against protection; an NPC against mobGriefing).
     */
    public static int crater(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        return crater(level, at, normal, force, by, false, false);
    }

    private static int crater(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by, boolean ki, boolean extras) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!c.destructionEnabled.get() || force < CRACK_FORCE || isProtected(level, at)) return 0;
        if (!(by instanceof Player) && !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return 0;
        ServerPlayer blame = by instanceof ServerPlayer sp ? sp : null;
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        double r = ki ? kiRadius(force) : radius(force), depth = r * 0.7, rim = r > 0 ? r * 1.45 + 0.6 : 1.3 + force;
        double hard = hardnessLimit(force);
        Vec3 centre = at.subtract(n.scale(r * 0.3));
        int maxBlocks = ki ? c.kiCraterMaxBlocks.get() : c.destructionMaxBlocks.get();
        int reach = (int) Math.ceil(rim) + 1;
        BlockPos base = BlockPos.containing(centre);
        long seed = base.asLong() * 31 + level.getGameTime();
        List<long[]> dig = new ArrayList<>();                                  // {pos, distance squared}: dug from the middle outward
        List<BlockPos> crack = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) for (int dy = -reach; dy <= reach; dy++) for (int dz = -reach; dz <= reach; dz++) {
            BlockPos pos = base.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) continue;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || hardness > hard) continue;
            Vec3 v = Vec3.atCenterOf(pos).subtract(centre);
            double along = v.dot(n);
            double lateral = v.subtract(n.scale(along)).length();
            double jitter = 1 + 0.18 * (Mth.sin((float) (seed + pos.asLong() * 7919 % 1000)) );   // a ragged edge
            boolean digs = r > 0 && Math.pow(lateral / r, 2) + Math.pow(along / depth, 2) <= jitter;
            boolean cracks = !digs && Math.pow(lateral / rim, 2) + Math.pow(along / Math.max(1, depth * 1.4), 2) <= jitter
                    && (Math.floorMod(pos.asLong() * 2654435761L + seed, 100) < 55);
            if (digs || (cracks && state.canBeReplaced())) dig.add(new long[]{pos.asLong(), (long) (v.lengthSqr() * 16)});
            else if (cracks) crack.add(pos);
        }
        dig.sort((a, b) -> Long.compare(a[1], b[1]));
        if (dig.size() > maxBlocks) dig = new ArrayList<>(dig.subList(0, maxBlocks));
        Job job = new Job(level, by, blame, dig, crack, seed, extras && r >= 3 ? n : null, centre, r);
        int removed;
        if (ki && r > 5) {                                                     // a big one is carved over a few ticks, outward
            JOBS.add(job);
            removed = dig.size();
        } else {
            removed = job.run(Integer.MAX_VALUE);
            job.finish();
        }
        float size = (float) Math.max(0.6, r);                                 // its dust, debris and rumble on the clients (phase 6: no vanilla bang)
        ImpactPacket.at(at, n, ImpactPacket.CRATER, size, 0xA08A6A, by == null ? -1 : by.getId()).send(level);
        return removed;
    }

    // ------------------------------------------------------------------ carving (the ki attack rework, phase 6)

    private static final List<Job> JOBS = new ArrayList<>();
    /** What craters took, to grow back (when {@code regrowSeconds} is on). */
    private static final List<Regrow> REGROW = new ArrayList<>();

    private record Regrow(ServerLevel level, long pos, BlockState was, BlockState placed, long due) {}

    /** One crater being carved: blocks dug from the middle outward, then its rim cracked, its floor scorched, its lip raised. */
    private static final class Job {
        final ServerLevel level;
        final Entity by;
        final ServerPlayer blame;
        final List<long[]> dig;
        final List<BlockPos> crack;
        final long seed;
        final Vec3 extras, centre;                                              // extras: the normal, when it gets a floor, lip and debris
        final double r;
        final boolean regrow = DBZConfig.SERVER.craterRegrowSeconds.get() > 0;
        final List<Regrow> taken = new ArrayList<>();
        int next, removed, effects, debris;

        Job(ServerLevel level, Entity by, ServerPlayer blame, List<long[]> dig, List<BlockPos> crack, long seed, Vec3 extras, Vec3 centre, double r) {
            this.level = level;
            this.by = by;
            this.blame = blame;
            this.dig = dig;
            this.crack = crack;
            this.seed = seed;
            this.extras = extras;
            this.centre = centre;
            this.r = r;
        }

        boolean done() {
            return next >= dig.size();
        }

        /** Digs up to {@code budget} blocks; returns how many went. */
        int run(int budget) {
            double drops = DBZConfig.SERVER.destructionDrops.get();
            boolean fling = extras != null && !regrow && DBZConfig.SERVER.craterDebris.get();
            int maxDebris = (int) Math.min(48, r * 2.5);
            int went = 0;
            while (next < dig.size() && went < budget) {
                BlockPos pos = BlockPos.of(dig.get(next++)[0]);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) continue;
                if (blame != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, blame))) continue;
                if (drops > 0 && Math.floorMod(pos.asLong() * 40503L + seed, 1000) < drops * 1000) Block.dropResources(state, level, pos, null, by, ItemStack.EMPTY);
                if (fling && debris < maxDebris && level.getBlockState(pos.above()).isAir() && Math.floorMod(pos.asLong() * 69069L + seed, 100) < 18) {
                    Vec3 out = Vec3.atCenterOf(pos).subtract(centre).multiply(1, 0, 1);   // thrown up and out over the rim
                    if (out.lengthSqr() < 1e-4) out = new Vec3(1, 0, 0);
                    out = out.normalize();
                    double sp = 0.35 + Math.floorMod(pos.asLong() * 31L + seed, 100) / 100.0 * 0.5;
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, state);
                    fb.setDeltaMovement(out.x * sp, 0.55 + sp * 0.6, out.z * sp);
                    fb.dropItem = false;
                    debris++;
                } else {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                if (regrow) taken.add(new Regrow(level, pos.asLong(), state, null, 0));
                removed++;
                went++;
                if (effects < 48 && (removed % 3 == 0 || r < 2)) {
                    level.levelEvent(2001, pos, Block.getId(state));                                       // break particles and sound
                    effects++;
                }
            }
            return went;
        }

        /** Done digging: the rim cracks; a ki crater's floor is scorched and its lip raised with what was thrown out. */
        void finish() {
            for (BlockPos pos : crack) {
                BlockState state = level.getBlockState(pos);
                Block to = CRACKED.get(state.getBlock());
                if (to == null || to == state.getBlock()) continue;
                if (blame != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, blame))) continue;
                level.setBlock(pos, to.defaultBlockState(), 3);
                if (regrow) taken.add(new Regrow(level, pos.asLong(), state, to.defaultBlockState(), 0));
            }
            if (extras != null) {
                scorch();
                if (extras.y > 0.6) lip();
            }
            if (regrow && !taken.isEmpty()) {
                long due = level.getGameTime() + DBZConfig.SERVER.craterRegrowSeconds.get() * 20L;
                taken.sort((a, b) -> Integer.compare(BlockPos.getY(a.pos), BlockPos.getY(b.pos)));   // it grows back from the bottom up
                for (Regrow g : taken) REGROW.add(new Regrow(level, g.pos, g.was, g.placed, due));
            }
        }

        /** The floor left burnt: blackstone, coarse earth and tuff where the energy struck, most at the middle. */
        private void scorch() {
            for (int i = 0; i < next; i++) {
                BlockPos below = BlockPos.of(dig.get(i)[0]).below();
                BlockState state = level.getBlockState(below);
                if (state.isAir() || !CRACKED.containsKey(state.getBlock()) || !level.getBlockState(below.above()).isAir()) continue;
                int roll = Math.floorMod(below.asLong() * 2246822519L + seed, 100);
                double d = Vec3.atCenterOf(below).distanceTo(centre) / Math.max(1, r);
                Block to = roll < 45 * (1.2 - d) ? Blocks.BLACKSTONE : roll < 70 ? Blocks.COARSE_DIRT : roll < 82 ? Blocks.TUFF : null;
                if (to == null || to == state.getBlock()) continue;
                if (blame != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, below, state, blame))) continue;
                level.setBlock(below, to.defaultBlockState(), 3);
                if (regrow) taken.add(new Regrow(level, below.asLong(), state, to.defaultBlockState(), 0));
            }
        }

        /** The lip: earth thrown out piled in a ring round the edge, highest just outside it, made of what the ground is. */
        private void lip() {
            int ring = (int) Math.ceil(r * 1.45);
            BlockPos mid = BlockPos.containing(centre);
            double peak = Math.max(1, r * 0.13);
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) / r;
                if (d < 0.95 || d > 1.45) continue;
                double h = peak * (1 - Math.abs(d - 1.12) / 0.33) * (0.75 + 0.5 * Math.floorMod(mid.asLong() + dx * 341873128712L + dz * 132897987541L + seed, 100) / 100.0);
                int blocks = (int) Math.round(h);
                if (blocks <= 0) continue;
                BlockPos top = surface(mid.getX() + dx, mid.getZ() + dz, mid.getY() + (int) Math.ceil(r), mid.getY() - (int) Math.ceil(r));
                if (top == null) continue;
                BlockState ground = level.getBlockState(top);
                Block pile = ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM) ? Blocks.DIRT
                        : CRACKED.getOrDefault(ground.getBlock(), ground.getBlock());
                if (pile == Blocks.AIR || pile == Blocks.ICE) pile = Blocks.COARSE_DIRT;
                for (int k = 1; k <= blocks; k++) {
                    BlockPos p = top.above(k);
                    if (!level.getBlockState(p).isAir()) break;
                    if (blame != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(), level, p),
                            level.getBlockState(top), blame))) break;
                    BlockState put = (k == blocks && pile == Blocks.DIRT ? Blocks.COARSE_DIRT : pile).defaultBlockState();
                    level.setBlock(p, put, 3);
                    if (regrow) taken.add(new Regrow(level, p.asLong(), Blocks.AIR.defaultBlockState(), put, 0));
                }
            }
        }

        /** The highest solid block of a column between two heights, or null. */
        private BlockPos surface(int x, int z, int fromY, int toY) {
            for (int y = fromY; y >= toY; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                if (!s.getFluidState().isEmpty()) return null;
                if (s.isSolid() && level.getBlockState(p.above()).isAir()) return p;
            }
            return null;
        }
    }

    /** Each server tick: carve the big craters a little further, and grow back what is due. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int budget = DBZConfig.SERVER.craterBlocksPerTick.get();
        while (!JOBS.isEmpty() && budget > 0) {
            Job job = JOBS.get(0);
            budget -= Math.max(1, job.run(budget));
            if (job.done()) {
                job.finish();
                JOBS.remove(0);
            }
        }
        if (REGROW.isEmpty()) return;
        int grow = 300;
        java.util.Iterator<Regrow> it = REGROW.iterator();
        while (it.hasNext() && grow > 0) {
            Regrow g = it.next();
            if (g.level.getGameTime() < g.due) continue;
            it.remove();
            grow--;
            BlockPos p = BlockPos.of(g.pos);
            BlockState now = g.level.getBlockState(p);
            boolean ours = g.placed == null ? now.isAir() : now == g.placed;   // never over something built or placed since
            if (ours) g.level.setBlock(p, g.was, 3);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
        REGROW.clear();
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
