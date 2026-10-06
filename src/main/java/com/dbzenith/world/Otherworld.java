package com.dbzenith.world;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.OtherworldPackets;
import com.dbzenith.race.Alignment;
import com.dbzenith.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The other world (CX-12). With the setting on, a fighter who dies is judged: their soul wakes at the check-in station
 * in the golden clouds, a halo over its head, or, if it was evil, in Limbo below. The dead keep their bodies (fighters
 * earn that) and may train: Snake Way runs from the station to the little planet of the Kai of the north, where gravity
 * is ten times the world's; the Grand Kai's paradise has springs of godly ki. Enma, judge of the dead, sends a soul back
 * once its time is served; the Eternal Dragon can bring back every soul at once. Falling through the clouds drops a
 * soul into Limbo; the ogres haul a soul that has served its time there back up to the station.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Otherworld {
    public static final ResourceKey<Level> OTHERWORLD = key("otherworld");
    public static final ResourceKey<Level> LIMBO = key("limbo");

    /** Where souls wake: before the station gate, facing the judge. */
    public static final Vec3 ARRIVAL = new Vec3(0.5, 66, -40.5);
    public static final Vec3 LIMBO_ARRIVAL = new Vec3(0.5, 61, 0.5);
    /** The Kai of the north's little planet, at the end of Snake Way (centre and radius); and its gravity. */
    public static final Vec3 KAI_PLANET = new Vec3(OtherworldBuilder.SNAKE_END_X + 0.5, 96, OtherworldBuilder.SNAKE_LENGTH + 30.5);
    public static final int KAI_PLANET_RADIUS = 16;
    public static final double KAI_GRAVITY = 10;
    /** Seconds of soaking in the springs (meditating) that awaken godly ki. */
    public static final int SOAK_SECONDS = 60;

    private Otherworld() {}

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DBZenith.MOD_ID, name));
    }

    public static boolean enabled() {
        try {
            return DBZConfig.SERVER.otherworldEnabled.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static int deathSeconds() {
        return DBZConfig.SERVER.otherworldDeathSeconds.get();
    }

    public static boolean isOtherworld(Level level) {
        return level.dimension() == OTHERWORLD || level.dimension() == LIMBO;
    }

    /** Seconds before this soul may return (0: now). */
    public static int secondsLeft(ServerPlayer player, PlayerData d) {
        long served = (player.level().getGameTime() - d.getDeathTick()) / 20;
        return (int) Math.max(0, deathSeconds() - served);
    }

    // ------------------------------------------------------------------ dying and waking

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled() || !enabled() || player.isCreative()) return;
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return;
        boolean evil = Alignment.of(d) == Alignment.Standing.EVIL;
        if (!d.isDead()) d.setDead(true, player.level().getGameTime(), evil);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null || !d.isDead() || !enabled()) return;
        if (d.isEvilSoul()) sendTo(player, LIMBO, LIMBO_ARRIVAL);
        else sendTo(player, OTHERWORLD, ARRIVAL);
        player.displayClientMessage(Component.translatable(d.isEvilSoul() ? "message.dbzenith.woke_limbo" : "message.dbzenith.woke_otherworld"), false);
        com.dbzenith.data.PlayerDataEvents.sync(player);
    }

    static void sendTo(ServerPlayer player, ResourceKey<Level> dim, Vec3 at) {
        ServerLevel level = player.server.getLevel(dim);
        if (level == null) return;
        OtherworldBuilder.ensureBuilt(level);
        player.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
        player.fallDistance = 0;
    }

    // ------------------------------------------------------------------ judgement and return

    /** Enma hears your case. */
    public static void openJudgement(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return;
        ModNetwork.sendTo(player, new OtherworldPackets.Judgement(d.isDead(), d.isDead() ? secondsLeft(player, d) : 0));
    }

    /** Back among the living, at your own respawn point. */
    public static boolean returnToLife(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null || !isOtherworld(player.level())) return false;
        if (d.isDead() && secondsLeft(player, d) > 0 && !player.isCreative()) return false;
        d.setDead(false, 0, false);
        MinecraftServer server = player.server;
        BlockPos respawn = player.getRespawnPosition();
        ServerLevel home = respawn != null ? server.getLevel(player.getRespawnDimension()) : null;
        if (home == null || home.dimension() == OTHERWORLD || home.dimension() == LIMBO) {
            home = server.overworld();
            respawn = home.getSharedSpawnPos();
        }
        player.teleportTo(home, respawn.getX() + 0.5, respawn.getY() + 0.1, respawn.getZ() + 0.5, player.getYRot(), 0f);
        player.fallDistance = 0;
        home.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.3f);
        player.displayClientMessage(Component.translatable("message.dbzenith.returned_to_life"), false);
        com.dbzenith.data.PlayerDataEvents.sync(player);
        return true;
    }

    /** The Eternal Dragon's wish: every dead soul comes back at once. Returns how many. */
    public static int reviveAll(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d == null || !d.isDead()) continue;
            d.setDead(false, 0, false);
            if (isOtherworld(p.level())) returnToLife(p);
            com.dbzenith.data.PlayerDataEvents.sync(p);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ the other world, once a second

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (!isOtherworld(level)) return;
        if (level.dimension() == OTHERWORLD && player.getY() < 40) {                          // fell through the clouds
            player.displayClientMessage(Component.translatable("message.dbzenith.fell_to_limbo"), false);
            sendTo(player, LIMBO, LIMBO_ARRIVAL);
            return;
        }
        if (level.dimension() == LIMBO && player.getY() < 0) {
            sendTo(player, LIMBO, LIMBO_ARRIVAL);
            return;
        }
        if (player.tickCount % 20 != 0) return;
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return;
        if (level.dimension() == LIMBO && d.isDead() && secondsLeft(player, d) <= -deathSeconds() / 2) {   // time served twice over below
            player.displayClientMessage(Component.translatable("message.dbzenith.hauled_up"), false);
            sendTo(player, OTHERWORLD, ARRIVAL);
            return;
        }
        if (level.dimension() == OTHERWORLD && level.getBlockState(player.blockPosition()).is(ModBlocks.SACRED_SPRING.get()) && d.isMeditating()) {
            int soaked = d.getSpringSoak() + 1;                                               // the springs of paradise
            d.setSpringSoak(soaked);
            if (soaked == SOAK_SECONDS) {
                d.setFlag("spring_soaked", true);
                player.displayClientMessage(Component.translatable("message.dbzenith.spring_awakened"), false);
            }
            if (d.hasFlag(com.dbzenith.transform.GodKi.FLAG)) d.setGodKiXp(d.getGodKiXp() + 0.6);
            ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, player.getX(), player.getY() + 0.5, player.getZ(),
                    3, 0.4, 0.4, 0.4, 0.01);
        }
    }

    /** Ten times gravity on and around the Kai of the north's little planet. */
    public static boolean nearKaiPlanet(ServerPlayer player) {
        return player.level().dimension() == OTHERWORLD && player.position().distanceTo(KAI_PLANET) < KAI_PLANET_RADIUS + 10;
    }
}
