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
 * The people of the other world (CX-12). None can be hurt; each answers in its own way. King Yemma, judge of the dead, hears
 * your case and sends you back among the living once your time is served. His ogre clerks keep the queues moving and
 * have a word for everyone. King Kai trains those who reach the end of Snake Way; the Grand Kai keeps the
 * springs of his planet. On Beerus's Planet (CX-17b) the God of Destruction teaches Hakai, and Whis, his attendant,
 * teaches Ultra Instinct: every swing at him slips past, and touching him once is his first lesson.
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

    public enum Role { ENMA, OGRE_CLERK, NORTH_KAI, GRAND_KAI, BEERUS, WHIS }

    /** The quest event a touch on Whis counts towards (his first lesson). */
    public static final String WHIS_TOUCHED = "dbzenith:touch_whis";
    private static final int WHIS_LINES = 5;

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
                case BEERUS -> ModNetwork.sendTo(sp, new QuestPackets.Open(Quest.Giver.BEERUS));
                case WHIS -> ModNetwork.sendTo(sp, new QuestPackets.Open(Quest.Giver.WHIS));
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Whis is never where the blow lands: now and then a fighter brushes him (more often in Ultra Instinct). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (role == Role.WHIS && !level().isClientSide && source.getEntity() instanceof ServerPlayer player) {
            com.dbzenith.data.PlayerData d = com.dbzenith.data.ModCapabilities.get(player).orElse(null);
            double chance = d != null && com.dbzenith.transform.UltraInstinct.isIn(d) ? 0.35 : 0.08;
            net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) level();
            if (random.nextDouble() < chance) {
                com.dbzenith.quest.QuestManager.event(player, WHIS_TOUCHED);
                player.displayClientMessage(Component.translatable("message.dbzenith.whis_touched"), true);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 16, 0.3, 0.5, 0.3, 0.05);
            } else {
                double a = random.nextDouble() * Math.PI * 2;                                   // a step aside, unhurried
                double x = getX() + Math.cos(a) * 2.5, z = getZ() + Math.sin(a) * 2.5;
                if (level().noCollision(this, getBoundingBox().move(x - getX(), 0, z - getZ()))) teleportTo(x, getY(), z);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 4, 0.2, 0.5, 0.2, 0.01);
                level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.ILLUSIONER_MIRROR_MOVE,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.6f, 1.5f);
                if (random.nextInt(3) == 0) {
                    player.displayClientMessage(Component.translatable("entity.dbzenith.whis.says",
                            Component.translatable("npc.dbzenith.whis." + random.nextInt(WHIS_LINES))), true);
                }
            }
            lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
            return false;
        }
        return super.hurt(source, amount);
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
