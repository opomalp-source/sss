package com.dbzenith.living;

import com.dbzenith.nemesis.NamedFighter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A companion on the move ({@link Companions}): it keeps with its player (walking, running to catch up, and appearing
 * beside them when left far behind or across a wall), or stays where it was told to wait. Its fights are the combat
 * AI's; {@link Target} picks them by its mode.
 */
final class CompanionGoal extends Goal {
    static final double NEAR = 3.5, FAR = 12, LOST = 28;
    private final Resident r;
    private int repath;

    CompanionGoal(Resident r) {
        this.r = r;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (r.level().isClientSide || r.getTarget() != null || r.talkingTo != null || !LivingConfig.enabled()) return false;
        Companions.Mode m = Companions.mode(r.character());
        return m != null && m != Companions.Mode.HOME;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        r.getNavigation().stop();
    }

    @Override
    public void tick() {
        NamedFighter c = r.character();
        if (c == null) return;
        if (r.isSleeping()) r.stopSleeping();
        if (Companions.mode(c) == Companions.Mode.WAIT) {
            BlockPos w = BlockPos.of(c.waitPos);
            if (r.distanceToSqr(w.getX() + 0.5, w.getY(), w.getZ() + 0.5) > 4 && --repath <= 0) {
                repath = 20;
                r.getNavigation().moveTo(w.getX() + 0.5, w.getY(), w.getZ() + 0.5, 0.8);
            }
            return;
        }
        ServerPlayer p = Companions.leader(r, c);
        if (p == null || p.level() != r.level()) {                                       // gone elsewhere: it waits here for now
            r.getNavigation().stop();
            return;
        }
        r.getLookControl().setLookAt(p, 30f, 30f);
        double d = r.distanceTo(p);
        if (d > LOST || d > FAR && !r.hasLineOfSight(p) && r.getNavigation().isDone()) {   // left behind: it catches up (as fighters do)
            BlockPos at = Companions.besides(p);
            r.getNavigation().stop();
            if (at != null) r.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.getYRot(), 0);
            else if (p.onGround()) r.moveTo(p.getX(), p.getY(), p.getZ(), r.getYRot(), 0);       // no room beside you: where you stand
            else r.getNavigation().moveTo(p, 1.3);
            return;
        }
        if (d > NEAR) {
            if (--repath <= 0) {
                repath = 10;
                r.getNavigation().moveTo(p, d > FAR ? 1.3 : 1.0);
            }
        } else {
            r.getNavigation().stop();
        }
    }

    /** Whom a companion fights, by its mode: who attacks you (defend), and also whom you attack and threats near you (fight alongside). */
    static final class Target extends TargetGoal {
        private final Resident r;
        private LivingEntity pick;

        Target(Resident r) {
            super(r, false);
            this.r = r;
        }

        @Override
        public boolean canUse() {
            NamedFighter c = r.character();
            Companions.Mode m = Companions.mode(c);
            if (m != Companions.Mode.DEFEND && m != Companions.Mode.AGGRESSIVE) return false;
            ServerPlayer p = Companions.leader(r, c);
            if (p == null || p.level() != r.level()) return false;
            pick = valid(p.getLastHurtByMob(), p.getLastHurtByMobTimestamp(), p);
            if (pick == null) for (UUID id : Companions.partyMates(c, r.getServer())) {        // and your party's, close by
                ServerPlayer mate = r.getServer().getPlayerList().getPlayer(id);
                if (mate == null || mate.level() != r.level() || mate.distanceToSqr(r) > 16 * 16) continue;
                pick = valid(mate.getLastHurtByMob(), mate.getLastHurtByMobTimestamp(), mate);
                if (pick != null) break;
            }
            if (pick == null && m == Companions.Mode.AGGRESSIVE) {
                pick = valid(p.getLastHurtMob(), p.getLastHurtMobTimestamp(), p);
                if (pick == null) {
                    double best = 12 * 12;
                    for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12), Living::hostile)) {
                        double dd = e.distanceToSqr(p);
                        if (dd < best && r.canAttack(e)) {
                            best = dd;
                            pick = e;
                        }
                    }
                }
            }
            return pick != null;
        }

        LivingEntity valid(LivingEntity e, int when, ServerPlayer p) {
            if (e == null || !e.isAlive() || e == r || p.tickCount - when > 200 || e.distanceToSqr(p) > 24 * 24) return null;
            if (e instanceof ServerPlayer other && !com.dbzenith.combat.PvpRules.isPvp(other)) return null;   // no fights with players who don't want them
            return r.canAttack(e, TargetingConditions.DEFAULT) ? e : null;
        }

        @Override
        public void start() {
            r.setTarget(pick);
            super.start();
        }
    }
}
