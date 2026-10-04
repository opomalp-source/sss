package com.dbzenith.world;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/** Places a Space Pod can fly to. Gravity is applied to everyone on the planet (see TrainingTicker). */
public enum Planet {
    EARTH(Level.OVERWORLD, 1),
    NAMEK(key("namek"), 1),
    NORTHERN_PLANET(key("northern_planet"), 10);

    public static final int TRAVEL_COOLDOWN_TICKS = 1200;

    private final ResourceKey<Level> dimension;
    private final double gravity;

    Planet(ResourceKey<Level> dimension, double gravity) {
        this.dimension = dimension;
        this.gravity = gravity;
    }

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DBZenith.MOD_ID, name));
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public double gravity() {
        return gravity;
    }

    public String translationKey() {
        return "planet.dbzenith." + name().toLowerCase(Locale.ROOT);
    }

    /** The planet a level belongs to, or null (e.g. the Nether or the Time Chamber). */
    public static Planet of(Level level) {
        for (Planet p : values()) if (p.dimension == level.dimension()) return p;
        return null;
    }

    /** Fly there. Fails during the cooldown, from the Time Chamber, or to the planet you are on. */
    public static boolean travel(ServerPlayer player, Planet target) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        long now = player.level().getGameTime();
        if (d == null || TimeChamber.isIn(player) || Planet.of(player.level()) == target) return false;
        if (d.isOnCooldown("space_travel", now) && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.dbzenith.pod_recharging"), true);
            return false;
        }
        ServerLevel level = player.server.getLevel(target.dimension);
        if (level == null) return false;
        BlockPos base = target == EARTH ? level.getSharedSpawnPos() : BlockPos.ZERO;
        level.getChunk(base.getX() >> 4, base.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
        player.teleportTo(level, base.getX() + 0.5, y, base.getZ() + 0.5, player.getYRot(), 0f);
        player.fallDistance = 0;
        d.setCooldown("space_travel", now + TRAVEL_COOLDOWN_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(Component.translatable("message.dbzenith.pod_landed", Component.translatable(target.translationKey())), false);
        return true;
    }
}
