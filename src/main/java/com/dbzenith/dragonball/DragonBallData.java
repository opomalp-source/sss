package com.dbzenith.dragonball;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Where the seven Dragon Balls are. One instance per server, stored with the overworld. */
public class DragonBallData extends SavedData {
    private static final String NAME = "dbzenith_dragon_balls";

    public enum State { UNSET, PLACED, HELD, INERT }

    public static final class Entry {
        public State state = State.UNSET;
        public ResourceKey<Level> dimension = Level.OVERWORLD;
        public BlockPos pos = BlockPos.ZERO;
    }

    private final Entry[] balls = new Entry[DragonBalls.COUNT];
    private long inertUntil = -1;

    public DragonBallData() {
        for (int i = 0; i < balls.length; i++) balls[i] = new Entry();
    }

    public static DragonBallData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DragonBallData::load, DragonBallData::new, NAME);
    }

    /** Star is 1..7. */
    public Entry entry(int star) {
        return balls[star - 1];
    }

    public void set(int star, State state, ResourceKey<Level> dimension, BlockPos pos) {
        Entry e = entry(star);
        e.state = state;
        if (dimension != null) e.dimension = dimension;
        if (pos != null) e.pos = pos.immutable();
        setDirty();
    }

    public long getInertUntil() {
        return inertUntil;
    }

    public void setInertUntil(long gameTime) {
        inertUntil = gameTime;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : balls) {
            CompoundTag t = new CompoundTag();
            t.putString("state", e.state.name());
            t.putString("dim", e.dimension.location().toString());
            t.putLong("pos", e.pos.asLong());
            list.add(t);
        }
        tag.put("balls", list);
        tag.putLong("inertUntil", inertUntil);
        return tag;
    }

    public static DragonBallData load(CompoundTag tag) {
        DragonBallData d = new DragonBallData();
        ListTag list = tag.getList("balls", 10);
        for (int i = 0; i < Math.min(list.size(), d.balls.length); i++) {
            CompoundTag t = list.getCompound(i);
            Entry e = d.balls[i];
            try {
                e.state = State.valueOf(t.getString("state"));
            } catch (IllegalArgumentException ex) {
                e.state = State.UNSET;
            }
            e.dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("dim")));
            e.pos = BlockPos.of(t.getLong("pos"));
        }
        d.inertUntil = tag.getLong("inertUntil");
        return d;
    }
}
