package com.dbzenith.tournament;

import com.dbzenith.npc.KiFighter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A fighter of the World Martial Arts Tournament (CX-17c). It only fights the opponent the tournament gives it, never
 * wanders off, never despawns (not even on peaceful) and is never saved: it lives for one match.
 */
public class TournamentFighter extends KiFighter {
    private final Roster who;
    private LivingEntity opponent;

    public TournamentFighter(EntityType<? extends KiFighter> type, Level level, Roster who) {
        super(type, level, new Profile(who.nameKey(), 35, who.techniques(), 70));
        this.who = who;
        setPersistenceRequired();
    }

    public Roster who() {
        return who;
    }

    /** The one it fights; null keeps it standing (before the bell). */
    public void setOpponent(LivingEntity opponent) {
        this.opponent = opponent;
        setTarget(opponent);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new com.dbzenith.npc.ai.CombatAiGoal(this));
        goalSelector.addGoal(3, new LegacyMelee(this, 1.2, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (opponent != null && opponent.isAlive() && !opponent.isRemoved()) {
            if (getTarget() != opponent) setTarget(opponent);
        } else if (getTarget() != null) {
            setTarget(null);
        }
        if (tickCount > 40 && !Tournament.isFighter(this)) discard();      // a match it no longer belongs to
    }

    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target == null || target == opponent ? target : opponent);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return (opponent == null && !source.isCreativePlayer()) || super.isInvulnerableTo(source);
    }

    /** The ring: the combat AI keeps its fight on the tiles (no flying, no retreating out). */
    @Override
    public net.minecraft.world.phys.AABB aiBounds() {
        net.minecraft.core.BlockPos ring = Tournament.current() == null ? null : Tournament.current().ring;
        if (ring == null) return null;
        int r = TournamentGrounds.RING;
        return new net.minecraft.world.phys.AABB(ring.getX() - r + 1, ring.getY() - 2, ring.getZ() - r + 1, ring.getX() + r, ring.getY() + 6, ring.getZ() + r);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
