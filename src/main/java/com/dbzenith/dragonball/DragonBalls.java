package com.dbzenith.dragonball;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.OpenWishPacket;
import com.dbzenith.network.RadarPacket;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.registry.ModEntities;
import com.dbzenith.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dragon Ball cycle: scatter around spawn, collect (break the block to pick up, place to put down),
 * bring all seven together and use one to summon the dragon, make a wish, then the balls turn to stone
 * for {@code dragon_balls.inertTicks} before scattering again.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DragonBalls {
    public static final int COUNT = 7;
    public static final int GATHER_RADIUS = 4;

    /** Set while this class moves blocks itself, so block callbacks don't re-record the change. */
    private static boolean moving;

    private DragonBalls() {}

    public static Block block(int star) {
        return ModBlocks.DRAGON_BALLS.get(star - 1).get();
    }

    public static Item item(int star) {
        return block(star).asItem();
    }

    // ------------------------------------------------------------------ tracking

    static void onPlaced(ServerLevel level, int star, BlockPos pos) {
        if (moving) return;
        DragonBallData.get(level.getServer()).set(star, DragonBallData.State.PLACED, level.dimension(), pos);
    }

    static void onRemoved(ServerLevel level, int star, BlockPos pos) {
        if (moving) return;
        DragonBallData data = DragonBallData.get(level.getServer());
        DragonBallData.Entry e = data.entry(star);
        if (e.state == DragonBallData.State.PLACED && e.pos.equals(pos)) data.set(star, DragonBallData.State.HELD, null, null);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 100 == 0) maintain(server, now);
        if (now % 40 == 0) sendRadar(server);
    }

    /** Scatter unset balls; wake inert ones when their time is up. */
    public static void maintain(MinecraftServer server, long now) {
        if (!DBZConfig.SERVER.dragonBallsEnabled.get()) return;
        DragonBallData data = DragonBallData.get(server);
        boolean inert = data.getInertUntil() > now;
        for (int star = 1; star <= COUNT; star++) {
            DragonBallData.State s = data.entry(star).state;
            if (s == DragonBallData.State.UNSET || (s == DragonBallData.State.INERT && !inert)) scatter(server, star);
        }
    }

    /** Places one ball at a random dry surface spot within the scatter radius of world spawn. */
    public static BlockPos scatter(MinecraftServer server, int star) {
        ServerLevel level = server.overworld();
        RandomSource rnd = level.random;
        int radius = DBZConfig.SERVER.dragonBallScatterRadius.get();
        BlockPos spawn = level.getSharedSpawnPos();
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = spawn.getX() + rnd.nextInt(radius * 2 + 1) - radius;
            int z = spawn.getZ() + rnd.nextInt(radius * 2 + 1) - radius;
            level.getChunk(x >> 4, z >> 4); // load or generate first: getHeight reports the world bottom for unloaded chunks
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(pos.below());
            if (!level.getFluidState(pos).isEmpty() || !below.isSolidRender(level, pos.below()) || y <= level.getMinBuildHeight()) continue;
            moving = true;
            try {
                level.setBlockAndUpdate(pos, block(star).defaultBlockState());
            } finally {
                moving = false;
            }
            DragonBallData.get(server).set(star, DragonBallData.State.PLACED, Level.OVERWORLD, pos);
            return pos;
        }
        return null; // try again next maintenance pass
    }

    // ------------------------------------------------------------------ summoning

    /** All seven placed within {@link #GATHER_RADIUS} of {@code clicked}: remove them, spawn the dragon, ask for a wish. */
    public static boolean trySummon(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        List<BlockPos> found = new ArrayList<>();
        for (int star = 1; star <= COUNT; star++) {
            BlockPos at = findNear(level, clicked, block(star));
            if (at == null) return false;
            found.add(at);
        }
        moving = true;
        try {
            for (BlockPos p : found) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        } finally {
            moving = false;
        }
        DragonBallData data = DragonBallData.get(level.getServer());
        for (int star = 1; star <= COUNT; star++) data.set(star, DragonBallData.State.INERT, null, null);
        data.setInertUntil(level.getGameTime() + DBZConfig.SERVER.dragonBallInertTicks.get());

        DragonSpiritEntity dragon = new DragonSpiritEntity(ModEntities.DRAGON_SPIRIT.get(), level);
        dragon.setPos(clicked.getX() + 0.5, clicked.getY(), clicked.getZ() + 0.5);
        dragon.setSummoner(player.getUUID());
        com.dbzenith.data.ModCapabilities.get(player).ifPresent(d -> d.setFlag("summoned_dragon", true));
        level.addFreshEntity(dragon);
        level.setWeatherParameters(0, 2400, true, true);
        level.playSound(null, clicked, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT, 4f, 0.6f);
        level.sendParticles(ParticleTypes.FLASH, clicked.getX() + 0.5, clicked.getY() + 1, clicked.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.dbzenith.dragon_summoned", player.getDisplayName()), false);
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

    // ------------------------------------------------------------------ radar

    public static boolean holdsRadar(ServerPlayer p) {
        Item radar = ModItems.DRAGON_RADAR.get();
        return p.getMainHandItem().is(radar) || p.getOffhandItem().is(radar);
    }

    /** Balls placed in the player's dimension within radar range, plus balls carried by online players there. */
    public static List<RadarPacket.Blip> blipsFor(ServerPlayer player) {
        List<RadarPacket.Blip> out = new ArrayList<>();
        DragonBallData data = DragonBallData.get(player.server);
        int range = DBZConfig.SERVER.radarRange.get();
        for (int star = 1; star <= COUNT; star++) {
            DragonBallData.Entry e = data.entry(star);
            if (e.state == DragonBallData.State.PLACED && e.dimension == player.level().dimension()
                    && e.pos.distSqr(player.blockPosition()) <= (double) range * range) {
                out.add(new RadarPacket.Blip(star, e.pos.getX(), e.pos.getZ()));
            }
        }
        for (ServerPlayer other : player.serverLevel().players()) {
            for (int star = 1; star <= COUNT; star++) {
                if (other.getInventory().contains(new ItemStack(item(star))) && other.distanceToSqr(player) <= (double) range * range) {
                    out.add(new RadarPacket.Blip(star, other.getBlockX(), other.getBlockZ()));
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
