package com.dbzenith.npc.ai;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.npc.KiFighter;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * The one goal that runs an NPC fighter's {@link NpcBrain} in a fight (it replaces the old melee and ki goals). Runs
 * every tick while there is a foe; away from every player (beyond {@code npc_ai.activeRange}) and not in a fight with
 * one, it thinks at the light level only.
 */
public final class CombatAiGoal extends Goal {
    private final KiFighter mob;

    public CombatAiGoal(KiFighter mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean enabled() {
        try {
            return DBZConfig.SERVER.aiEnabled.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    public boolean canUse() {
        return enabled() && mob.brain().wantsToFight(mob.level().getGameTime());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        AiEvents.activate(mob);
    }

    @Override
    public void stop() {
        AiEvents.deactivate(mob);
        mob.getNavigation().stop();
        mob.brain().lowerGuard();
    }

    @Override
    public void tick() {
        NpcBrain b = mob.brain();
        boolean near = mob.getTarget() instanceof Player || nearPlayer();
        AiProfile.Tier want = NpcBrain.effectiveTier(near ? b.profile.tier() : AiProfile.Tier.LIGHT);
        if (b.tier != want) b.tier = want;
        b.tick(mob.level().getGameTime());
    }

    private boolean nearPlayer() {
        int range;
        try {
            range = DBZConfig.SERVER.aiActiveRange.get();
        } catch (IllegalStateException e) {
            range = 48;
        }
        if (mob.tickCount % 20 != 0 && lastNear >= 0) return lastNear == 1;
        lastNear = mob.level().getNearestPlayer(mob, range) != null ? 1 : 0;
        return lastNear == 1;
    }

    private int lastNear = -1;
}
