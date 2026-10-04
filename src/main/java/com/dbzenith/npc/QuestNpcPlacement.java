package com.dbzenith.npc;

import com.dbzenith.DBZenith;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Places the Master and the Patrol Officer near world spawn, once per world. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestNpcPlacement {
    private QuestNpcPlacement() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) placeIfNeeded(level.getServer().overworld());
    }

    public static void placeIfNeeded(ServerLevel overworld) {
        Placed placed = overworld.getDataStorage().computeIfAbsent(Placed::load, Placed::new, "dbzenith_quest_npcs");
        if (placed.done) return;
        BlockPos spawn = overworld.getSharedSpawnPos();
        spawnAt(overworld, ModNpcs.MASTER.get(), spawn.offset(4, 0, 4));
        spawnAt(overworld, ModNpcs.PATROL_OFFICER.get(), spawn.offset(-4, 0, 4));
        placed.done = true;
        placed.setDirty();
    }

    private static void spawnAt(ServerLevel level, EntityType<? extends QuestGiverEntity> type, BlockPos near) {
        level.getChunk(near.getX() >> 4, near.getZ() >> 4);
        BlockPos at = new BlockPos(near.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()), near.getZ());
        type.spawn(level, at, MobSpawnType.EVENT);
    }

    static final class Placed extends SavedData {
        boolean done;

        static Placed load(CompoundTag tag) {
            Placed p = new Placed();
            p.done = tag.getBoolean("done");
            return p;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("done", done);
            return tag;
        }
    }
}
