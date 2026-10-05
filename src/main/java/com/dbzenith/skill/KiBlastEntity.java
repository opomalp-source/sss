package com.dbzenith.skill;

import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * A ki projectile. Size, color and style are synced for rendering; damage and behavior are server-only.
 * The hurt amount it passes is raw DBZ damage (see {@link ModDamageTypes#kiBlast}).
 */
public class KiBlastEntity extends Projectile {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);

    private static final double HOMING_STRENGTH = 0.12;

    private double damage;
    private int pierceLeft;
    private int lifeTicks = 60;
    private int age;
    private float explosionPower;
    private int homingTargetId = -1;
    private Technique.Effect effect = Technique.Effect.NONE;
    private double effectPower;
    private final Set<Integer> hitIds = new HashSet<>();
    /** Ball-drop: ticks left hovering above the caster before it is hurled. */
    private int holdTicks;
    private float launchSpeed;

    public KiBlastEntity(EntityType<? extends KiBlastEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static KiBlastEntity create(Level level, LivingEntity owner, Technique technique, double damage) {
        KiBlastEntity blast = new KiBlastEntity(ModEntities.KI_BLAST.get(), level);
        blast.setOwner(owner);
        blast.damage = damage;
        blast.pierceLeft = technique.pierce();
        blast.lifeTicks = technique.lifeTicks();
        blast.explosionPower = technique.explosionPower();
        blast.effect = technique.effect();
        blast.effectPower = technique.effectPower();
        blast.holdTicks = technique.holdTicks();
        blast.launchSpeed = technique.speed();
        blast.entityData.set(SIZE, technique.size());
        blast.entityData.set(COLOR, technique.color());
        blast.entityData.set(STYLE, technique.style().ordinal());
        blast.refreshDimensions();
        return blast;
    }

    public void setHomingTarget(Entity target) {
        homingTargetId = target == null ? -1 : target.getId();
    }

    public double getDamage() {
        return damage;
    }

    public float getSize() {
        return entityData.get(SIZE);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    public Technique.Style getStyle() {
        int i = entityData.get(STYLE);
        return Technique.Style.values()[Math.max(0, Math.min(i, Technique.Style.values().length - 1))];
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SIZE, 0.5f);
        entityData.define(COLOR, 0xFFFFFF);
        entityData.define(STYLE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = getSize();
        return EntityDimensions.scalable(s, s);
    }

    @Override
    public void tick() {
        super.tick();
        Level level = level();
        if (!level.isClientSide && ++age > lifeTicks) {
            discard();
            return;
        }

        if (holdTicks > 0) {
            if (!level.isClientSide) hover();
            return;
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            onHit(hit);
            if (isRemoved()) return;
        }

        Vec3 motion = getDeltaMovement();
        if (!level.isClientSide && homingTargetId >= 0) {
            Entity target = level.getEntity(homingTargetId);
            if (target != null && target.isAlive()) {
                Vec3 toTarget = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(motion.length());
                motion = motion.lerp(toTarget, HOMING_STRENGTH);
                setDeltaMovement(motion);
            }
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5f);

        if (level.isClientSide) {
            int c = getColor();
            Vector3f rgb = new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
            level.addParticle(new DustParticleOptions(rgb, Math.max(0.4f, getSize())), getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
        }
    }

    /** Above the caster's head while it forms; then hurled at whatever the caster is looking at. */
    private void hover() {
        if (!(getOwner() instanceof LivingEntity caster) || !caster.isAlive() || caster.level() != level()) {
            discard();
            return;
        }
        Vec3 above = caster.getEyePosition().add(0, 2.0 + getSize() / 2.0, 0);
        setPos(above.x, above.y - getSize() / 2.0, above.z);
        setDeltaMovement(Vec3.ZERO);
        if (--holdTicks > 0) return;
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(64));
        BlockHitResult aim = level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        Vec3 dir = aim.getLocation().subtract(getBoundingBox().getCenter());
        if (dir.lengthSqr() < 1e-4) dir = caster.getLookAngle();
        setDeltaMovement(dir.normalize().scale(launchSpeed));
        hasImpulse = true;
    }

    public boolean isHovering() {
        return holdTicks > 0;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != getOwner() && !hitIds.contains(target.getId());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity target = result.getEntity();
        if (target instanceof net.minecraft.world.entity.player.Player p && deflectedBy(p)) return;
        hitIds.add(target.getId());
        if (effect == Technique.Effect.CANDY && TechniqueEffects.candy(target, effectPower)) {
            discard();
            return;
        }
        if (effect == Technique.Effect.KI_SEAL && target instanceof LivingEntity living) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.dbzenith.registry.ModEffects.KI_SEAL.get(), (int) effectPower, 0));
        }
        target.invulnerableTime = 0; // volleys must not be eaten by i-frames
        target.hurt(ModDamageTypes.kiBlast(level(), this, getOwner()), (float) damage);
        if (explosionPower <= 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.dbzenith.network.ImpactPacket.at(target.getBoundingBox().getCenter(), getDeltaMovement().normalize(),
                    com.dbzenith.network.ImpactPacket.KI_HIT, Math.max(0.5f, getSize()), getColor(), getOwner() == null ? -1 : getOwner().getId()).send(sl);
        }
        if (pierceLeft-- <= 0) impact();
    }

    /**
     * A guard raised just in time knocks the blast away where the defender is looking, and it becomes theirs
     * (a homing blast turns on its thrower). One deflect per raise of the guard.
     */
    private boolean deflectedBy(net.minecraft.world.entity.player.Player p) {
        com.dbzenith.data.PlayerData d = com.dbzenith.data.ModCapabilities.get(p).orElse(null);
        long now = level().getGameTime();
        if (d == null || !com.dbzenith.combat.GuardRules.inWindow(d, now, DBZConfig.SERVER.deflectWindowTicks.get())) return false;
        d.setGuardStartTick(Long.MIN_VALUE / 2);
        Entity thrower = getOwner();
        setOwner(p);
        double speed = Math.max(0.6, getDeltaMovement().length());
        setDeltaMovement(p.getLookAngle().scale(speed * 1.15));
        setHomingTarget(thrower instanceof LivingEntity l && l.isAlive() ? l : null);
        hitIds.clear();
        hitIds.add(p.getId());
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.dbzenith.network.ImpactPacket.at(position(), p.getLookAngle(), com.dbzenith.network.ImpactPacket.DEFLECT,
                    Math.max(0.6f, getSize()), getColor(), p.getId()).send(sl);
        }
        return true;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide) impact();
    }

    private void impact() {
        if (explosionPower > 0) {
            Level.ExplosionInteraction interaction = DBZConfig.SERVER.kiBlastsBreakBlocks.get()
                    ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
            level().explode(this, getX(), getY(), getZ(), explosionPower, interaction);
            if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.dbzenith.network.ImpactPacket.at(position().add(0, getBbHeight() / 2, 0), getDeltaMovement().normalize(),
                        com.dbzenith.network.ImpactPacket.EXPLOSION, explosionPower, getColor(), getOwner() == null ? -1 : getOwner().getId()).send(sl);
            }
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("damage", damage);
        tag.putInt("pierce", pierceLeft);
        tag.putInt("life", lifeTicks);
        tag.putInt("age", age);
        tag.putFloat("explosion", explosionPower);
        tag.putFloat("size", getSize());
        tag.putInt("color", getColor());
        tag.putInt("style", entityData.get(STYLE));
        tag.putString("effect", effect.name());
        tag.putDouble("effectPower", effectPower);
        tag.putInt("hold", holdTicks);
        tag.putFloat("launchSpeed", launchSpeed);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getDouble("damage");
        pierceLeft = tag.getInt("pierce");
        lifeTicks = tag.getInt("life");
        age = tag.getInt("age");
        explosionPower = tag.getFloat("explosion");
        entityData.set(SIZE, tag.getFloat("size"));
        entityData.set(COLOR, tag.getInt("color"));
        entityData.set(STYLE, tag.getInt("style"));
        try {
            effect = Technique.Effect.valueOf(tag.getString("effect"));
        } catch (IllegalArgumentException e) {
            effect = Technique.Effect.NONE;
        }
        effectPower = tag.getDouble("effectPower");
        holdTicks = tag.getInt("hold");
        launchSpeed = tag.getFloat("launchSpeed");
    }
}
