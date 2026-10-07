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
import java.util.List;
import java.util.Set;

/**
 * A ki projectile. Size, color and style are synced for rendering; damage and behavior are server-only.
 * The hurt amount it passes is raw DBZ damage (see {@link ModDamageTypes#kiBlast}).
 */
public class KiBlastEntity extends Projectile {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
    /** Shown as the Spirit Bomb (a solid, bright sphere). */
    private static final EntityDataAccessor<Boolean> SPIRIT = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.BOOLEAN);

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
    // Ki Creator v2 traits
    private Technique.KiType kiType = Technique.KiType.PURE;
    private int flags;
    private int bouncesLeft;
    private boolean split;
    // the Spirit Bomb (CX-17a): energy gathered while it hovers, and who lent it
    private boolean spiritBomb;
    private double energy, baseDamage;
    private float baseSize, baseExplosion;
    private boolean called;
    private final Set<java.util.UUID> contributors = new HashSet<>();
    // the combat engine's ki blasts (CX-19): not saved, a blast lives a few seconds
    private int combatStun = -1;
    private double combatKnock;
    private boolean combatHeavy;

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
        blast.kiType = technique.kiType();
        blast.flags = technique.flags();
        blast.bouncesLeft = technique.has(Technique.BOUNCE) ? 2 : 0;
        blast.entityData.set(SIZE, technique.size());
        blast.entityData.set(COLOR, technique.color());
        blast.entityData.set(STYLE, technique.style().ordinal());
        blast.spiritBomb = technique.effect() == Technique.Effect.SPIRIT_BOMB;
        blast.entityData.set(SPIRIT, blast.spiritBomb);
        blast.baseDamage = damage;
        blast.baseSize = technique.size();
        blast.baseExplosion = technique.explosionPower();
        blast.refreshDimensions();
        return blast;
    }

    public void setColor(int rgb) {
        entityData.set(COLOR, rgb);
    }

    /** A ki blast of the combat engine (CX-19): it stuns, pushes and builds the special meter when it lands. */
    public void setCombat(int hitstun, double knockback, boolean heavy) {
        combatStun = hitstun;
        combatKnock = knockback;
        combatHeavy = heavy;
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
        entityData.define(SPIRIT, false);
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

        if ((flags & Technique.PLACED) != 0) {                        // a mine: waits where it was put, then goes off
            setDeltaMovement(Vec3.ZERO);
            if (!level.isClientSide && (age >= lifeTicks - 1 || !level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.0),
                    e -> e != getOwner() && e.isAlive() && !e.isSpectator()).isEmpty())) {
                detonate();
            }
            return;
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            onHit(hit);
            if (isRemoved()) return;
        }

        Vec3 motion = getDeltaMovement();
        if (!level.isClientSide && (flags & Technique.GUIDED) != 0 && getOwner() instanceof LivingEntity caster && caster.isAlive()) {
            Vec3 aim = caster.getEyePosition().add(caster.getLookAngle().scale(48));   // follows the thrower's crosshair
            motion = motion.lerp(aim.subtract(position()).normalize().scale(motion.length()), 0.18);
            setDeltaMovement(motion);
        }
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
        if (spiritBomb) gather(caster);
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

    public boolean isSpiritBomb() {
        return entityData.get(SPIRIT);
    }

    /** Energy the Spirit Bomb has gathered (ki points). */
    public double gathered() {
        return energy;
    }

    /** The Spirit Bomb spares its thrower and everyone who lent it energy. */
    public boolean spares(Entity e) {
        return spiritBomb && e != null && (e == getOwner() || contributors.contains(e.getUUID()));
    }

    /** Throws a gathering Spirit Bomb at once (its thrower cast it again). */
    public void launchNow() {
        if (holdTicks > 1) holdTicks = 1;
    }

    /** A player casts the Spirit Bomb again while theirs is still gathering: it is thrown. Returns whether there was one. */
    public static boolean releaseSpiritBomb(net.minecraft.server.level.ServerPlayer player) {
        for (KiBlastEntity b : player.level().getEntitiesOfClass(KiBlastEntity.class, player.getBoundingBox().inflate(40),
                b -> b.spiritBomb && b.isHovering() && b.getOwner() == player)) {
            b.launchNow();
            return true;
        }
        return false;
    }

    /**
     * One tick of gathering: the thrower's ki, every player within 48 blocks who holds Charge (they lend 8% of their ki a
     * second and are spared by the blast), and a trickle from the living things around. Streams of light run into the
     * ball from each giver; it grows, and so do its damage and its blast.
     */
    private void gather(LivingEntity caster) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        com.dbzenith.data.PlayerData cd = caster instanceof net.minecraft.world.entity.player.Player p ? com.dbzenith.data.ModCapabilities.get(p).orElse(null) : null;
        double maxKi = cd == null ? 100 : Math.max(1, cd.getDerived().maxKi());
        Vec3 ball = getBoundingBox().getCenter();
        if (!called) {                                                       // the call goes out once
            called = true;
            for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
                if (p != caster && p.distanceToSqr(caster) < 48 * 48) {
                    p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.spirit_bomb_call", caster.getDisplayName())
                            .withStyle(net.minecraft.ChatFormatting.AQUA));
                }
            }
        }
        if (cd != null && cd.getKi() > maxKi * 0.0025) {                    // 5% of your ki a second: a full bar over the twenty seconds
            cd.setKi(cd.getKi() - maxKi * 0.0025);
            energy += maxKi * 0.0025;
        }
        if (age % 5 == 0) {
            for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
                if (p == caster || p.isSpectator() || p.distanceToSqr(caster) > 48 * 48) continue;
                com.dbzenith.data.PlayerData pd = com.dbzenith.data.ModCapabilities.get(p).orElse(null);
                if (pd == null || !pd.isCharging()) continue;
                double give = pd.getDerived().maxKi() * 0.02;
                if (pd.getKi() < give) continue;
                pd.setKi(pd.getKi() - give);
                energy += give;
                contributors.add(p.getUUID());
                stream(sl, p.getEyePosition(), ball);
            }
        }
        if (age % 10 == 0) {
            List<LivingEntity> life = level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(24),
                    e -> !(e instanceof net.minecraft.world.entity.player.Player) && !(e instanceof net.minecraft.world.entity.monster.Enemy) && e.isAlive());
            energy += Math.min(20, life.size()) * maxKi * 0.002;
            for (int i = 0; i < Math.min(4, life.size()); i++) stream(sl, life.get(i).position().add(0, life.get(i).getBbHeight() * 0.6, 0), ball);
            sl.playSound(null, ball.x, ball.y, ball.z, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.8f + (float) Math.min(0.8, energy / maxKi * 0.2));
        }
        double share = energy / maxKi;
        float size = (float) Math.min(12, baseSize * (1 + Math.sqrt(share) * 1.3));
        if (Math.abs(size - getSize()) > 0.05f) {
            entityData.set(SIZE, size);
            refreshDimensions();
        }
        damage = baseDamage * Math.min(12, 1 + share * 3);
        explosionPower = (float) (baseExplosion * Math.min(2.2, 1 + share * 0.6));
        caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false));
    }

    /** A thin stream of light from a giver into the ball. */
    private void stream(net.minecraft.server.level.ServerLevel sl, Vec3 from, Vec3 to) {
        for (int k = 0; k < 6; k++) {
            Vec3 p = from.lerp(to, (k + (age % 5) / 5.0) / 6.0);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != getOwner() && !hitIds.contains(target.getId()) && !spares(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        com.dbzenith.combat.PvpRules.actor(getOwner());
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
        com.dbzenith.combat.engine.CombatEngine.outcomeImpact = -1;
        com.dbzenith.combat.engine.CombatEngine.outcomeDealt = 0;
        com.dbzenith.combat.engine.CombatEngine.outcomeGuarded = false;
        boolean struck = target.hurt(ModDamageTypes.kiBlast(level(), this, getOwner()), (float) damage);
        if (combatStun >= 0 && target instanceof LivingEntity living && getOwner() instanceof LivingEntity owner) {
            com.dbzenith.combat.engine.CombatEngine.kiHit(owner, living, struck, combatStun, combatKnock, combatHeavy);
        }
        KiTraits.onHit(this, getOwner(), kiType, flags, target, damage, getDeltaMovement(), null);
        if (explosionPower <= 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.dbzenith.network.ImpactPacket.at(target.getBoundingBox().getCenter(), getDeltaMovement().normalize(),
                    com.dbzenith.network.ImpactPacket.KI_HIT, Math.max(0.5f, getSize()), getColor(), getOwner() == null ? -1 : getOwner().getId()).send(sl);
        }
        if (pierceLeft-- <= 0) {
            splitApart(getDeltaMovement().scale(-1));
            impact();
        }
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
        if (level().isClientSide) return;
        if (bouncesLeft > 0) {                                        // ricochet off the face it struck
            bouncesLeft--;
            Vec3 n = Vec3.atLowerCornerOf(result.getDirection().getNormal());
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))));
            hitIds.clear();
            level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f);
            return;
        }
        splitApart(Vec3.atLowerCornerOf(result.getDirection().getNormal()));
        impact();
    }

    /** A mine goes off: everything near takes the blast. */
    private void detonate() {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5),
                e -> e != getOwner() && e.isAlive() && !e.isSpectator())) {
            e.invulnerableTime = 0;
            e.hurt(ModDamageTypes.kiBlast(level(), this, getOwner()), (float) damage);
            KiTraits.onHit(this, getOwner(), kiType, flags, e, damage, e.position().subtract(position()), null);
        }
        if (explosionPower <= 0) explosionPower = 1.2f;
        impact();
    }

    /** Split: three smaller blasts fan out away from what this one struck. */
    private void splitApart(Vec3 away) {
        if ((flags & Technique.SPLIT) == 0 || split || !(getOwner() instanceof LivingEntity owner)) return;
        split = true;
        Vec3 base = away.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : away.normalize();
        Vec3 side = base.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        for (int i = -1; i <= 1; i++) {
            KiBlastEntity child = new KiBlastEntity(ModEntities.KI_BLAST.get(), level());
            child.setOwner(owner);
            child.damage = damage * 0.4;
            child.lifeTicks = 25;
            child.kiType = kiType;
            child.flags = flags & ~(Technique.SPLIT | Technique.PLACED);
            child.split = true;
            child.entityData.set(SIZE, Math.max(0.25f, getSize() * 0.6f));
            child.entityData.set(COLOR, getColor());
            child.entityData.set(STYLE, entityData.get(STYLE));
            child.refreshDimensions();
            child.hitIds.addAll(hitIds);
            child.setPos(getX(), getY(), getZ());
            child.setDeltaMovement(base.add(side.scale(i * 0.7)).add(0, 0.2, 0).normalize().scale(Math.max(0.8, getDeltaMovement().length() * 0.8)));
            level().addFreshEntity(child);
        }
    }

    private void impact() {
        com.dbzenith.combat.PvpRules.actor(getOwner());
        if (spiritBomb) {                                                    // everything where it lands takes it, but its givers
            double reach = getSize() * 1.5 + 3;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
                    e -> e.isAlive() && !e.isSpectator() && !spares(e) && !hitIds.contains(e.getId()))) {
                double fall = 1 - Math.min(0.7, e.distanceTo(this) / (reach * 2));
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.kiBlast(level(), this, getOwner()), (float) (damage * fall));
            }
        }
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
        tag.putBoolean("spiritBomb", spiritBomb);
        tag.putDouble("energy", energy);
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
        tag.putString("kiType", kiType.name());
        tag.putInt("flags", flags);
        tag.putInt("bounces", bouncesLeft);
        tag.putBoolean("split", split);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getDouble("damage");
        spiritBomb = tag.getBoolean("spiritBomb");
        entityData.set(SPIRIT, spiritBomb);
        energy = tag.getDouble("energy");
        baseDamage = damage;
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
        try {
            kiType = Technique.KiType.valueOf(tag.getString("kiType"));
        } catch (IllegalArgumentException e) {
            kiType = Technique.KiType.PURE;
        }
        flags = tag.getInt("flags");
        bouncesLeft = tag.getInt("bounces");
        split = tag.getBoolean("split");
    }
}
