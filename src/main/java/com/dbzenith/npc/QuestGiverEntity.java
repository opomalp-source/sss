package com.dbzenith.npc;

import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.QuestPackets;
import com.dbzenith.quest.Quest;
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

/** A friendly quest giver (Master or Patrol Officer). Can't be hurt; right-click opens its quest board. */
public class QuestGiverEntity extends PathfinderMob {
    private final Quest.Giver giver;

    public QuestGiverEntity(EntityType<? extends QuestGiverEntity> type, Level level, Quest.Giver giver) {
        super(type, level);
        this.giver = giver;
        setPersistenceRequired();
    }

    public Quest.Giver giver() {
        return giver;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10f, 1f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) ModNetwork.sendTo(sp, new QuestPackets.Open(giver));
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
