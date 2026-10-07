package com.dbzenith.npc;

import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Evasion;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The training dummy (CX-19 phase 9): a stuffed sparring partner that never dies and heals itself after three seconds
 * left alone. Its name shows the running string of blows (hits, damage, damage per second). Right-click with an empty
 * hand to change what it does (Shift: back a step):
 * <ul>
 *   <li><b>stand</b>: takes everything;</li>
 *   <li><b>guard</b>: blocks blows from the front (chip damage; guard breaks still need a guard meter, so it never breaks);</li>
 *   <li><b>perfect guard</b>: parries every blow from the front (practise for what follows a parry);</li>
 *   <li><b>dodge</b>: vanishes from about half your blows (practise baiting it);</li>
 *   <li><b>counter</b>: vanishes and counters;</li>
 *   <li><b>attack</b>: throws strings at you when you are close (practise guarding, perfect guards and vanishing);</li>
 *   <li><b>random</b>: a new one of those every few seconds.</li>
 * </ul>
 */
public class TrainingDummy extends PathfinderMob {
    public enum Mode { STAND, GUARD, PERFECT_GUARD, DODGE, COUNTER, ATTACK, RANDOM }

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.INT);
    private static final int RESET_TICKS = 60;
    private static final Moves.Input JAB = new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true);

    private double stringDamage, biggest;
    private int stringHits;
    private long stringStart = -1, lastHit = Long.MIN_VALUE / 2;
    private Mode rolled = Mode.GUARD;
    private long nextRoll, nextAttack;
    private int counterTarget = -1;
    private net.minecraft.world.phys.Vec3 home;                                 // where it was set up: it goes back there when left alone

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        refreshName();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MODE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 12f));
    }

    public Mode mode() {
        int i = entityData.get(MODE);
        return Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, i))];
    }

    public void setMode(Mode m) {
        entityData.set(MODE, m.ordinal());
        refreshName();
    }

    /** What it does this moment (random picks one). */
    public Mode acting() {
        return mode() == Mode.RANDOM ? rolled : mode();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            Mode[] all = Mode.values();
            Mode next = all[Math.floorMod(mode().ordinal() + (player.isShiftKeyDown() ? -1 : 1), all.length)];
            setMode(next);
            player.displayClientMessage(Component.translatable("message.dbzenith.dummy_mode", Component.translatable("dummy.dbzenith.mode." + next.name().toLowerCase()))
                    .withStyle(ChatFormatting.GOLD), true);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ------------------------------------------------------------------ taking blows

    /** A blow landed for {@code dealt} (DBZ damage): the string's numbers. Called by CombatEvents. */
    public void record(double dealt) {
        long now = level().getGameTime();
        if (now - lastHit > RESET_TICKS || stringStart < 0) {
            stringStart = now;
            stringDamage = 0;
            stringHits = 0;
            biggest = 0;
        }
        lastHit = now;
        stringDamage += dealt;
        stringHits++;
        biggest = Math.max(biggest, dealt);
        refreshName();
    }

    /**
     * Whether its guard takes a blow from {@code attacker} (guard and perfect guard modes, from the front): 0 no, 1 a
     * block, 2 a parry. Called by CombatEvents before the damage.
     */
    public int guards(Entity attacker, boolean melee) {
        Mode m = acting();
        if (attacker == null || !(m == Mode.GUARD || m == Mode.PERFECT_GUARD)) return 0;
        if (!Evasion.guardCovers(this, attacker)) return 0;
        return m == Mode.PERFECT_GUARD && melee ? 2 : 1;
    }

    /** It never dies: what would kill it fills it back up. */
    @Override
    public void die(DamageSource source) {
        setHealth(getMaxHealth());
        stringStart = -1;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 1f, 0.8f);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ------------------------------------------------------------------ what it does

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        long now = level().getGameTime();
        if (home == null) home = position();
        if (now - lastHit > RESET_TICKS && getHealth() < getMaxHealth()) {      // left alone: back to full, the string done
            setHealth(getMaxHealth());
            refreshName();
        }
        if (now - lastHit > RESET_TICKS && home != null && position().distanceToSqr(home) > 2.25 && onGround()) {   // knocked away: back home
            ((net.minecraft.server.level.ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            teleportTo(home.x, home.y, home.z);
        }
        if (mode() == Mode.RANDOM && now >= nextRoll) {
            Mode[] pick = {Mode.GUARD, Mode.PERFECT_GUARD, Mode.DODGE, Mode.COUNTER, Mode.ATTACK, Mode.STAND};
            rolled = pick[getRandom().nextInt(pick.length)];
            nextRoll = now + 60 + getRandom().nextInt(60);
            refreshName();
        }
        Player foe = level().getNearestPlayer(this, 6);
        if (foe == null || foe.isSpectator()) return;
        Mode m = acting();
        Fighter theirs = CombatEngine.peek(foe);
        boolean winding = theirs != null && theirs.move() != null && theirs.moveTick() < theirs.move().startup;
        if ((m == Mode.DODGE || m == Mode.COUNTER) && winding && distanceTo(foe) < 4.5 && getRandom().nextFloat() < (m == Mode.COUNTER ? 0.8f : 0.5f)) {
            Evasion.openVanish(this, 4);                                       // reads the blow coming
            counterTarget = m == Mode.COUNTER ? foe.getId() : -1;
        }
        if ((counterTarget >= 0 || m == Mode.PERFECT_GUARD) && Evasion.counterReady(this, now)) {   // vanished behind them, or parried: the counter
            lookAt(EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
            CombatEngine.press(this, JAB);
            counterTarget = -1;
        }
        if (m == Mode.ATTACK && now >= nextAttack && distanceTo(foe) < 3.2) {    // a string at them
            lookAt(EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
            boolean heavy = getRandom().nextInt(4) == 0;
            CombatEngine.press(this, new Moves.Input(heavy ? Move.Button.HEAVY : Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, onGround()));
            nextAttack = now + (heavy ? 30 : 10 + getRandom().nextInt(10));
        }
    }

    /** Its name: the mode, and the string of blows it is taking. */
    void refreshName() {
        Component mode = Component.translatable("dummy.dbzenith.mode." + mode().name().toLowerCase());
        if (mode() == Mode.RANDOM) mode = mode.copy().append(" (").append(Component.translatable("dummy.dbzenith.mode." + rolled.name().toLowerCase())).append(")");
        Component name = Component.translatable("entity.dbzenith.training_dummy").append(" · ").append(mode);
        if (stringStart >= 0 && level() != null && level().getGameTime() - lastHit <= RESET_TICKS) {
            double secs = Math.max(0.5, (lastHit - stringStart) / 20.0);
            name = Component.translatable("dummy.dbzenith.string_line", Component.translatable("entity.dbzenith.training_dummy"), mode,
                    stringHits, compact(stringDamage), compact(stringDamage / secs));
        }
        setCustomName(name);
        setCustomNameVisible(true);
    }

    static String compact(double v) {
        if (v < 1000) return String.valueOf(Math.round(v));
        String[] units = {"K", "M", "B", "T"};
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format("%.1f", v) : String.valueOf((int) v)) + units[u];
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DummyMode", mode().ordinal());
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(MODE, tag.getInt("DummyMode"));
        if (tag.contains("HomeX")) home = new net.minecraft.world.phys.Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        refreshName();
    }

    /** Tests: the string so far (hits, damage). */
    public double[] string() {
        return new double[]{stringHits, stringDamage};
    }
}
