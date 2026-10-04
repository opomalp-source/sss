package com.dbzenith.npc;

import com.dbzenith.DBZenith;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Builds a dojo with the Master and an outpost with the Patrol Officer near world spawn, once per world. */
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
        buildWithNpc(overworld, NpcStructures.Kind.DOJO, spawn.offset(12, 0, 12));
        buildWithNpc(overworld, NpcStructures.Kind.OUTPOST, spawn.offset(-12, 0, 12));
        placed.done = true;
        placed.setDirty();
    }

    /** Builds the dojo (Master) or the outpost (Patrol Officer) at {@code center} and puts its NPC inside. */
    public static void buildWithNpc(ServerLevel level, NpcStructures.Kind kind, BlockPos center) {
        BlockPos stand = NpcStructures.build(level, center, kind);
        EntityType<? extends QuestGiverEntity> type = kind == NpcStructures.Kind.DOJO ? ModNpcs.MASTER.get() : ModNpcs.PATROL_OFFICER.get();
        QuestGiverEntity npc = type.spawn(level, stand, MobSpawnType.EVENT);
        if (npc != null) npc.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180f, 0f); // facing the open north side
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
