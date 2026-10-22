package com.dbzenith.living;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** In a conversation with a player: it stands still and faces them until the conversation ends. */
final class TalkGoal extends Goal {
    private final Resident r;

    TalkGoal(Resident r) {
        this.r = r;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return r.talkingTo != null && r.getTarget() == null;
    }

    @Override
    public void start() {
        r.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player p = r.level().getPlayerByUUID(r.talkingTo);
        if (p == null || p.distanceTo(r) > Conversations.REACH + 4) {
            r.talkingTo = null;
            return;
        }
        r.getNavigation().stop();
        r.getLookControl().setLookAt(p, 30f, 30f);
    }
}
