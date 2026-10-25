package com.dbzenith.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.phys.AABB;

/**
 * Opening doors on the way, and closing them behind (docs/WORLD_STRUCTURES.md): the game's own goal, except that the
 * door is left open while anyone else is in or at the doorway, so it is never shut on the next in line.
 */
final class DoorGoal extends OpenDoorGoal {
    DoorGoal(Mob mob) {
        super(mob, true);
    }

    @Override
    public void stop() {
        AABB doorway = new AABB(doorPos.below()).expandTowards(0, 1, 0).inflate(1.0, 0, 1.0);
        if (mob.level().getEntitiesOfClass(LivingEntity.class, doorway, e -> e != mob && e.isAlive()).isEmpty()) super.stop();
    }
}
