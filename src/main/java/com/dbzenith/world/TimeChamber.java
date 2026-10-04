package com.dbzenith.world;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Hyperbolic Time Chamber: a white void dimension (data/dbzenith/dimension) with heavy gravity where
 * training counts several times over. Entered and left through Time Chamber Doors; stays are time-limited.
 */
public final class TimeChamber {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DBZenith.MOD_ID, "time_chamber"));
    /** Floor top of the flat generator (bedrock + 3 white concrete from y = 0). */
    public static final int FLOOR_Y = 4;
    public static final BlockPos EXIT_DOOR = new BlockPos(0, FLOOR_Y, 3);

    private TimeChamber() {}

    public static boolean isIn(Player player) {
        return player.level().dimension() == KEY;
    }

    public static boolean enter(ServerPlayer player) {
        ServerLevel chamber = player.server.getLevel(KEY);
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (chamber == null || data == null || isIn(player)) return false;
        data.setChamberReturn(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
                player.level().getGameTime());
        if (!chamber.getBlockState(EXIT_DOOR).is(ModBlocks.TIME_CHAMBER_DOOR.get())) {
            chamber.setBlockAndUpdate(EXIT_DOOR, ModBlocks.TIME_CHAMBER_DOOR.get().defaultBlockState());
        }
        player.teleportTo(chamber, 0.5, FLOOR_Y, 0.5, 180f, 0f);
        chamber.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(Component.translatable("message.dbzenith.chamber_enter"), false);
        return true;
    }

    public static boolean exit(ServerPlayer player) {
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !isIn(player)) return false;
        ServerLevel back = null;
        if (!data.getReturnDimension().isEmpty()) {
            back = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(data.getReturnDimension())));
        }
        if (back == null) {
            back = player.server.overworld();
            BlockPos spawn = back.getSharedSpawnPos();
            player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
        } else {
            player.teleportTo(back, data.getReturnX(), data.getReturnY(), data.getReturnZ(), player.getYRot(), 0f);
        }
        data.clearChamber();
        player.displayClientMessage(Component.translatable("message.dbzenith.chamber_exit"), true);
        return true;
    }

    /** Called once per second: sends players home when their stay runs out. */
    public static void tick(ServerPlayer player, PlayerData data, long now) {
        if (!isIn(player)) return;
        long entered = data.getChamberEnteredAt();
        if (entered < 0) {
            data.setChamberReturn("", 0, 0, 0, now); // arrived another way (e.g. /tp): start the clock now
            return;
        }
        if (now - entered >= DBZConfig.SERVER.chamberMaxStayTicks.get()) {
            player.displayClientMessage(Component.translatable("message.dbzenith.chamber_time_up"), false);
            exit(player);
        }
    }
}
