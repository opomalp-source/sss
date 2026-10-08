package com.dbzenith.skill;

import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A sustained ki beam. Anchored to its caster every tick (follows their aim), extends at {@code speed}
 * blocks/tick until it hits a block, and damages every living thing along it every
 * {@code combat.beamDamageIntervalTicks}. The entity sits at the beam origin; length, width and color are synced.
 */
public class KiBeamEntity extends Entity {
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.INT);

    public static final float MAX_RANGE = 48f;

    private double damagePerPulse;
    private Technique.KiType kiType = Technique.KiType.PURE;
    private int flags;
    /** Charged beams gather for this long before they fire. */
    private int chargeTicks;
    private final java.util.Set<Integer> struck = new java.util.HashSet<>();
    private float extendSpeed = 3f;
    private int lifeTicks = 30;
    private float explosionPower;
    /** Tears the land up where it strikes (CX-20). */
    private boolean destructive = true;
    private boolean hitBlock;
    BeamStruggle struggle;          // locked with another beam (server only)

    public KiBeamEntity(EntityType<? extends KiBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static KiBeamEntity create(Level level, LivingEntity owner, Technique technique, double totalDamage) {
        KiBeamEntity beam = new KiBeamEntity(ModEntities.KI_BEAM.get(), level);
        beam.entityData.set(OWNER, owner.getId());
        beam.entityData.set(WIDTH, technique.size());
        beam.entityData.set(COLOR, technique.color());
        beam.extendSpeed = technique.speed();
        beam.lifeTicks = technique.lifeTicks();
        beam.explosionPower = technique.explosionPower();
        beam.destructive = technique.destructive();
        beam.kiType = technique.kiType();
        beam.flags = technique.flags();
        beam.chargeTicks = technique.has(Technique.CHARGED) ? 20 : 0;
        int pulses = Math.max(1, (technique.lifeTicks() - beam.chargeTicks) / DBZConfig.SERVER.beamDamageIntervalTicks.get());
        beam.damagePerPulse = totalDamage / pulses;
        beam.anchorTo(owner);
        return beam;
    }

    public float getLength() {
        return entityData.get(LENGTH);
    }

    public float getWidth() {
        return entityData.get(WIDTH);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    public Entity getOwner() {
        return level().getEntity(entityData.get(OWNER));
    }

    /** Unit vector the beam points along (from synced rotation). */
    public Vec3 direction() {
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(LENGTH, 0f);
        entityData.define(WIDTH, 0.6f);
        entityData.define(COLOR, 0xFFFFFF);
        entityData.define(OWNER, -1);
    }

    private void anchorTo(Entity owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 origin = owner.getEyePosition().add(look.scale(0.7)).subtract(0, 0.25, 0);
        moveTo(origin.x, origin.y, origin.z, owner.getYRot(), owner.getXRot());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive() || tickCount > lifeTicks) {
            finish();
            return;
        }
        anchorTo(owner);
        if (struggle != null) {
            lifeTicks = Math.max(lifeTicks, tickCount + 2);  // the struggle decides when this ends
            if (struggle.a == this) struggle.tick();
            return;                                          // locked: no extending, no damage
        }

        if (tickCount <= chargeTicks) {                                  // gathering: ki streams into the hands
            if (level() instanceof net.minecraft.server.level.ServerLevel sl && tickCount % 2 == 0) {
                int c = getColor();
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1.2f);
                Vec3 at = position();
                for (int i = 0; i < 4; i++) {
                    Vec3 from = at.add((random.nextDouble() - 0.5) * 3, (random.nextDouble() - 0.5) * 3, (random.nextDouble() - 0.5) * 3);
                    Vec3 v = at.subtract(from).scale(0.25);
                    sl.sendParticles(dust, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
                }
            }
            return;
        }
        Vec3 start = position();
        Vec3 dir = direction();
        float length = Math.min(MAX_RANGE, getLength() + extendSpeed);
        BlockHitResult block = level().clip(new ClipContext(start, start.add(dir.scale(length)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        hitBlock = block.getType() == HitResult.Type.BLOCK;
        if (hitBlock) length = (float) block.getLocation().distanceTo(start);
        entityData.set(LENGTH, length);
        BeamStruggle.lookFor(this);
        if (struggle != null) return;

        if (hitBlock && destructive && tickCount % 6 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) {   // it carves into what it strikes (CX-20)
            com.dbzenith.combat.Destruction.ki(sl, block.getLocation(), Vec3.atLowerCornerOf(block.getDirection().getNormal()),
                    0.45 + getWidth() * 0.5, owner != null ? owner : this);
        }
        if (tickCount % DBZConfig.SERVER.beamDamageIntervalTicks.get() == 0) pulse(owner, start, start.add(dir.scale(length)));
    }

    private void pulse(Entity owner, Vec3 start, Vec3 end) {
        com.dbzenith.combat.PvpRules.actor(owner);
        double r = getWidth() / 2.0;
        AABB sweep = new AABB(start, end).inflate(r + 0.5);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, sweep, e -> e != owner && e.isAlive() && !e.isSpectator())) {
            AABB box = target.getBoundingBox().inflate(r);
            Optional<Vec3> hit = box.clip(start, end);
            if (hit.isPresent() || box.contains(start)) {
                target.invulnerableTime = 0;
                target.hurt(ModDamageTypes.kiBlast(level(), this, owner), (float) damagePerPulse);
                KiTraits.onHit(this, owner, kiType, flags, target, damagePerPulse, end.subtract(start), struck);
                if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    com.dbzenith.network.ImpactPacket.at(hit.orElse(target.getBoundingBox().getCenter()), end.subtract(start).normalize(),
                            com.dbzenith.network.ImpactPacket.KI_HIT, getWidth(), getColor(), owner == null ? -1 : owner.getId()).send(sl);
                }
            }
        }
    }

    private void finish() {
        if (struggle != null && !struggle.over) {
            struggle.abort();                                 // ends both beams
            return;
        }
        if (explosionPower > 0 && hitBlock && getOwner() != null) {
            Vec3 end = position().add(direction().scale(getLength()));
            Level.ExplosionInteraction interaction = DBZConfig.SERVER.kiBlastsBreakBlocks.get()
                    ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
            level().explode(this, end.x, end.y, end.z, explosionPower, interaction);
            if (destructive && level() instanceof net.minecraft.server.level.ServerLevel sl2) {     // the crater (CX-20)
                com.dbzenith.combat.Destruction.ki(sl2, end, direction().scale(-1), 0.6 + explosionPower * 0.55, getOwner());
            }
            if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.dbzenith.network.ImpactPacket.at(end, direction(), com.dbzenith.network.ImpactPacket.EXPLOSION, explosionPower, getColor(), getOwner().getId()).send(sl);
            }
        }
        discard();
    }

    /** Struggling: point at the clash and reach exactly to it. */
    void holdTo(Vec3 clash) {
        Vec3 d = clash.subtract(position());
        double len = d.length();
        if (len < 1e-3) return;
        float yRot = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float xRot = (float) (Math.toDegrees(-Math.asin(d.y / len)));
        moveTo(getX(), getY(), getZ(), yRot, xRot);
        entityData.set(LENGTH, (float) len);
    }

    double damagePerPulse() {
        return damagePerPulse;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        return distanceSq < 128 * 128;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Beams last a second or two; they are not worth persisting.
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
