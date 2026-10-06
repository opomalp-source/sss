package com.dbzenith.dragonball;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.OpenWishPacket;
import com.dbzenith.network.RadarPacket;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.registry.ModEntities;
import com.dbzenith.registry.ModItems;
import com.dbzenith.world.Planet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Dragon Ball cycle, for each of the three sets ({@link BallSet}): scatter, collect (break the block to pick up,
 * place to put down), bring all seven together and use one to summon the set's dragon, make a wish, then the balls turn
 * to stone for {@code dragon_balls.inertTicks} before scattering again.
 * <p>
 * Earth's balls scatter around spawn, the Super Dragon Balls far out in the overworld, the Black Star balls across the
 * other planets. A Black Star wish starts the curse: summon the seven again before it runs out (that dragon grants
 * nothing but lifting it), or meteors fall around everyone on Earth until it is lifted.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DragonBalls {
    public static final int COUNT = 7;
    public static final int GATHER_RADIUS = 4;

    /** Set while this class moves blocks itself, so block callbacks don't re-record the change. */
    private static boolean moving;

    private DragonBalls() {}

    public static List<RegistryObject<Block>> blocks(BallSet set) {
        return switch (set) {
            case EARTH -> ModBlocks.DRAGON_BALLS;
            case BLACK_STAR -> ModBlocks.BLACK_STAR_BALLS;
            case SUPER -> ModBlocks.SUPER_BALLS;
        };
    }

    public static Block block(BallSet set, int star) {
        return blocks(set).get(star - 1).get();
    }

    /** One of Earth's balls. */
    public static Block block(int star) {
        return block(BallSet.EARTH, star);
    }

    public static Item item(BallSet set, int star) {
        return block(set, star).asItem();
    }

    public static boolean enabled(BallSet set) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!c.dragonBallsEnabled.get()) return false;
        return switch (set) {
            case EARTH -> true;
            case BLACK_STAR -> c.blackStarEnabled.get();
            case SUPER -> c.superBallsEnabled.get();
        };
    }

    // ------------------------------------------------------------------ tracking

    static void onPlaced(ServerLevel level, BallSet set, int star, BlockPos pos) {
        if (moving) return;
        DragonBallData.get(level.getServer(), set).set(star, DragonBallData.State.PLACED, level.dimension(), pos);
    }

    static void onRemoved(ServerLevel level, BallSet set, int star, BlockPos pos) {
        if (moving) return;
        DragonBallData data = DragonBallData.get(level.getServer(), set);
        DragonBallData.Entry e = data.entry(star);
        if (e.state == DragonBallData.State.PLACED && e.pos.equals(pos)) data.set(star, DragonBallData.State.HELD, null, null);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 100 == 0) for (BallSet set : BallSet.values()) maintain(server, set, now);
        if (now % 40 == 0) sendRadar(server);
        tickCurse(server, now);
        tickMeteors(server);
    }

    /** Earth's set. */
    public static void maintain(MinecraftServer server, long now) {
        maintain(server, BallSet.EARTH, now);
    }

    /** Scatter unset balls; wake inert ones when their time is up. */
    public static void maintain(MinecraftServer server, BallSet set, long now) {
        if (!enabled(set)) return;
        DragonBallData data = DragonBallData.get(server, set);
        boolean inert = data.getInertUntil() > now;
        for (int star = 1; star <= COUNT; star++) {
            DragonBallData.State s = data.entry(star).state;
            if (s == DragonBallData.State.UNSET || (s == DragonBallData.State.INERT && !inert)) scatter(server, set, star);
        }
    }

    /** One of Earth's balls. */
    public static BlockPos scatter(MinecraftServer server, int star) {
        return scatter(server, BallSet.EARTH, star);
    }

    /**
     * Places one ball at a random dry surface spot: around world spawn for Earth's set, much further out for the Super
     * set, around the middle of a random other planet for the Black Star set.
     */
    public static BlockPos scatter(MinecraftServer server, BallSet set, int star) {
        RandomSource rnd = server.overworld().random;
        ServerLevel level = server.overworld();
        BlockPos center = level.getSharedSpawnPos();
        int radius = DBZConfig.SERVER.dragonBallScatterRadius.get();
        if (set == BallSet.SUPER) radius = DBZConfig.SERVER.superScatterRadius.get();
        if (set == BallSet.BLACK_STAR) {
            List<ServerLevel> planets = new ArrayList<>();
            for (Planet p : Planet.values()) {
                ServerLevel l = p == Planet.EARTH ? null : server.getLevel(p.dimension());
                if (l != null) planets.add(l);
            }
            if (!planets.isEmpty()) {
                level = planets.get(rnd.nextInt(planets.size()));
                center = BlockPos.ZERO;
            }
            radius = DBZConfig.SERVER.blackStarScatterRadius.get();
        }
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = center.getX() + rnd.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + rnd.nextInt(radius * 2 + 1) - radius;
            level.getChunk(x >> 4, z >> 4); // load or generate first: getHeight reports the world bottom for unloaded chunks
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(pos.below());
            if (!level.getFluidState(pos).isEmpty() || !below.isSolidRender(level, pos.below()) || y <= level.getMinBuildHeight()) continue;
            moving = true;
            try {
                level.setBlockAndUpdate(pos, block(set, star).defaultBlockState());
            } finally {
                moving = false;
            }
            DragonBallData.get(server, set).set(star, DragonBallData.State.PLACED, level.dimension(), pos);
            return pos;
        }
        return null; // try again next maintenance pass
    }

    // ------------------------------------------------------------------ summoning

    /** Earth's set. */
    public static boolean trySummon(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        return trySummon(level, player, clicked, BallSet.EARTH);
    }

    /** All seven of a set placed within {@link #GATHER_RADIUS} of {@code clicked}: remove them, raise the dragon, ask for a wish. */
    public static boolean trySummon(ServerLevel level, ServerPlayer player, BlockPos clicked, BallSet set) {
        List<BlockPos> found = new ArrayList<>();
        for (int star = 1; star <= COUNT; star++) {
            BlockPos at = findNear(level, clicked, block(set, star));
            if (at == null) return false;
            found.add(at);
        }
        moving = true;
        try {
            for (BlockPos p : found) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        } finally {
            moving = false;
        }
        DragonBallData data = DragonBallData.get(level.getServer(), set);
        for (int star = 1; star <= COUNT; star++) data.set(star, DragonBallData.State.INERT, null, null);
        data.setInertUntil(level.getGameTime() + DBZConfig.SERVER.dragonBallInertTicks.get());

        DragonSpiritEntity dragon = new DragonSpiritEntity(ModEntities.DRAGON_SPIRIT.get(), level);
        dragon.setPos(clicked.getX() + 0.5, clicked.getY(), clicked.getZ() + 0.5);
        dragon.setSummoner(player.getUUID());
        dragon.setSet(set);
        dragon.setLiftsCurse(set == BallSet.BLACK_STAR && data.isCursed());
        com.dbzenith.data.ModCapabilities.get(player).ifPresent(d -> d.setFlag("summoned_dragon", true));
        level.addFreshEntity(dragon);
        level.setWeatherParameters(0, 2400, true, true);
        level.playSound(null, clicked, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT, 4f, set == BallSet.SUPER ? 0.4f : 0.6f);
        level.sendParticles(ParticleTypes.FLASH, clicked.getX() + 0.5, clicked.getY() + 1, clicked.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.dbzenith.dragon_summoned_" + set.id(), player.getDisplayName()), false);
        ModNetwork.sendTo(player, new OpenWishPacket(dragon.getId()));
        return true;
    }

    private static BlockPos findNear(ServerLevel level, BlockPos center, Block block) {
        int r = GATHER_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
            if (level.getBlockState(p).is(block)) return p.immutable();
        }
        return null;
    }

    static void dragonDeparts(DragonSpiritEntity dragon) {
        if (dragon.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 10, dragon.getZ(), 120, 3, 10, 3, 0.2);
            level.playSound(null, dragon.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 4f, 0.5f);
            level.setWeatherParameters(6000, 0, false, false);
        }
        dragon.discard();
    }

    // ------------------------------------------------------------------ the Black Star curse

    /** A Black Star wish was granted: the seven scatter across the galaxy, and the clock starts. */
    public static void curse(MinecraftServer server, long now) {
        DragonBallData data = DragonBallData.get(server, BallSet.BLACK_STAR);
        data.setCurse(now + DBZConfig.SERVER.blackStarCurseTicks.get(), false);
        data.setInertUntil(now);                                               // they scatter at once: the race is on
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.black_star_curse",
                DBZConfig.SERVER.blackStarCurseTicks.get() / 24000.0 >= 1 ? String.format("%.0f", DBZConfig.SERVER.blackStarCurseTicks.get() / 24000.0)
                        : String.format("%.2f", DBZConfig.SERVER.blackStarCurseTicks.get() / 24000.0)).withStyle(ChatFormatting.DARK_RED), false);
    }

    /** The seven came back in time (or after the doom): the curse is lifted. */
    public static void liftCurse(MinecraftServer server) {
        DragonBallData.get(server, BallSet.BLACK_STAR).setCurse(-1, false);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.black_star_lifted").withStyle(ChatFormatting.GOLD), false);
    }

    /** The curse runs down a day at a time; when it runs out the doom begins. */
    public static void tickCurse(MinecraftServer server, long now) {
        DragonBallData data = DragonBallData.get(server, BallSet.BLACK_STAR);
        long until = data.getCurseUntil();
        if (until < 0 || data.isDoom()) return;
        long left = until - now;
        if (left <= 0) {
            data.setCurse(until, true);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.black_star_doom").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        } else if (left % 24000 == 0) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.black_star_days", left / 24000).withStyle(ChatFormatting.RED), false);
        }
    }

    /** A meteor on its way down: where it lands and when. */
    private record Meteor(ServerLevel level, Vec3 from, Vec3 to, long start, long land) {}

    private static final List<Meteor> METEORS = new ArrayList<>();
    private static final int METEOR_FALL = 24;

    /** While doomed, a meteor comes down near each player on Earth now and then. */
    static void tickMeteors(MinecraftServer server) {
        ServerLevel earth = server.overworld();
        long now = earth.getGameTime();
        if (DragonBallData.get(server, BallSet.BLACK_STAR).isDoom() && now % DBZConfig.SERVER.meteorIntervalTicks.get() == 0) {
            for (ServerPlayer p : earth.players()) if (!p.isSpectator()) meteorNear(earth, p.position(), now);
        }
        for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
            Meteor m = it.next();
            if (m.level.getServer() != server) {
                it.remove();
                continue;
            }
            float f = Math.min(1f, (now - m.start) / (float) METEOR_FALL);
            Vec3 at = m.from.lerp(m.to, f);
            m.level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.02);
            m.level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 6, 0.6, 0.6, 0.6, 0.01);
            m.level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0);
            m.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.25, 0.25, 0.25, 0.01);   // the white-hot core
            if (now >= m.land) {
                it.remove();
                land(m);
            }
        }
    }

    /** Sends a meteor down 10 to 28 blocks from {@code near}, falling in from high and to one side. */
    public static void meteorNear(ServerLevel level, Vec3 near, long now) {
        RandomSource rnd = level.random;
        double a = rnd.nextDouble() * Math.PI * 2, r = 10 + rnd.nextDouble() * 18;
        int x = (int) Math.floor(near.x + Math.cos(a) * r), z = (int) Math.floor(near.z + Math.sin(a) * r);
        level.getChunk(x >> 4, z >> 4);
        meteorAt(level, new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5), now);
    }

    /** Sends a meteor down onto {@code to}, falling in from high and to one side. */
    public static void meteorAt(ServerLevel level, Vec3 to, long now) {
        double a = level.random.nextDouble() * Math.PI * 2;
        Vec3 from = to.add(Math.cos(a) * 18, 46, Math.sin(a) * 18);
        METEORS.add(new Meteor(level, from, to, now, now + METEOR_FALL));
        level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3f, 0.5f);
    }

    private static void land(Meteor m) {
        Vec3 at = m.to;
        Level.ExplosionInteraction how = DBZConfig.SERVER.meteorsBreakBlocks.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        m.level.explode(null, at.x, at.y, at.z, 2.6f, how == Level.ExplosionInteraction.TNT, how);   // no fires either, unless meteors may wreck the place
        m.level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 24, 1.2, 0.4, 1.2, 0.2);
        ImpactPacket.at(at.add(0, 0.5, 0), m.to.subtract(m.from).normalize(), ImpactPacket.EXPLOSION, 3f, 0xFF6A20, -1).send(m.level);
    }

    /** Meteors in flight (tests). */
    public static int meteorsFalling() {
        return METEORS.size();
    }

    // ------------------------------------------------------------------ radar

    public static boolean holdsRadar(ServerPlayer p) {
        Item radar = ModItems.DRAGON_RADAR.get();
        return p.getMainHandItem().is(radar) || p.getOffhandItem().is(radar);
    }

    /**
     * Balls of every set placed in the player's dimension within radar range, plus balls carried by online players there.
     * Blips are numbered by set ({@link BallSet#radarOffset}).
     */
    public static List<RadarPacket.Blip> blipsFor(ServerPlayer player) {
        List<RadarPacket.Blip> out = new ArrayList<>();
        int range = DBZConfig.SERVER.radarRange.get();
        ResourceKey<Level> here = player.level().dimension();
        for (BallSet set : BallSet.values()) {
            DragonBallData data = DragonBallData.get(player.server, set);
            for (int star = 1; star <= COUNT; star++) {
                DragonBallData.Entry e = data.entry(star);
                if (e.state == DragonBallData.State.PLACED && e.dimension == here
                        && e.pos.distSqr(player.blockPosition()) <= (double) range * range) {
                    out.add(new RadarPacket.Blip(star + set.radarOffset(), e.pos.getX(), e.pos.getZ()));
                }
            }
            for (ServerPlayer other : player.serverLevel().players()) {
                for (int star = 1; star <= COUNT; star++) {
                    if (other.getInventory().contains(new ItemStack(item(set, star))) && other.distanceToSqr(player) <= (double) range * range) {
                        out.add(new RadarPacket.Blip(star + set.radarOffset(), other.getBlockX(), other.getBlockZ()));
                    }
                }
            }
        }
        return out;
    }

    private static void sendRadar(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (holdsRadar(p)) ModNetwork.sendTo(p, new RadarPacket(blipsFor(p)));
        }
    }
}
