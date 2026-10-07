package com.dbzenith.npc;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * A hostile martial artist. On spawn its level is set from the strongest nearby player's power level
 * (health and damage scale with it). Ki users fire their techniques from range through {@link KiAttackGoal}.
 */
public class KiFighter extends Monster {
    /** What kind of fighter: name key, base ki damage, techniques it knows, ranged attack interval. */
    public record Profile(String nameKey, double baseKiDamage, List<Technique> techniques, int kiInterval) {}

    private final Profile profile;
    private int level = 1;
    private int kiCooldown = 40;
    /** Type defaults, captured before any level scaling. */
    private final double baseHealth;
    private final double baseDamage;

    public KiFighter(EntityType<? extends KiFighter> type, Level world, Profile profile) {
        super(type, world);
        this.profile = profile;
        this.baseHealth = getAttributeBaseValue(Attributes.MAX_HEALTH);
        this.baseDamage = getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        xpReward = 10;
        // Not in registerGoals(): Mob calls that from its constructor, before this.profile is set.
        if (!profile.techniques().isEmpty()) goalSelector.addGoal(2, new KiAttackGoal(this));
    }

    public static AttributeSupplier.Builder attributes(double health, double damage, double speed) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.ARMOR, 2);
    }

    public Profile profile() {
        return profile;
    }

    public int fighterLevel() {
        return level;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData data, CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(world, difficulty, reason, data, tag);
        Player nearest = world.getNearestPlayer(this, 64);
        long power = nearest == null ? 0 : ModCapabilities.get(nearest).map(this::scaleTo).orElse(0L);
        setFighterLevel(levelFor(power));
        return result;
    }

    /** The power this fighter matches itself to (bosses override: they expect your best form). */
    protected long scaleTo(com.dbzenith.data.PlayerData d) {
        return StatCalculator.fullPower(d);
    }

    /** Level from a full power level: power / enemies.powerPerLevel (linear, so foes keep pace), at least 1, capped. */
    public static int levelFor(long power) {
        DBZConfig.Server c = DBZConfig.SERVER;
        int lvl = (int) Math.round(Math.max(0, power) / c.enemyPowerPerLevel.get());
        return Mth.clamp(lvl, 1, c.enemyMaxLevel.get());
    }

    public void setFighterLevel(int lvl) {
        level = Math.max(1, lvl);
        var health = getAttribute(Attributes.MAX_HEALTH);
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (health != null) health.setBaseValue(defaultHealth()); // levels add toughness, not health (vanilla caps health at 1024)
        if (damage != null) damage.setBaseValue(defaultDamage() * (1 + DBZConfig.SERVER.enemyDamagePerLevel.get() * (level - 1)));
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("entity.dbzenith.leveled", Component.translatable(profile.nameKey()), level));
        setCustomNameVisible(false);
    }

    /** Incoming damage is divided by this: 1 + enemies.healthPerLevel x (level - 1). */
    public double toughness() {
        return 1.0 + DBZConfig.SERVER.enemyHealthPerLevel.get() * (level - 1);
    }

    /** Health as it really lasts: max health x toughness for fighters, plain max health for anything else. */
    public static double effectiveMaxHealth(LivingEntity e) {
        return e instanceof KiFighter f ? e.getMaxHealth() * f.toughness() : e.getMaxHealth();
    }

    private double defaultHealth() {
        return baseHealth;
    }

    private double defaultDamage() {
        return baseDamage;
    }

    /** Raw DBZ ki damage of this fighter's techniques. */
    public double kiDamage() {
        return profile.baseKiDamage() * (1 + DBZConfig.SERVER.enemyDamagePerLevel.get() * (level - 1)) * damageMultiplier();
    }

    /** Melee goes through the combat engine (CX-19): a chain of blows with a finisher now and then, like a player's. */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        if (target instanceof LivingEntity t && com.dbzenith.combat.engine.Moves.loaded()) {
            com.dbzenith.combat.engine.CombatEngine.npcAttack(this, t);
            return true;
        }
        return super.doHurtTarget(target);
    }

    /** How much harder than its base this fighter hits in melee (bosses when enraged). */
    public double meleeMultiplier() {
        return damageMultiplier();
    }

    /** Bosses override (enraged phase). */
    protected double damageMultiplier() {
        return 1.0;
    }

    protected Technique chooseTechnique() {
        List<Technique> t = profile.techniques();
        return t.get(random.nextInt(t.size()));
    }

    /** Aim at the target and fire. */
    public void fireAt(LivingEntity target) {
        Vec3 to = target.getEyePosition().subtract(getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYHeadRot(yaw);
        setXRot(pitch);
        TechniqueHandler.spawn((ServerLevel) level(), this, chooseTechnique(), kiDamage());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (kiCooldown > 0) kiCooldown--;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("fighterLevel", level);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        level = Math.max(1, tag.getInt("fighterLevel"));
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() != defaultHealth()) { // saved before levels became toughness
            health.setBaseValue(defaultHealth());
            setHealth(Math.min(getHealth(), getMaxHealth()));
        }
    }

    /** Ranged ki attacks when the target is visible and not too close. */
    public static class KiAttackGoal extends Goal {
        private final KiFighter mob;

        public KiAttackGoal(KiFighter mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive() || mob.kiCooldown > 0) return false;
            if (com.dbzenith.registry.ModEffects.isStunned(mob) || com.dbzenith.registry.ModEffects.isKiSealed(mob)) return false;
            double d = mob.distanceToSqr(t);
            return d > 4 * 4 && d < 28 * 28 && mob.hasLineOfSight(t);
        }

        @Override
        public void start() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30f, 30f);
            mob.fireAt(t);
            mob.kiCooldown = mob.profile.kiInterval() + mob.random.nextInt(20);
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
