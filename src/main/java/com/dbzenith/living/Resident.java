package com.dbzenith.living;

import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.SkinVariant;
import com.dbzenith.npc.ai.AiProfile;
import com.dbzenith.npc.ai.CombatAiGoal;
import com.dbzenith.skill.Techniques;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * A townsperson (docs/LIVING_WORLD.md): the body of a named character ({@link NamedFighter}, the Nemesis system's
 * identity, with an occupation, a settlement and a home) that lives its day by its schedule ({@link RoutineGoal}),
 * talks to its neighbours ({@link Social}) and to players ({@link Conversations}). Fighters (students, the master,
 * guards, travelers) fight with the combat AI and defend the town; civilians run for home ({@link FleeGoal}).
 * Its look (one of the town's skins) is synced; its name and level come from its character.
 */
public class Resident extends KiFighter implements SkinVariant {
    public static final KiFighter.Profile PROFILE = new KiFighter.Profile("entity.dbzenith.resident", 20, List.of(Techniques.KI_BLAST), 80);
    /** The town's skins (see tools/ArtGen.java townsfolk), by occupation. */
    public static final List<String> CITIZEN_SKINS = List.of("townsfolk_citizen_a", "townsfolk_citizen_b", "townsfolk_citizen_c", "townsfolk_citizen_d");
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(Resident.class, EntityDataSerializers.STRING);

    // ---- what it is doing (server; the routine and social life set these)
    public Schedules.Activity activity = Schedules.Activity.WANDER;
    /** Whom it is chatting with and until when; when its drill at the dojo ends. */
    public UUID chatWith;
    public long chatUntil, drillUntil, lastChat;
    /** A player it is in a conversation with (it stops and faces them). */
    public UUID talkingTo;
    /** Running for its life until (civilians). */
    public long fleeUntil;
    /** A traveler on its way out of town (its routine takes it to a gate). */
    public boolean leaving;
    private AiProfile profileCache;

    public Resident(EntityType<? extends KiFighter> type, Level world) {
        super(type, world, PROFILE);
        xpReward = 0;
        if (getNavigation() instanceof GroundPathNavigation g) g.setCanPassDoors(true);       // the town's doorways are open
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKIN, CITIZEN_SKINS.get(0));
    }

    @Override
    public String skin() {
        return entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        entityData.set(SKIN, skin);
    }

    /** Whether it is running for its life now (civilians in danger). */
    public boolean fleeing() {
        return level().getGameTime() < fleeUntil;
    }

    /** Which skin a character wears: one of its occupation's (citizens and merchants vary by their look index). */
    public static String skinFor(NamedFighter c) {
        return switch (Occupation.of(c)) {
            case MERCHANT -> "townsfolk_merchant";
            case COOK -> "townsfolk_cook";
            case STUDENT -> Math.floorMod(c.look, 2) == 0 ? "townsfolk_student" : "townsfolk_student_b";
            case MASTER -> "townsfolk_master";
            case GUARD -> "townsfolk_guard";
            case TRAVELER -> "townsfolk_traveler";
            case CITIZEN -> CITIZEN_SKINS.get(Math.floorMod(c.look, CITIZEN_SKINS.size()));
        };
    }

    /** Its character (server), or null before it is bound. */
    public NamedFighter character() {
        return Nemesis.of(this);
    }

    public Occupation occupation() {
        return Occupation.of(character());
    }

    // ------------------------------------------------------------------ the combat AI's view of it

    @Override
    public AiProfile aiProfile() {
        if (level().isClientSide) return super.aiProfile();
        NamedFighter c = character();
        if (c == null) return Residents.profile(null);
        if (profileCache == null) profileCache = Residents.profile(c);
        return profileCache;
    }

    @Override
    public boolean aiMayFight() {
        if (level().isClientSide) return true;
        return occupation().fighter;
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal(this));
        goalSelector.addGoal(2, new CombatAiGoal(this));
        goalSelector.addGoal(3, new LegacyMelee(this, 1.1, false));
        goalSelector.addGoal(4, new TalkGoal(this));
        goalSelector.addGoal(5, new CompanionGoal(this));
        goalSelector.addGoal(6, new RoutineGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new CompanionGoal.Target(this));                       // a companion fights by its orders
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return aiMayFight() && Companions.mode(character()) != Companions.Mode.PASSIVE && super.canUse();
            }
        }.setAlertOthers(Resident.class));
        targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, 10, true, false,
                e -> Living.defends(this, e)));                                                     // the town's fighters see off threats
        targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> Living.wanted(this, e)));                                                      // and whoever attacked its people
    }

    /** Talking to a resident (right-click): it stops and a conversation opens ({@link Conversations}). */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || getTarget() != null || FleeGoal.fleeing(this)) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) Conversations.open(this, sp);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Whether it travels with this player. */
    public boolean isCompanionOf(Player p) {
        return p != null && Companions.of(character(), p.getUUID());
    }

    /** Never its own player or that player's other companions; a passive companion fights no one. */
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        if (!level().isClientSide && !target.getUUID().equals(sparWith)) {                   // a training spar is allowed
            NamedFighter c = character();
            if (c != null && c.companionOf != null) {
                if (target.getUUID().equals(c.companionOf)) return false;
                if (target instanceof Resident o && Companions.of(o.character(), c.companionOf)) return false;
                if (Companions.mode(c) == Companions.Mode.PASSIVE) return false;
            }
        }
        return super.canAttack(target);
    }

    // ------------------------------------------------------------------ a townsperson, not a monster

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return false;
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
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("dbzSkin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("dbzSkin")) setSkin(tag.getString("dbzSkin"));
    }
}
