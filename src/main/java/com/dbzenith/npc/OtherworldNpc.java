package com.dbzenith.npc;

import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.QuestPackets;
import com.dbzenith.quest.Quest;
import com.dbzenith.world.Otherworld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The people of the other world (CX-12). None can be hurt; each answers in its own way. Enma, judge of the dead, hears
 * your case and sends you back among the living once your time is served. His ogre clerks keep the queues moving and
 * have a word for everyone. The Kai of the north trains those who reach the end of Snake Way; the Grand Kai keeps the
 * springs of his paradise.
 */
public class OtherworldNpc extends PathfinderMob {
    private final HomeKeeper home = new HomeKeeper();

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        home.save(tag);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home.load(tag);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        home.tick(this);
    }

    public enum Role { ENMA, OGRE_CLERK, NORTH_KAI, GRAND_KAI }

    private static final int CLERK_LINES = 8;

    private final Role role;

    public OtherworldNpc(EntityType<? extends OtherworldNpc> type, Level level, Role role) {
        super(type, level);
        this.role = role;
        setPersistenceRequired();
    }

    public Role role() {
        return role;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, role == Role.ENMA ? 24f : 10f, 1f));
        if (role == Role.OGRE_CLERK) goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.5));
        if (role != Role.ENMA) goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    /** The judge faces his court: the hall and its gate (north), whatever his head is doing. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (role == Role.ENMA) {
            setYBodyRot(180f);
            yBodyRotO = 180f;
            setYRot(180f);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
            switch (role) {
                case ENMA -> Otherworld.openJudgement(sp);
                case OGRE_CLERK -> sp.sendSystemMessage(Component.translatable("entity.dbzenith.ogre_clerk.says",
                        Component.translatable("npc.dbzenith.ogre_clerk." + random.nextInt(CLERK_LINES))));
                case NORTH_KAI -> ModNetwork.sendTo(sp, new QuestPackets.Open(Quest.Giver.NORTH_KAI));
                case GRAND_KAI -> ModNetwork.sendTo(sp, new QuestPackets.Open(Quest.Giver.GRAND_KAI));
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.isCreativePlayer();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return role == Role.OGRE_CLERK;
    }
}
