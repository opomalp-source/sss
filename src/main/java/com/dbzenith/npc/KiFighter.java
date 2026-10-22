package com.dbzenith.npc;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.npc.ai.CombatAiGoal;
import com.dbzenith.npc.ai.NpcBrain;
import com.dbzenith.npc.ai.NpcForms;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * A hostile martial artist. On spawn its level is set from the strongest nearby player's power level
 * (health and damage scale with it). In a fight it is run by the combat AI ({@link NpcBrain}, docs/COMBAT_AI.md); with
 * the AI switched off it falls back to walking up and swinging, and firing its techniques through {@link KiAttackGoal}.
 */
public class KiFighter extends Monster implements Animated {
    /** What kind of fighter: name key, base ki damage, techniques it knows, ranged attack interval. */
    public record Profile(String nameKey, double baseKiDamage, List<Technique> techniques, int kiInterval) {}

    /** What clients see of the AI (bits of {@link #AI_FLAGS}): flying, charging (ki or a technique), guarding, powering up. */
    public static final int AI_FLYING = 1, AI_CHARGING = 2, AI_GUARDING = 4, AI_TRANSFORMING = 8;
    private static final EntityDataAccessor<Integer> AI_FLAGS = SynchedEntityData.defineId(KiFighter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> AI_FORM = SynchedEntityData.defineId(KiFighter.class, EntityDataSerializers.STRING);

    private final Profile profile;
    private int level = 1;
    private int kiCooldown = 40;
    /** Type defaults, captured before any level scaling. */
    private final double baseHealth;
    private final double baseDamage;
    private NpcBrain brain;
    // the Nemesis system (docs/NEMESIS.md): the character this body belongs to, and what it is up to
    private java.util.UUID nemesisId;
    private List<Technique> learned = List.of();
    /** When it leaves the world after its fight (0: it stays), whether it is training, whom it spars with or watches over. */
    public long leaveAt;
    public boolean training;
    public java.util.UUID sparWith, protecting, spared;
    /** Who knocked it down (a player), for when it gets away. */
    public java.util.UUID downedBy;
    /** Whether it has greeted its foe this fight; when its last spar ended. */
    public boolean greeted;
    public long lastSparEnd;
    public long lastTalk;

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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AI_FLAGS, 0);
        entityData.define(AI_FORM, Forms.BASE.id());
    }

    public Profile profile() {
        return profile;
    }

    public int fighterLevel() {
        return level;
    }

    // ------------------------------------------------------------------ the combat AI

    /** Its mind (server only; made on first use). */
    public NpcBrain brain() {
        if (brain == null) brain = new NpcBrain(this);
        return brain;
    }

    /** Its mind if it has one yet (event hooks must not make one). */
    public NpcBrain brainOrNull() {
        return brain;
    }

    public boolean hasAiFlag(int flag) {
        return (entityData.get(AI_FLAGS) & flag) != 0;
    }

    public void setAiFlag(int flag, boolean on) {
        int f = entityData.get(AI_FLAGS);
        int n = on ? f | flag : f & ~flag;
        if (n != f) entityData.set(AI_FLAGS, n);
    }

    public int aiFlags() {
        return entityData.get(AI_FLAGS);
    }

    /** The form it is in (synced: clients draw its aura). */
    public String aiFormId() {
        return entityData.get(AI_FORM);
    }

    /** The AI changed its form: synced, and its blows change with it. */
    public void aiFormChanged(Form f) {
        entityData.set(AI_FORM, f.id());
    }

    /** The aura its power shows in: its form's, or its own while charging in base. */
    public int aiAuraColor() {
        Form f = Forms.byId(aiFormId());
        return f.isBase() ? com.dbzenith.npc.ai.AiProfile.of(this).aura() : f.auraColor();
    }

    /** What its form adds to its blows and ki (1 in base). */
    public double formFactor() {
        return NpcForms.factor(Forms.byId(aiFormId()));
    }

    /** Everything that multiplies its blows now: fury (bosses) and form. */
    public double powerFactor() {
        return damageMultiplier() * formFactor();
    }

    /** The named character this body belongs to, or null for an ordinary fighter. */
    public java.util.UUID nemesisId() {
        return nemesisId;
    }

    public void setNemesisId(java.util.UUID id) {
        nemesisId = id;
    }

    /** The techniques it fights with: its kind's, and any its character has learned. */
    public List<Technique> techniques() {
        if (learned.isEmpty()) return profile.techniques();
        List<Technique> all = new java.util.ArrayList<>(profile.techniques());
        for (Technique t : learned) if (!all.contains(t)) all.add(t);
        return all;
    }

    public void setLearned(List<Technique> list) {
        learned = list == null ? List.of() : List.copyOf(list);
    }

    /** Shows it as a named character: its name over its head, and its level ({@code fresh}: a new body, at full health). */
    public void showAs(String name, int lvl, boolean fresh) {
        if (fresh || lvl != level) {
            float share = getHealth() / Math.max(1f, getMaxHealth());
            setFighterLevel(lvl);
            if (!fresh) setHealth(Math.max(1f, getMaxHealth() * share));
        }
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    /** Where it must stay to fight (the tournament ring), or null. */
    public AABB aiBounds() {
        return null;
    }

    @Override
    public boolean isFlyingNow() {
        return hasAiFlag(AI_FLYING);
    }

    @Override
    public boolean isChargingKi() {
        return hasAiFlag(AI_CHARGING) || hasAiFlag(AI_TRANSFORMING);
    }

    /** Fighters of one faction are allies (scoreboard teams first). */
    @Override
    public boolean isAlliedTo(Entity other) {
        if (super.isAlliedTo(other)) return true;
        return getTeam() == null && other instanceof LivingEntity l && l.getTeam() == null && com.dbzenith.npc.ai.Faction.allied(this, l);
    }

    /** Tests confine a fighter's fights to their own arena. */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (brain != null && brain.arena != null && !brain.arena.contains(target.position())) return false;
        if (nemesisId != null && target instanceof Player p && !p.getUUID().equals(sparWith) && (p.getUUID().equals(spared) || com.dbzenith.nemesis.Nemesis.friendlyTo(this, p))) return false;   // friends, the spared
        return super.canAttack(target);
    }

    /** A move it starts must be paid for in stamina (and ki, if the move costs any). Called by the combat engine. */
    public boolean aiPayMove(double stamina, double ki) {
        if (level().isClientSide || !CombatAiGoal.enabled()) return true;
        NpcBrain b = brain();
        long now = level().getGameTime();
        if (b.resources().ki() < ki) return false;
        if (!b.resources().spendStamina(stamina, now) && stamina > 0) return false;
        b.resources().spendKi(ki, now);
        return true;
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CombatAiGoal(this));
        goalSelector.addGoal(3, new LegacyMelee(this, 1.1, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Talking to a named character (right-click, when it isn't fighting): it answers from what it remembers. */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (nemesisId != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND && getTarget() == null) {
            if (!level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) com.dbzenith.nemesis.Nemesis.talk(this, sp);
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /** Walk up and swing: the old melee, only while the combat AI is switched off. */
    public static class LegacyMelee extends MeleeAttackGoal {
        public LegacyMelee(KiFighter mob, double speed, boolean followEvenIfNotSeen) {
            super(mob, speed, followEvenIfNotSeen);
        }

        @Override
        public boolean canUse() {
            return !CombatAiGoal.enabled() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !CombatAiGoal.enabled() && super.canContinueToUse();
        }
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
        if (brain != null) brain.resources().setLevel(level);
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

    /** Raw DBZ ki damage of this fighter's techniques (its form included). */
    public double kiDamage() {
        return profile.baseKiDamage() * (1 + DBZConfig.SERVER.enemyDamagePerLevel.get() * (level - 1)) * damageMultiplier() * formFactor();
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

    /** How much harder than its base this fighter hits in melee (bosses when enraged, any form it is in). */
    public double meleeMultiplier() {
        return damageMultiplier() * formFactor();
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
        if (!level().isClientSide && CombatAiGoal.enabled()) brain().passiveTick(level().getGameTime());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("fighterLevel", level);
        if (nemesisId != null) tag.putUUID("dbzNemesis", nemesisId);
        if (brain != null) tag.put("dbzAi", brain.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        level = Math.max(1, tag.getInt("fighterLevel"));
        nemesisId = tag.hasUUID("dbzNemesis") ? tag.getUUID("dbzNemesis") : null;
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() != defaultHealth()) { // saved before levels became toughness
            health.setBaseValue(defaultHealth());
            setHealth(Math.min(getHealth(), getMaxHealth()));
        }
        if (tag.contains("dbzAi") && !level().isClientSide) {
            brain().load(tag.getCompound("dbzAi"));
            entityData.set(AI_FORM, brain.forms().current().id());
        }
    }

    /** Ranged ki attacks when the target is visible and not too close (only while the combat AI is switched off). */
    public static class KiAttackGoal extends Goal {
        private final KiFighter mob;

        public KiAttackGoal(KiFighter mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (CombatAiGoal.enabled()) return false;
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive() || mob.kiCooldown > 0 || !com.dbzenith.combat.Limbs.canUseKi(mob)) return false;   // no hands, no ki (CX-53)
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
            mob.kiCooldown = (int) ((mob.profile.kiInterval() + mob.random.nextInt(20)) * com.dbzenith.combat.Wounds.cooldown(mob));   // slower badly hurt (CX-52)
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
