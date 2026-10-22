package com.dbzenith.living;

import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A civilian in danger (hurt, or a threat close by while the town is under attack): it runs for home, and away from
 * the threat once there. Fighters don't flee here (the combat AI decides their retreats).
 */
final class FleeGoal extends Goal {
    private final Resident r;
    private LivingEntity threat;
    private long recheck;

    FleeGoal(Resident r) {
        this.r = r;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Whether it is running for its life now (it won't stop to talk). */
    static boolean fleeing(Resident r) {
        return r.level().getGameTime() < r.fleeUntil;
    }

    @Override
    public boolean canUse() {
        if (r.level().isClientSide || r.aiMayFight() || !LivingConfig.enabled()) return false;
        long now = r.level().getGameTime();
        if (now < recheck && !fleeing(r)) return false;
        recheck = now + 10;
        threat = threat(now);
        if (threat == null) return false;
        r.fleeUntil = now + 200;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return fleeing(r) && threat != null && threat.isAlive();
    }

    @Override
    public void start() {
        r.talkingTo = null;
        r.chatWith = null;
        if (r.isSleeping()) r.stopSleeping();
        run();
    }

    @Override
    public void tick() {
        if (r.getNavigation().isDone() || r.tickCount % 20 == 0) run();
    }

    @Override
    public void stop() {
        threat = null;
        r.getNavigation().stop();
    }

    void run() {
        var c = r.character();
        BlockPos home = c == null || c.home == 0 ? null : BlockPos.of(c.home);
        if (home != null && r.distanceToSqr(Vec3.atBottomCenterOf(home)) > 4 && threat.distanceToSqr(Vec3.atBottomCenterOf(home)) > 36) {
            r.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.15);
            return;
        }
        Vec3 away = DefaultRandomPos.getPosAway(r, 12, 5, threat.position());
        if (away != null) r.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
    }

    /** Who it is afraid of: whoever hurt it lately, or, with the town under attack, any hostile within 16 blocks. */
    LivingEntity threat(long now) {
        LivingEntity hurt = r.getLastHurtByMob();
        if (hurt != null && hurt.isAlive() && r.tickCount - r.getLastHurtByMobTimestamp() < 200) return hurt;
        var c = r.character();
        Settlement s = c == null ? null : LivingData.get(r.getServer()).settlement(c.settlement);
        if (s == null || !s.alarmed(now)) return null;
        LivingEntity best = null;
        double bd = 16 * 16;
        for (LivingEntity e : r.level().getEntitiesOfClass(LivingEntity.class, r.getBoundingBox().inflate(16), e -> e != r && e.isAlive())) {
            boolean hostile = e instanceof KiFighter k ? Faction.enemies(r, k) || k.getTarget() instanceof Resident
                    : e instanceof Enemy || e instanceof Player p && s.wanted.contains(p.getUUID());
            double d = r.distanceToSqr(e);
            if (hostile && d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
