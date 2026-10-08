package com.dbzenith.style;

import com.dbzenith.data.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A master who teaches fighting styles (CX-20): stands where placed, watches you, can't be hurt. A right-click talks
 * (their screen: affinity, training, the styles they teach) or, holding something they like, gives it to them.
 */
public class StyleMaster extends PathfinderMob {
    private final MasterRoster roster;

    public StyleMaster(EntityType<? extends StyleMaster> type, Level level, MasterRoster roster) {
        super(type, level);
        this.roster = roster;
        setPersistenceRequired();
    }

    public MasterRoster roster() {
        return roster;
    }

    public String masterId() {
        return roster.id();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10f, 1f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            ModCapabilities.get(sp).ifPresent(d -> StyleLogic.talk(sp, d, this, sp.getItemInHand(hand)));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
