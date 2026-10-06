package com.dbzenith.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Mob;

/**
 * Keeps an other-world resident at home (CX-12): the clouds will not hold anyone, and a monkey chased off the edge of a
 * little planet would fall forever. Anyone who drops well below where they were placed is set back there.
 */
final class HomeKeeper {
    private BlockPos home;

    void tick(Mob mob) {
        if (mob.level().isClientSide) return;
        if (home == null) home = mob.blockPosition();
        if (mob.getY() < home.getY() - 12) {
            mob.teleportTo(home.getX() + 0.5, home.getY() + 0.1, home.getZ() + 0.5);
            mob.setDeltaMovement(0, 0, 0);
            mob.resetFallDistance();
        }
    }

    void save(CompoundTag tag) {
        if (home != null) tag.put("dbzHome", NbtUtils.writeBlockPos(home));
    }

    void load(CompoundTag tag) {
        if (tag.contains("dbzHome")) home = NbtUtils.readBlockPos(tag.getCompound("dbzHome"));
    }
}
